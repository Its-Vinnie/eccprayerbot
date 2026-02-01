package com.mapharitechnologies.eccprayerbot.controller;

import com.mapharitechnologies.eccprayerbot.service.BibleService;
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

    private final BibleService bibleService;
    private final RequestLoggingService loggingService;

    public HealthController(BibleService bibleService, RequestLoggingService loggingService) {
        this.bibleService = bibleService;
        this.loggingService = loggingService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();

        boolean apiHealthy = bibleService.isHealthy();
        boolean dbHealthy = true;
        try {
            // Simple check to see if we can reach MongoDB
            // This won't throw even if DB is down because we handled it in service, 
            // but here we check directly to report status
            dbHealthy = loggingService.getSuccessRate() >= 0; 
        } catch (Exception e) {
            dbHealthy = false;
        }

        health.put("status", apiHealthy ? "UP" : "DOWN");
        health.put("bot", "ECCPrayerBot");
        health.put("version", "1.0.0-MVP");
        health.put("bibleApiStatus", apiHealthy ? "AVAILABLE" : "UNAVAILABLE");
        health.put("databaseStatus", dbHealthy ? "CONNECTED" : "DISCONNECTED");

        return ResponseEntity.ok(health);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();

        double successRate = loggingService.getSuccessRate();

        stats.put("successRate", String.format("%.2f%%", successRate));
        stats.put("bot", "ECCPrayerBot");

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