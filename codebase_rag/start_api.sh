#!/bin/bash
# File: start_api.sh
# Place this in the root of your repository

echo "Starting FastAPI server..."
export TARGET_REPO_PATH="/home/aahafez/RbsEAR_(1).zip.src"

# Set your model configurations (adjust as needed)
# export ORCHESTRATOR_PROVIDER="ollama"
# export ORCHESTRATOR_MODEL="llama3.2"
# export CYPHER_PROVIDER="ollama"
# export CYPHER_MODEL="codellama"

uvicorn codebase_rag.api_server:app --host 0.0.0.0 --port 8000 --reload

---

#!/bin/bash
# File: start_web.sh
# Place this in the root of your repository

echo "Starting Streamlit web interface..."
streamlit run codebase_rag/web_ui.py --server.port 8501 --server.address 0.0.0.0

---

#!/bin/bash
# File: start_both.sh
# Place this in the root of your repository
# This script starts both API and web interface

export TARGET_REPO_PATH="/home/aahafez/RbsEAR_(1).zip.src"

# Start API server in background
echo "Starting FastAPI server..."
uvicorn codebase_rag.api_server:app --host 0.0.0.0 --port 8000 &
API_PID=$!

# Wait for API to be ready
echo "Waiting for API to be ready..."
sleep 5

# Start Streamlit
echo "Starting Streamlit web interface..."
streamlit run codebase_rag/web_ui.py --server.port 8501 --server.address 0.0.0.0 &
WEB_PID=$!

echo "================================"
echo "Services started!"
echo "API Server: http://localhost:8000"
echo "Web Interface: http://localhost:8501"
echo "================================"
echo "Press Ctrl+C to stop both services"

# Wait for Ctrl+C
trap "kill $API_PID $WEB_PID; exit" INT
wait