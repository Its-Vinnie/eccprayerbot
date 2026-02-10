## ECC Prayer Bot Listener (Live Group Call)

This service joins a Telegram **group call** using a **user account** (MTProto),
transcribes live audio, detects Bible references (explicit or paraphrase), and
sends verses back to the group using the existing bot token.

### Why a user account?
The Telegram Bot API cannot join group calls. A user session is required.

### Required Env Vars
```
LISTENER_ENABLED=true

TG_API_ID=your_telegram_api_id
TG_API_HASH=your_telegram_api_hash
TG_SESSION_STRING=your_user_session_string
TG_SESSION_NAME=ecc_listener

LISTENER_CHAT=t.me/mapharitechnologies

TELEGRAM_BOT_TOKEN=existing_bot_token

API_BIBLE_KEY=...
YOUVERSION_API_KEY=...

DEFAULT_TRANSLATION=KJV

# Optional tuning
LISTENER_CHUNK_SECONDS=2
LISTENER_DEBOUNCE_SECONDS=3
LISTENER_DUPLICATE_COOLDOWN_SECONDS=30

WHISPER_MODEL=base.en
WHISPER_COMPUTE_TYPE=int8

PARAPHRASE_ENABLED=true
PARAPHRASE_MIN_SCORE=0.55
PARAPHRASE_STRONG_SCORE=0.65
PARAPHRASE_TOP_K=3

VERSE_DATA_PATH=listener/data/verses-1769.json
VERSE_EMBEDDINGS_PATH=listener/data/verse_embeddings.npy
VERSE_EMBEDDINGS_META_PATH=listener/data/verse_meta.json
```

### Python Version
This listener requires **Python 3.11+**.

Local setup:
```
python3.11 -m venv .venv311
source .venv311/bin/activate
python -m pip install -r listener/requirements.txt
python -m listener.app
```

### Verse Data Format
The listener accepts either:
1. JSON mapping (`.json`) of `reference -> verse text`
2. JSONL (`.jsonl`) with `{ "ref": "...", "text": "..." }`

Example JSONL:
```
{"ref":"John 3:16","text":"For God so loved the world..."}
```

### Notes
1. The listener will not run unless `LISTENER_ENABLED=true`.
2. `LISTENER_CHAT` can be a username, `t.me/...`, or a numeric chat id.
3. If the transcript doesn't include a translation, it defaults to `DEFAULT_TRANSLATION`.
4. When paraphrase confidence is low, it sends 2-3 possible matches.

### KJV Source
We use the public-domain KJV JSON mapping from:
```
https://unpkg.com/kjv@1.0.0/json/verses-1769.json
```

Download helper:
```
python3 listener/download_kjv.py
```
