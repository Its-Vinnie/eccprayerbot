package com.mapharitechnologies.eccprayerbot.analytics.service;

import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsDashboardSummary;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatDetail;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatSummary;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatUserUsage;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsPage;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsEventType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    @Override
    public AnalyticsPage<AnalyticsChatSummary> listChats(String type, Boolean active, String search,
                                                         boolean includePrivate, int limit, int offset) {
        return new AnalyticsPage<>(List.of(), 0, limit, offset);
    }

    @Override
    public Optional<AnalyticsChatDetail> getChatDetail(long telegramChatId) {
        return Optional.empty();
    }

    @Override
    public AnalyticsPage<AnalyticsChatUserUsage> listChatUsers(long telegramChatId, int limit, int offset) {
        return new AnalyticsPage<>(List.of(), 0, limit, offset);
    }
}
