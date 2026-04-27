CREATE TABLE IF NOT EXISTS telegram_users (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL UNIQUE,
    username TEXT,
    first_name TEXT,
    last_name TEXT,
    language_code TEXT,
    is_bot BOOLEAN NOT NULL DEFAULT FALSE,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    last_private_interaction_at TIMESTAMPTZ,
    total_interaction_count BIGINT NOT NULL DEFAULT 0,
    verse_request_count BIGINT NOT NULL DEFAULT 0,
    search_count BIGINT NOT NULL DEFAULT 0,
    inline_query_count BIGINT NOT NULL DEFAULT 0,
    callback_query_count BIGINT NOT NULL DEFAULT 0,
    successful_interaction_count BIGINT NOT NULL DEFAULT 0,
    failed_interaction_count BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS telegram_chats (
    id BIGSERIAL PRIMARY KEY,
    telegram_chat_id BIGINT NOT NULL UNIQUE,
    chat_type VARCHAR(32) NOT NULL,
    title TEXT,
    username TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    bot_added_at TIMESTAMPTZ,
    bot_removed_at TIMESTAMPTZ,
    total_interaction_count BIGINT NOT NULL DEFAULT 0,
    verse_request_count BIGINT NOT NULL DEFAULT 0,
    search_count BIGINT NOT NULL DEFAULT 0,
    inline_query_count BIGINT NOT NULL DEFAULT 0,
    callback_query_count BIGINT NOT NULL DEFAULT 0,
    successful_interaction_count BIGINT NOT NULL DEFAULT 0,
    failed_interaction_count BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS telegram_chat_user_stats (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL REFERENCES telegram_chats(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES telegram_users(id) ON DELETE CASCADE,
    first_interaction_at TIMESTAMPTZ NOT NULL,
    last_interaction_at TIMESTAMPTZ NOT NULL,
    total_interaction_count BIGINT NOT NULL DEFAULT 0,
    verse_request_count BIGINT NOT NULL DEFAULT 0,
    search_count BIGINT NOT NULL DEFAULT 0,
    inline_query_count BIGINT NOT NULL DEFAULT 0,
    callback_query_count BIGINT NOT NULL DEFAULT 0,
    successful_interaction_count BIGINT NOT NULL DEFAULT 0,
    failed_interaction_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_telegram_chat_user UNIQUE (chat_id, user_id)
);

CREATE TABLE IF NOT EXISTS bot_usage_events (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(255) NOT NULL UNIQUE,
    telegram_update_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    chat_id BIGINT REFERENCES telegram_chats(id) ON DELETE SET NULL,
    user_id BIGINT REFERENCES telegram_users(id) ON DELETE SET NULL,
    message_text TEXT,
    query_text TEXT,
    parsed_reference TEXT,
    successful BOOLEAN,
    response_time_ms BIGINT,
    metadata JSONB,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_telegram_users_last_seen_at
    ON telegram_users (last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_telegram_chats_last_seen_at
    ON telegram_chats (last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_telegram_chats_active_type
    ON telegram_chats (is_active, chat_type);

CREATE INDEX IF NOT EXISTS idx_telegram_chat_user_stats_last_interaction_at
    ON telegram_chat_user_stats (last_interaction_at DESC);

CREATE INDEX IF NOT EXISTS idx_bot_usage_events_occurred_at
    ON bot_usage_events (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_bot_usage_events_chat_id_occurred_at
    ON bot_usage_events (chat_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_bot_usage_events_user_id_occurred_at
    ON bot_usage_events (user_id, occurred_at DESC);
