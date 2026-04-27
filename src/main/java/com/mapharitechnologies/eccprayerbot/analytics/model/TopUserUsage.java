package com.mapharitechnologies.eccprayerbot.analytics.model;

/**
 * Ranking entry for user usage.
 */
public record TopUserUsage(
        long telegramUserId,
        String username,
        String firstName,
        long totalInteractions,
        long verseRequests,
        long searches,
        long successfulInteractions,
        long failedInteractions) {
}
