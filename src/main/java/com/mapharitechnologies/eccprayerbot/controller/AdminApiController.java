package com.mapharitechnologies.eccprayerbot.controller;

import com.mapharitechnologies.eccprayerbot.model.ApiKey;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos.ApiKeyResponse;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos.CreateApiKeyRequest;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos.ErrorResponse;
import com.mapharitechnologies.eccprayerbot.repository.ApiKeyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin-only endpoints for managing API keys.
 * Protected by X-Admin-Token header matching the configured admin token.
 */
@RestController
@RequestMapping("/api/admin/keys")
@Tag(name = "Admin — API Keys", description = "Manage API keys for consumer apps (requires admin token)")
@SecurityRequirement(name = "Admin Token")
public class AdminApiController {

    private static final Logger log = LoggerFactory.getLogger(AdminApiController.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Optional<ApiKeyRepository> apiKeyRepository;
    private final String adminToken;
    private final int defaultRateLimit;

    public AdminApiController(
            Optional<ApiKeyRepository> apiKeyRepository,
            @Value("${api.auth.admin-token:}") String adminToken,
            @Value("${api.rate-limit.default-rpm:60}") int defaultRateLimit) {
        this.apiKeyRepository = apiKeyRepository;
        this.adminToken = adminToken;
        this.defaultRateLimit = defaultRateLimit;
    }

    // ─── Create key ──────────────────────────────────────────────────────

    @Operation(
            summary = "Create a new API key",
            description = "Generates a new API key for a consumer app. The key prefix is `zb_` followed by a random UUID. "
                    + "Pass the app name and optional rate limit in the request body.",
            security = @SecurityRequirement(name = "Admin Token")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Key created successfully",
                    content = @Content(schema = @Schema(implementation = ApiKeyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing appName",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Invalid admin token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<?> createKey(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "App name and optional rate limit",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateApiKeyRequest.class)))
            @RequestBody CreateApiKeyRequest request,

            @Parameter(description = "Admin authentication token", required = true)
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {

        if (!isAdmin(token)) {
            return ResponseEntity.status(403).body(
                    new ErrorResponse(403, "Forbidden", "Invalid or missing admin token."));
        }

        if (apiKeyRepository.isEmpty()) {
            return ResponseEntity.status(503).body(
                    new ErrorResponse(503, "Service Unavailable", "Database not configured."));
        }

        if (request == null || request.appName() == null || request.appName().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(400, "Bad Request", "appName is required."));
        }

        String keyValue = "zb_" + UUID.randomUUID().toString().replace("-", "");
        int rpm = (request.rateLimitPerMinute() != null && request.rateLimitPerMinute() > 0)
                ? request.rateLimitPerMinute()
                : defaultRateLimit;

        ApiKey apiKey = ApiKey.builder()
                .key(keyValue)
                .appName(request.appName().trim())
                .rateLimitPerMinute(rpm)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        apiKey = apiKeyRepository.get().save(apiKey);
        log.info("Created API key for app '{}' (id={})", apiKey.getAppName(), apiKey.getId());

        return ResponseEntity.ok(toResponse(apiKey));
    }

    // ─── List keys ───────────────────────────────────────────────────────

    @Operation(
            summary = "List all API keys",
            description = "Returns all API keys (including disabled ones). Key values are visible to admins.",
            security = @SecurityRequirement(name = "Admin Token")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of API keys",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiKeyResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Invalid admin token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<?> listKeys(
            @Parameter(description = "Admin authentication token", required = true)
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {

        if (!isAdmin(token)) {
            return ResponseEntity.status(403).body(
                    new ErrorResponse(403, "Forbidden", "Invalid or missing admin token."));
        }

        if (apiKeyRepository.isEmpty()) {
            return ResponseEntity.status(503).body(
                    new ErrorResponse(503, "Service Unavailable", "Database not configured."));
        }

        List<ApiKeyResponse> keys = apiKeyRepository.get().findAll().stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(keys);
    }

    // ─── Disable key ─────────────────────────────────────────────────────

    @Operation(
            summary = "Disable an API key",
            description = "Soft-deletes an API key by setting it to disabled. "
                    + "The key can be re-enabled later. The key stops working immediately.",
            security = @SecurityRequirement(name = "Admin Token")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Key disabled",
                    content = @Content(schema = @Schema(implementation = ApiKeyResponse.class))),
            @ApiResponse(responseCode = "403", description = "Invalid admin token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Key not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> disableKey(
            @Parameter(description = "API key MongoDB ID", required = true)
            @PathVariable String id,

            @Parameter(description = "Admin authentication token", required = true)
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {

        if (!isAdmin(token)) {
            return ResponseEntity.status(403).body(
                    new ErrorResponse(403, "Forbidden", "Invalid or missing admin token."));
        }

        if (apiKeyRepository.isEmpty()) {
            return ResponseEntity.status(503).body(
                    new ErrorResponse(503, "Service Unavailable", "Database not configured."));
        }

        return apiKeyRepository.get().findById(id)
                .<ResponseEntity<?>>map(key -> {
                    key.setEnabled(false);
                    apiKeyRepository.get().save(key);
                    log.info("Disabled API key id={} for app '{}'", id, key.getAppName());
                    return ResponseEntity.ok(toResponse(key));
                })
                .orElse(ResponseEntity.status(404).body(
                        new ErrorResponse(404, "Not Found", "API key not found.")));
    }

    // ─── Re-enable key ───────────────────────────────────────────────────

    @Operation(
            summary = "Re-enable a disabled API key",
            description = "Restores a previously disabled API key. The key becomes active immediately.",
            security = @SecurityRequirement(name = "Admin Token")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Key re-enabled",
                    content = @Content(schema = @Schema(implementation = ApiKeyResponse.class))),
            @ApiResponse(responseCode = "403", description = "Invalid admin token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Key not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/enable")
    public ResponseEntity<?> enableKey(
            @Parameter(description = "API key MongoDB ID", required = true)
            @PathVariable String id,

            @Parameter(description = "Admin authentication token", required = true)
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {

        if (!isAdmin(token)) {
            return ResponseEntity.status(403).body(
                    new ErrorResponse(403, "Forbidden", "Invalid or missing admin token."));
        }

        if (apiKeyRepository.isEmpty()) {
            return ResponseEntity.status(503).body(
                    new ErrorResponse(503, "Service Unavailable", "Database not configured."));
        }

        return apiKeyRepository.get().findById(id)
                .<ResponseEntity<?>>map(key -> {
                    key.setEnabled(true);
                    apiKeyRepository.get().save(key);
                    log.info("Re-enabled API key id={} for app '{}'", id, key.getAppName());
                    return ResponseEntity.ok(toResponse(key));
                })
                .orElse(ResponseEntity.status(404).body(
                        new ErrorResponse(404, "Not Found", "API key not found.")));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private boolean isAdmin(String token) {
        if (adminToken == null || adminToken.isBlank()) {
            log.warn("Admin token not configured — rejecting admin request");
            return false;
        }
        return adminToken.equals(token);
    }

    private ApiKeyResponse toResponse(ApiKey key) {
        return new ApiKeyResponse(
                key.getId(),
                key.getKey(),
                key.getAppName(),
                key.getRateLimitPerMinute(),
                key.isEnabled(),
                key.getCreatedAt() != null ? key.getCreatedAt().format(ISO) : null
        );
    }
}
