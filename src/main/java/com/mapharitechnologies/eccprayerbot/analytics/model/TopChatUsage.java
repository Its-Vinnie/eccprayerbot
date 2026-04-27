package com.mapharitechnologies.eccprayerbot.analytics.model;

/**
 * Ranking entry for chat usage.
 */
public record TopChatUsage(
        long telegramChatId,
        String title,
        String chatType,
        boolean active,
        long totalInteractions,
        long verseRequests,
        long searches,
        long successfulInteractions,
        long failedInteractions) {
}
