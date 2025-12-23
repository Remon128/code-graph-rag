"""
Streamlit web interface for code-graph-rag with improved error handling.
"""

import os
import time
import requests
import streamlit as st
from streamlit_chat import message as st_message
from PIL import Image
from pathlib import Path

# Configuration
API_URL = os.getenv("API_URL", "http://localhost:8000")

# Page configuration
st.set_page_config(
    page_title="Code Mind",
    page_icon="🤖",
    layout="wide",
    initial_sidebar_state="expanded",
)

# -------------------- Styles --------------------
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
.status-warning {
    background-color: #fff3cd;
    border: 1px solid #ffeeba;
    color: #856404;
}
.upload-box {
    padding: 1rem;
    border-radius: 0.5rem;
    border: 2px dashed #1f77b4;
    margin-bottom: 1rem;
}
</style>
""",
    unsafe_allow_html=True,
)

# -------------------- Helpers --------------------
def check_api_health():
    try:
        r = requests.get(f"{API_URL}/health", timeout=5)
        return r.status_code == 200, r.json() if r.ok else None
    except Exception:
        return False, None


def check_upload_status():
    """Check if an upload is in progress."""
    try:
        r = requests.get(f"{API_URL}/upload-status", timeout=5)
        if r.ok:
            return r.json()
        return {"upload_in_progress": False, "status": "unknown"}
    except Exception:
        return {"upload_in_progress": False, "status": "error"}


def send_message(message: str, session_id: str | None = None):
    payload = {"message": message}
    if session_id:
        payload["session_id"] = session_id

    try:
        r = requests.post(f"{API_URL}/chat", json=payload, timeout=300)
        if r.ok:
            return r.json()
        else:
            error_detail = "Unknown error"
            try:
                error_data = r.json()
                error_detail = error_data.get("detail", r.text)
            except:
                error_detail = r.text
            
            return {
                "response": f"❌ Error: {error_detail}",
                "status": "error",
                "session_id": session_id
            }
    except requests.exceptions.Timeout:
        return {
            "response": "❌ Request timed out. The system might be processing a large query.",
            "status": "error",
            "session_id": session_id
        }
    except Exception as e:
        return {
            "response": f"❌ Connection error: {str(e)}",
            "status": "error",
            "session_id": session_id
        }


def upload_repo(zip_file):
    """Upload repository with proper error handling."""
    try:
        files = {"file": (zip_file.name, zip_file.getvalue(), "application/zip")}
        
        # Use longer timeout for large files
        timeout = 600  # 10 minutes
        
        r = requests.post(
            f"{API_URL}/upload-repo",
            files=files,
            timeout=timeout
        )
        
        if r.ok:
            return r.json()
        else:
            error_detail = "Unknown error"
            try:
                error_data = r.json()
                error_detail = error_data.get("detail", r.text)
            except:
                error_detail = r.text
            
            raise RuntimeError(error_detail)
            
    except requests.exceptions.Timeout:
        raise RuntimeError("Upload timed out. File might be too large.")
    except requests.exceptions.ConnectionError:
        raise RuntimeError("Connection error. Is the backend running?")
    except Exception as e:
        raise RuntimeError(str(e))


def init_state():
    st.session_state.setdefault("messages", [])
    st.session_state.setdefault("session_id", None)
    st.session_state.setdefault("api_healthy", False)
    st.session_state.setdefault("indexing", False)
    st.session_state.setdefault("upload_result", None)


# -------------------- UI --------------------
def main():
    init_state()

    # Header
    try:
        logo = Path(__file__).parent / "assets" / "ejada_logo.png"
        if logo.exists():
            st.image(str(logo), width=300)
        else:
            raise FileNotFoundError
    except Exception:
        st.markdown('<div class="main-header">🤖 Ejada</div>', unsafe_allow_html=True)

    st.markdown("### Chat with your codebase")

    # Check upload status
    upload_status_data = check_upload_status()
    is_indexing = upload_status_data.get("upload_in_progress", False)

    # ---------------- Sidebar ----------------
    with st.sidebar:
        st.header("📂 Project Upload")

        # Show indexing status if in progress
        if is_indexing:
            st.markdown(
                '<div class="status-box status-warning">⏳ <b>Indexing in Progress</b><br/>Please wait...</div>',
                unsafe_allow_html=True,
            )
        
        st.markdown(
            '<div class="upload-box">Upload a <b>.zip</b> repository to reindex<br/>'
            '<small>Limit: 500MB | Supports Java, Python, JS, TS, C++, Go, Rust</small></div>',
            unsafe_allow_html=True,
        )

        uploaded = st.file_uploader(
            "Upload repository (ZIP only)",
            type=["zip"],
            accept_multiple_files=False,
            disabled=is_indexing,
        )

        upload_button_disabled = is_indexing or uploaded is None
        
        if st.button(
            "🚀 Upload & Reindex",
            use_container_width=True,
            disabled=upload_button_disabled
        ):
            if uploaded:
                # Check file size before uploading
                file_size_mb = len(uploaded.getvalue()) / (1024 * 1024)
                
                if file_size_mb > 500:
                    st.error(f"File too large ({file_size_mb:.1f}MB). Maximum size is 500MB.")
                else:
                    st.session_state.messages = []
                    st.session_state.session_id = None
                    st.session_state.upload_result = None

                    with st.spinner(f"Uploading repository ({file_size_mb:.1f}MB)..."):
                        try:
                            result = upload_repo(uploaded)
                            st.session_state.upload_result = {
                                "success": True,
                                "data": result
                            }
                        except Exception as e:
                            st.session_state.upload_result = {
                                "success": False,
                                "error": str(e)
                            }
                        finally:
                            st.rerun()

        # Display upload result if available
        if st.session_state.upload_result:
            result = st.session_state.upload_result
            if result["success"]:
                data = result["data"]
                st.success(data.get("message", "Upload successful!"))
                
                if data.get("file_count"):
                    st.info(f"📊 **Files to process:** {data['file_count']:,}")
                
                if data.get("estimated_time"):
                    st.info(f"⏱️ **Estimated time:** {data['estimated_time']}")
                
                if st.button("✅ Dismiss", use_container_width=True):
                    st.session_state.upload_result = None
                    st.rerun()
            else:
                st.error(f"Upload failed: {result['error']}")
                if st.button("❌ Dismiss", use_container_width=True):
                    st.session_state.upload_result = None
                    st.rerun()

        st.divider()

        st.header("⚙️ System Status")
        healthy, data = check_api_health()
        st.session_state.api_healthy = healthy

        if healthy:
            st.markdown(
                '<div class="status-box status-healthy">✅ API Healthy</div>',
                unsafe_allow_html=True,
            )
            
            if data:
                # Show upload status
                if data.get("upload_in_progress"):
                    st.warning("⏳ Indexing in progress...")
                
                status_info = {
                    "Memgraph": "✅" if data.get("memgraph_connected") else "❌",
                    "Agent": "✅" if data.get("agent_initialized") else "❌",
                    "Status": "Indexing" if data.get("upload_in_progress") else "Ready"
                }
                st.json(status_info)
        else:
            st.markdown(
                '<div class="status-box status-error">❌ API Down</div>',
                unsafe_allow_html=True,
            )

        st.divider()

        if st.button("🗑️ Clear Chat", use_container_width=True):
            st.session_state.messages = []
            st.session_state.session_id = None
            st.rerun()

    # ---------------- Main Area ----------------
    if is_indexing:
        st.warning("⏳ Indexing in progress. Chat is temporarily disabled. This may take several minutes for large repositories.")
        
        # Show progress indicator
        with st.spinner("Processing repository..."):
            time.sleep(2)  # Prevent too frequent refreshes
            st.rerun()
        return

    if not st.session_state.api_healthy:
        st.warning("⚠️ API not available. Please check the system status in the sidebar.")
        return

    # Display chat messages
    for i, msg in enumerate(st.session_state.messages):
        st_message(msg["content"], is_user=msg["role"] == "user", key=str(i))

    st.divider()

    # Chat input form
    with st.form("chat_form", clear_on_submit=True):
        user_input = st.text_area(
            "Your message",
            placeholder="Ask something about your codebase…",
            height=100,
            label_visibility="collapsed",
        )
        submitted = st.form_submit_button("Send 📤")

    if submitted and user_input:
        st.session_state.messages.append({"role": "user", "content": user_input})

        with st.spinner("🤔 Thinking..."):
            result = send_message(user_input, st.session_state.session_id)

        if result.get("session_id"):
            st.session_state.session_id = result["session_id"]

        response_content = result.get("response", "No response received")
        st.session_state.messages.append(
            {"role": "assistant", "content": response_content}
        )
        st.rerun()


if __name__ == "__main__":
    main()