package com.mapharitechnologies.eccprayerbot.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapharitechnologies.eccprayerbot.analytics.config.AnalyticsBackfillProperties;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsCounterDelta;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsEventType;
import com.mapharitechnologies.eccprayerbot.model.BotRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backfills historical MongoDB request logs into the Supabase analytics schema.
 */
@Service
@ConditionalOnBean({NamedParameterJdbcTemplate.class, PlatformTransactionManager.class})
public class LegacyMongoAnalyticsBackfillService {

    private static final Logger log = LoggerFactory.getLogger(LegacyMongoAnalyticsBackfillService.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final ZoneId backfillZoneId;

    public LegacyMongoAnalyticsBackfillService(NamedParameterJdbcTemplate jdbcTemplate,
                                               PlatformTransactionManager analyticsTransactionManager,
                                               ObjectMapper objectMapper,
                                               AnalyticsBackfillProperties backfillProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(analyticsTransactionManager);
        this.objectMapper = objectMapper;
        this.backfillZoneId = resolveZoneId(backfillProperties.getTimeZone());
    }

    public BackfillResult importRequest(BotRequest request) {
        if (request == null || request.getId() == null || request.getChatId() == null) {
            return BackfillResult.SKIPPED;
        }

        try {
            return transactionTemplate.execute(status -> importRequestTransactional(request));
        } catch (DataAccessException e) {
            log.error("Failed to backfill legacy request {}", request.getId(), e);
            return BackfillResult.FAILED;
        }
    }

    private BackfillResult importRequestTransactional(BotRequest request) {
        String eventKey = buildEventKey(request);
        if (usageEventExists(eventKey)) {
            return BackfillResult.SKIPPED;
        }

        AnalyticsEventType eventType = inferEventType(request);
        Boolean successful = request.getSuccessful();
        AnalyticsCounterDelta delta = AnalyticsCounterDelta.from(eventType, successful);
        Instant occurredAt = resolveOccurredAt(request);

        Long userId = upsertLegacyUser(request, occurredAt);
        Long chatId = upsertLegacyChat(request, occurredAt);

        if (!insertUsageEvent(eventKey, request, chatId, userId, eventType, occurredAt)) {
            return BackfillResult.SKIPPED;
        }

        if (userId != null) {
            incrementUserCounters(userId, occurredAt, delta, isPrivateChat(request.getChatId()));
        }
        if (chatId != null) {
            incrementChatCounters(chatId, occurredAt, delta);
        }
        if (userId != null && chatId != null && eventType.incrementsTotalInteractions()) {
            incrementChatUserStats(chatId, userId, occurredAt, delta);
        }

        return BackfillResult.IMPORTED;
    }

    private boolean usageEventExists(String eventKey) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM bot_usage_events
                WHERE event_key = :event_key
                """, Map.of("event_key", eventKey), Integer.class);
        return count != null && count > 0;
    }

    private Long upsertLegacyUser(BotRequest request, Instant occurredAt) {
        Long telegramUserId = normalizeUserId(request.getUserId());
        if (telegramUserId == null) {
            return null;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("telegram_user_id", telegramUserId)
                .addValue("username", emptyToNull(request.getUsername()))
                .addValue("first_name", emptyToNull(request.getFirstName()))
                .addValue("occurred_at", timestamp(occurredAt))
                .addValue("last_private_interaction_at", isPrivateChat(request.getChatId()) ? timestamp(occurredAt) : null);

        return jdbcTemplate.queryForObject("""
                INSERT INTO telegram_users (
                    telegram_user_id, username, first_name, last_name, language_code, is_bot,
                    first_seen_at, last_seen_at, last_private_interaction_at,
                    total_interaction_count, verse_request_count, search_count, inline_query_count,
                    callback_query_count, successful_interaction_count, failed_interaction_count
                ) VALUES (
                    :telegram_user_id, :username, :first_name, NULL, NULL, FALSE,
                    :occurred_at, :occurred_at, :last_private_interaction_at,
                    0, 0, 0, 0, 0, 0, 0
                )
                ON CONFLICT (telegram_user_id) DO UPDATE SET
                    username = COALESCE(EXCLUDED.username, telegram_users.username),
                    first_name = COALESCE(EXCLUDED.first_name, telegram_users.first_name),
                    last_seen_at = GREATEST(telegram_users.last_seen_at, EXCLUDED.last_seen_at),
                    last_private_interaction_at = CASE
                        WHEN EXCLUDED.last_private_interaction_at IS NULL THEN telegram_users.last_private_interaction_at
                        WHEN telegram_users.last_private_interaction_at IS NULL THEN EXCLUDED.last_private_interaction_at
                        ELSE GREATEST(telegram_users.last_private_interaction_at, EXCLUDED.last_private_interaction_at)
                    END
                RETURNING id
                """, params, Long.class);
    }

    private Long upsertLegacyChat(BotRequest request, Instant occurredAt) {
        Long telegramChatId = request.getChatId();
        if (telegramChatId == null) {
            return null;
        }

        String chatType = inferChatType(telegramChatId);
        String title = "private".equals(chatType) ? emptyToNull(request.getFirstName()) : null;
        String username = "private".equals(chatType) ? emptyToNull(request.getUsername()) : null;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("telegram_chat_id", telegramChatId)
                .addValue("chat_type", chatType)
                .addValue("title", title)
                .addValue("username", username)
                .addValue("occurred_at", timestamp(occurredAt));

        return jdbcTemplate.queryForObject("""
                INSERT INTO telegram_chats (
                    telegram_chat_id, chat_type, title, username, is_active,
                    first_seen_at, last_seen_at, bot_added_at, bot_removed_at,
                    total_interaction_count, verse_request_count, search_count, inline_query_count,
                    callback_query_count, successful_interaction_count, failed_interaction_count
                ) VALUES (
                    :telegram_chat_id, :chat_type, :title, :username, TRUE,
                    :occurred_at, :occurred_at, NULL, NULL,
                    0, 0, 0, 0, 0, 0, 0
                )
                ON CONFLICT (telegram_chat_id) DO UPDATE SET
                    chat_type = COALESCE(telegram_chats.chat_type, EXCLUDED.chat_type),
                    title = COALESCE(telegram_chats.title, EXCLUDED.title),
                    username = COALESCE(telegram_chats.username, EXCLUDED.username),
                    last_seen_at = GREATEST(telegram_chats.last_seen_at, EXCLUDED.last_seen_at)
                RETURNING id
                """, params, Long.class);
    }

    private boolean insertUsageEvent(String eventKey, BotRequest request, Long chatId, Long userId,
                                     AnalyticsEventType eventType, Instant occurredAt) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "legacy_mongo_backfill");
        metadata.put("legacy_request_id", request.getId());
        metadata.put("legacy_error_message", emptyToNull(request.getErrorMessage()));
        metadata.put("legacy_requested_at", request.getRequestedAt() != null ? request.getRequestedAt().toString() : null);
        metadata.put("legacy_responded_at", request.getRespondedAt() != null ? request.getRespondedAt().toString() : null);
        metadata.put("inferred_chat_type", inferChatType(request.getChatId()));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("event_key", eventKey)
                .addValue("event_type", eventType.name())
                .addValue("chat_id", chatId)
                .addValue("user_id", userId)
                .addValue("message_text", emptyToNull(request.getMessageText()))
                .addValue("query_text", null)
                .addValue("parsed_reference", emptyToNull(request.getParsedReference()))
                .addValue("successful", request.getSuccessful())
                .addValue("response_time_ms", request.getResponseTimeMs())
                .addValue("metadata", toJson(metadata))
                .addValue("occurred_at", timestamp(occurredAt));

        Long insertedId = jdbcTemplate.query("""
                INSERT INTO bot_usage_events (
                    event_key, telegram_update_id, event_type, chat_id, user_id,
                    message_text, query_text, parsed_reference, successful, response_time_ms,
                    metadata, occurred_at
                ) VALUES (
                    :event_key, NULL, :event_type, :chat_id, :user_id,
                    :message_text, :query_text, :parsed_reference, :successful, :response_time_ms,
                    CAST(:metadata AS jsonb), :occurred_at
                )
                ON CONFLICT (event_key) DO NOTHING
                RETURNING id
                """, params, rs -> rs.next() ? rs.getLong("id") : null);

        return insertedId != null;
    }

    private void incrementUserCounters(Long userId, Instant occurredAt, AnalyticsCounterDelta delta,
                                       boolean privateChat) {
        MapSqlParameterSource params = buildDeltaParams(delta, occurredAt)
                .addValue("id", userId)
                .addValue("last_private_interaction_at", privateChat ? timestamp(occurredAt) : null);

        jdbcTemplate.update("""
                UPDATE telegram_users
                SET last_seen_at = GREATEST(last_seen_at, :occurred_at),
                    last_private_interaction_at = CASE
                        WHEN :last_private_interaction_at IS NULL THEN last_private_interaction_at
                        WHEN last_private_interaction_at IS NULL THEN :last_private_interaction_at
                        ELSE GREATEST(last_private_interaction_at, :last_private_interaction_at)
                    END,
                    total_interaction_count = total_interaction_count + :total_interaction_count,
                    verse_request_count = verse_request_count + :verse_request_count,
                    search_count = search_count + :search_count,
                    inline_query_count = inline_query_count + :inline_query_count,
                    callback_query_count = callback_query_count + :callback_query_count,
                    successful_interaction_count = successful_interaction_count + :successful_interaction_count,
                    failed_interaction_count = failed_interaction_count + :failed_interaction_count
                WHERE id = :id
                """, params);
    }

    private void incrementChatCounters(Long chatId, Instant occurredAt, AnalyticsCounterDelta delta) {
        MapSqlParameterSource params = buildDeltaParams(delta, occurredAt)
                .addValue("id", chatId);

        jdbcTemplate.update("""
                UPDATE telegram_chats
                SET last_seen_at = GREATEST(last_seen_at, :occurred_at),
                    total_interaction_count = total_interaction_count + :total_interaction_count,
                    verse_request_count = verse_request_count + :verse_request_count,
                    search_count = search_count + :search_count,
                    inline_query_count = inline_query_count + :inline_query_count,
                    callback_query_count = callback_query_count + :callback_query_count,
                    successful_interaction_count = successful_interaction_count + :successful_interaction_count,
                    failed_interaction_count = failed_interaction_count + :failed_interaction_count
                WHERE id = :id
                """, params);
    }

    private void incrementChatUserStats(Long chatId, Long userId, Instant occurredAt, AnalyticsCounterDelta delta) {
        MapSqlParameterSource params = buildDeltaParams(delta, occurredAt)
                .addValue("chat_id", chatId)
                .addValue("user_id", userId);

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
                    first_interaction_at = LEAST(
                        telegram_chat_user_stats.first_interaction_at, EXCLUDED.first_interaction_at
                    ),
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

    private MapSqlParameterSource buildDeltaParams(AnalyticsCounterDelta delta, Instant occurredAt) {
        return new MapSqlParameterSource()
                .addValue("occurred_at", timestamp(occurredAt))
                .addValue("total_interaction_count", delta.totalInteractions())
                .addValue("verse_request_count", delta.verseRequests())
                .addValue("search_count", delta.searches())
                .addValue("inline_query_count", delta.inlineQueries())
                .addValue("callback_query_count", delta.callbackQueries())
                .addValue("successful_interaction_count", delta.successfulInteractions())
                .addValue("failed_interaction_count", delta.failedInteractions());
    }

    private AnalyticsEventType inferEventType(BotRequest request) {
        String messageText = request.getMessageText();
        if (StringUtils.hasText(messageText) && messageText.trim().toLowerCase().startsWith("/search")) {
            return AnalyticsEventType.SEARCH_REQUEST;
        }
        return AnalyticsEventType.VERSE_REQUEST;
    }

    private Instant resolveOccurredAt(BotRequest request) {
        LocalDateTime source = request.getRequestedAt();
        if (source == null) {
            source = request.getRespondedAt();
        }
        if (source == null) {
            return Instant.now();
        }
        return source.atZone(backfillZoneId).toInstant();
    }

    private String inferChatType(Long chatId) {
        if (chatId == null) {
            return "private";
        }
        if (chatId > 0) {
            return "private";
        }

        String value = Long.toString(chatId);
        if (value.startsWith("-100")) {
            return "supergroup";
        }
        return "group";
    }

    private boolean isPrivateChat(Long chatId) {
        return chatId != null && chatId > 0;
    }

    private Long normalizeUserId(Long userId) {
        return userId != null && userId > 0 ? userId : null;
    }

    private String buildEventKey(BotRequest request) {
        return "legacy_bot_request:" + request.getId();
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize legacy analytics metadata", e);
            return "{}";
        }
    }

    private ZoneId resolveZoneId(String configuredZone) {
        try {
            return StringUtils.hasText(configuredZone) ? ZoneId.of(configuredZone.trim()) : ZoneId.of("UTC");
        } catch (Exception e) {
            log.warn("Invalid analytics backfill time zone '{}', defaulting to UTC", configuredZone);
            return ZoneId.of("UTC");
        }
    }

    public enum BackfillResult {
        IMPORTED,
        SKIPPED,
        FAILED
    }
}
