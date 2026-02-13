import os


from typing import Optional


def env(name: str, default: Optional[str] = None) -> Optional[str]:
    value = os.getenv(name)
    if value is None or value == "":
        return default
    return value


class Settings:
    # Telegram MTProto (user account) session
    tg_api_id = env("TG_API_ID")
    tg_api_hash = env("TG_API_HASH")
    tg_session_string = env("TG_SESSION_STRING")
    tg_session_name = env("TG_SESSION_NAME", "ecc_listener")

    # Telegram Bot (send messages)
    bot_token = env("TELEGRAM_BOT_TOKEN")
    target_chat = env("LISTENER_CHAT")  # username or chat id

    # Listener behavior
    enabled = env("LISTENER_ENABLED", "false").lower() == "true"
    chunk_seconds = float(env("LISTENER_CHUNK_SECONDS", "2"))
    debounce_seconds = float(env("LISTENER_DEBOUNCE_SECONDS", "3"))
    duplicate_cooldown = float(env("LISTENER_DUPLICATE_COOLDOWN_SECONDS", "30"))
    sample_rate = int(env("LISTENER_SAMPLE_RATE", "48000"))
    channels = int(env("LISTENER_CHANNELS", "1"))
    debug_audio = env("LISTENER_DEBUG_AUDIO", "true").lower() == "true"
    debug_audio_interval = float(env("LISTENER_DEBUG_AUDIO_INTERVAL_SECONDS", "5"))
    pytgcalls_logs = env("LISTENER_PYTG_LOGS", "true").lower() == "true"
    debug_audio_signature = env("LISTENER_DEBUG_AUDIO_SIGNATURE", "false").lower() == "true"
    receive_mode = env("LISTENER_RECEIVE_MODE", "raw").lower()
    file_output_path = env("LISTENER_FILE_OUTPUT_PATH", "/tmp/voice_call.wav")
    audio_source = env("LISTENER_AUDIO_SOURCE", "tgcaller").lower()
    audio_bridge_host = env("LISTENER_AUDIO_HOST", "127.0.0.1")
    audio_bridge_port = int(env("LISTENER_AUDIO_PORT", "5045"))
    audio_bridge_reconnect_seconds = float(env("LISTENER_AUDIO_RECONNECT_SECONDS", "5"))

    # Whisper
    whisper_model = env("WHISPER_MODEL", "base.en")
    whisper_compute_type = env("WHISPER_COMPUTE_TYPE", "int8")

    # Paraphrase matching
    verse_data_path = env("VERSE_DATA_PATH", "listener/data/verses-1769.json")
    embeddings_path = env("VERSE_EMBEDDINGS_PATH", "listener/data/verse_embeddings.npy")
    embeddings_meta_path = env("VERSE_EMBEDDINGS_META_PATH", "listener/data/verse_meta.json")
    paraphrase_enabled = env("PARAPHRASE_ENABLED", "true").lower() == "true"
    paraphrase_min_score = float(env("PARAPHRASE_MIN_SCORE", "0.55"))
    paraphrase_strong_score = float(env("PARAPHRASE_STRONG_SCORE", "0.65"))
    paraphrase_top_k = int(env("PARAPHRASE_TOP_K", "3"))

    # Bible API
    bible_api_base = env("BIBLE_API_BASE_URL", "https://rest.api.bible/v1")
    bible_api_key = env("BIBLE_API_KEY")
    youversion_base = env("YOUVERSION_BASE_URL", "https://api.youversion.com")
    youversion_api_key = env("YOUVERSION_API_KEY")

    # Defaults
    default_translation = env("DEFAULT_TRANSLATION", "KJV")

    @classmethod
    def validate(cls) -> None:
        required = {
            "TELEGRAM_BOT_TOKEN": cls.bot_token,
            "LISTENER_CHAT": cls.target_chat,
        }
        if cls.audio_source != "bridge":
            required.update(
                {
                    "TG_API_ID": cls.tg_api_id,
                    "TG_API_HASH": cls.tg_api_hash,
                    "TG_SESSION_STRING": cls.tg_session_string,
                }
            )
        missing = [k for k, v in required.items() if not v]
        if missing:
            raise RuntimeError(f"Missing required env vars: {', '.join(missing)}")
