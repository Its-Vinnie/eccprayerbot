import os
import subprocess
import tempfile
from typing import Iterable

from faster_whisper import WhisperModel


class WhisperTranscriber:
    def __init__(self, model_name: str, compute_type: str):
        self.model = WhisperModel(model_name, device="cpu", compute_type=compute_type)

    def transcribe_raw_pcm(self, raw_pcm: bytes, sample_rate: int = 48000, channels: int = 2) -> str:
        if not raw_pcm:
            return ""

        with tempfile.TemporaryDirectory() as tmpdir:
            raw_path = os.path.join(tmpdir, "chunk.raw")
            wav_path = os.path.join(tmpdir, "chunk.wav")

            with open(raw_path, "wb") as handle:
                handle.write(raw_pcm)

            cmd = [
                "ffmpeg",
                "-hide_banner",
                "-loglevel",
                "error",
                "-f",
                "s16le",
                "-ar",
                str(sample_rate),
                "-ac",
                str(channels),
                "-i",
                raw_path,
                "-ar",
                "16000",
                "-ac",
                "1",
                wav_path,
            ]
            subprocess.run(cmd, check=True)

            segments, _ = self.model.transcribe(wav_path, language="en", vad_filter=True)
            text = " ".join(seg.text.strip() for seg in segments if seg.text)
            return text.strip()

