package com.mapharitechnologies.eccprayerbot.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * API response DTOs for the Bible REST API.
 * These records decouple the public API contract from internal MongoDB models.
 */
public final class ApiDtos {

    private ApiDtos() {}

    // ─── Verse ───────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VerseResponse(
            String reference,
            String text,
            String translation,
            String versionName,
            String book,
            Integer chapter,
            Integer verse,
            Integer verseEnd
    ) {}

    // ─── Chapter ─────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChapterResponse(
            String reference,
            String book,
            Integer chapter,
            String translation,
            String versionName,
            List<VerseResult> verses
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VerseResult(
            Integer verse,
            String text
    ) {}

    // ─── Search ──────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SearchResponse(
            String query,
            List<SearchResult> results,
            int totalResults
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SearchResult(
            String reference,
            String text,
            String translation,
            String versionName
    ) {}

    // ─── Books ───────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BookResponse(
            String name,
            String abbreviation,
            String testament,
            int chapters
    ) {}

    // ─── Translations ────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TranslationResponse(
            String id,
            String name,
            String abbreviation,
            String provider,
            boolean available
    ) {}

    // ─── Error ───────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
            int status,
            String error,
            String message
    ) {}

    // ─── Admin: API Key Management ───────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiKeyResponse(
            String id,
            String key,
            String appName,
            int rateLimitPerMinute,
            boolean enabled,
            String createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateApiKeyRequest(
            String appName,
            Integer rateLimitPerMinute
    ) {}

    // ─── Forward: Send verse to Telegram ────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ForwardRequest(
            String ref,
            String translation,
            String chatId
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ForwardResponse(
            String reference,
            String text,
            String translation,
            String chatId,
            boolean forwarded
    ) {}

    // ─── Unified Query: Single endpoint for all Bible lookups ───────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QueryRequest(
            String query,
            String translation,
            String chatId
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QueryResponse(
            String type,
            String reference,
            String text,
            String translation,
            String versionName,
            String chatId,
            Boolean forwarded,
            List<SearchResult> searchResults
    ) {}
}
