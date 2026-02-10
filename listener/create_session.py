import os
import shutil
import tempfile
from pyrogram import Client


def main() -> None:
    api_id = os.getenv("TG_API_ID")
    api_hash = os.getenv("TG_API_HASH")

    if not api_id or not api_hash:
        raise RuntimeError("Set TG_API_ID and TG_API_HASH before running")

    temp_dir = tempfile.mkdtemp(prefix="ecc_listener_session_")
    try:
        with Client("ecc_listener_session", api_id=int(api_id), api_hash=api_hash, workdir=temp_dir) as app:
            session_string = app.export_session_string()
            print("TG_SESSION_STRING=" + session_string)
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
