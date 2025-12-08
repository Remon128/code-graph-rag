#!/usr/bin/env python3
"""
scripts/generate_embeddings_classname_assumption.py

Assumptions:
 - Each method belongs to a class whose file is ClassName.java
 - The qualified_name stored in Memgraph contains the fully qualified
   path where the last token is the method name and the previous token
   is the class name (common with Java).
 - embed_code(...) and store_embedding(...) exist in your repo (used by GraphUpdater).

What it does:
 - queries Memgraph for Method nodes (id, qualified_name, start_line, end_line)
 - for each method, extracts class name, finds ClassName.java in repo via rglob
 - extracts source lines [start_line, end_line]
 - runs embed_code(source) and store_embedding(node_id, embedding, qualified_name)
 - logs times, writes a JSONL report with per-method results
"""

import argparse
import json
import logging
import sys
import time
from pathlib import Path
from typing import Optional

# Adjust imports to match your project's package layout
try:
    from codebase_rag.graph_updater import MemgraphIngestor
    from codebase_rag.embedder import embed_code
    from codebase_rag.vector_store import store_embedding
except Exception as e:
    # Try alternative import paths if needed
    raise RuntimeError(
        "Failed to import project-specific utilities. Ensure this script runs within your project's venv and package layout.\n"
        f"Original error: {e}"
    ) from e

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("embed-gen")

def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--repo-root", required=True, help="Path to repository root")
    p.add_argument("--memgraph-host", default="localhost", help="Memgraph host")
    p.add_argument("--memgraph-port", type=int, default=7687, help="Memgraph port")
    p.add_argument("--batch-size", type=int, default=500, help="Memgraph batch size")
    p.add_argument(
        "--output",
        default="embedded_report.jsonl",
        help="JSONL output file logging each embedded method and timings",
    )
    p.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Optional: limit number of methods to process (0=no limit)",
    )
    return p.parse_args()

def extract_class_and_method_from_qn(qualified_name: str) -> tuple[Optional[str], Optional[str]]:
    """
    Examples of qualified_name values we've seen:
      - data.SamaRbsServices.SamaRbsServices.partyBlockRelAdd(PartyBlockRelAddRqType)
      - RbsEAR (1).zip.src.RbsWeb.war.src.WEB-INF.classes.com.finmeccanica.services.rbs.sama.SamaRbsServices.SamaRbsServices.partyBlockRelAdd(PartyBlockRelAddRqType)

    Approach:
      - Remove parentheses and params (anything from '(')
      - Split by '.' and take last token as method_name, previous as class_name
    """
    if not qualified_name:
        return None, None
    q = qualified_name.split("(")[0]  # remove method signature part
    parts = q.split(".")
    if len(parts) < 2:
        return None, None
    method_name = parts[-1]
    class_name = parts[-2]
    return class_name, method_name

def find_class_file(repo_root: Path, class_name: str) -> Optional[Path]:
    """
    Search for ClassName.java under repo_root. Use rglob which is fast enough
    for a few thousand files. Return the first match.
    """
    # Quick guard
    if not class_name:
        return None

    target_filename = f"{class_name}.java"
    # First try common fast locations (if any known)
    common_candidates = [
        repo_root,
        repo_root / "RbsWeb.war.src",
        repo_root / "RbsWeb.war.src" / "WEB-INF" / "classes",
        repo_root / "src",
        repo_root / "src" / "main" / "java",
    ]
    seen = set()
    for base in filter(lambda p: p.exists(), common_candidates):
        candidate = base / target_filename
        if candidate.exists():
            return candidate
    # Fallback: rglob (recursive)
    logger.debug(f"Searching for {target_filename} via rglob under {repo_root}")
    try:
        for p in repo_root.rglob(target_filename):
            # return first match
            return p
    except Exception as e:
        logger.warning(f"rglob search failed for {target_filename}: {e}")
        return None
    return None

def extract_lines_from_file(file_path: Path, start: int, end: int) -> str:
    try:
        text = file_path.read_text(encoding="utf-8", errors="ignore")
        lines = text.splitlines()
        if start < 1:
            start = 1
        if end > len(lines):
            end = len(lines)
        # start/end are 1-based in DB -> convert to 0-based slice
        snippet = "\n".join(lines[start - 1 : end])
        return snippet
    except Exception as e:
        logger.exception(f"Failed to read {file_path}: {e}")
        return ""

def main():
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    logger.info(f"Repository root: {repo_root}")
    if not repo_root.exists():
        logger.error("Repository root does not exist")
        sys.exit(1)

    out_file = Path(args.output)
    logger.info(f"Output JSONL will be written to: {out_file}")

    processed = 0
    stored_count = 0
    skipped_count = 0

    with MemgraphIngestor(host=args.memgraph_host, port=args.memgraph_port, batch_size=args.batch_size) as ingestor:
        # Get all methods from the graph with location info
        rows = ingestor.fetch_all(
            """
            MATCH (m:Method)
            RETURN id(m) AS node_id, m.qualified_name AS qualified_name,
                   m.start_line AS start_line, m.end_line AS end_line
            ORDER BY id(m)
            """
        )

        logger.info(f"Found {len(rows)} Method nodes in Memgraph")

        with out_file.open("w", encoding="utf-8") as fout:
            for r in rows:
                if args.limit and processed >= args.limit:
                    break

                node_id = r.get("node_id")
                qualified_name = r.get("qualified_name")
                start_line = r.get("start_line")
                end_line = r.get("end_line")

                processed += 1

                # Basic validation
                if not qualified_name or not start_line or not end_line:
                    skipped_count += 1
                    logger.debug(f"Skipping node {node_id} (missing location or qn)")
                    fout.write(json.dumps({
                        "node_id": node_id,
                        "qualified_name": qualified_name,
                        "status": "skipped",
                        "reason": "missing_qualified_name_or_location"
                    }) + "\n")
                    continue

                class_name, method_name = extract_class_and_method_from_qn(qualified_name)
                if not class_name:
                    skipped_count += 1
                    logger.debug(f"Could not derive class for {qualified_name} (node {node_id})")
                    fout.write(json.dumps({
                        "node_id": node_id,
                        "qualified_name": qualified_name,
                        "status": "skipped",
                        "reason": "could_not_derive_class_name"
                    }) + "\n")
                    continue

                # Find file assuming ClassName.java
                file_path = find_class_file(repo_root, class_name)
                if file_path is None:
                    skipped_count += 1
                    logger.debug(f"No file found for class {class_name} (node {node_id})")
                    fout.write(json.dumps({
                        "node_id": node_id,
                        "qualified_name": qualified_name,
                        "class_name": class_name,
                        "status": "skipped",
                        "reason": "file_not_found"
                    }) + "\n")
                    continue

                # Extract source snippet
                snippet = extract_lines_from_file(file_path, int(start_line), int(end_line))
                if not snippet.strip():
                    skipped_count += 1
                    logger.debug(f"Empty snippet for {qualified_name} at {file_path} lines {start_line}-{end_line}")
                    fout.write(json.dumps({
                        "node_id": node_id,
                        "qualified_name": qualified_name,
                        "class_name": class_name,
                        "file_path": str(file_path),
                        "start_line": start_line,
                        "end_line": end_line,
                        "status": "skipped",
                        "reason": "empty_snippet"
                    }) + "\n")
                    continue

                # Time the embedding step
                t0 = time.perf_counter()
                status = "ok"
                err = None
                try:
                    embedding = embed_code(snippet)
                    # store_embedding should persist into Qdrant (no-op if Qdrant missing according to your vector_store)
                    store_embedding(node_id, embedding, qualified_name)
                    stored_count += 1
                except Exception as e:
                    status = "error"
                    err = str(e)
                    logger.exception(f"Failed to embed/store for {qualified_name} (node {node_id}): {e}")
                t1 = time.perf_counter()
                elapsed_ms = (t1 - t0) * 1000.0

                log_entry = {
                    "node_id": node_id,
                    "qualified_name": qualified_name,
                    "class_name": class_name,
                    "method_name": method_name,
                    "file_path": str(file_path),
                    "start_line": start_line,
                    "end_line": end_line,
                    "time_ms": round(elapsed_ms, 3),
                    "status": status,
                    "error": err,
                }
                fout.write(json.dumps(log_entry) + "\n")

                if processed % 50 == 0:
                    logger.info(f"Processed {processed} methods, embedded {stored_count}, skipped {skipped_count}")

    logger.info(f"Done. Processed {processed}, stored embeddings for {stored_count}, skipped {skipped_count}")
    logger.info(f"Per-method log available at: {out_file}")

if __name__ == "__main__":
    main()
