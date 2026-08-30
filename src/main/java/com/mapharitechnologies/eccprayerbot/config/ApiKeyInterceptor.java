package com.mapharitechnologies.eccprayerbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapharitechnologies.eccprayerbot.model.ApiKey;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos;
import com.mapharitechnologies.eccprayerbot.repository.ApiKeyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepts requests to /api/v1/** and enforces:
 * 1. API key authentication via X-API-Key header
 * 2. Per-key rate limiting (sliding 1-minute window)
 *
 * Keys are cached in-memory for 5 minutes to avoid hitting MongoDB on every request.
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyInterceptor.class);
    private static final long KEY_CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Optional<ApiKeyRepository> apiKeyRepository;
    private final boolean authEnabled;
    private final int defaultRateLimit;

    /** key string → CachedApiKey (key data + cache timestamp). */
    private final ConcurrentHashMap<String, CachedApiKey> keyCache = new ConcurrentHashMap<>();

    /** key string → [windowMinute, requestCount]. */
    private final ConcurrentHashMap<String, long[]> rateLimits = new ConcurrentHashMap<>();

    public ApiKeyInterceptor(
            Optional<ApiKeyRepository> apiKeyRepository,
            @Value("${api.auth.enabled:true}") boolean authEnabled,
            @Value("${api.rate-limit.default-rpm:60}") int defaultRateLimit) {
        this.apiKeyRepository = apiKeyRepository;
        this.authEnabled = authEnabled;
        this.defaultRateLimit = defaultRateLimit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        if (!authEnabled || apiKeyRepository.isEmpty()) {
            return true; // Auth disabled or no DB — let everything through
        }

        String apiKeyValue = request.getHeader("X-API-Key");
        if (apiKeyValue == null || apiKeyValue.isBlank()) {
            sendError(response, 401, "Unauthorized", "Missing API key. Include X-API-Key header.");
            return false;
        }

        // Look up the key (with caching)
        ApiKey apiKey = lookupKey(apiKeyValue);
        if (apiKey == null || !apiKey.isEnabled()) {
            sendError(response, 401, "Unauthorized", "Invalid or disabled API key.");
            return false;
        }

        // Rate limit check
        int rpm = apiKey.getRateLimitPerMinute() > 0
                ? apiKey.getRateLimitPerMinute()
                : defaultRateLimit;

        if (!tryAcquireRateLimit(apiKeyValue, rpm)) {
            response.setHeader("Retry-After", "60");
            sendError(response, 429, "Too Many Requests",
                    "Rate limit exceeded (" + rpm + " requests/minute). Try again later.");
            return false;
        }

        // Update last-used timestamp asynchronously (fire-and-forget)
        updateLastUsed(apiKey);

        // Attach consumer info so controllers can use it
        request.setAttribute("apiKeyAppName", apiKey.getAppName());
        request.setAttribute("apiKeyRpm", rpm);

        return true;
    }

    // ─── Key lookup with cache ───────────────────────────────────────────

    private ApiKey lookupKey(String keyValue) {
        CachedApiKey cached = keyCache.get(keyValue);

        if (cached != null && !cached.isExpired()) {
            return cached.apiKey();
        }

        // Cache miss or expired — fetch from DB
        return apiKeyRepository.get().findByKeyAndEnabledTrue(keyValue)
                .map(apiKey -> {
                    keyCache.put(keyValue, new CachedApiKey(apiKey, System.currentTimeMillis()));
                    return apiKey;
                })
                .orElse(null);
    }

    private void updateLastUsed(ApiKey apiKey) {
        // Fire-and-forget: update lastUsedAt without blocking the request
        try {
            apiKey.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.get().save(apiKey);
        } catch (Exception e) {
            log.debug("Failed to update lastUsedAt for key {}: {}", apiKey.getId(), e.getMessage());
        }
    }

    // ─── Rate limiting (sliding 1-minute window) ─────────────────────────

    private boolean tryAcquireRateLimit(String key, int maxPerMinute) {
        long now = System.currentTimeMillis();
        long currentWindow = now / 60_000; // 1-minute buckets

        long[] entry = rateLimits.compute(key, (k, existing) -> {
            if (existing == null || existing[0] != currentWindow) {
                return new long[]{currentWindow, 1};
            }
            existing[1]++;
            return existing;
        });

        return entry[1] <= maxPerMinute;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private void sendError(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiDtos.ErrorResponse body = new ApiDtos.ErrorResponse(status, error, message);
        response.getWriter().write(mapper.writeValueAsString(body));
    }

    private record CachedApiKey(ApiKey apiKey, long cachedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > KEY_CACHE_TTL_MS;
        }
    }
}
