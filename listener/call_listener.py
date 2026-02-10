import asyncio
import threading
import time
from queue import Queue, Empty
from typing import Optional

from pyrogram import Client
from pytgcalls import GroupCallFactory


class AudioChunker:
    def __init__(self, chunk_seconds: float, sample_rate: int = 48000, channels: int = 2):
        self.sample_rate = sample_rate
        self.channels = channels
        self.sample_width = 2
        self.chunk_bytes = int(sample_rate * channels * self.sample_width * chunk_seconds)
        self.buffer = bytearray()
        self.lock = threading.Lock()
        self.queue: Queue[bytes] = Queue()

    def add(self, raw: bytes) -> None:
        if not raw:
            return
        with self.lock:
            self.buffer.extend(raw)
            while len(self.buffer) >= self.chunk_bytes:
                chunk = bytes(self.buffer[: self.chunk_bytes])
                del self.buffer[: self.chunk_bytes]
                self.queue.put(chunk)

    def get(self, timeout: float = 0.2) -> Optional[bytes]:
        try:
            return self.queue.get(timeout=timeout)
        except Empty:
            return None


class TelegramGroupCallListener:
    def __init__(self, api_id: int, api_hash: str, session_name: str, session_string: str, chat_target: str, chunker: AudioChunker):
        # Pyrogram 2.x supports session strings via session_string parameter.
        self.client = Client(session_name, api_id=api_id, api_hash=api_hash, session_string=session_string)
        self.chat_target = chat_target
        self.chunker = chunker
        self.group_call = None
        self.thread = None

    def _on_recorded_data(self, *args, **kwargs):
        # PyTgCalls passes raw PCM bytes; signature differs by version.
        for arg in args:
            if isinstance(arg, (bytes, bytearray)):
                self.chunker.add(bytes(arg))
                return
        data = kwargs.get("data")
        if isinstance(data, (bytes, bytearray)):
            self.chunker.add(bytes(data))

    def start(self):
        self.thread = threading.Thread(target=self._run_async, daemon=True)
        self.thread.start()

    def _run_async(self):
        asyncio.run(self._start_async())

    async def _start_async(self):
        await self.client.start()
        factory = GroupCallFactory(self.client, enable_logs_to_console=False)
        self.group_call = factory.get_raw_group_call(on_recorded_data=self._on_recorded_data)

        chat_id = await self._resolve_chat_id(self.chat_target)
        print(f"[listener] Resolved chat target {self.chat_target} -> {chat_id}")
        await self._join_call_with_retry(chat_id)

        # Keep running
        await asyncio.Event().wait()

    async def _resolve_chat_id(self, target: str) -> int:
        if target.startswith("@"):
            target = target[1:]
        if target.startswith("t.me/"):
            target = target.replace("t.me/", "")
        chat = await self.client.get_chat(target)
        return chat.id

    async def _join_call_with_retry(self, chat_id: int, retries: int = 20, delay: float = 5.0):
        for _ in range(retries):
            try:
                await self.group_call.start(chat_id)
                return
            except Exception as exc:
                print(f"[listener] Join call failed: {type(exc).__name__}: {exc}")
                await asyncio.sleep(delay)
        raise RuntimeError("Failed to join group call after retries")
