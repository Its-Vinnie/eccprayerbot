package com.mapharitechnologies.eccprayerbot.analytics.service;

import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsDashboardSummary;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsEventType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.Instant;

/**
 * Fallback analytics implementation used when Supabase tracking is disabled.
 */
@Service
@ConditionalOnProperty(prefix = "analytics.supabase", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpAnalyticsTrackingService implements AnalyticsTrackingService {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void trackMessageInteraction(Integer updateId, Chat chat, User user, String messageText,
                                        AnalyticsEventType eventType, Boolean successful, Long responseTimeMs,
                                        String parsedReference, Instant occurredAt) {
        // Analytics disabled.
    }

    @Override
    public void trackInlineInteraction(Integer updateId, User user, String queryText, Boolean successful,
                                       Long responseTimeMs, String parsedReference, Instant occurredAt) {
        // Analytics disabled.
    }

    @Override
    public void trackCallbackInteraction(Integer updateId, Chat chat, User user, String callbackData,
                                         Boolean successful, Instant occurredAt) {
        // Analytics disabled.
    }

    @Override
    public void trackMembershipUpdate(Integer updateId, Chat chat, User actor, String oldStatus,
                                      String newStatus, Instant occurredAt) {
        // Analytics disabled.
    }

    @Override
    public AnalyticsDashboardSummary getDashboardSummary(int topLimit) {
        return AnalyticsDashboardSummary.disabled();
    }
}
