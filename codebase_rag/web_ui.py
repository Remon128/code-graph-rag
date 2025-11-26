"""
Streamlit web interface for code-graph-rag.
Place this file in: codebase_rag/web_ui.py
"""

import os
import requests
import streamlit as st
from streamlit_chat import message as st_message
from PIL import Image

# Configuration - Use environment variable for Docker compatibility
API_URL = os.getenv("API_URL", "http://localhost:8000")

# Page configuration
st.set_page_config(
    page_title="Code Chat",
    page_icon="🤖",
    layout="wide",
    initial_sidebar_state="expanded",
)

# Custom CSS
st.markdown(
    """
<style>
.main-header {
    font-size: 2.5rem;
    font-weight: bold;
    color: #1f77b4;
    margin-bottom: 1rem;
}
.status-box {
    padding: 1rem;
    border-radius: 0.5rem;
    margin-bottom: 1rem;
}
.status-healthy {
    background-color: #d4edda;
    border: 1px solid #c3e6cb;
    color: #155724;
}
.status-error {
    background-color: #f8d7da;
    border: 1px solid #f5c6cb;
    color: #721c24;
}
.chat-container {
    height: 600px;
    overflow-y: auto;
    padding: 1rem;
    border: 1px solid #ddd;
    border-radius: 0.5rem;
    margin-bottom: 1rem;
}
</style>
""",
    unsafe_allow_html=True,
)


def check_api_health():
    """Check if the API is running and healthy."""
    try:
        response = requests.get(f"{API_URL}/health", timeout=5)
        if response.status_code == 200:
            return True, response.json()
        return False, None
    except requests.exceptions.RequestException:
        return False, None


def send_message(message: str, session_id: str | None = None):
    """Send a message to the API and get a response."""
    try:
        payload = {"message": message}
        if session_id:
            payload["session_id"] = session_id

        response = requests.post(
            f"{API_URL}/chat",
            json=payload,
            timeout=300,  # 5 minutes timeout for long operations
        )

        if response.status_code == 200:
            return response.json()
        else:
            return {
                "response": f"Error: {response.status_code} - {response.text}",
                "session_id": session_id,
                "status": "error",
            }
    except requests.exceptions.Timeout:
        return {
            "response": "Request timed out. The operation might still be running on the server.",
            "session_id": session_id,
            "status": "error",
        }
    except requests.exceptions.RequestException as e:
        return {
            "response": f"Connection error: {str(e)}",
            "session_id": session_id,
            "status": "error",
        }


def init_session_state():
    """Initialize session state variables."""
    if "messages" not in st.session_state:
        st.session_state.messages = []
    if "session_id" not in st.session_state:
        st.session_state.session_id = None
    if "api_healthy" not in st.session_state:
        st.session_state.api_healthy = False


def main():
    """Main Streamlit application."""
    init_session_state()

    # Header with Ejada Logo
    try:
        logo = Image.open("/home/aahafez/code-graph-rag/ejada_logo.png")
        st.image(logo, width=300)
    except Exception as e:
        st.error(f"Could not load logo: {e}")
        st.markdown('<div class="main-header">🤖 Ejada</div>', unsafe_allow_html=True)

    st.markdown("### chat with code")

    # Sidebar
    with st.sidebar:
        st.header("⚙️ Settings")

        # Display API URL
        st.info(f"**API URL:** {API_URL}")

        # API Health Check
        is_healthy, health_data = check_api_health()
        st.session_state.api_healthy = is_healthy

        if is_healthy:
            st.markdown(
                '<div class="status-box status-healthy">✅ API Status: Healthy</div>',
                unsafe_allow_html=True,
            )
            if health_data:
                st.json(
                    {
                        "Memgraph": (
                            "Connected"
                            if health_data.get("memgraph_connected")
                            else "Disconnected"
                        ),
                        "Agent": (
                            "Initialized"
                            if health_data.get("agent_initialized")
                            else "Not Initialized"
                        ),
                    }
                )
        else:
            st.markdown(
                '<div class="status-box status-error">❌ API Status: Unavailable</div>',
                unsafe_allow_html=True,
            )
            st.error(
                "API server is not responding. Please check if all Docker containers are running:\n```bash\ndocker-compose ps\n```"
            )

        st.divider()

        # Session Info
        st.subheader("📊 Session Info")
        if st.session_state.session_id:
            st.info(f"**Session ID:** {st.session_state.session_id[:8]}...")
            st.metric("Messages", len(st.session_state.messages))
        else:
            st.info("No active session")

        # Clear Chat Button
        if st.button("🗑️ Clear Chat", use_container_width=True):
            st.session_state.messages = []
            st.session_state.session_id = None
            st.rerun()

        st.divider()

        # About
        st.subheader("ℹ️ About")
        st.markdown(
            """
        This interface allows you to:
        - Query your codebase
        - Search through code semantically
        - Read and modify files
        - Execute shell commands
        - Analyze documents
        
        The agent has access to all code-graph-rag tools.
        """
        )

    # Main Chat Area
    if not st.session_state.api_healthy:
        st.warning(
            "⚠️ API is not available. Please ensure all Docker services are running."
        )
        return

    # Display chat messages
    chat_container = st.container()
    with chat_container:
        for idx, msg in enumerate(st.session_state.messages):
            if msg["role"] == "user":
                st_message(msg["content"], is_user=True, key=f"user_{idx}")
            else:
                st_message(msg["content"], is_user=False, key=f"assistant_{idx}")

    # Chat Input
    st.divider()

    # Use a form to handle Enter key submission
    with st.form(key="chat_form", clear_on_submit=True):
        col1, col2 = st.columns([6, 1])

        with col1:
            user_input = st.text_area(
                "Your message:",
                placeholder="Ask a question about your codebase",
                height=100,
                label_visibility="collapsed",
            )

        with col2:
            submit_button = st.form_submit_button("Send 📤", use_container_width=True)

    # Process message
    if submit_button and user_input:
        # Add user message to chat
        st.session_state.messages.append({"role": "user", "content": user_input})

        # Show thinking indicator
        with st.spinner("🤔 Agent is thinking..."):
            # Send to API
            result = send_message(user_input, st.session_state.session_id)

            # Update session ID
            if result.get("session_id"):
                st.session_state.session_id = result["session_id"]

            # Add assistant response to chat
            st.session_state.messages.append(
                {
                    "role": "assistant",
                    "content": result.get("response", "Error: No response received"),
                }
            )

        # Rerun to update chat display
        st.rerun()


if __name__ == "__main__":
    main()
