package com.mapharitechnologies.eccprayerbot.analytics.model;

/**
 * Supported analytics event types.
 */
public enum AnalyticsEventType {
    START_COMMAND,
    PRIVATE_MESSAGE,
    GROUP_MESSAGE,
    VERSE_REQUEST,
    SEARCH_REQUEST,
    INLINE_QUERY,
    CALLBACK_QUERY,
    BOT_ADDED_TO_CHAT,
    BOT_REMOVED_FROM_CHAT;

    public boolean incrementsTotalInteractions() {
        return switch (this) {
            case START_COMMAND, PRIVATE_MESSAGE, VERSE_REQUEST, SEARCH_REQUEST, INLINE_QUERY, CALLBACK_QUERY -> true;
            case GROUP_MESSAGE, BOT_ADDED_TO_CHAT, BOT_REMOVED_FROM_CHAT -> false;
        };
    }

    public boolean incrementsVerseRequests() {
        return this == VERSE_REQUEST;
    }

    public boolean incrementsSearches() {
        return this == SEARCH_REQUEST;
    }

    public boolean incrementsInlineQueries() {
        return this == INLINE_QUERY;
    }

    public boolean incrementsCallbackQueries() {
        return this == CALLBACK_QUERY;
    }
}
