package com.mapharitechnologies.eccprayerbot.analytics.service;

import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsDashboardSummary;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatDetail;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatSummary;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatUserUsage;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsPage;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsEventType;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.Instant;
import java.util.Optional;

/**
 * Analytics service abstraction with a no-op fallback when Supabase is disabled.
 */
public interface AnalyticsTrackingService {

    boolean isEnabled();

    void trackMessageInteraction(
            Integer updateId,
            Chat chat,
            User user,
            String messageText,
            AnalyticsEventType eventType,
            Boolean successful,
            Long responseTimeMs,
            String parsedReference,
            Instant occurredAt);

    void trackInlineInteraction(
            Integer updateId,
            User user,
            String queryText,
            Boolean successful,
            Long responseTimeMs,
            String parsedReference,
            Instant occurredAt);

    void trackCallbackInteraction(
            Integer updateId,
            Chat chat,
            User user,
            String callbackData,
            Boolean successful,
            Instant occurredAt);

    void trackMembershipUpdate(
            Integer updateId,
            Chat chat,
            User actor,
            String oldStatus,
            String newStatus,
            Instant occurredAt);

    AnalyticsDashboardSummary getDashboardSummary(int topLimit);

    AnalyticsPage<AnalyticsChatSummary> listChats(String type, Boolean active, String search,
                                                  boolean includePrivate, int limit, int offset);

    Optional<AnalyticsChatDetail> getChatDetail(long telegramChatId);

    AnalyticsPage<AnalyticsChatUserUsage> listChatUsers(long telegramChatId, int limit, int offset);
}
