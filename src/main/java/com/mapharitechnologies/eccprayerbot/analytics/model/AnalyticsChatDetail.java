package com.mapharitechnologies.eccprayerbot.analytics.model;

import java.time.Instant;

/**
 * Detailed analytics view for a single Telegram chat.
 */
public record AnalyticsChatDetail(
        long telegramChatId,
        String title,
        String username,
        String chatType,
        boolean active,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant botAddedAt,
        Instant botRemovedAt,
        long totalInteractions,
        long verseRequests,
        long searches,
        long inlineQueries,
        long callbackQueries,
        long successfulInteractions,
        long failedInteractions) {
}
