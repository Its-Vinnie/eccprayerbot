import asyncio
import os
import threading
import time
from queue import Queue, Empty
from typing import Optional

from pyrogram import Client
from pytgcalls import GroupCallFactory


class AudioChunker:
    def __init__(
        self,
        chunk_seconds: float,
        sample_rate: int = 48000,
        channels: int = 1,
        debug_audio: bool = False,
    ):
        self.sample_rate = sample_rate
        self.channels = channels
        self.sample_width = 2
        self.chunk_bytes = int(sample_rate * channels * self.sample_width * chunk_seconds)
        self.buffer = bytearray()
        self.lock = threading.Lock()
        self.queue: Queue[bytes] = Queue()
        self.debug_audio = debug_audio

    def add(self, raw: bytes) -> None:
        if not raw:
            return
        with self.lock:
            self.buffer.extend(raw)
            while len(self.buffer) >= self.chunk_bytes:
                chunk = bytes(self.buffer[: self.chunk_bytes])
                del self.buffer[: self.chunk_bytes]
                self.queue.put(chunk)
                if self.debug_audio:
                    print(f"[listener] Audio chunk queued: {len(chunk)} bytes (queue {self.queue.qsize()})")

    def get(self, timeout: float = 0.2) -> Optional[bytes]:
        try:
            return self.queue.get(timeout=timeout)
        except Empty:
            return None


class FileAudioTailer:
    def __init__(self, path: str, chunker: AudioChunker, debug_audio: bool = False):
        self.path = path
        self.chunker = chunker
        self.debug_audio = debug_audio
        self.thread = None
        self._stop = False
        self._offset = 0
        self._header_skipped = False

    def start(self):
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def stop(self):
        self._stop = True

    def _run(self):
        while not self._stop:
            if not os.path.exists(self.path):
                time.sleep(0.2)
                continue

            with open(self.path, "rb") as handle:
                handle.seek(self._offset)
                data = handle.read()

            if not data:
                time.sleep(0.2)
                continue

            audio = data
            if not self._header_skipped and self.path.endswith(".wav"):
                if self._offset == 0 and len(data) < 44:
                    time.sleep(0.2)
                    continue
                if self._offset == 0:
                    audio = data[44:]
                    self._header_skipped = True

            self._offset += len(data)
            if audio:
                self.chunker.add(audio)
                if self.debug_audio:
                    print(f"[listener] File tailer read {len(audio)} bytes (offset {self._offset})")


class TelegramGroupCallListener:
    def __init__(
        self,
        api_id: int,
        api_hash: str,
        session_name: str,
        session_string: str,
        chat_target: str,
        chunker: AudioChunker,
        debug_audio: bool = False,
        debug_audio_interval: float = 5.0,
        pytgcalls_logs: bool = False,
        debug_audio_signature: bool = False,
        receive_mode: str = "raw",
        file_output_path: str = "/tmp/voice_call.wav",
    ):
        # Pyrogram 2.x supports session strings via session_string parameter.
        self.client = Client(session_name, api_id=api_id, api_hash=api_hash, session_string=session_string)
        self.chat_target = chat_target
        self.chunker = chunker
        self.group_call = None
        self.thread = None
        self.debug_audio = debug_audio
        self.debug_audio_interval = debug_audio_interval
        self.pytgcalls_logs = pytgcalls_logs
        self.debug_audio_signature = debug_audio_signature
        self.receive_mode = receive_mode
        self.file_output_path = file_output_path
        self._debug_last_log = time.time()
        self._debug_bytes = 0
        self._debug_calls = 0
        self._debug_signature_logged = False
        self._file_tailer = None

    def _on_recorded_data(self, *args, **kwargs):
        # PyTgCalls passes raw PCM bytes; signature differs by version.
        candidates: list[bytes] = []
        for arg in args:
            if isinstance(arg, (bytes, bytearray)):
                candidates.append(bytes(arg))
            else:
                data_attr = getattr(arg, "data", None)
                if isinstance(data_attr, (bytes, bytearray)):
                    candidates.append(bytes(data_attr))
                frame_attr = getattr(arg, "frame", None)
                if isinstance(frame_attr, (bytes, bytearray)):
                    candidates.append(bytes(frame_attr))
        for key, value in kwargs.items():
            if isinstance(value, (bytes, bytearray)):
                candidates.append(bytes(value))
            else:
                data_attr = getattr(value, "data", None)
                if isinstance(data_attr, (bytes, bytearray)):
                    candidates.append(bytes(data_attr))
                frame_attr = getattr(value, "frame", None)
                if isinstance(frame_attr, (bytes, bytearray)):
                    candidates.append(bytes(frame_attr))

        data = None
        best_score = -1
        for buf in candidates:
            if not buf:
                continue
            # sample non-zero bytes to avoid full scan
            step = max(1, len(buf) // 256)
            score = sum(1 for i in range(0, len(buf), step) if buf[i] != 0)
            if score > best_score:
                best_score = score
                data = buf

        if self.debug_audio_signature and not self._debug_signature_logged:
            arg_types = [type(a).__name__ for a in args]
            kw_types = {k: type(v).__name__ for k, v in kwargs.items()}
            cand_sizes = [len(c) for c in candidates]
            print(f"[listener] Audio callback signature args={arg_types} kwargs={kw_types} candidates={cand_sizes}")
            self._debug_signature_logged = True

        if data:
            self.chunker.add(data)
            if self.debug_audio:
                self._debug_bytes += len(data)
                self._debug_calls += 1
                now = time.time()
                if now - self._debug_last_log >= self.debug_audio_interval:
                    queued = self.chunker.queue.qsize()
                    buf_len = len(self.chunker.buffer)
                    interval = now - self._debug_last_log
                    print(
                        f"[listener] Audio received: {self._debug_bytes} bytes, "
                        f"{self._debug_calls} callbacks in {interval:.1f}s "
                        f"(queue {queued}, buffer {buf_len})"
                    )
                    self._debug_last_log = now
                    self._debug_bytes = 0
                    self._debug_calls = 0

    def start(self):
        self.thread = threading.Thread(target=self._run_async, daemon=True)
        self.thread.start()

    def _run_async(self):
        asyncio.run(self._start_async())

    async def _start_async(self):
        await self.client.start()
        me = await self.client.get_me()
        print(f"[listener] Logged in as {me.id} @{me.username or 'no-username'} (bot={me.is_bot})")
        factory = GroupCallFactory(self.client, enable_logs_to_console=self.pytgcalls_logs)
        if self.receive_mode == "file":
            self.group_call = factory.get_file_group_call(output_filename=self.file_output_path)
            self._file_tailer = FileAudioTailer(self.file_output_path, self.chunker, self.debug_audio)
            self._file_tailer.start()
            print(f"[listener] File receive mode enabled -> {self.file_output_path}")
        else:
            self.group_call = factory.get_raw_group_call(on_recorded_data=self._on_recorded_data)

        chat = await self._resolve_chat(self.chat_target)
        chat_id = chat.id
        title = chat.title or chat.username or str(chat.id)
        print(f"[listener] Resolved chat target {self.chat_target} -> {chat_id} ({title})")
        await self._join_call_with_retry(chat_id)

        # Keep running
        await asyncio.Event().wait()

    async def _resolve_chat(self, target: str):
        if target.startswith("@"):
            target = target[1:]
        if target.startswith("t.me/"):
            target = target.replace("t.me/", "")
        return await self.client.get_chat(target)

    async def _join_call_with_retry(self, chat_id: int, retries: int = 20, delay: float = 5.0):
        for _ in range(retries):
            try:
                await self.group_call.start(chat_id)
                return
            except Exception as exc:
                print(f"[listener] Join call failed: {type(exc).__name__}: {exc}")
                await asyncio.sleep(delay)
        raise RuntimeError("Failed to join group call after retries")
