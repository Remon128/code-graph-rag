"""
FastAPI backend for code-graph-rag web interface.
Place this file in: codebase_rag/api_server.py
"""
import asyncio
import uuid
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
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


# Request/Response Models
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


# Global state
sessions: dict[str, dict[str, Any]] = {}
rag_agent: Any = None
ingestor: MemgraphIngestor | None = None
project_root: Path | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize services on startup and cleanup on shutdown."""
    global rag_agent, ingestor, project_root
    
    logger.info("Initializing FastAPI server...")
    
    # Initialize project root
    project_root = Path(settings.TARGET_REPO_PATH).resolve()
    
    # Initialize Memgraph connection
    # Note: We don't use context manager here because tools manage their own connections
    ingestor = MemgraphIngestor(
        host=settings.MEMGRAPH_HOST,
        port=settings.MEMGRAPH_PORT,
        batch_size=settings.MEMGRAPH_BATCH_SIZE,
    )
    
    logger.info("Memgraph ingestor initialized (tools will manage connections)")
    
    # Initialize all tools and agent
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
    
    # Create tools
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
    
    # Create RAG agent
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
    
    logger.info("RAG Agent initialized successfully")
    
    yield
    
    # Cleanup
    if ingestor:
        logger.info("Shutting down Memgraph ingestor")
    
    logger.info("FastAPI server shutdown complete")


app = FastAPI(
    title="Code Graph RAG API",
    description="API for interacting with code-graph-rag",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "message": "Code Graph RAG API",
        "version": "1.0.0",
        "status": "running"
    }


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "memgraph_connected": ingestor is not None,
        "agent_initialized": rag_agent is not None,
    }


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """
    Process a chat message and return the agent's response.
    """
    if not rag_agent:
        raise HTTPException(status_code=500, detail="RAG agent not initialized")
    
    if not ingestor:
        raise HTTPException(status_code=500, detail="Memgraph connection not available")
    
    # Get or create session
    session_id = request.session_id or str(uuid.uuid4())
    
    if session_id not in sessions:
        sessions[session_id] = {
            "message_history": [],
            "created_at": asyncio.get_event_loop().time(),
        }
    
    session = sessions[session_id]
    
    try:
        logger.info(f"Processing message for session {session_id}: {request.message[:100]}...")
        
        # Run the agent
        response = await rag_agent.run(
            request.message,
            message_history=session["message_history"]
        )
        
        # Update message history
        session["message_history"].extend(response.new_messages())
        
        logger.info(f"Response generated for session {session_id}")
        
        return ChatResponse(
            response=response.output,
            session_id=session_id,
            status="success"
        )
        
    except Exception as e:
        # Use repr to safely log any error without KeyError issues
        error_str = repr(e) if hasattr(e, '__repr__') else str(type(e))
        logger.error(f"Error processing chat message: {error_str}", exc_info=True)
        
        # Provide user-friendly error messages
        error_detail = str(e)
        
        # Check for specific error types
        if "context_length_exceeded" in error_detail.lower():
            error_detail = "The conversation has become too long. Please clear the chat and start a new session."
        elif "tool_use_failed" in error_detail.lower():
            error_detail = "The AI encountered an issue using tools. Please try rephrasing your question or clear the chat."
        elif "cypher error" in error_detail.lower() or "parsing error" in error_detail.lower():
            error_detail = "The database query was malformed. This is usually temporary - please try asking your question differently or clear the chat."
        elif not error_detail or error_detail == "":
            error_detail = "An unknown error occurred. Please try again or clear the chat."
        
        raise HTTPException(status_code=500, detail=f"Error: {error_detail}")


@app.get("/session/{session_id}", response_model=SessionInfo)
async def get_session(session_id: str):
    """Get information about a session."""
    if session_id not in sessions:
        raise HTTPException(status_code=404, detail="Session not found")
    
    session = sessions[session_id]
    return SessionInfo(
        session_id=session_id,
        message_count=len(session["message_history"]),
        created_at=str(session["created_at"]),
    )


@app.get("/sessions")
async def list_sessions():
    """List all active sessions."""
    return {
        "sessions": [
            {
                "session_id": sid,
                "message_count": len(session["message_history"]),
                "created_at": session["created_at"],
            }
            for sid, session in sessions.items()
        ]
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)