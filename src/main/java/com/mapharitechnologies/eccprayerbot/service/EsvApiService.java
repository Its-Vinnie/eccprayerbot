package com.mapharitechnologies.eccprayerbot.service;

import com.mapharitechnologies.eccprayerbot.model.BibleReference;
import com.mapharitechnologies.eccprayerbot.model.BibleVerse;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Service for fetching Bible verses from the ESV API (api.esv.org)
 */
@Service
public class EsvApiService {

    private static final Logger log = LoggerFactory.getLogger(EsvApiService.class);

    private final WebClient webClient;
    private final int timeoutMs;

    public EsvApiService(
            @Value("${bible.esv.base-url:https://api.esv.org}") String baseUrl,
            @Value("${bible.esv.token:}") String apiToken,
            @Value("${bible.esv.timeout:5000}") int timeoutMs) {

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Token " + apiToken)
                .defaultHeader("Accept", "application/json")
                .build();
        this.timeoutMs = timeoutMs;
    }

    public boolean isSupported(String translation) {
        return "ESV".equalsIgnoreCase(translation);
    }

    @CircuitBreaker(name = "esvApi", fallbackMethod = "fetchVerseFallback")
    public BibleVerse fetchVerse(BibleReference reference) {
        log.debug("Fetching from ESV API: {}", reference.toDisplayString());

        String query = buildQuery(reference);

        JsonNode response = this.webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/passage/text/")
                        .queryParam("q", query)
                        .queryParam("include-verse-numbers", true)
                        .queryParam("include-passage-references", false)
                        .queryParam("include-headings", false)
                        .queryParam("include-footnotes", false)
                        .queryParam("include-short-copyright", false)
                        .queryParam("indent-paragraphs", 0)
                        .queryParam("indent-poetry", false)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofMillis(timeoutMs));

        if (response == null) {
            log.error("Null response from ESV API for query: {}", query);
            return null;
        }

        return mapToBibleVerse(response, reference);
    }

    public BibleVerse fetchVerseFallback(BibleReference reference, Throwable throwable) {
        log.error("ESV API fallback triggered for reference: {}. Error: {}",
                reference.toDisplayString(), throwable.getMessage());
        return null;
    }

    private String buildQuery(BibleReference reference) {
        StringBuilder query = new StringBuilder()
                .append(reference.getBook())
                .append(" ")
                .append(reference.getChapter());

        if (reference.getVerseStart() != null) {
            query.append(":").append(reference.getVerseStart());
            if (reference.getVerseEnd() != null && !reference.getVerseEnd().equals(reference.getVerseStart())) {
                query.append("-").append(reference.getVerseEnd());
            }
        }

        return query.toString();
    }

    private BibleVerse mapToBibleVerse(JsonNode response, BibleReference reference) {
        JsonNode passagesNode = response.path("passages");
        if (!passagesNode.isArray() || passagesNode.isEmpty()) {
            log.warn("No passages found in ESV API response for: {}", reference.toDisplayString());
            return null;
        }

        // Join all passages (usually just one)
        StringBuilder textBuilder = new StringBuilder();
        for (JsonNode passage : passagesNode) {
            if (textBuilder.length() > 0) {
                textBuilder.append("\n\n");
            }
            textBuilder.append(passage.asText());
        }

        String rawText = textBuilder.toString();

        // ESV API returns verse numbers in brackets like [1] when include-verse-numbers=true
        // Convert to bold format for Telegram consistency
        String formattedText = rawText.replaceAll("\\[(\\d+)\\]", "<b>$1</b> ");

        String canonical = response.path("canonical").asText(reference.toDisplayString());

        return BibleVerse.builder()
                .reference(canonical)
                .text(formattedText.trim())
                .translation("ESV")
                .book(reference.getBook())
                .chapter(reference.getChapter())
                .verse(reference.getVerseStart())
                .versionName("ESV")
                .fetchedAt(LocalDateTime.now())
                .build();
    }

    public boolean isApiAvailable() {
        try {
            this.webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/passage/text/")
                            .queryParam("q", "John 3:16")
                            .build())
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
            return true;
        } catch (Exception e) {
            log.warn("ESV API health check failed: {}", e.getMessage());
            return false;
        }
    }
}
