"""
Streamlit web interface for code-graph-rag
Chat UI + Project Upload
"""

import os
import time
import requests
import streamlit as st
from streamlit_chat import message as st_message
from pathlib import Path

# ===============================
# Configuration
# ===============================
INTERNAL_API_URL = os.getenv("INTERNAL_API_URL", "http://backend:8000")
PUBLIC_API_URL = os.getenv("PUBLIC_API_URL", "http://localhost:8000")

# ===============================
# Page config
# ===============================
st.set_page_config(
    page_title="Code Mind",
    page_icon="🤖",
    layout="wide",
    initial_sidebar_state="expanded",
)

# ===============================
# Styles
# ===============================
st.markdown(
    """
<style>
.upload-box {
    padding: 1rem;
    border-radius: 0.5rem;
    border: 2px dashed #1f77b4;
    margin-bottom: 1rem;
}
.status-warning {
    background-color: #fff3cd;
    border: 1px solid #ffeeba;
    color: #856404;
    padding: 0.75rem;
    border-radius: 0.5rem;
}
.indexing-banner {
    background: linear-gradient(90deg, #ff9800 0%, #ff5722 100%);
    color: white;
    padding: 1.25rem;
    border-radius: 0.5rem;
    text-align: center;
    font-weight: bold;
}
</style>
""",
    unsafe_allow_html=True,
)

# ===============================
# Helpers
# ===============================
def check_api_health():
    try:
        r = requests.get(f"{INTERNAL_API_URL}/health", timeout=5)
        return r.ok
    except Exception:
        return False


def check_upload_status():
    try:
        r = requests.get(f"{INTERNAL_API_URL}/upload-status", timeout=5)
        return r.json() if r.ok else {"upload_in_progress": False}
    except Exception:
        return {"upload_in_progress": False}


def send_message(message, session_id=None):
    payload = {"message": message}
    if session_id:
        payload["session_id"] = session_id

    try:
        r = requests.post(
            f"{INTERNAL_API_URL}/chat",
            json=payload,
            timeout=300,
        )

        if r.status_code == 200:
            return r.json()

        # 🔴 Handle backend errors gracefully
        return {
            "response": f"Error: {r.status_code} - {r.text}",
            "session_id": session_id,
            "status": "error",
        }

    except requests.exceptions.RequestException as e:
        return {
            "response": f"Connection error: {str(e)}",
            "session_id": session_id,
            "status": "error",
        }

    except ValueError:
        # JSONDecodeError lands here
        return {
            "response": "Error: Backend returned an invalid response.",
            "session_id": session_id,
            "status": "error",
        }


def init_state():
    st.session_state.setdefault("messages", [])
    st.session_state.setdefault("session_id", None)
    st.session_state.setdefault("processing_message", False)
    st.session_state.setdefault("last_upload_check", 0)


# ===============================
# UI
# ===============================
def main():
    init_state()

    # Header
    try:
        logo = Path(__file__).parent / "assets" / "ejada_logo.png"
        if logo.exists():
            st.image(str(logo), width=300)
    except Exception:
        st.title("Ejada")

    st.markdown("### Code reverse engineering")

    # Upload status polling
    now = time.time()
    if now - st.session_state.last_upload_check > 2:
        upload_status = check_upload_status()
        st.session_state.last_upload_check = now
    else:
        upload_status = {"upload_in_progress": False}

    is_indexing = upload_status.get("upload_in_progress", False)

    # ================= Sidebar =================
    with st.sidebar:
        st.header("📂 Project Upload")

        if is_indexing:
            st.markdown(
                '<div class="status-warning">⏳ Indexing in progress…</div>',
                unsafe_allow_html=True,
            )

        st.markdown(
            """
            <div class="upload-box">
                Upload a <b>.zip</b> repository to reindex<br/>
                <small>Limit: 500MB | Java, Python, JS, TS, C++, Go, Rust</small>
            </div>
            """,
            unsafe_allow_html=True,
        )

        st.components.v1.html(
            f"""
            <form action="{PUBLIC_API_URL}/upload-repo"
                  method="post"
                  enctype="multipart/form-data"
                  target="_blank">
                <input type="file" name="file" accept=".zip" required style="width:100%; margin-bottom:10px;" />
                <button type="submit"
                        style="width:100%; padding:10px; background:#1f77b4; color:white;
                               border:none; border-radius:6px; font-weight:bold;">
                    🚀 Upload & Reindex
                </button>
            </form>
            """,
            height=160,
        )

        st.divider()
        st.markdown(
            """
            ### ✅ What is supported now

            - 📦 Uploading repositories directly from the UI  

            ---

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

            - 🌍 High-level system overview questions  
            - *“Give me an overview of the system”*  
            - *“Explain the architecture”*  
                        """
        )

    # ================= Main =================
    if is_indexing:
        st.markdown(
            '<div class="indexing-banner">⏳ Repository Indexing in Progress – Chat Disabled</div>',
            unsafe_allow_html=True,
        )
        return

    if not check_api_health():
        st.warning("⚠️ API not available")
        return

    for i, msg in enumerate(st.session_state.messages):
        st_message(msg["content"], is_user=msg["role"] == "user", key=f"msg_{i}")

    st.divider()

    # 🔑 Dynamic form key (CRITICAL)
    form_key = f"chat_form_{len(st.session_state.messages)}"

    with st.form(form_key, clear_on_submit=True):
        col1, col2 = st.columns([6, 1])

        with col1:
            user_input = st.text_area(
                "Your message",
                placeholder="Ask a question about your codebase",
                height=100,
                label_visibility="collapsed",
            )

        with col2:
            st.markdown(
            """
            <style>
            div[data-testid="stForm"] div.stButton > button {
                margin-top: -18px;
            }
            </style>
            """,
            unsafe_allow_html=True,
        )
            send_btn = st.form_submit_button("Send 📤", use_container_width=True)
            clear_btn = st.form_submit_button(" Clear Chat 🗑️", use_container_width=True)

            st.markdown("</div>", unsafe_allow_html=True)

    # Handle Clear Chat
    if clear_btn:
        st.session_state.messages = []
        st.session_state.session_id = None
        st.session_state.processing_message = False
        st.rerun()

    # 🛑 Infinite loop guard
    if send_btn and user_input and not st.session_state.processing_message:
        st.session_state.processing_message = True

        st.session_state.messages.append({"role": "user", "content": user_input})

        with st.spinner("🤔 Agent is thinking..."):
            result = send_message(user_input, st.session_state.session_id)

        if result.get("session_id"):
            st.session_state.session_id = result["session_id"]

        st.session_state.messages.append(
            {"role": "assistant", "content": result.get("response", "")}
        )

        st.session_state.processing_message = False
        st.rerun()


if __name__ == "__main__":
    main()
