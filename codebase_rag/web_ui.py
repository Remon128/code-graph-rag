"""
Streamlit web interface for code-graph-rag.
Place this file in: codebase_rag/web_ui.py
"""

import os
import requests
import streamlit as st
from streamlit_chat import message as st_message
from PIL import Image
from pathlib import Path

# Configuration - Use environment variable for Docker compatibility
API_URL = os.getenv("API_URL", "http://localhost:8000")

# Page configuration
st.set_page_config(
    page_title="Code Mind",
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
    try:
        response = requests.get(f"{API_URL}/health", timeout=5)
        if response.status_code == 200:
            return True, response.json()
        return False, None
    except requests.exceptions.RequestException:
        return False, None


def send_message(message: str, session_id: str | None = None):
    try:
        payload = {"message": message}
        if session_id:
            payload["session_id"] = session_id

        response = requests.post(
            f"{API_URL}/chat",
            json=payload,
            timeout=300,
        )

        if response.status_code == 200:
            return response.json()
        else:
            return {
                "response": f"Error: {response.status_code} - {response.text}",
                "session_id": session_id,
                "status": "error",
            }
    except Exception as e:
        return {
            "response": f"Connection error: {str(e)}",
            "session_id": session_id,
            "status": "error",
        }


def init_session_state():
    if "messages" not in st.session_state:
        st.session_state.messages = []
    if "session_id" not in st.session_state:
        st.session_state.session_id = None
    if "api_healthy" not in st.session_state:
        st.session_state.api_healthy = False


def main():
    init_session_state()

    # Header with Logo
    try:
        logo_path = Path(__file__).parent / "assets" / "ejada_logo.png"
        if logo_path.exists():
            st.image(str(logo_path), width=300)
        else:
            raise FileNotFoundError(logo_path)
    except Exception:
        st.markdown('<div class="main-header">🤖 Ejada</div>', unsafe_allow_html=True)

    st.markdown("### Code reverse engineering")

    # Sidebar
    with st.sidebar:
        st.markdown(
            """
### ✅ What is supported now

**Deep code-centric analysis**, including:

**🔍 Code Understanding**
- File-level, class-level, and method-level questions  
- Very specific and detailed implementation questions  

**🧠 Semantic Understanding**
- Search by *meaning*, not exact keywords  

**🔗 Relationship Analysis**
- Methods inside a class  
- Methods inside a file  
- Method-to-method calling relationships  

**📝 Code Review**
- Positive or negative feedback  
- Suggestions to improve quality or readability  

---

### 🧪 Example Queries

- *What are the methods in `SamaRbsServices` class?*  
- *What is the method that blocks a party?*  
- *What are the parts related to debit cards?*  
- *What are the business rules for `BkOrdInq`?*  

---

### 🚧 Coming in future versions

- 📦 Uploading repositories directly from the UI  
- 🌍 High-level system overview questions  
  - *“Give me an overview of the system”*  
  - *“Explain the architecture”*  
            """
        )

    # Main Chat Area
    is_healthy, _ = check_api_health()
    st.session_state.api_healthy = is_healthy

    if not st.session_state.api_healthy:
        st.warning("⚠️ API is not available.")
        return

    for idx, msg in enumerate(st.session_state.messages):
        st_message(msg["content"], is_user=(msg["role"] == "user"), key=str(idx))

    st.divider()

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

            clear_button = st.form_submit_button("🗑️ Clear Chat", use_container_width=True)

    if clear_button:
        st.session_state.messages = []
        st.session_state.session_id = None
        st.rerun()

    if submit_button and user_input:
        st.session_state.messages.append({"role": "user", "content": user_input})
        with st.spinner("🤔 Agent is thinking..."):
            result = send_message(user_input, st.session_state.session_id)
            if result.get("session_id"):
                st.session_state.session_id = result["session_id"]
            st.session_state.messages.append(
                {"role": "assistant", "content": result.get("response", "")}
            )
        st.rerun()


if __name__ == "__main__":
    main()
