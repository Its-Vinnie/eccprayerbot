package com.mapharitechnologies.eccprayerbot.controller;

import com.mapharitechnologies.eccprayerbot.analytics.service.AnalyticsTrackingService;
import com.mapharitechnologies.eccprayerbot.service.RequestLoggingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check and status endpoints
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final RequestLoggingService loggingService;
    private final AnalyticsTrackingService analyticsTrackingService;

    public HealthController(RequestLoggingService loggingService,
                            AnalyticsTrackingService analyticsTrackingService) {
        this.loggingService = loggingService;
        this.analyticsTrackingService = analyticsTrackingService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();

        health.put("status", "UP");
        health.put("bot", "ECCPrayerBot");
        health.put("version", "1.0.0-MVP");
        health.put("bibleApiStatus", "NOT_CHECKED");
        health.put("databaseStatus", "NOT_CHECKED");
        health.put("analyticsStatus", analyticsTrackingService.isEnabled() ? "ENABLED" : "DISABLED");

        return ResponseEntity.ok(health);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();

        double successRate = loggingService.getSuccessRate();

        stats.put("successRate", String.format("%.2f%%", successRate));
        stats.put("bot", "ECCPrayerBot");
        stats.put("analyticsEnabled", analyticsTrackingService.isEnabled());
        if (analyticsTrackingService.isEnabled()) {
            stats.put("usageSummary", analyticsTrackingService.getDashboardSummary(5));
        }

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        Map<String, String> info = new HashMap<>();
        info.put("bot", "ECCPrayerBot");
        info.put("version", "1.0.0-MVP");
        info.put("description", "Telegram bot for instant Bible verse retrieval");
        info.put("principle", "ECCPrayerBot must always serve the sermon and never interrupt it");

        return ResponseEntity.ok(info);
    }
}
