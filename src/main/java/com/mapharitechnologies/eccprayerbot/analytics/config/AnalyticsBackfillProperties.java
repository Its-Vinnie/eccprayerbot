package com.mapharitechnologies.eccprayerbot.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Controls the one-time legacy analytics backfill from MongoDB into Supabase.
 */
@Component
@ConfigurationProperties(prefix = "analytics.backfill")
public class AnalyticsBackfillProperties {

    private boolean enabled;
    private int batchSize = 500;
    private String timeZone = "UTC";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
}
