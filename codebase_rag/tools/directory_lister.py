import os
from pathlib import Path

from loguru import logger
from pydantic_ai import Tool


class DirectoryLister:
    def __init__(self, project_root: str):
        self.project_root = Path(project_root).resolve()

    def list_directory_contents(self, directory_path: str) -> str:
        """
        Lists the contents of a specified directory within the project root.
        
        Args:
            directory_path: Path relative to project root. Use "." or "" for root directory.
        """
        target_path = self._get_safe_path(directory_path)
        logger.info(f"Listing contents of directory: {target_path}")

        try:
            if not target_path.is_dir():
                return f"Error: '{directory_path}' is not a valid directory."

            if contents := os.listdir(target_path):
                # Sort with directories first, then files
                items = sorted(
                    [target_path / item for item in contents],
                    key=lambda p: (not p.is_dir(), p.name)
                )
                
                result = []
                for item in items:
                    rel_path = item.relative_to(self.project_root)
                    item_type = "[DIR]" if item.is_dir() else "[FILE]"
                    result.append(f"{item_type} {rel_path}")
                
                return "\n".join(result)
            else:
                return f"The directory '{directory_path or '.'}' is empty."

        except Exception as e:
            logger.error(f"Error listing directory {directory_path}: {e}")
            return f"Error: Could not list contents of '{directory_path}'."

    def _get_safe_path(self, file_path: str) -> Path:
        """
        Resolves the file path relative to the root and ensures it's within
        the project directory.
        
        Handles:
        - Empty string or "." -> project root
        - "/" or similar root paths -> project root
        - Relative paths -> relative to project root
        - Absolute paths -> checked if within project root, otherwise treated as relative
        """
        # Handle empty or current directory references
        if not file_path or file_path.strip() in (".", "./", ".\\"):
            return self.project_root
        
        file_path = file_path.strip()
        path_obj = Path(file_path)
        
        # Handle absolute paths
        if path_obj.is_absolute():
            resolved_path = path_obj.resolve()
            
            # Check if the absolute path is already within project root
            try:
                resolved_path.relative_to(self.project_root)
                safe_path = resolved_path
            except ValueError:
                # Absolute path is outside project root
                # Treat it as relative by stripping leading separators
                # This handles cases like "/" -> project root, "/src" -> project_root/src
                relative_parts = str(path_obj).lstrip('/').lstrip('\\')
                
                if not relative_parts:
                    # Just "/" maps to project root
                    safe_path = self.project_root
                else:
                    # "/some/path" becomes "some/path" relative to project root
                    safe_path = (self.project_root / relative_parts).resolve()
        else:
            # Relative path - resolve against project root
            safe_path = (self.project_root / path_obj).resolve()

        # Security check: ensure final path is within project root
        try:
            safe_path.relative_to(self.project_root)
        except ValueError as e:
            raise PermissionError(
                f"Access denied: Path '{file_path}' resolves to '{safe_path}' "
                f"which is outside the project root '{self.project_root}'."
            ) from e

        # Additional check for symlinks that might bypass relative_to
        if not str(safe_path).startswith(str(self.project_root)):
            raise PermissionError(
                "Access denied: Cannot access files outside the project root."
            )

        return safe_path


def create_directory_lister_tool(directory_lister: DirectoryLister) -> Tool:
    return Tool(
        function=directory_lister.list_directory_contents,
        description=(
            "Lists the contents of a directory within the project to explore the codebase. "
            "Provide a relative path from the project root (e.g., 'src', 'src/main', etc.). "
            "Use '.' or empty string to list the project root directory. "
            "Returns a formatted list showing [DIR] for directories and [FILE] for files."
        ),
    )