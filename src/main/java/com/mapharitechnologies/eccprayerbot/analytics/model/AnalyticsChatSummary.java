package com.mapharitechnologies.eccprayerbot.analytics.model;

import java.time.Instant;

/**
 * Summary view of a tracked Telegram chat.
 */
public record AnalyticsChatSummary(
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
        long successfulInteractions,
        long failedInteractions) {
}
