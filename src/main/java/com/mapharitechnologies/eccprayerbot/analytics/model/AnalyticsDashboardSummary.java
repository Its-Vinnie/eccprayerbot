package com.mapharitechnologies.eccprayerbot.analytics.model;

import java.util.List;

/**
 * Summary returned by the analytics endpoint.
 */
public record AnalyticsDashboardSummary(
        boolean enabled,
        long totalKnownUsers,
        long activeUsersLast30Days,
        long totalKnownChats,
        long activeChatsLast30Days,
        long installedGroupChats,
        long activeInstalledGroupChats,
        long totalInteractions,
        long successfulInteractions,
        long failedInteractions,
        List<TopUserUsage> topUsers,
        List<TopChatUsage> topChats) {

    public static AnalyticsDashboardSummary disabled() {
        return new AnalyticsDashboardSummary(
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of()
        );
    }
}
