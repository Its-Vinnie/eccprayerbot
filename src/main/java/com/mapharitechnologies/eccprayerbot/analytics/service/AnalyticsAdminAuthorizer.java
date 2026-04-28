package com.mapharitechnologies.eccprayerbot.analytics.service;

import com.mapharitechnologies.eccprayerbot.analytics.config.AnalyticsAccessProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Protects analytics endpoints with a shared admin token.
 */
@Component
public class AnalyticsAdminAuthorizer {

    public static final String TOKEN_HEADER = "X-Analytics-Admin-Token";

    private final AnalyticsAccessProperties accessProperties;

    public AnalyticsAdminAuthorizer(AnalyticsAccessProperties accessProperties) {
        this.accessProperties = accessProperties;
    }

    public void authorize(String tokenHeader, String authorizationHeader) {
        String configuredToken = normalize(accessProperties.getAdminToken());
        if (!StringUtils.hasText(configuredToken)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Analytics admin token is not configured"
            );
        }

        String candidate = normalize(tokenHeader);
        if (!StringUtils.hasText(candidate)) {
            candidate = extractBearerToken(authorizationHeader);
        }

        if (!configuredToken.equals(candidate)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid analytics admin token");
        }
    }

    public String getTokenHeaderName() {
        return TOKEN_HEADER;
    }

    private String extractBearerToken(String authorizationHeader) {
        String normalized = normalize(authorizationHeader);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7) && normalized.length() > 7) {
            return normalized.substring(7).trim();
        }

        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
