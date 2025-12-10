import asyncio
import uuid
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger
from pydantic import BaseModel

import os
import sys

# Get the directory of codebase_rag
current_dir = os.path.dirname(os.path.abspath(__file__))

# Add parent directory so package imports work
parent_dir = os.path.abspath(os.path.join(current_dir, ".."))
sys.path.insert(0, parent_dir)

# Make codebase_rag importable as a package
sys.path.insert(0, current_dir)


from codebase_rag.config import settings
from codebase_rag.graph_updater import MemgraphIngestor
from codebase_rag.services.llm import CypherGenerator, create_rag_orchestrator
from codebase_rag.tools.code_retrieval import CodeRetriever, create_code_retrieval_tool
from codebase_rag.tools.codebase_query import create_query_tool
from codebase_rag.tools.directory_lister import DirectoryLister, create_directory_lister_tool
from codebase_rag.tools.document_analyzer import DocumentAnalyzer, create_document_analyzer_tool
from codebase_rag.tools.file_editor import FileEditor, create_file_editor_tool
from codebase_rag.tools.file_reader import FileReader, create_file_reader_tool
from codebase_rag.tools.file_writer import FileWriter, create_file_writer_tool
from codebase_rag.tools.semantic_search import (
    create_get_function_source_tool,
    create_semantic_search_tool,
)
from codebase_rag.tools.shell_command import ShellCommander, create_shell_command_tool



def main_factory():
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
    
    return rag_agent, ingestor, project_root



def chat(query: str):
    """
    Process a chat message and return the agent's response.
    """
    # if not rag_agent:
    #     raise HTTPException(status_code=500, detail="RAG agent not initialized")
    
    # if not ingestor:
    #     raise HTTPException(status_code=500, detail="Memgraph connection not available")
    
    # # Get or create session
    # session_id = request.session_id or str(uuid.uuid4())
    
    # if session_id not in sessions:
    #     sessions[session_id] = {
    #         "message_history": [],
    #         "created_at": asyncio.get_event_loop().time(),
    #     }
    
    # session = sessions[session_id]
    
    try:
        # logger.info(f"Processing message for session {session_id}: {request.message[:100]}...")
        
        # Run the agent
        rag_agent, ingestor, project_root = main_factory()
        response = rag_agent.run(query=query)

        print("Response:", response)
        # Update message history
        # session["message_history"].extend(response.new_messages())
        
        # logger.info(f"Response generated for session {session_id}")
        
        # return ChatResponse(
        #     response=response.output,
        #     session_id=session_id,
        #     status="success"
        # )
        
    except Exception as e:
        print(e)
        # Use repr to safely log any error without KeyError issues
        # error_str = repr(e) if hasattr(e, '__repr__') else str(type(e))
        # logger.error(f"Error processing chat message: {error_str}", exc_info=True)
        
        # # Provide user-friendly error messages
        # error_detail = str(e)
        
        # # Check for specific error types
        # if "context_length_exceeded" in error_detail.lower():
        #     error_detail = "The conversation has become too long. Please clear the chat and start a new session."
        # elif "tool_use_failed" in error_detail.lower():
        #     error_detail = "The AI encountered an issue using tools. Please try rephrasing your question or clear the chat."
        # elif "cypher error" in error_detail.lower() or "parsing error" in error_detail.lower():
        #     error_detail = "The database query was malformed. This is usually temporary - please try asking your question differently or clear the chat."
        # elif not error_detail or error_detail == "":
        #     error_detail = "An unknown error occurred. Please try again or clear the chat."
        
        # raise HTTPException(status_code=500, detail=f"Error: {error_detail}")


if __name__ == "__main__":
    while True:
        user_input = input("You: ")
        chat(user_input)

def main():
    empty_count = 0

    while True:
        query = input("Enter query: ")

        if query == "":
            empty_count += 1
            if empty_count == 2:
                print("Exiting...")
                break
        else:
            empty_count = 0
            print("You entered:", query)
            chat(query=query)


if __name__ == "__main__":
    main()