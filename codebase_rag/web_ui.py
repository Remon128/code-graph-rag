"""
Streamlit web interface for code-graph-rag.
Place this file in: codebase_rag/web_ui.py
"""

import os
import requests
import streamlit as st
from pathlib import Path

# Configuration - Use environment variable for Docker compatibility
API_URL = os.getenv("API_URL", "http://localhost:8000")

# Page configuration
st.set_page_config(
    page_title="Code Mind",
    page_icon="🤖",
    layout="wide",
    initial_sidebar_state="collapsed",
)

# Custom CSS — Ejada/Code Mind design
st.markdown(
    """
<style>
/* Hide default Streamlit header and sidebar toggle */
[data-testid="stSidebar"] { display: none; }
[data-testid="collapsedControl"] { display: none; }
#MainMenu { visibility: hidden; }
footer { visibility: hidden; }
header { visibility: hidden; }

/* Full-page layout */
.block-container {
    padding: 0 !important;
    max-width: 100% !important;
}

/* Top header bar */
.ejada-header {
    background-color: #1a1f6e;
    color: white;
    padding: 0 24px;
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    position: sticky;
    top: 0;
    z-index: 999;
}
.ejada-header-left {
    display: flex;
    align-items: center;
    gap: 10px;
}
.ejada-header-right {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 15px;
    font-weight: 500;
}
.ejada-logo-text {
    font-size: 13px;
    color: #a8b4ff;
    letter-spacing: 0.5px;
}
.ejada-badge {
    background: #2d35a0;
    color: #a8b4ff;
    font-size: 11px;
    padding: 3px 10px;
    border-radius: 20px;
    border: 1px solid #4a55c8;
}
.ejada-btn {
    background: #2d35a0;
    color: white;
    border: 1px solid #4a55c8;
    border-radius: 20px;
    padding: 5px 14px;
    font-size: 12px;
    cursor: pointer;
}
.ejada-lang-btn {
    background: transparent;
    color: #a8b4ff;
    border: 1px solid #4a55c8;
    border-radius: 20px;
    padding: 5px 12px;
    font-size: 12px;
}

/* Chat messages */
.chat-wrapper {
    max-width: 860px;
    margin: 0 auto;
    padding: 24px 16px 120px;
}
.msg-bot-row {
    display: flex;
    gap: 10px;
    margin-bottom: 16px;
    max-width: 80%;
}
.bot-avatar {
    width: 34px;
    height: 34px;
    min-width: 34px;
    background: #1a1f6e;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
}
.bot-avatar-inner {
    width: 18px;
    height: 18px;
    background: white;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
}
.bot-avatar-dot {
    width: 8px;
    height: 8px;
    background: #1a1f6e;
    border-radius: 50%;
}
.msg-bubble-bot {
    background: white;
    border: 1px solid #e0e0e0;
    border-radius: 0 12px 12px 12px;
    padding: 12px 14px;
}
.msg-sender {
    font-size: 12px;
    font-weight: 600;
    color: #1a1f6e;
    margin-bottom: 5px;
}
.msg-text {
    font-size: 14px;
    color: #222;
    line-height: 1.6;
}
.msg-time {
    font-size: 11px;
    color: #888;
    margin-top: 5px;
}
.msg-user-row {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 16px;
}
.msg-bubble-user {
    background: #1a1f6e;
    color: white;
    border-radius: 12px 0 12px 12px;
    padding: 10px 14px;
    max-width: 70%;
    font-size: 14px;
    line-height: 1.6;
}

/* Footer bar */
.ejada-footer {
    background: #1a1f6e;
    color: #a8b4ff;
    font-size: 10px;
    padding: 6px 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    z-index: 998;
}

/* Input area override */
.stTextArea textarea {
    border-radius: 24px !important;
    background: #f4f4f8 !important;
    border: 1px solid #ddd !important;
    font-size: 13px !important;
    padding: 12px 16px !important;
    resize: none !important;
}
.stButton button {
    border-radius: 20px !important;
    background: #1a1f6e !important;
    color: white !important;
    border: none !important;
    font-size: 13px !important;
}
.stButton button:hover {
    background: #2d35a0 !important;
}
.disclaimer-text {
    font-size: 11px;
    color: #888;
    text-align: center;
    margin-top: 4px;
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


def render_header():
    st.markdown(
        """
<div class="ejada-header">
    <div class="ejada-header-left">
        <button class="ejada-btn">+ New chat</button>
        <button class="ejada-lang-btn">EN</button>
        <span class="ejada-badge">Beta</span>
    </div>
    <div class="ejada-header-right">
        <span class="ejada-logo-text">ejada</span>
        <span>Code Mind — Smart Assistant</span>
        <div style="width:28px;height:28px;background:#2d35a0;border-radius:50%;border:1px solid #4a55c8;"></div>
    </div>
</div>
""",
        unsafe_allow_html=True,
    )


def render_footer():
    st.markdown(
        """
<div class="ejada-footer">
    <span>© 2026 Ejada Systems. All rights reserved.</span>
    <span>Following OWASP Top 10 for LLM Applications &amp; Generative AI</span>
</div>
""",
        unsafe_allow_html=True,
    )


def render_message(role: str, content: str):
    from datetime import datetime
    time_str = datetime.now().strftime("%I:%M %p")

    if role == "assistant":
        st.markdown(
            f"""
<div class="msg-bot-row">
    <div class="bot-avatar">
        <div class="bot-avatar-inner">
            <div class="bot-avatar-dot"></div>
        </div>
    </div>
    <div>
        <div class="msg-bubble-bot">
            <div class="msg-sender">Code Mind</div>
            <div class="msg-text">{content}</div>
            <div class="msg-time">{time_str}</div>
        </div>
    </div>
</div>
""",
            unsafe_allow_html=True,
        )
    else:
        st.markdown(
            f"""
<div class="msg-user-row">
    <div class="msg-bubble-user">{content}</div>
</div>
""",
            unsafe_allow_html=True,
        )


def main():
    init_session_state()

    render_header()

    # API health check
    is_healthy, _ = check_api_health()
    st.session_state.api_healthy = is_healthy

    if not st.session_state.api_healthy:
        st.warning("⚠️ API is not available. Please ensure the backend is running.")
        render_footer()
        return

    # Welcome message if no chat yet
    st.markdown('<div class="chat-wrapper">', unsafe_allow_html=True)

    if not st.session_state.messages:
        from datetime import datetime
        time_str = datetime.now().strftime("%I:%M %p")
        st.markdown(
            f"""
<div class="msg-bot-row">
    <div class="bot-avatar">
        <div class="bot-avatar-inner">
            <div class="bot-avatar-dot"></div>
        </div>
    </div>
    <div>
        <div class="msg-bubble-bot">
            <div class="msg-sender">Code Mind</div>
            <div class="msg-text">Welcome! I am Code Mind, the smart assistant. How can I help you?</div>
            <div class="msg-time">{time_str}</div>
        </div>
    </div>
</div>
""",
            unsafe_allow_html=True,
        )

    # Render chat history
    for msg in st.session_state.messages:
        render_message(msg["role"], msg["content"])

    st.markdown("</div>", unsafe_allow_html=True)

    st.divider()

    # Input area
    with st.form(key="chat_form", clear_on_submit=True):
        col1, col2, col3 = st.columns([7, 1, 1])
        with col1:
            user_input = st.text_area(
                "Message",
                placeholder="Ask any question about your codebase...",
                height=80,
                label_visibility="collapsed",
            )
        with col2:
            submit_button = st.form_submit_button("Send 📤", use_container_width=True)
        with col3:
            clear_button = st.form_submit_button("🗑️ Clear", use_container_width=True)

    st.markdown('<div class="disclaimer-text">Code Mind may display inaccurate information</div>', unsafe_allow_html=True)

    if clear_button:
        st.session_state.messages = []
        st.session_state.session_id = None
        st.rerun()

    if submit_button and user_input:
        st.session_state.messages.append({"role": "user", "content": user_input})
        with st.spinner("🤔 Code Mind is thinking..."):
            result = send_message(user_input, st.session_state.session_id)
            if result.get("session_id"):
                st.session_state.session_id = result["session_id"]
            st.session_state.messages.append(
                {"role": "assistant", "content": result.get("response", "")}
            )
        st.rerun()

    render_footer()


if __name__ == "__main__":
    main()
