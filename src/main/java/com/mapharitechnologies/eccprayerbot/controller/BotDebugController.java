package com.mapharitechnologies.eccprayerbot.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bot")
public class BotDebugController {

    @Value("${telegram.bot.token}")
    private String botToken;

    @GetMapping("/clear-webhook")
    public ResponseEntity<Map<String, Object>> clearWebhook() {
        Map<String, Object> response = new HashMap<>();

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/deleteWebhook?drop_pending_updates=true";
            RestTemplate restTemplate = new RestTemplate();
            String result = restTemplate.getForObject(url, String.class);

            response.put("success", true);
            response.put("telegram_response", result);
            response.put("message", "Webhook cleared. Restart your app to start polling.");

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/webhook-info")
    public ResponseEntity<Map<String, Object>> getWebhookInfo() {
        Map<String, Object> response = new HashMap<>();

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/getWebhookInfo";
            RestTemplate restTemplate = new RestTemplate();
            String result = restTemplate.getForObject(url, String.class);

            response.put("telegram_response", result);

        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> response = new HashMap<>();

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/getMe";
            RestTemplate restTemplate = new RestTemplate();
            String result = restTemplate.getForObject(url, String.class);

            response.put("telegram_response", result);
            response.put("message", "If you see bot info above, the token is valid");

        } catch (Exception e) {
            response.put("error", e.getMessage());
            response.put("message", "Token might be invalid");
        }

        return ResponseEntity.ok(response);
    }
}