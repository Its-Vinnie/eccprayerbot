package com.mapharitechnologies.eccprayerbot.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Access control configuration for analytics endpoints.
 */
@Component
@ConfigurationProperties(prefix = "analytics.access")
public class AnalyticsAccessProperties {

    private String adminToken;

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }
}
