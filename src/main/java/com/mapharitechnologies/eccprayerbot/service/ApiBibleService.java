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

/**
 * Service for fetching Bible verses from API.Bible (Scripture API)
 */
@Service
public class ApiBibleService {

    private static final Logger log = LoggerFactory.getLogger(ApiBibleService.class);

    private final WebClient webClient;
    private final String defaultVersionId;
    private final int timeoutMs;

    // Mapping for common Bible version abbreviations to API.Bible IDs
    private static final Map<String, String> VERSION_MAPPINGS = new HashMap<>();

    static {
        VERSION_MAPPINGS.put("KJV", "de4e12af7f28f599-01");
        VERSION_MAPPINGS.put("ASV", "06125adad2d5898a-01");
        VERSION_MAPPINGS.put("WEB", "9879dbb7cfe39e4d-04");
        VERSION_MAPPINGS.put("BBE", "7142879509583d59-01"); // Mapping BBE to WEBBE as closest available public domain
        VERSION_MAPPINGS.put("WEBBE", "7142879509583d59-01");

        // Premium Versions (Pro Plan)
        VERSION_MAPPINGS.put("NIV", "78a9f6124f344018-01");
        VERSION_MAPPINGS.put("CSB","a556c5305ee15c3f-01");
        VERSION_MAPPINGS.put("NKJV", "63097d2a0a2f7db3-01");
        VERSION_MAPPINGS.put("NLT", "d6e14a625393b4da-01");
        VERSION_MAPPINGS.put("AMP", "a81b73293d3080c9-01");
        VERSION_MAPPINGS.put("MSG", "6f11a7de016f942e-01");
    }

    // Bible book ID mappings for API.Bible (standard USFM abbreviations)
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
        BOOK_IDS.put("joel", "JOL"); // Updated from JOE to JOL per scriptures names.json
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

    public ApiBibleService(
            @Value("${bible.api-bible.base-url:https://rest.api.bible/v1}") String baseUrl,
            @Value("${bible.api-bible.key:}") String apiKey,
            @Value("${bible.api-bible.default-version:de4e12af7f28f599-01}") String defaultVersionId,
            @Value("${bible.api-bible.timeout:5000}") int timeoutMs) {

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("Accept", "application/json")
                .build();
        this.defaultVersionId = defaultVersionId;
        this.timeoutMs = timeoutMs;
    }

    public boolean isSupported(String translation) {
        if (translation == null) return true;
        return VERSION_MAPPINGS.containsKey(translation.toUpperCase());
    }

    @CircuitBreaker(name = "apiBible", fallbackMethod = "fetchVerseFallback")
    public BibleVerse fetchVerse(BibleReference reference) {
        log.debug("Fetching from API.Bible: {}", reference.toDisplayString());

        String translation = reference.getTranslation();
        String versionId = getVersionId(translation);
        String passageId = buildPassageId(reference);

        if (passageId == null) {
            log.error("Could not build passage ID for reference: {}", reference);
            return null;
        }

        // Use passages endpoint for all requests as it handles single verses, ranges, and chapters well
        Mono<JsonNode> responseMono = this.webClient.get()
                .uri("/bibles/{bibleId}/passages/{passageId}?content-type=text&include-verse-numbers=true", versionId, passageId)
                .retrieve()
                .bodyToMono(JsonNode.class);

        // Fetch version metadata for display name
        Mono<JsonNode> versionMono = this.webClient.get()
                .uri("/bibles/{bibleId}", versionId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(e -> Mono.empty());

        return Mono.zip(responseMono, versionMono.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()))
                .map(tuple -> {
                    JsonNode dataNode = tuple.getT1().path("data");
                    java.util.Optional<JsonNode> versionNodeOpt = tuple.getT2();
                    String versionName = translation != null ? translation : "KJV";
                    if (versionNodeOpt.isPresent()) {
                        JsonNode vData = versionNodeOpt.get().path("data");
                        versionName = vData.has("abbreviation") ? vData.get("abbreviation").asText() : versionName;
                    }

                    // Format engKJV to KJV as requested
                    if ("engKJV".equalsIgnoreCase(versionName)) {
                        versionName = "KJV";
                    }

                    // Normalize NIV variants if they ever come from here
                    if (versionName != null && versionName.toUpperCase().startsWith("NIV")) {
                        versionName = "NIV";
                    }

                    return mapToBibleVerse(dataNode, reference, versionName);
                })
                .block(Duration.ofMillis(timeoutMs));
    }

    public BibleVerse fetchVerseFallback(BibleReference reference, Throwable throwable) {
        log.error("API.Bible fallback triggered for reference: {}. Error: {}",
                reference.toDisplayString(), throwable.getMessage());
        return null;
    }

    private String getVersionId(String translation) {
        if (translation == null) return defaultVersionId;
        return VERSION_MAPPINGS.getOrDefault(translation.toUpperCase(), defaultVersionId);
    }

    private String buildPassageId(BibleReference reference) {
        String bookId = BOOK_IDS.get(reference.getBook().toLowerCase());
        if (bookId == null) return null;

        if (reference.getVerseStart() == null) {
            // Chapter ID format: JHN.3
            return bookId + "." + reference.getChapter();
        } else if (reference.getVerseEnd() == null || reference.getVerseEnd().equals(reference.getVerseStart())) {
            // Single verse ID format: JHN.3.16
            return bookId + "." + reference.getChapter() + "." + reference.getVerseStart();
        } else {
            // Range ID format for passages endpoint: JHN.3.16-JHN.3.18
            return bookId + "." + reference.getChapter() + "." + reference.getVerseStart() + "-" +
                   bookId + "." + reference.getChapter() + "." + reference.getVerseEnd();
        }
    }

    private BibleVerse mapToBibleVerse(JsonNode dataNode, BibleReference reference, String versionName) {
        String rawText = dataNode.path("content").asText();

        // The API in text mode with include-verse-numbers=true usually returns numbers in brackets like [1] or [12]
        // We want to format them for Telegram. Bold verse numbers like <b>1</b> look good.
        String formattedText = rawText.replaceAll("\\[(\\d+)\\]", "<b>$1</b> ");

        String ref = dataNode.path("reference").asText(reference.toDisplayString());

        return BibleVerse.builder()
                .reference(ref)
                .text(formattedText.trim())
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
            this.webClient.get()
                    .uri("/bibles")
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
            return true;
        } catch (Exception e) {
            log.warn("API.Bible health check failed: {}", e.getMessage());
            return false;
        }
    }
}
