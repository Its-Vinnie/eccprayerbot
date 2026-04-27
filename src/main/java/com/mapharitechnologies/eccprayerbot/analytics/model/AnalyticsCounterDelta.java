package com.mapharitechnologies.eccprayerbot.analytics.model;

/**
 * Aggregated counter increments derived from a single analytics event.
 */
public record AnalyticsCounterDelta(
        long totalInteractions,
        long verseRequests,
        long searches,
        long inlineQueries,
        long callbackQueries,
        long successfulInteractions,
        long failedInteractions) {

    public static AnalyticsCounterDelta from(AnalyticsEventType eventType, Boolean successful) {
        long success = Boolean.TRUE.equals(successful) && eventType.incrementsTotalInteractions() ? 1 : 0;
        long failure = Boolean.FALSE.equals(successful) && eventType.incrementsTotalInteractions() ? 1 : 0;

        return new AnalyticsCounterDelta(
                eventType.incrementsTotalInteractions() ? 1 : 0,
                eventType.incrementsVerseRequests() ? 1 : 0,
                eventType.incrementsSearches() ? 1 : 0,
                eventType.incrementsInlineQueries() ? 1 : 0,
                eventType.incrementsCallbackQueries() ? 1 : 0,
                success,
                failure
        );
    }
}
