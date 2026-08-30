package com.mapharitechnologies.eccprayerbot.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Represents an API key for authenticating external consumers (e.g., Mount Zion app).
 * Each key is tied to a specific application and has its own rate limit.
 */
@Document(collection = "api_keys")
public class ApiKey {

    @Id
    private String id;

    /** The actual API key string (UUID-based). */
    private String key;

    /** Human-readable name of the consuming application. */
    private String appName;

    /** Maximum requests allowed per minute for this key. */
    private int rateLimitPerMinute;

    /** Whether this key is currently active. */
    private boolean enabled;

    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;

    public ApiKey() {
    }

    public ApiKey(String id, String key, String appName, int rateLimitPerMinute,
                  boolean enabled, LocalDateTime createdAt, LocalDateTime lastUsedAt) {
        this.id = id;
        this.key = key;
        this.appName = appName;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
    }

    public static ApiKeyBuilder builder() {
        return new ApiKeyBuilder();
    }

    // --- Getters & Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    // --- Builder ---

    public static class ApiKeyBuilder {
        private String id;
        private String key;
        private String appName;
        private int rateLimitPerMinute = 60;
        private boolean enabled = true;
        private LocalDateTime createdAt;
        private LocalDateTime lastUsedAt;

        public ApiKeyBuilder id(String id) { this.id = id; return this; }
        public ApiKeyBuilder key(String key) { this.key = key; return this; }
        public ApiKeyBuilder appName(String appName) { this.appName = appName; return this; }
        public ApiKeyBuilder rateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; return this; }
        public ApiKeyBuilder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public ApiKeyBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public ApiKeyBuilder lastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; return this; }

        public ApiKey build() {
            return new ApiKey(id, key, appName, rateLimitPerMinute, enabled, createdAt, lastUsedAt);
        }
    }
}
