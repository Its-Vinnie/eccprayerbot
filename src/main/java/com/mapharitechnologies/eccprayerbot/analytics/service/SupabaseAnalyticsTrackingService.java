package com.mapharitechnologies.eccprayerbot.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatDetail;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatSummary;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatUserUsage;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsCounterDelta;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsDashboardSummary;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsEventType;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsPage;
import com.mapharitechnologies.eccprayerbot.analytics.model.TopChatUsage;
import com.mapharitechnologies.eccprayerbot.analytics.model.TopUserUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Production analytics writer backed by Supabase Postgres.
 */
@Service
@ConditionalOnBean(NamedParameterJdbcTemplate.class)
public class SupabaseAnalyticsTrackingService implements AnalyticsTrackingService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAnalyticsTrackingService.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SupabaseAnalyticsTrackingService(NamedParameterJdbcTemplate analyticsJdbcTemplate,
                                            ObjectMapper objectMapper) {
        this.jdbcTemplate = analyticsJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    @Async
    public void trackMessageInteraction(Integer updateId, Chat chat, User user, String messageText,
                                        AnalyticsEventType eventType, Boolean successful, Long responseTimeMs,
                                        String parsedReference, Instant occurredAt) {
        persistEvent(updateId, chat, user, eventType, messageText, null, parsedReference, successful,
                responseTimeMs, occurredAt, Map.of());
    }

    @Override
    @Async
    public void trackInlineInteraction(Integer updateId, User user, String queryText, Boolean successful,
                                       Long responseTimeMs, String parsedReference, Instant occurredAt) {
        persistEvent(updateId, null, user, AnalyticsEventType.INLINE_QUERY, null, queryText, parsedReference,
                successful, responseTimeMs, occurredAt, Map.of("source", "inline_query"));
    }

    @Override
    @Async
    public void trackCallbackInteraction(Integer updateId, Chat chat, User user, String callbackData,
                                         Boolean successful, Instant occurredAt) {
        persistEvent(updateId, chat, user, AnalyticsEventType.CALLBACK_QUERY, null, callbackData, null,
                successful, null, occurredAt, Map.of("callback_data", callbackData));
    }

    @Override
    @Async
    public void trackMembershipUpdate(Integer updateId, Chat chat, User actor, String oldStatus,
                                      String newStatus, Instant occurredAt) {
        AnalyticsEventType eventType = isActiveStatus(newStatus)
                ? AnalyticsEventType.BOT_ADDED_TO_CHAT
                : AnalyticsEventType.BOT_REMOVED_FROM_CHAT;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("old_status", oldStatus);
        metadata.put("new_status", newStatus);
        metadata.put("actor_user_id", actor != null ? actor.getId() : null);
        persistEvent(updateId, chat, actor, eventType, null, null, null, null, null, occurredAt, metadata);
    }

    @Override
    public AnalyticsDashboardSummary getDashboardSummary(int topLimit) {
        if (topLimit < 1) {
            topLimit = 10;
        }

        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT
                    (SELECT COUNT(*) FROM telegram_users) AS total_known_users,
                    (SELECT COUNT(*) FROM telegram_users
                        WHERE last_seen_at >= NOW() - INTERVAL '30 days') AS active_users_last_30_days,
                    (SELECT COUNT(*) FROM telegram_chats) AS total_known_chats,
                    (SELECT COUNT(*) FROM telegram_chats
                        WHERE last_seen_at >= NOW() - INTERVAL '30 days') AS active_chats_last_30_days,
                    (SELECT COUNT(*) FROM telegram_chats
                        WHERE chat_type IN ('group', 'supergroup', 'channel')) AS installed_group_chats,
                    (SELECT COUNT(*) FROM telegram_chats
                        WHERE chat_type IN ('group', 'supergroup', 'channel')
                          AND is_active = TRUE) AS active_installed_group_chats,
                    (SELECT COALESCE(SUM(total_interaction_count), 0) FROM telegram_users) AS total_interactions,
                    (SELECT COALESCE(SUM(successful_interaction_count), 0) FROM telegram_users) AS successful_interactions,
                    (SELECT COALESCE(SUM(failed_interaction_count), 0) FROM telegram_users) AS failed_interactions
                """, Map.of());

        return new AnalyticsDashboardSummary(
                true,
                ((Number) summary.get("total_known_users")).longValue(),
                ((Number) summary.get("active_users_last_30_days")).longValue(),
                ((Number) summary.get("total_known_chats")).longValue(),
                ((Number) summary.get("active_chats_last_30_days")).longValue(),
                ((Number) summary.get("installed_group_chats")).longValue(),
                ((Number) summary.get("active_installed_group_chats")).longValue(),
                ((Number) summary.get("total_interactions")).longValue(),
                ((Number) summary.get("successful_interactions")).longValue(),
                ((Number) summary.get("failed_interactions")).longValue(),
                jdbcTemplate.query("""
                        SELECT telegram_user_id, username, first_name, total_interaction_count,
                               verse_request_count, search_count, successful_interaction_count,
                               failed_interaction_count
                        FROM telegram_users
                        WHERE total_interaction_count > 0
                        ORDER BY total_interaction_count DESC, last_seen_at DESC
                        LIMIT :limit
                        """, Map.of("limit", topLimit), topUserMapper()),
                jdbcTemplate.query("""
                        SELECT telegram_chat_id, title, chat_type, is_active, total_interaction_count,
                               verse_request_count, search_count, successful_interaction_count,
                               failed_interaction_count
                        FROM telegram_chats
                        WHERE total_interaction_count > 0
                        ORDER BY total_interaction_count DESC, last_seen_at DESC
                        LIMIT :limit
                        """, Map.of("limit", topLimit), topChatMapper())
        );
    }

    @Override
    public AnalyticsPage<AnalyticsChatSummary> listChats(String type, Boolean active, String search,
                                                         boolean includePrivate, int limit, int offset) {
        int safeLimit = normalizeLimit(limit, 20, 100);
        int safeOffset = Math.max(offset, 0);
        String normalizedType = emptyToNull(type);
        String normalizedSearch = emptyToNull(search);

        StringBuilder whereClause = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", safeLimit)
                .addValue("offset", safeOffset);

        if (normalizedType != null) {
            whereClause.append(" AND chat_type = :chat_type");
            params.addValue("chat_type", normalizedType.toLowerCase());
        } else if (!includePrivate) {
            whereClause.append(" AND chat_type IN (:visible_types)");
            params.addValue("visible_types", List.of("group", "supergroup", "channel"));
        }

        if (active != null) {
            whereClause.append(" AND is_active = :active");
            params.addValue("active", active);
        }

        if (normalizedSearch != null) {
            whereClause.append("""
                     AND (
                         LOWER(COALESCE(title, '')) LIKE :search
                         OR LOWER(COALESCE(username, '')) LIKE :search
                     )
                    """);
            params.addValue("search", "%" + normalizedSearch.toLowerCase() + "%");
        }

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM telegram_chats" + whereClause,
                params,
                Long.class
        );

        List<AnalyticsChatSummary> items = jdbcTemplate.query("""
                SELECT telegram_chat_id, title, username, chat_type, is_active, first_seen_at,
                       last_seen_at, bot_added_at, bot_removed_at, total_interaction_count,
                       verse_request_count, search_count, successful_interaction_count,
                       failed_interaction_count
                FROM telegram_chats
                """ + whereClause + """
                ORDER BY last_seen_at DESC, telegram_chat_id DESC
                LIMIT :limit OFFSET :offset
                """, params, chatSummaryMapper());

        return new AnalyticsPage<>(items, total, safeLimit, safeOffset);
    }

    @Override
    public Optional<AnalyticsChatDetail> getChatDetail(long telegramChatId) {
        List<AnalyticsChatDetail> items = jdbcTemplate.query("""
                SELECT telegram_chat_id, title, username, chat_type, is_active, first_seen_at,
                       last_seen_at, bot_added_at, bot_removed_at, total_interaction_count,
                       verse_request_count, search_count, inline_query_count, callback_query_count,
                       successful_interaction_count, failed_interaction_count
                FROM telegram_chats
                WHERE telegram_chat_id = :telegram_chat_id
                LIMIT 1
                """, Map.of("telegram_chat_id", telegramChatId), chatDetailMapper());

        return items.stream().findFirst();
    }

    @Override
    public AnalyticsPage<AnalyticsChatUserUsage> listChatUsers(long telegramChatId, int limit, int offset) {
        int safeLimit = normalizeLimit(limit, 20, 100);
        int safeOffset = Math.max(offset, 0);
        Long chatId = jdbcTemplate.query("""
                SELECT id
                FROM telegram_chats
                WHERE telegram_chat_id = :telegram_chat_id
                LIMIT 1
                """, Map.of("telegram_chat_id", telegramChatId), rs -> rs.next() ? rs.getLong("id") : null);

        if (chatId == null) {
            return new AnalyticsPage<>(List.of(), 0, safeLimit, safeOffset);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("chat_id", chatId)
                .addValue("limit", safeLimit)
                .addValue("offset", safeOffset);

        long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM telegram_chat_user_stats
                WHERE chat_id = :chat_id
                """, params, Long.class);

        List<AnalyticsChatUserUsage> items = jdbcTemplate.query("""
                SELECT u.telegram_user_id, u.username, u.first_name, u.last_name,
                       s.first_interaction_at, s.last_interaction_at, s.total_interaction_count,
                       s.verse_request_count, s.search_count, s.inline_query_count,
                       s.callback_query_count, s.successful_interaction_count,
                       s.failed_interaction_count
                FROM telegram_chat_user_stats s
                JOIN telegram_users u ON u.id = s.user_id
                WHERE s.chat_id = :chat_id
                ORDER BY s.total_interaction_count DESC, s.last_interaction_at DESC, u.telegram_user_id DESC
                LIMIT :limit OFFSET :offset
                """, params, chatUserUsageMapper());

        return new AnalyticsPage<>(items, total, safeLimit, safeOffset);
    }

    private void persistEvent(Integer updateId, Chat chat, User user, AnalyticsEventType eventType,
                              String messageText, String queryText, String parsedReference,
                              Boolean successful, Long responseTimeMs, Instant occurredAt,
                              Map<String, Object> metadata) {
        Instant safeOccurredAt = occurredAt == null ? Instant.now() : occurredAt;
        AnalyticsCounterDelta delta = AnalyticsCounterDelta.from(eventType, successful);

        try {
            Long userId = upsertUser(user, chat, eventType, safeOccurredAt, delta);
            Long chatId = upsertChat(chat, user, eventType, safeOccurredAt, delta);

            if (userId != null && chatId != null && eventType.incrementsTotalInteractions()) {
                upsertChatUserStats(chatId, userId, safeOccurredAt, delta);
            }

            insertUsageEvent(buildEventKey(updateId, eventType, queryText), updateId, chatId, userId,
                    eventType, messageText, queryText, parsedReference, successful, responseTimeMs,
                    metadata, safeOccurredAt);
        } catch (DataAccessException e) {
            log.error("Failed to persist analytics event {}", eventType, e);
        } catch (Exception e) {
            log.error("Unexpected analytics failure for event {}", eventType, e);
        }
    }

    private Long upsertUser(User user, Chat chat, AnalyticsEventType eventType, Instant occurredAt,
                            AnalyticsCounterDelta delta) {
        if (user == null || user.getId() == null) {
            return null;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("telegram_user_id", user.getId())
                .addValue("username", emptyToNull(user.getUserName()))
                .addValue("first_name", emptyToNull(user.getFirstName()))
                .addValue("last_name", emptyToNull(user.getLastName()))
                .addValue("language_code", emptyToNull(user.getLanguageCode()))
                .addValue("is_bot", Boolean.TRUE.equals(user.getIsBot()))
                .addValue("occurred_at", timestamp(occurredAt))
                .addValue("last_private_interaction_at", isPrivateChat(chat) ? timestamp(occurredAt) : null)
                .addValue("total_interaction_count", delta.totalInteractions())
                .addValue("verse_request_count", delta.verseRequests())
                .addValue("search_count", delta.searches())
                .addValue("inline_query_count", delta.inlineQueries())
                .addValue("callback_query_count", delta.callbackQueries())
                .addValue("successful_interaction_count", delta.successfulInteractions())
                .addValue("failed_interaction_count", delta.failedInteractions());

        return jdbcTemplate.queryForObject("""
                INSERT INTO telegram_users (
                    telegram_user_id, username, first_name, last_name, language_code, is_bot,
                    first_seen_at, last_seen_at, last_private_interaction_at,
                    total_interaction_count, verse_request_count, search_count, inline_query_count,
                    callback_query_count, successful_interaction_count, failed_interaction_count
                ) VALUES (
                    :telegram_user_id, :username, :first_name, :last_name, :language_code, :is_bot,
                    :occurred_at, :occurred_at, :last_private_interaction_at,
                    :total_interaction_count, :verse_request_count, :search_count, :inline_query_count,
                    :callback_query_count, :successful_interaction_count, :failed_interaction_count
                )
                ON CONFLICT (telegram_user_id) DO UPDATE SET
                    username = COALESCE(EXCLUDED.username, telegram_users.username),
                    first_name = COALESCE(EXCLUDED.first_name, telegram_users.first_name),
                    last_name = COALESCE(EXCLUDED.last_name, telegram_users.last_name),
                    language_code = COALESCE(EXCLUDED.language_code, telegram_users.language_code),
                    is_bot = EXCLUDED.is_bot,
                    last_seen_at = GREATEST(telegram_users.last_seen_at, EXCLUDED.last_seen_at),
                    last_private_interaction_at = CASE
                        WHEN EXCLUDED.last_private_interaction_at IS NULL THEN telegram_users.last_private_interaction_at
                        WHEN telegram_users.last_private_interaction_at IS NULL THEN EXCLUDED.last_private_interaction_at
                        ELSE GREATEST(telegram_users.last_private_interaction_at, EXCLUDED.last_private_interaction_at)
                    END,
                    total_interaction_count = telegram_users.total_interaction_count + :total_interaction_count,
                    verse_request_count = telegram_users.verse_request_count + :verse_request_count,
                    search_count = telegram_users.search_count + :search_count,
                    inline_query_count = telegram_users.inline_query_count + :inline_query_count,
                    callback_query_count = telegram_users.callback_query_count + :callback_query_count,
                    successful_interaction_count = telegram_users.successful_interaction_count + :successful_interaction_count,
                    failed_interaction_count = telegram_users.failed_interaction_count + :failed_interaction_count
                RETURNING id
                """, params, Long.class);
    }

    private Long upsertChat(Chat chat, User user, AnalyticsEventType eventType, Instant occurredAt,
                            AnalyticsCounterDelta delta) {
        if (chat == null || chat.getId() == null) {
            return null;
        }

        boolean active = eventType != AnalyticsEventType.BOT_REMOVED_FROM_CHAT;
        Timestamp addedAt = eventType == AnalyticsEventType.BOT_ADDED_TO_CHAT ? timestamp(occurredAt) : null;
        Timestamp removedAt = eventType == AnalyticsEventType.BOT_REMOVED_FROM_CHAT ? timestamp(occurredAt) : null;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("telegram_chat_id", chat.getId())
                .addValue("chat_type", emptyToNull(chat.getType()))
                .addValue("title", deriveChatTitle(chat, user))
                .addValue("username", emptyToNull(chat.getUserName()))
                .addValue("is_active", active)
                .addValue("occurred_at", timestamp(occurredAt))
                .addValue("bot_added_at", addedAt)
                .addValue("bot_removed_at", removedAt)
                .addValue("total_interaction_count", delta.totalInteractions())
                .addValue("verse_request_count", delta.verseRequests())
                .addValue("search_count", delta.searches())
                .addValue("inline_query_count", delta.inlineQueries())
                .addValue("callback_query_count", delta.callbackQueries())
                .addValue("successful_interaction_count", delta.successfulInteractions())
                .addValue("failed_interaction_count", delta.failedInteractions());

        return jdbcTemplate.queryForObject("""
                INSERT INTO telegram_chats (
                    telegram_chat_id, chat_type, title, username, is_active,
                    first_seen_at, last_seen_at, bot_added_at, bot_removed_at,
                    total_interaction_count, verse_request_count, search_count, inline_query_count,
                    callback_query_count, successful_interaction_count, failed_interaction_count
                ) VALUES (
                    :telegram_chat_id, :chat_type, :title, :username, :is_active,
                    :occurred_at, :occurred_at, :bot_added_at, :bot_removed_at,
                    :total_interaction_count, :verse_request_count, :search_count, :inline_query_count,
                    :callback_query_count, :successful_interaction_count, :failed_interaction_count
                )
                ON CONFLICT (telegram_chat_id) DO UPDATE SET
                    chat_type = COALESCE(EXCLUDED.chat_type, telegram_chats.chat_type),
                    title = COALESCE(EXCLUDED.title, telegram_chats.title),
                    username = COALESCE(EXCLUDED.username, telegram_chats.username),
                    is_active = EXCLUDED.is_active,
                    last_seen_at = GREATEST(telegram_chats.last_seen_at, EXCLUDED.last_seen_at),
                    bot_added_at = CASE
                        WHEN EXCLUDED.bot_added_at IS NULL THEN telegram_chats.bot_added_at
                        WHEN telegram_chats.bot_added_at IS NULL THEN EXCLUDED.bot_added_at
                        ELSE LEAST(telegram_chats.bot_added_at, EXCLUDED.bot_added_at)
                    END,
                    bot_removed_at = CASE
                        WHEN EXCLUDED.bot_removed_at IS NULL THEN telegram_chats.bot_removed_at
                        WHEN telegram_chats.bot_removed_at IS NULL THEN EXCLUDED.bot_removed_at
                        ELSE GREATEST(telegram_chats.bot_removed_at, EXCLUDED.bot_removed_at)
                    END,
                    total_interaction_count = telegram_chats.total_interaction_count + :total_interaction_count,
                    verse_request_count = telegram_chats.verse_request_count + :verse_request_count,
                    search_count = telegram_chats.search_count + :search_count,
                    inline_query_count = telegram_chats.inline_query_count + :inline_query_count,
                    callback_query_count = telegram_chats.callback_query_count + :callback_query_count,
                    successful_interaction_count = telegram_chats.successful_interaction_count + :successful_interaction_count,
                    failed_interaction_count = telegram_chats.failed_interaction_count + :failed_interaction_count
                RETURNING id
                """, params, Long.class);
    }

    private void upsertChatUserStats(Long chatId, Long userId, Instant occurredAt, AnalyticsCounterDelta delta) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("chat_id", chatId)
                .addValue("user_id", userId)
                .addValue("occurred_at", timestamp(occurredAt))
                .addValue("total_interaction_count", delta.totalInteractions())
                .addValue("verse_request_count", delta.verseRequests())
                .addValue("search_count", delta.searches())
                .addValue("inline_query_count", delta.inlineQueries())
                .addValue("callback_query_count", delta.callbackQueries())
                .addValue("successful_interaction_count", delta.successfulInteractions())
                .addValue("failed_interaction_count", delta.failedInteractions());

        jdbcTemplate.update("""
                INSERT INTO telegram_chat_user_stats (
                    chat_id, user_id, first_interaction_at, last_interaction_at,
                    total_interaction_count, verse_request_count, search_count, inline_query_count,
                    callback_query_count, successful_interaction_count, failed_interaction_count
                ) VALUES (
                    :chat_id, :user_id, :occurred_at, :occurred_at,
                    :total_interaction_count, :verse_request_count, :search_count, :inline_query_count,
                    :callback_query_count, :successful_interaction_count, :failed_interaction_count
                )
                ON CONFLICT (chat_id, user_id) DO UPDATE SET
                    last_interaction_at = GREATEST(
                        telegram_chat_user_stats.last_interaction_at, EXCLUDED.last_interaction_at
                    ),
                    total_interaction_count = telegram_chat_user_stats.total_interaction_count + :total_interaction_count,
                    verse_request_count = telegram_chat_user_stats.verse_request_count + :verse_request_count,
                    search_count = telegram_chat_user_stats.search_count + :search_count,
                    inline_query_count = telegram_chat_user_stats.inline_query_count + :inline_query_count,
                    callback_query_count = telegram_chat_user_stats.callback_query_count + :callback_query_count,
                    successful_interaction_count = telegram_chat_user_stats.successful_interaction_count + :successful_interaction_count,
                    failed_interaction_count = telegram_chat_user_stats.failed_interaction_count + :failed_interaction_count
                """, params);
    }

    private void insertUsageEvent(String eventKey, Integer updateId, Long chatId, Long userId,
                                  AnalyticsEventType eventType, String messageText, String queryText,
                                  String parsedReference, Boolean successful, Long responseTimeMs,
                                  Map<String, Object> metadata, Instant occurredAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("event_key", eventKey)
                .addValue("telegram_update_id", updateId != null ? Long.valueOf(updateId) : null)
                .addValue("event_type", eventType.name())
                .addValue("chat_id", chatId)
                .addValue("user_id", userId)
                .addValue("message_text", emptyToNull(messageText))
                .addValue("query_text", emptyToNull(queryText))
                .addValue("parsed_reference", emptyToNull(parsedReference))
                .addValue("successful", successful)
                .addValue("response_time_ms", responseTimeMs)
                .addValue("metadata", toJson(metadata))
                .addValue("occurred_at", timestamp(occurredAt));

        jdbcTemplate.update("""
                INSERT INTO bot_usage_events (
                    event_key, telegram_update_id, event_type, chat_id, user_id,
                    message_text, query_text, parsed_reference, successful, response_time_ms,
                    metadata, occurred_at
                ) VALUES (
                    :event_key, :telegram_update_id, :event_type, :chat_id, :user_id,
                    :message_text, :query_text, :parsed_reference, :successful, :response_time_ms,
                    CAST(:metadata AS jsonb), :occurred_at
                )
                ON CONFLICT (event_key) DO NOTHING
                """, params);
    }

    private String buildEventKey(Integer updateId, AnalyticsEventType eventType, String discriminator) {
        StringBuilder key = new StringBuilder(eventType.name());
        if (updateId != null) {
            key.append(':').append(updateId);
        }
        if (StringUtils.hasText(discriminator)) {
            key.append(':').append(Math.abs(discriminator.hashCode()));
        }
        return key.toString();
    }

    private boolean isActiveStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        return switch (status.toLowerCase()) {
            case "member", "administrator", "restricted" -> true;
            default -> false;
        };
    }

    private boolean isPrivateChat(Chat chat) {
        return chat != null && "private".equalsIgnoreCase(chat.getType());
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String deriveChatTitle(Chat chat, User user) {
        if (chat == null) {
            return null;
        }
        if (StringUtils.hasText(chat.getTitle())) {
            return chat.getTitle();
        }
        if (isPrivateChat(chat) && user != null) {
            String fullName = (emptyToNull(user.getFirstName()) == null ? "" : user.getFirstName()) +
                    (emptyToNull(user.getLastName()) == null ? "" : " " + user.getLastName());
            return emptyToNull(fullName.trim());
        }
        return null;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize analytics metadata", e);
            return "{}";
        }
    }

    private int normalizeLimit(int requestedLimit, int defaultLimit, int maxLimit) {
        if (requestedLimit < 1) {
            return defaultLimit;
        }
        return Math.min(requestedLimit, maxLimit);
    }

    private Instant instant(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private RowMapper<AnalyticsChatSummary> chatSummaryMapper() {
        return (rs, rowNum) -> new AnalyticsChatSummary(
                rs.getLong("telegram_chat_id"),
                rs.getString("title"),
                rs.getString("username"),
                rs.getString("chat_type"),
                rs.getBoolean("is_active"),
                instant(rs, "first_seen_at"),
                instant(rs, "last_seen_at"),
                instant(rs, "bot_added_at"),
                instant(rs, "bot_removed_at"),
                rs.getLong("total_interaction_count"),
                rs.getLong("verse_request_count"),
                rs.getLong("search_count"),
                rs.getLong("successful_interaction_count"),
                rs.getLong("failed_interaction_count")
        );
    }

    private RowMapper<AnalyticsChatDetail> chatDetailMapper() {
        return (rs, rowNum) -> new AnalyticsChatDetail(
                rs.getLong("telegram_chat_id"),
                rs.getString("title"),
                rs.getString("username"),
                rs.getString("chat_type"),
                rs.getBoolean("is_active"),
                instant(rs, "first_seen_at"),
                instant(rs, "last_seen_at"),
                instant(rs, "bot_added_at"),
                instant(rs, "bot_removed_at"),
                rs.getLong("total_interaction_count"),
                rs.getLong("verse_request_count"),
                rs.getLong("search_count"),
                rs.getLong("inline_query_count"),
                rs.getLong("callback_query_count"),
                rs.getLong("successful_interaction_count"),
                rs.getLong("failed_interaction_count")
        );
    }

    private RowMapper<AnalyticsChatUserUsage> chatUserUsageMapper() {
        return (rs, rowNum) -> new AnalyticsChatUserUsage(
                rs.getLong("telegram_user_id"),
                rs.getString("username"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                instant(rs, "first_interaction_at"),
                instant(rs, "last_interaction_at"),
                rs.getLong("total_interaction_count"),
                rs.getLong("verse_request_count"),
                rs.getLong("search_count"),
                rs.getLong("inline_query_count"),
                rs.getLong("callback_query_count"),
                rs.getLong("successful_interaction_count"),
                rs.getLong("failed_interaction_count")
        );
    }

    private RowMapper<TopUserUsage> topUserMapper() {
        return (rs, rowNum) -> new TopUserUsage(
                rs.getLong("telegram_user_id"),
                rs.getString("username"),
                rs.getString("first_name"),
                rs.getLong("total_interaction_count"),
                rs.getLong("verse_request_count"),
                rs.getLong("search_count"),
                rs.getLong("successful_interaction_count"),
                rs.getLong("failed_interaction_count")
        );
    }

    private RowMapper<TopChatUsage> topChatMapper() {
        return (rs, rowNum) -> new TopChatUsage(
                rs.getLong("telegram_chat_id"),
                rs.getString("title"),
                rs.getString("chat_type"),
                rs.getBoolean("is_active"),
                rs.getLong("total_interaction_count"),
                rs.getLong("verse_request_count"),
                rs.getLong("search_count"),
                rs.getLong("successful_interaction_count"),
                rs.getLong("failed_interaction_count")
        );
    }
}
