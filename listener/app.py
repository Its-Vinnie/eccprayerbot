import hashlib
import math
import struct
import time

from listener.bible_fetcher import BibleFetcher, format_for_telegram
from listener.call_listener import AudioChunker, TelegramGroupCallListener
from listener.config import Settings
from listener.matcher import VerseMatcher
from listener.ref_parser import parse_reference
from listener.telegram_sender import TelegramSender
from listener.transcriber import WhisperTranscriber


def normalize_chat_target(target: str) -> str:
    if target.startswith("t.me/"):
        target = target.replace("t.me/", "")
    if not target.startswith("@") and not target.lstrip("-").isdigit():
        target = "@" + target
    return target


def main() -> None:
    if not Settings.enabled:
        print("Listener disabled. Set LISTENER_ENABLED=true to run.")
        return

    Settings.validate()

    chunker = AudioChunker(
        chunk_seconds=Settings.chunk_seconds,
        sample_rate=Settings.sample_rate,
        channels=Settings.channels,
        debug_audio=Settings.debug_audio,
    )
    listener = TelegramGroupCallListener(
        api_id=int(Settings.tg_api_id),
        api_hash=Settings.tg_api_hash,
        session_name=Settings.tg_session_name,
        session_string=Settings.tg_session_string,
        chat_target=Settings.target_chat,
        chunker=chunker,
        debug_audio=Settings.debug_audio,
        debug_audio_interval=Settings.debug_audio_interval,
        pytgcalls_logs=Settings.pytgcalls_logs,
    )

    transcriber = WhisperTranscriber(Settings.whisper_model, Settings.whisper_compute_type)

    verse_index = None
    if Settings.paraphrase_enabled:
        from listener.verse_index import VerseIndex
        verse_index = VerseIndex(
            Settings.verse_data_path,
            Settings.embeddings_path,
            Settings.embeddings_meta_path,
            "all-MiniLM-L6-v2",
        )
        verse_index.load()

    matcher = VerseMatcher(
        verse_index=verse_index,
        default_translation=Settings.default_translation,
        min_score=Settings.paraphrase_min_score,
        strong_score=Settings.paraphrase_strong_score,
        top_k=Settings.paraphrase_top_k,
    )

    fetcher = BibleFetcher(
        Settings.bible_api_base,
        Settings.bible_api_key,
        Settings.youversion_base,
        Settings.youversion_api_key,
    )

    sender = TelegramSender(Settings.bot_token, normalize_chat_target(Settings.target_chat))

    listener.start()

    last_sent = {}
    last_transcript_hash = None
    last_transcript_time = 0.0

    while True:
        chunk = chunker.get(timeout=0.5)
        if not chunk:
            continue

        if Settings.debug_audio:
            sample_count = len(chunk) // 2
            rms = 0.0
            if sample_count > 0:
                samples = struct.unpack("<" + "h" * sample_count, chunk)
                mean_sq = sum((s * s) for s in samples) / sample_count
                rms = math.sqrt(mean_sq)
                dbfs = 20.0 * math.log10(rms / 32768.0) if rms > 0 else -120.0
                print(f"[listener] Processing chunk: {len(chunk)} bytes, rms={rms:.1f}, dBFS={dbfs:.1f}")
            else:
                print(f"[listener] Processing chunk: {len(chunk)} bytes, rms=0.0, dBFS=-120.0")
        transcript = transcriber.transcribe_raw_pcm(
            chunk,
            sample_rate=Settings.sample_rate,
            channels=Settings.channels,
        )
        if not transcript:
            continue
        print(f"[listener] Transcript: {transcript}")

        now = time.time()
        if now - last_transcript_time < Settings.debounce_seconds:
            continue

        transcript_hash = hashlib.sha1(transcript.encode("utf-8")).hexdigest()
        if transcript_hash == last_transcript_hash:
            continue

        last_transcript_hash = transcript_hash
        last_transcript_time = now

        match = matcher.detect(transcript)
        if not match:
            print("[listener] No match found")
            continue

        # Explicit reference
        if match.reference:
            ref = match.reference
            translation = match.translation or Settings.default_translation
            print(f"[listener] Explicit reference: {ref.display()} ({translation})")

            key = f"{ref.display()}:{translation}"
            if key in last_sent and now - last_sent[key] < Settings.duplicate_cooldown:
                continue

            verse = fetcher.fetch(ref, translation)
            if not verse:
                print("[listener] Failed to fetch verse")
                continue

            sender.send_chunks(format_for_telegram(verse))
            print("[listener] Sent verse")
            last_sent[key] = now
            continue

        # Paraphrase candidates
        candidates = match.candidates
        if not candidates:
            continue

        top_score = candidates[0][1]
        strong = matcher.is_strong(top_score)
        max_count = 1 if strong else Settings.paraphrase_top_k
        print(f"[listener] Paraphrase candidates: {[(c.ref, s) for c, s in candidates]}")

        for idx, (candidate, score) in enumerate(candidates[:max_count]):
            ref = parse_reference(candidate.ref)
            if not ref:
                continue

            translation = match.translation or Settings.default_translation
            key = f"{ref.display()}:{translation}"
            if key in last_sent and now - last_sent[key] < Settings.duplicate_cooldown:
                continue

            verse = fetcher.fetch(ref, translation)
            if not verse:
                continue

            message = format_for_telegram(verse)
            if not strong:
                message = f"<i>Possible match from paraphrase (score {score:.2f})</i>\n\n" + message
            sender.send_chunks(message)
            last_sent[key] = now

        time.sleep(0.2)


if __name__ == "__main__":
    main()
