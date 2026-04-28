package com.mapharitechnologies.eccprayerbot.controller;

import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatDetail;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatSummary;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsChatUserUsage;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsDashboardSummary;
import com.mapharitechnologies.eccprayerbot.analytics.model.AnalyticsPage;
import com.mapharitechnologies.eccprayerbot.analytics.service.AnalyticsAdminAuthorizer;
import com.mapharitechnologies.eccprayerbot.analytics.service.AnalyticsTrackingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only analytics endpoints backed by Supabase when enabled.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsTrackingService analyticsTrackingService;
    private final AnalyticsAdminAuthorizer analyticsAdminAuthorizer;

    public AnalyticsController(AnalyticsTrackingService analyticsTrackingService,
                               AnalyticsAdminAuthorizer analyticsAdminAuthorizer) {
        this.analyticsTrackingService = analyticsTrackingService;
        this.analyticsAdminAuthorizer = analyticsAdminAuthorizer;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsDashboardSummary> summary(
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestHeader(name = AnalyticsAdminAuthorizer.TOKEN_HEADER, required = false) String analyticsToken,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        analyticsAdminAuthorizer.authorize(analyticsToken, authorizationHeader);
        return ResponseEntity.ok(analyticsTrackingService.getDashboardSummary(limit));
    }

    @GetMapping("/chats")
    public ResponseEntity<AnalyticsPage<AnalyticsChatSummary>> chats(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "includePrivate", defaultValue = "false") boolean includePrivate,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @RequestHeader(name = AnalyticsAdminAuthorizer.TOKEN_HEADER, required = false) String analyticsToken,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        analyticsAdminAuthorizer.authorize(analyticsToken, authorizationHeader);
        return ResponseEntity.ok(
                analyticsTrackingService.listChats(type, active, search, includePrivate, limit, offset)
        );
    }

    @GetMapping("/chats/{telegramChatId}")
    public ResponseEntity<AnalyticsChatDetail> chatDetail(
            @PathVariable long telegramChatId,
            @RequestHeader(name = AnalyticsAdminAuthorizer.TOKEN_HEADER, required = false) String analyticsToken,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        analyticsAdminAuthorizer.authorize(analyticsToken, authorizationHeader);
        return ResponseEntity.ok(analyticsTrackingService.getChatDetail(telegramChatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found")));
    }

    @GetMapping("/chats/{telegramChatId}/users")
    public ResponseEntity<AnalyticsPage<AnalyticsChatUserUsage>> chatUsers(
            @PathVariable long telegramChatId,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @RequestHeader(name = AnalyticsAdminAuthorizer.TOKEN_HEADER, required = false) String analyticsToken,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        analyticsAdminAuthorizer.authorize(analyticsToken, authorizationHeader);
        if (analyticsTrackingService.getChatDetail(telegramChatId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found");
        }
        return ResponseEntity.ok(analyticsTrackingService.listChatUsers(telegramChatId, limit, offset));
    }
}
