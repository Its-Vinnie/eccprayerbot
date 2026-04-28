package com.mapharitechnologies.eccprayerbot.analytics.model;

import java.time.Instant;

/**
 * Per-user usage summary inside a specific chat.
 */
public record AnalyticsChatUserUsage(
        long telegramUserId,
        String username,
        String firstName,
        String lastName,
        Instant firstInteractionAt,
        Instant lastInteractionAt,
        long totalInteractions,
        long verseRequests,
        long searches,
        long inlineQueries,
        long callbackQueries,
        long successfulInteractions,
        long failedInteractions) {
}
