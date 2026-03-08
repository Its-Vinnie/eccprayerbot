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
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for fetching Bible verses from YouVersion API
 */
@Service
public class YouVersionApiService {

    private static final Logger log = LoggerFactory.getLogger(YouVersionApiService.class);

    private final WebClient webClient;
    private final String defaultVersion;
    private final int timeoutMs;

    // Mapping for common Bible version abbreviations to YouVersion IDs
    private static final Map<String, String> VERSION_MAPPINGS = new HashMap<>();

    static {
        VERSION_MAPPINGS.put("EASY", "2079");
        VERSION_MAPPINGS.put("TPT", "1849");
        VERSION_MAPPINGS.put("AMP", "1588");
        VERSION_MAPPINGS.put("NLT", "116");
        VERSION_MAPPINGS.put("MSG", "97");
        VERSION_MAPPINGS.put("NKJV", "114");
    }

    // Bible book ID mappings for YouVersion (standard USFM abbreviations)
    private static final Map<String, String> BOOK_IDS = new HashMap<>();

    static {
        // Old Testament
        BOOK_IDS.put("genesis", "GEN");
        BOOK_IDS.put("exodus", "EXO");
        BOOK_IDS.put("leviticus", "LEV");
        BOOK_IDS.put("numbers", "NUM");
        BOOK_IDS.put("deuteronomy", "DEU");
        BOOK_IDS.put("joshua", "JOS");
        BOOK_IDS.put("judges", "JDG");
        BOOK_IDS.put("ruth", "RUT");
        BOOK_IDS.put("1 samuel", "1SA");
        BOOK_IDS.put("2 samuel", "2SA");
        BOOK_IDS.put("1 kings", "1KI");
        BOOK_IDS.put("2 kings", "2KI");
        BOOK_IDS.put("1 chronicles", "1CH");
        BOOK_IDS.put("2 chronicles", "2CH");
        BOOK_IDS.put("ezra", "EZR");
        BOOK_IDS.put("nehemiah", "NEH");
        BOOK_IDS.put("esther", "EST");
        BOOK_IDS.put("job", "JOB");
        BOOK_IDS.put("psalms", "PSA");
        BOOK_IDS.put("proverbs", "PRO");
        BOOK_IDS.put("ecclesiastes", "ECC");
        BOOK_IDS.put("song of solomon", "SNG");
        BOOK_IDS.put("isaiah", "ISA");
        BOOK_IDS.put("jeremiah", "JER");
        BOOK_IDS.put("lamentations", "LAM");
        BOOK_IDS.put("ezekiel", "EZK");
        BOOK_IDS.put("daniel", "DAN");
        BOOK_IDS.put("hosea", "HOS");
        BOOK_IDS.put("joel", "JOL"); // Updated from JOE to JOL
        BOOK_IDS.put("amos", "AMO");
        BOOK_IDS.put("obadiah", "OBA");
        BOOK_IDS.put("jonah", "JON");
        BOOK_IDS.put("micah", "MIC");
        BOOK_IDS.put("nahum", "NAM");
        BOOK_IDS.put("habakkuk", "HAB");
        BOOK_IDS.put("zephaniah", "ZEP");
        BOOK_IDS.put("haggai", "HAG");
        BOOK_IDS.put("zechariah", "ZEC");
        BOOK_IDS.put("malachi", "MAL");

        // New Testament
        BOOK_IDS.put("matthew", "MAT");
        BOOK_IDS.put("mark", "MRK");
        BOOK_IDS.put("luke", "LUK");
        BOOK_IDS.put("john", "JHN");
        BOOK_IDS.put("acts", "ACT");
        BOOK_IDS.put("romans", "ROM");
        BOOK_IDS.put("1 corinthians", "1CO");
        BOOK_IDS.put("2 corinthians", "2CO");
        BOOK_IDS.put("galatians", "GAL");
        BOOK_IDS.put("ephesians", "EPH");
        BOOK_IDS.put("philippians", "PHP");
        BOOK_IDS.put("colossians", "COL");
        BOOK_IDS.put("1 thessalonians", "1TH");
        BOOK_IDS.put("2 thessalonians", "2TH");
        BOOK_IDS.put("1 timothy", "1TI");
        BOOK_IDS.put("2 timothy", "2TI");
        BOOK_IDS.put("titus", "TIT");
        BOOK_IDS.put("philemon", "PHM");
        BOOK_IDS.put("hebrews", "HEB");
        BOOK_IDS.put("james", "JAS");
        BOOK_IDS.put("1 peter", "1PE");
        BOOK_IDS.put("2 peter", "2PE");
        BOOK_IDS.put("1 john", "1JN");
        BOOK_IDS.put("2 john", "2JN");
        BOOK_IDS.put("3 john", "3JN");
        BOOK_IDS.put("jude", "JUD");
        BOOK_IDS.put("revelation", "REV");
    }

    public YouVersionApiService(
            @Value("${bible.youversion.base-url}") String baseUrl,
            @Value("${bible.youversion.key}") String apiKey,
            @Value("${bible.youversion.default-version}") String defaultVersion,
            @Value("${bible.youversion.timeout}") int timeoutMs) {
        
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-YouVersion-Developer-Token", apiKey)
                .defaultHeader("X-YVP-App-Key", apiKey)
                .defaultHeader("Accept", "application/json")
                .build();
        this.defaultVersion = defaultVersion;
        this.timeoutMs = timeoutMs;
    }

    public boolean isSupported(String translation) {
        if (translation == null) return true; // default version is NIV which is supported
        return VERSION_MAPPINGS.containsKey(translation.toUpperCase());
    }

    @CircuitBreaker(name = "bibleApi", fallbackMethod = "fetchVerseFallback")
    public BibleVerse fetchVerse(BibleReference reference) {
        log.debug("Fetching from YouVersion API: {}", reference.toDisplayString());

        String versionId = getVersionId(reference.getTranslation());
        
        // Construct endpoint based on YouVersion API
        // If it's John 3:16, reference is JHN.3.16
        String refString = buildReferenceString(reference);

        // Fetch both the passage and the version info to get the localized title
        Mono<JsonNode> versionMono = this.webClient.get()
                .uri("/v1/bibles/{version}", versionId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(e -> Mono.empty());

        Mono<JsonNode> passageMono = this.webClient.get()
                .uri("/v1/bibles/{version}/passages/{reference}", versionId, refString)
                .retrieve()
                .bodyToMono(JsonNode.class);

        return Mono.zip(passageMono, versionMono.map(Optional::of).defaultIfEmpty(Optional.empty()))
                .map(tuple -> {
                    JsonNode passageNode = tuple.getT1();
                    Optional<JsonNode> versionNodeOpt = tuple.getT2();
                    JsonNode versionNode = versionNodeOpt.orElse(null);
                    String versionName = (versionNode != null && versionNode.has("abbreviation")) 
                            ? versionNode.get("abbreviation").asText() 
                            : (reference.getTranslation() != null ? reference.getTranslation() : "NIV");

                    // Normalize NIV11 (or similar) to NIV as requested
                    if (versionName != null && versionName.toUpperCase().startsWith("NIV")) {
                        versionName = "NIV";
                    }

                    return mapToBibleVerse(passageNode, reference, versionId, versionName);
                })
                .block(Duration.ofMillis(timeoutMs));
    }

    public BibleVerse fetchVerseFallback(BibleReference reference, Throwable throwable) {
        log.error("YouVersion API fallback triggered for reference: {}. Error: {}", 
                reference.toDisplayString(), throwable.getMessage());
        return null;
    }

    private String getVersionId(String translation) {
        if (translation == null) return defaultVersion;
        return VERSION_MAPPINGS.getOrDefault(translation.toUpperCase(), translation);
    }

    private String buildReferenceString(BibleReference reference) {
        // YouVersion often uses USFM-style IDs: JHN.3.16
        String bookId = BOOK_IDS.getOrDefault(reference.getBook().toLowerCase(), 
                reference.getBook().toUpperCase().substring(0, Math.min(reference.getBook().length(), 3)));
        
        StringBuilder sb = new StringBuilder(bookId)
                .append(".")
                .append(reference.getChapter());
        
        if (reference.getVerseStart() != null) {
            sb.append(".").append(reference.getVerseStart());
            if (reference.getVerseEnd() != null) {
                sb.append("-").append(reference.getVerseEnd());
            }
        }
        
        return sb.toString();
    }

    private BibleVerse mapToBibleVerse(JsonNode node, BibleReference reference, String versionId, String versionName) {
        // Map YouVersion JSON response to our model
        // Assume structure: { "content": "...", "reference": "..." }
        String rawText = node.path("content").asText();

        // YouVersion content often contains HTML or specific markers.
        // We ensure verse numbers are preserved. If They are in <span class="verse">1</span> format, 
        // they might need cleaning if we use text mode. 
        // However, the current implementation uses the text as-is and then BibleVerse.cleanText handles it.
        // Let's make sure we don't strip them in cleanText (which I already updated).
        
        String ref = node.path("reference").asText(reference.toDisplayString());

        return BibleVerse.builder()
                .reference(ref)
                .text(rawText)
                .translation(reference.getTranslation())
                .book(reference.getBook())
                .chapter(reference.getChapter())
                .verse(reference.getVerseStart())
                .versionName(versionName)
                .fetchedAt(LocalDateTime.now())
                .build();
    }

    public boolean isApiAvailable() {
        try {
            // Simple ping to an innocuous endpoint
            this.webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/bibles")
                            .queryParam("language_ranges[]", "en")
                            .build())
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
            return true;
        } catch (Exception e) {
            log.warn("API health check failed: {}", e.getMessage());
            return false;
        }
    }
}
