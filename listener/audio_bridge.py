import socket
import struct
import threading
import time
from typing import Callable, Optional

from listener.call_listener import AudioChunker

_FRAME_LENGTH_HEADER = struct.Struct(">I")


class AudioBridgeServer:
    def __init__(
        self,
        chunker: AudioChunker,
        host: str = "127.0.0.1",
        port: int = 5045,
        reconnect_seconds: float = 5.0,
    ) -> None:
        self.chunker = chunker
        self.host = host
        self.port = port
        self.reconnect_seconds = reconnect_seconds
        self._stop_event = threading.Event()
        self._thread: Optional[threading.Thread] = None

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop_event.set()
        if self._thread:
            self._thread.join(timeout=1.0)

    def _run(self) -> None:
        while not self._stop_event.is_set():
            try:
                with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
                    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                    server.bind((self.host, self.port))
                    server.listen(1)
                    server.settimeout(1.0)

                    print(f"[listener] Audio bridge listening on {self.host}:{self.port}")

                    while not self._stop_event.is_set():
                        try:
                            conn, addr = server.accept()
                        except socket.timeout:
                            continue
                        else:
                            self._handle_connection(conn, addr)
            except OSError as exc:
                print(
                    f"[listener] Audio bridge encounter error: {exc}. Retrying in {self.reconnect_seconds}s"
                )
                time.sleep(self.reconnect_seconds)

    def _handle_connection(self, conn: socket.socket, addr: tuple[str, int]) -> None:
        peer = f"{addr[0]}:{addr[1]}"
        print(f"[listener] Audio bridge connected from {peer}")
        with conn:
            conn.settimeout(1.0)
            while not self._stop_event.is_set():
                header = self._read_exact(conn, 4)
                if header is None:
                    break
                length = _FRAME_LENGTH_HEADER.unpack(header)[0]
                if length <= 0:
                    continue
                payload = self._read_exact(conn, length)
                if payload is None:
                    break
                self.chunker.add(payload)
        print(f"[listener] Audio bridge disconnected from {peer}")

    def _read_exact(self, conn: socket.socket, size: int) -> Optional[bytes]:
        buffers = bytearray()
        while len(buffers) < size and not self._stop_event.is_set():
            try:
                chunk = conn.recv(size - len(buffers))
            except socket.timeout:
                continue
            if not chunk:
                return None
            buffers.extend(chunk)
        if len(buffers) != size:
            return None
        return bytes(buffers)
