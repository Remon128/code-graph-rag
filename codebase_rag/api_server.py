"""
FastAPI backend for code-graph-rag web interface with proper size handling.
"""

import asyncio
import shutil
import subprocess
import uuid
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, UploadFile, File, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from loguru import logger
from pydantic import BaseModel

from .config import settings
from .graph_updater import MemgraphIngestor
from .services.llm import CypherGenerator, create_rag_orchestrator
from .tools.code_retrieval import CodeRetriever, create_code_retrieval_tool
from .tools.codebase_query import create_query_tool
from .tools.directory_lister import DirectoryLister, create_directory_lister_tool
from .tools.document_analyzer import DocumentAnalyzer, create_document_analyzer_tool
from .tools.file_editor import FileEditor, create_file_editor_tool
from .tools.file_reader import FileReader, create_file_reader_tool
from .tools.file_writer import FileWriter, create_file_writer_tool
from .tools.semantic_search import (
    create_get_function_source_tool,
    create_semantic_search_tool,
)
from .tools.shell_command import ShellCommander, create_shell_command_tool


# ============================
# Configuration
# ============================

# Maximum upload size in bytes (500MB)
MAX_UPLOAD_SIZE = 500 * 1024 * 1024  # 500MB

# Maximum number of files to process
MAX_FILES_TO_PROCESS = 50000  # Safety limit


# ============================
# Models
# ============================

class ChatRequest(BaseModel):
    message: str
    session_id: str | None = None


class ChatResponse(BaseModel):
    response: str
    session_id: str
    status: str = "success"


class SessionInfo(BaseModel):
    session_id: str
    message_count: int
    created_at: str


class UploadStatus(BaseModel):
    status: str
    message: str
    file_count: int | None = None
    estimated_time: str | None = None


# ============================
# Global State
# ============================

sessions: dict[str, dict[str, Any]] = {}
rag_agent: Any = None
ingestor: MemgraphIngestor | None = None
upload_in_progress = False

REPO_PATH = Path(settings.TARGET_REPO_PATH).resolve()


# ============================
# Lifespan
# ============================

@asynccontextmanager
async def lifespan(app: FastAPI):
    global rag_agent, ingestor

    logger.info("Initializing FastAPI server...")

    REPO_PATH.mkdir(parents=True, exist_ok=True)

    ingestor = MemgraphIngestor(
        host=settings.MEMGRAPH_HOST,
        port=settings.MEMGRAPH_PORT,
        batch_size=settings.MEMGRAPH_BATCH_SIZE,
    )

    cypher_generator = CypherGenerator()

    code_retriever = CodeRetriever(project_root=str(REPO_PATH), ingestor=ingestor)
    file_reader = FileReader(project_root=str(REPO_PATH))
    file_writer = FileWriter(project_root=str(REPO_PATH))
    file_editor = FileEditor(project_root=str(REPO_PATH))
    shell_commander = ShellCommander(
        project_root=str(REPO_PATH),
        timeout=settings.SHELL_COMMAND_TIMEOUT,
    )
    directory_lister = DirectoryLister(project_root=str(REPO_PATH))
    document_analyzer = DocumentAnalyzer(project_root=str(REPO_PATH))

    rag_agent = create_rag_orchestrator(
        tools=[
            create_query_tool(ingestor, cypher_generator, None),
            create_code_retrieval_tool(code_retriever),
            create_file_reader_tool(file_reader),
            create_file_writer_tool(file_writer),
            create_file_editor_tool(file_editor),
            create_shell_command_tool(shell_commander),
            create_directory_lister_tool(directory_lister),
            create_document_analyzer_tool(document_analyzer),
            create_semantic_search_tool(),
            create_get_function_source_tool(),
        ]
    )

    logger.info("RAG Agent initialized successfully")
    yield
    logger.info("FastAPI shutdown complete")


# ============================
# App
# ============================

app = FastAPI(
    title="Code Graph RAG API",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ============================
# Exception Handlers
# ============================

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"Global exception handler caught: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"detail": str(exc), "status": "error"}
    )


# ============================
# Middleware for Size Limits
# ============================

@app.middleware("http")
async def limit_upload_size(request: Request, call_next):
    """Middleware to enforce upload size limits."""
    if request.url.path == "/upload-repo" and request.method == "POST":
        content_length = request.headers.get("content-length")
        if content_length and int(content_length) > MAX_UPLOAD_SIZE:
            return JSONResponse(
                status_code=413,
                content={
                    "detail": f"File too large. Maximum size is {MAX_UPLOAD_SIZE / (1024*1024):.0f}MB",
                    "status": "error"
                }
            )
    
    response = await call_next(request)
    return response


# ============================
# Core Endpoints
# ============================

@app.get("/")
async def root():
    return {"status": "running", "upload_in_progress": upload_in_progress}


@app.get("/health")
async def health():
    return {
        "status": "healthy",
        "memgraph_connected": ingestor is not None,
        "agent_initialized": rag_agent is not None,
        "upload_in_progress": upload_in_progress,
    }


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    if not rag_agent or not ingestor:
        raise HTTPException(status_code=500, detail="Backend not initialized")
    
    if upload_in_progress:
        raise HTTPException(
            status_code=503, 
            detail="System is currently processing a repository upload. Please wait."
        )

    session_id = request.session_id or str(uuid.uuid4())
    session = sessions.setdefault(
        session_id,
        {"message_history": [], "created_at": asyncio.get_event_loop().time()},
    )

    response = await rag_agent.run(
        request.message,
        message_history=session["message_history"],
    )

    session["message_history"].extend(response.new_messages())

    return ChatResponse(
        response=response.output,
        session_id=session_id,
    )


# ============================
# Helper Functions
# ============================

def count_files_in_directory(directory: Path, extensions: set[str] | None = None) -> int:
    """Count files in directory, optionally filtering by extension."""
    count = 0
    try:
        for item in directory.rglob("*"):
            if item.is_file():
                if extensions is None or item.suffix.lower() in extensions:
                    count += 1
                    if count > MAX_FILES_TO_PROCESS:
                        return count
    except Exception as e:
        logger.warning(f"Error counting files: {e}")
    return count


def estimate_processing_time(file_count: int) -> str:
    """Estimate processing time based on file count."""
    # Rough estimate: 10 files per second
    seconds = file_count / 10
    
    if seconds < 60:
        return f"{int(seconds)} seconds"
    elif seconds < 3600:
        return f"{int(seconds / 60)} minutes"
    else:
        hours = int(seconds / 3600)
        minutes = int((seconds % 3600) / 60)
        return f"{hours}h {minutes}m"


# ============================
# Upload & Reindex (ZIP)
# ============================

@app.post("/upload-repo", response_model=UploadStatus)
async def upload_repo(file: UploadFile = File(...)):
    global upload_in_progress
    
    if upload_in_progress:
        raise HTTPException(
            status_code=409,
            detail="Another upload is already in progress. Please wait."
        )
    
    if not file.filename or not file.filename.lower().endswith(".zip"):
        raise HTTPException(
            status_code=400, 
            detail="Only .zip files are supported"
        )

    upload_in_progress = True
    temp_zip = None
    
    try:
        logger.info(f"Starting upload of repository: {file.filename}")
        
        # Clear repo contents without deleting mount point
        logger.info("Clearing existing repository contents...")
        for item in REPO_PATH.iterdir():
            try:
                if item.is_dir():
                    shutil.rmtree(item)
                else:
                    item.unlink()
            except Exception as e:
                logger.warning(f"Could not remove {item}: {e}")
        
        # Save uploaded file with size check
        temp_zip = REPO_PATH / f"temp_{uuid.uuid4().hex[:8]}_{file.filename}"
        
        logger.info("Saving uploaded file...")
        chunk_size = 1024 * 1024  # 1MB chunks
        total_size = 0
        
        with open(temp_zip, "wb") as f:
            while chunk := await file.read(chunk_size):
                total_size += len(chunk)
                if total_size > MAX_UPLOAD_SIZE:
                    raise HTTPException(
                        status_code=413,
                        detail=f"File too large. Maximum size is {MAX_UPLOAD_SIZE / (1024*1024):.0f}MB"
                    )
                f.write(chunk)
        
        logger.info(f"File saved ({total_size / (1024*1024):.1f}MB), unpacking...")
        
        # Unpack archive
        try:
            shutil.unpack_archive(temp_zip, REPO_PATH)
        except Exception as e:
            logger.error(f"Failed to unpack archive: {e}")
            raise HTTPException(
                status_code=400,
                detail=f"Failed to unpack ZIP file: {str(e)}"
            )
        finally:
            # Clean up temp zip file
            if temp_zip and temp_zip.exists():
                temp_zip.unlink()
                temp_zip = None
        
        # Count files for estimation
        logger.info("Analyzing repository contents...")
        code_extensions = {'.java', '.py', '.js', '.ts', '.cpp', '.c', '.h', '.go', '.rs'}
        file_count = count_files_in_directory(REPO_PATH, code_extensions)
        
        if file_count > MAX_FILES_TO_PROCESS:
            logger.warning(f"Repository has {file_count} files (exceeds recommended limit)")
        
        estimated_time = estimate_processing_time(file_count)
        logger.info(f"Found {file_count} code files, estimated processing time: {estimated_time}")
        
        # Start indexing in background with live logging
        logger.info("Starting background indexing process...")
        process = subprocess.Popen(
            [
                "python",
                "-m",
                "codebase_rag.main",
                "start",
                "--repo-path",
                str(REPO_PATH),
                "--update-graph",
                "--clean",
                "--batch-size",
                str(settings.MEMGRAPH_BATCH_SIZE),
                "--orchestrator",
                f"{settings.ORCHESTRATOR_PROVIDER}:{settings.ORCHESTRATOR_MODEL}",
                "--cypher",
                f"{settings.CYPHER_PROVIDER}:{settings.CYPHER_MODEL}",
                "--no-confirm",
            ],
            stdout=None,  # Inherit stdout from parent (shows in Docker logs)
            stderr=None,  # Inherit stderr from parent (shows in Docker logs)
        )
        
        logger.success(f"Repository uploaded successfully. Indexing started in background (PID: {process.pid})")
        
        return UploadStatus(
            status="success",
            message=f"Repository uploaded and indexing started. Found {file_count} code files.",
            file_count=file_count,
            estimated_time=estimated_time
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Upload failed: {e}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Upload failed: {str(e)}"
        )
    finally:
        # Clean up temp file if it still exists
        if temp_zip and temp_zip.exists():
            try:
                temp_zip.unlink()
            except Exception as e:
                logger.warning(f"Could not clean up temp file: {e}")
        
        upload_in_progress = False


@app.get("/upload-status")
async def upload_status():
    """Check if an upload/indexing is in progress."""
    return {
        "upload_in_progress": upload_in_progress,
        "status": "indexing" if upload_in_progress else "idle"
    }


# ============================
# Sessions
# ============================

@app.get("/session/{session_id}", response_model=SessionInfo)
async def get_session(session_id: str):
    session = sessions.get(session_id)
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    return SessionInfo(
        session_id=session_id,
        message_count=len(session["message_history"]),
        created_at=str(session["created_at"]),
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "codebase_rag.api_server:app", 
        host="0.0.0.0", 
        port=8000,
        timeout_keep_alive=120,
        limit_max_requests=1000,
    )