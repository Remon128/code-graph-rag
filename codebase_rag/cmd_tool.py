import asyncio
import os
import sys
from pathlib import Path
from typing import Any

from loguru import logger

# ----------------------------------------
# Make sure we can import codebase_rag
# ----------------------------------------
current_dir = os.path.dirname(os.path.abspath(__file__))
repo_root = current_dir  # this script is in the repo root

# Add repo root so "codebase_rag" is importable
if repo_root not in sys.path:
    sys.path.insert(0, repo_root)

# Now we can import the package
from codebase_rag.config import settings
from codebase_rag.graph_updater import MemgraphIngestor
from codebase_rag.services.llm import CypherGenerator, create_rag_orchestrator
from codebase_rag.tools.code_retrieval import CodeRetriever, create_code_retrieval_tool
from codebase_rag.tools.codebase_query import create_query_tool
from codebase_rag.tools.directory_lister import (
    DirectoryLister,
    create_directory_lister_tool,
)
from codebase_rag.tools.document_analyzer import (
    DocumentAnalyzer,
    create_document_analyzer_tool,
)
from codebase_rag.tools.file_editor import FileEditor, create_file_editor_tool
from codebase_rag.tools.file_reader import FileReader, create_file_reader_tool
from codebase_rag.tools.file_writer import FileWriter, create_file_writer_tool
from codebase_rag.tools.semantic_search import (
    create_get_function_source_tool,
    create_semantic_search_tool,
)
from codebase_rag.tools.shell_command import ShellCommander, create_shell_command_tool

# 👉 import the Typer command function
from codebase_rag.main import start as cli_start

# Globals for the agent
rag_agent: Any | None = None
ingestor: MemgraphIngestor | None = None
project_root: Path | None = None


# ----------------------------------------
# Factory: build tools + agent (same as API)
# ----------------------------------------
def main_factory() -> tuple[Any, MemgraphIngestor, Path]:
    """Initialize services and create the RAG agent (without CLI)."""
    global rag_agent, ingestor, project_root

    logger.info("Initializing RAG agent for debug runner...")

    project_root = Path(settings.TARGET_REPO_PATH).resolve()
    logger.info(f"PROJECT_ROOT = {project_root}")

    # Shared Memgraph ingestor (tools will use it as a context manager)
    ingestor = MemgraphIngestor(
        host=settings.MEMGRAPH_HOST,
        port=settings.MEMGRAPH_PORT,
        batch_size=settings.MEMGRAPH_BATCH_SIZE,
    )

    logger.info("Memgraph ingestor created (connection handled by tools).")

    # Initialize tools
    cypher_generator = CypherGenerator()
    code_retriever = CodeRetriever(project_root=str(project_root), ingestor=ingestor)
    file_reader = FileReader(project_root=str(project_root))
    file_writer = FileWriter(project_root=str(project_root))
    file_editor = FileEditor(project_root=str(project_root))
    shell_commander = ShellCommander(
        project_root=str(project_root), timeout=settings.SHELL_COMMAND_TIMEOUT
    )
    directory_lister = DirectoryLister(project_root=str(project_root))
    document_analyzer = DocumentAnalyzer(project_root=str(project_root))

    # Create pydantic-ai tools
    query_tool = create_query_tool(ingestor, cypher_generator, None)
    code_tool = create_code_retrieval_tool(code_retriever)
    file_reader_tool = create_file_reader_tool(file_reader)
    file_writer_tool = create_file_writer_tool(file_writer)
    file_editor_tool = create_file_editor_tool(file_editor)
    shell_command_tool = create_shell_command_tool(shell_commander)
    directory_lister_tool = create_directory_lister_tool(directory_lister)
    document_analyzer_tool = create_document_analyzer_tool(document_analyzer)
    semantic_search_tool = create_semantic_search_tool()
    function_source_tool = create_get_function_source_tool()

    rag_agent = create_rag_orchestrator(
        tools=[
            query_tool,
            code_tool,
            file_reader_tool,
            file_writer_tool,
            file_editor_tool,
            shell_command_tool,
            directory_lister_tool,
            document_analyzer_tool,
            semantic_search_tool,
            function_source_tool,
        ]
    )

    return rag_agent, ingestor, project_root


def ensure_agent() -> None:
    """Initialize the agent once, lazily."""
    global rag_agent, ingestor, project_root
    if rag_agent is None:
        rag_agent, ingestor, project_root = main_factory()


# ----------------------------------------
# 1 RUN INDEXING + EMBEDDINGS (what CLI does)
# ----------------------------------------
def run_indexing() -> None:
    """
    Programmatic equivalent of:
        python -m codebase_rag.main start 
            --repo-path /home/aahafez/code-graph-rag/data2 
            --update-graph
    """
    repo_path = "/home/aahafez/code-graph-rag/data2"

    logger.info("===================================================")
    logger.info("Starting GRAPH UPDATE via codebase_rag.main.start()")
    logger.info(f"Repo path: {repo_path}")
    logger.info("===================================================")

    # This calls the same Typer command function that the CLI uses.
    # Put breakpoints in:
    #   - start()
    #   - GraphUpdater.__init__ / GraphUpdater.run
    #   - GraphUpdater._generate_semantic_embeddings
    cli_start(
        repo_path=repo_path,
        update_graph=True,
        clean=False,
        output=None,
        orchestrator=None,
        cypher=None,
        no_confirm=False,
        batch_size=None,
    )

    logger.info("===== GRAPH UPDATE FINISHED (returned from start) =====")


# ----------------------------------------
# 2 CHAT LOOP USING THE AGENT
# ----------------------------------------
async def chat_once(query: str) -> None:
    """Run a single query through the RAG agent and print the answer."""
    ensure_agent()
    # rag_agent.run is async – we must await it
    result = await rag_agent.run(query, message_history=[])
    print("\nAssistant:", result.output, "\n")


def chat_loop() -> None:
    """Simple blocking REPL that uses chat_once via asyncio."""
    while True:
        user_input = input("You: ").strip()
        if not user_input:
            print("Empty line -> exit.")
            break
        try:
            asyncio.run(chat_once(user_input))
        except KeyboardInterrupt:
            print("\nInterrupted, exiting chat loop.")
            break
        except Exception as e:
            print("Error in chat:", e)


# ----------------------------------------
# ENTRY POINT
# ----------------------------------------
def main() -> None:
    # STEP 1: run indexing + embedding generation first
    run_indexing()

    # STEP 2: then enter interactive chat, using the DB + embeddings you just built
    chat_loop()


if __name__ == "__main__":
    main()
