"""
FastAPI backend for code-graph-rag with proper indexing status tracking.
"""
from fastapi import FastAPI, HTTPException, UploadFile, File, Request, BackgroundTasks
import asyncio
import shutil
import subprocess
import uuid
import threading
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

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

MAX_UPLOAD_SIZE = 500 * 1024 * 1024  # 500MB
MAX_FILES_TO_PROCESS = 50000


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
upload_lock = threading.Lock()
upload_in_progress = False
indexing_process = None

REPO_PATH = Path(settings.TARGET_REPO_PATH).resolve()


# ============================
# Background Indexing Monitor
# ============================

async def monitor_indexing_process():
    """Monitor the indexing subprocess and update status."""
    global upload_in_progress, indexing_process
    
    if indexing_process is None:
        return
    
    try:
        # Wait for process to complete
        await asyncio.to_thread(indexing_process.wait)
        
        # Check return code
        if indexing_process.returncode == 0:
            logger.success("Indexing process completed successfully")
        else:
            logger.error(f"Indexing process failed with code {indexing_process.returncode}")
    except Exception as e:
        logger.error(f"Error monitoring indexing process: {e}")
    finally:
        upload_in_progress = False
        indexing_process = None
        logger.info("Indexing status reset - chat now available")


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
# Middleware
# ============================

@app.middleware("http")
async def limit_upload_size(request: Request, call_next):
    """Middleware to enforce upload size limits."""
    if request.url.path == "/upload-repo" and request.method == "POST":
        logger.info(f"Upload request received. Current upload_in_progress: {upload_in_progress}")
        content_length = request.headers.get("content-length")
        if content_length:
            logger.info(f"Upload size: {int(content_length) / (1024*1024):.1f}MB")
            if int(content_length) > MAX_UPLOAD_SIZE:
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
            detail="System is currently processing a repository upload. Please wait until indexing completes."
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
    seconds = file_count / 10
    
    if seconds < 60:
        return f"{int(seconds)} seconds"
    elif seconds < 3600:
        return f"{int(seconds / 60)} minutes"
    else:
        hours = int(seconds / 3600)
        minutes = int((seconds % 3600) / 60)
        return f"{hours}h {minutes}m"


def process_uploaded_repo(temp_zip: Path):
    """
    Runs heavy repository processing OUTSIDE the request lifecycle.
    This prevents Streamlit timeouts for large repositories.
    """
    global upload_in_progress, indexing_process

    try:
        logger.info("Unpacking uploaded repository...")
        shutil.unpack_archive(temp_zip, REPO_PATH)
        temp_zip.unlink(missing_ok=True)

        logger.info("Counting code files...")
        code_extensions = {'.java', '.py', '.js', '.ts', '.cpp', '.c', '.h', '.go', '.rs'}
        file_count = count_files_in_directory(REPO_PATH, code_extensions)

        estimated_time = estimate_processing_time(file_count)
        logger.info(f"Found {file_count} files, estimated time {estimated_time}")

        logger.info("Starting indexing subprocess...")
        indexing_process = subprocess.Popen(
            [
                "python", "-m", "codebase_rag.main", "start",
                "--repo-path", str(REPO_PATH),
                "--update-graph",
                "--clean",
                "--batch-size", str(settings.MEMGRAPH_BATCH_SIZE),
                "--orchestrator",
                f"{settings.ORCHESTRATOR_PROVIDER}:{settings.ORCHESTRATOR_MODEL}",
                "--cypher",
                f"{settings.CYPHER_PROVIDER}:{settings.CYPHER_MODEL}",
                "--no-confirm",
            ],
            stdout=None,
            stderr=None,
        )

        asyncio.run(monitor_indexing_process())

    except Exception as e:
        logger.error(f"Background indexing failed: {e}", exc_info=True)
        upload_in_progress = False


# ============================
# Upload & Reindex
# ============================

@app.post("/upload-repo", response_model=UploadStatus)
async def upload_repo(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...)
):
    global upload_in_progress

    with upload_lock:
        if upload_in_progress:
            raise HTTPException(
                status_code=409,
                detail="Another upload/indexing process is already in progress."
            )
        upload_in_progress = True

    temp_zip: Path | None = None

    try:
        # Validate file type
        if not file.filename or not file.filename.endswith(".zip"):
            raise HTTPException(
                status_code=400,
                detail="Only .zip files are supported."
            )

        logger.info(f"Upload request received for: {file.filename}")

        # Clear existing repo contents
        logger.info("Clearing existing repository contents...")
        for item in REPO_PATH.iterdir():
            try:
                if item.is_dir():
                    shutil.rmtree(item)
                else:
                    item.unlink()
            except Exception as e:
                logger.warning(f"Could not remove {item}: {e}")

        # Save uploaded file to disk (streaming, non-blocking)
        temp_zip = REPO_PATH / f"temp_{uuid.uuid4().hex[:8]}_{file.filename}"

        total_size = 0
        chunk_size = 1024 * 1024  # 1MB

        with open(temp_zip, "wb") as f:
            while chunk := await file.read(chunk_size):
                total_size += len(chunk)
                if total_size > MAX_UPLOAD_SIZE:
                    raise HTTPException(
                        status_code=413,
                        detail=f"File too large. Maximum allowed size is "
                               f"{MAX_UPLOAD_SIZE / (1024*1024):.0f}MB."
                    )
                f.write(chunk)

        logger.info(
            f"Upload complete ({total_size / (1024*1024):.1f}MB). "
            "Starting background processing..."
        )

        # Run heavy unpacking + indexing OUTSIDE request lifecycle
        background_tasks.add_task(process_uploaded_repo, temp_zip)

        return UploadStatus(
            status="success",
            message="Repository uploaded successfully. Indexing started in background.",
            file_count=None,
            estimated_time=None,
        )

    except HTTPException:
        upload_in_progress = False
        if temp_zip and temp_zip.exists():
            temp_zip.unlink(missing_ok=True)
        raise

    except Exception as e:
        upload_in_progress = False
        if temp_zip and temp_zip.exists():
            temp_zip.unlink(missing_ok=True)
        logger.error(f"Upload failed: {e}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Upload failed: {str(e)}"
        )


@app.get("/upload-status")
async def upload_status():
    """Check if an upload/indexing is in progress."""
    # Check if process is still running
    if indexing_process and indexing_process.poll() is None:
        return {
            "upload_in_progress": True,
            "status": "indexing",
            "pid": indexing_process.pid
        }
    
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