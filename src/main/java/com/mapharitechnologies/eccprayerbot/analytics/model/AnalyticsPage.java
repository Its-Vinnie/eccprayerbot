package com.mapharitechnologies.eccprayerbot.analytics.model;

import java.util.List;

/**
 * Simple page wrapper for analytics responses.
 */
public record AnalyticsPage<T>(
        List<T> items,
        long total,
        int limit,
        int offset) {
}
