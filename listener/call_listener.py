import asyncio
import threading
import time
from queue import Queue, Empty
from typing import Optional

import numpy as np
from pyrogram import Client
from tgcaller import AudioConfig, TgCaller, VideoConfig
from tgcaller.plugins import BasePlugin


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


class AudioCapturePlugin(BasePlugin):
    name = "audio_capture"

    def __init__(self, chunker: AudioChunker, debug_audio: bool = False, debug_signature: bool = False):
        super().__init__()
        self.chunker = chunker
        self.debug_audio = debug_audio
        self.debug_signature = debug_signature
        self._logged_signature = False

    async def process_audio(self, audio_frame):
        if self.debug_signature and not self._logged_signature:
            frame_type = type(audio_frame).__name__
            shape = getattr(audio_frame, "shape", None)
            dtype = getattr(audio_frame, "dtype", None)
            print(f"[listener] TgCaller audio frame type={frame_type} shape={shape} dtype={dtype}")
            self._logged_signature = True

        try:
            arr = np.asarray(audio_frame)
        except Exception:
            return audio_frame

        if arr.size == 0:
            return audio_frame

        if arr.dtype != np.int16:
            if np.issubdtype(arr.dtype, np.floating):
                arr = np.clip(arr * 32768.0, -32768, 32767).astype(np.int16)
            else:
                arr = arr.astype(np.int16)

        self.chunker.add(arr.tobytes())
        return audio_frame


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
        self.client = Client(session_name, api_id=api_id, api_hash=api_hash, session_string=session_string)
        self.chat_target = chat_target
        self.chunker = chunker
        self.thread = None
        self.debug_audio = debug_audio
        self.debug_audio_interval = debug_audio_interval
        self.debug_audio_signature = debug_audio_signature
        self._debug_last_log = time.time()
        self._debug_bytes = 0
        self._debug_calls = 0
        self._caller = None
        self._plugin = AudioCapturePlugin(chunker, debug_audio, debug_audio_signature)

    def start(self):
        self.thread = threading.Thread(target=self._run_async, daemon=True)
        self.thread.start()

    def _run_async(self):
        asyncio.run(self._start_async())

    async def _start_async(self):
        await self.client.start()
        me = await self.client.get_me()
        print(f"[listener] Logged in as {me.id} @{me.username or 'no-username'} (bot={me.is_bot})")

        self._caller = TgCaller(self.client)
        self._caller.register_plugin(self._plugin)
        await self._caller.start()

        chat = await self._resolve_chat(self.chat_target)
        chat_id = chat.id
        title = chat.title or chat.username or str(chat.id)
        print(f"[listener] Resolved chat target {self.chat_target} -> {chat_id} ({title})")

        await self._join_call_with_retry(chat_id)

        await asyncio.Event().wait()

    async def _resolve_chat(self, target: str):
        if target.startswith("@"):
            target = target[1:]
        if target.startswith("t.me/"):
            target = target.replace("t.me/", "")
        return await self.client.get_chat(target)

    async def _join_call_with_retry(self, chat_id: int, retries: int = 20, delay: float = 5.0):
        audio_config = AudioConfig.voice_call()
        video_config = VideoConfig.hd_720p()
        for _ in range(retries):
            try:
                await self._caller.join_call(chat_id, audio_config=audio_config, video_config=video_config)
                return
            except Exception as exc:
                print(f"[listener] Join call failed: {type(exc).__name__}: {exc}")
                await asyncio.sleep(delay)
        raise RuntimeError("Failed to join group call after retries")
