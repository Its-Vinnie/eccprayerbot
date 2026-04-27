package com.mapharitechnologies.eccprayerbot.controller;

import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsDashboardSummary;
import com.mapharitechnologies.eccprayerbot.analytics.service.AnalyticsTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only analytics endpoints backed by Supabase when enabled.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsTrackingService analyticsTrackingService;

    public AnalyticsController(AnalyticsTrackingService analyticsTrackingService) {
        this.analyticsTrackingService = analyticsTrackingService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsDashboardSummary> summary(
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsTrackingService.getDashboardSummary(limit));
    }
}
