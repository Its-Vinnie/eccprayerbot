package com.mapharitechnologies.eccprayerbot.controller;

import com.mapharitechnologies.eccprayerbot.model.BibleReference;
import com.mapharitechnologies.eccprayerbot.model.BibleVerse;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos.BookResponse;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos.ChapterResponse;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos.ErrorResponse;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos.SearchResponse;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos.SearchResult;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos.TranslationResponse;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos.VerseResponse;
import com.mapharitechnologies.eccprayerbot.model.dto.ApiDtos.VerseResult;
import com.mapharitechnologies.eccprayerbot.service.BibleService;
import com.mapharitechnologies.eccprayerbot.util.BibleReferenceParser;
import com.mapharitechnologies.eccprayerbot.util.ChapterTextParser;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for Bible data — serves Mount Zion and any future consumer apps.
 *
 * All endpoints are under /api/v1/bible and require an X-API-Key header
 * (unless api.auth.enabled=false).
 */
@RestController
@RequestMapping("/api/v1/bible")
@Tag(name = "Bible", description = "Bible verse lookup, chapter reading, and text search")
@SecurityRequirement(name = "API Key")
public class BibleApiController {

    private static final Logger log = LoggerFactory.getLogger(BibleApiController.class);

    private final BibleService bibleService;
    private final BibleReferenceParser referenceParser;

    public BibleApiController(BibleService bibleService, BibleReferenceParser referenceParser) {
        this.bibleService = bibleService;
        this.referenceParser = referenceParser;
    }

    // ─── GET /verse ──────────────────────────────────────────────────────

    @Operation(
            summary = "Fetch a Bible verse",
            description = "Fetch a single verse or verse range. Accepts either a free-form `ref` string "
                    + "(e.g. \"John 3:16\", \"Rom 8:28-30\") or structured `book`+`chapter`+`verse` params. "
                    + "If the requested translation is unavailable, falls back to KJV.",
            security = @SecurityRequirement(name = "API Key")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verse found",
                    content = @Content(schema = @Schema(implementation = VerseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Verse not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/verse")
    public ResponseEntity<?> getVerse(
            @Parameter(description = "Free-form Bible reference, e.g. \"John 3:16\" or \"1 Cor 13:4-7\"")
            @RequestParam(required = false) String ref,

            @Parameter(description = "Book name, e.g. \"John\" (used with chapter)")
            @RequestParam(required = false) String book,

            @Parameter(description = "Chapter number")
            @RequestParam(required = false) Integer chapter,

            @Parameter(description = "Starting verse number")
            @RequestParam(required = false) Integer verse,

            @Parameter(description = "Ending verse number (for ranges)")
            @RequestParam(required = false) Integer verseEnd,

            @Parameter(description = "Translation code", example = "KJV")
            @RequestParam(defaultValue = "KJV") String translation) {

        BibleReference reference = buildReference(ref, book, chapter, verse, verseEnd, translation);
        if (reference == null) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(400, "Bad Request",
                            "Provide either 'ref' (e.g. John 3:16) or 'book'+'chapter' params."));
        }

        BibleVerse bibleVerse = bibleService.getVerse(reference);
        if (bibleVerse == null || bibleVerse.getText() == null || bibleVerse.getText().isBlank()) {
            return ResponseEntity.status(404).body(
                    new ErrorResponse(404, "Not Found",
                            "Verse not found: " + reference.toDisplayString()));
        }

        VerseResponse response = toVerseResponse(bibleVerse);
        return ResponseEntity.ok(response);
    }

    // ─── GET /chapter ────────────────────────────────────────────────────

    @Operation(
            summary = "Fetch a full Bible chapter",
            description = "Returns a chapter broken into individual verses. Large chapters "
                    + "(e.g. Psalm 119 with 176 verses) may return substantial payloads.",
            security = @SecurityRequirement(name = "API Key")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chapter found",
                    content = @Content(schema = @Schema(implementation = ChapterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chapter not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/chapter")
    public ResponseEntity<?> getChapter(
            @Parameter(description = "Book name, e.g. \"Genesis\"", required = true)
            @RequestParam String book,

            @Parameter(description = "Chapter number", required = true, example = "1")
            @RequestParam Integer chapter,

            @Parameter(description = "Translation code", example = "KJV")
            @RequestParam(defaultValue = "KJV") String translation) {

        BibleReference reference = BibleReference.builder()
                .book(book)
                .chapter(chapter)
                .translation(translation)
                .build();

        BibleVerse bibleVerse = bibleService.getVerse(reference);
        if (bibleVerse == null || bibleVerse.getText() == null || bibleVerse.getText().isBlank()) {
            return ResponseEntity.status(404).body(
                    new ErrorResponse(404, "Not Found",
                            "Chapter not found: " + book + " " + chapter));
        }

        List<ChapterTextParser.ParsedVerse> parsed = ChapterTextParser.parse(bibleVerse.getText());
        List<VerseResult> verses = parsed.stream()
                .map(pv -> new VerseResult(pv.verseNumber(), pv.text()))
                .toList();

        String refString = book + " " + chapter;
        ChapterResponse response = new ChapterResponse(
                refString,
                book,
                chapter,
                bibleVerse.getTranslation(),
                bibleVerse.getVersionName(),
                verses
        );

        return ResponseEntity.ok(response);
    }

    // ─── GET /search ─────────────────────────────────────────────────────

    @Operation(
            summary = "Search Bible verses by text",
            description = "Search for verses matching a quote, keyword, or paraphrase. "
                    + "Works best with direct quotes or key phrases (3+ words recommended).",
            security = @SecurityRequirement(name = "API Key")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results (may be empty)",
                    content = @Content(schema = @Schema(implementation = SearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing query parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/search")
    public ResponseEntity<?> searchVerses(
            @Parameter(description = "Search query (quotes, keywords, paraphrases)", required = true,
                    example = "For God so loved")
            @RequestParam String q,

            @Parameter(description = "Translation to search in", example = "KJV")
            @RequestParam(defaultValue = "KJV") String translation,

            @Parameter(description = "Max results (1-20)", example = "5")
            @RequestParam(defaultValue = "5") int limit) {

        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(400, "Bad Request", "Query parameter 'q' is required."));
        }

        if (limit < 1 || limit > 20) {
            limit = Math.max(1, Math.min(20, limit));
        }

        List<BibleVerse> results = bibleService.searchVerses(q);

        List<SearchResult> searchResults = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, results.size()); i++) {
            BibleVerse v = results.get(i);
            searchResults.add(new SearchResult(
                    v.getReference(),
                    stripHtml(v.getText()),
                    v.getTranslation(),
                    v.getVersionName()
            ));
        }

        SearchResponse response = new SearchResponse(q, searchResults, searchResults.size());
        return ResponseEntity.ok(response);
    }

    // ─── GET /books ──────────────────────────────────────────────────────

    @Operation(
            summary = "List all Bible books",
            description = "Returns all 66 books of the Bible with name, abbreviation, "
                    + "testament (OLD/NEW), and total chapter count. Useful for building book/chapter pickers."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of all books",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = BookResponse.class))))
    })
    @GetMapping("/books")
    public ResponseEntity<List<BookResponse>> getBooks() {
        return ResponseEntity.ok(ALL_BOOKS);
    }

    // ─── GET /translations ───────────────────────────────────────────────

    @Operation(
            summary = "List supported Bible translations",
            description = "Returns all available translations with name, abbreviation, "
                    + "upstream provider, and availability status. Some premium translations "
                    + "may require additional API access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of translations",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TranslationResponse.class))))
    })
    @GetMapping("/translations")
    public ResponseEntity<List<TranslationResponse>> getTranslations() {
        return ResponseEntity.ok(SUPPORTED_TRANSLATIONS);
    }

    // ─── GET /health ─────────────────────────────────────────────────────

    @Operation(
            summary = "Bible API health check",
            description = "Lightweight health check — no authentication required. "
                    + "Returns UP if at least one upstream Bible API provider is reachable.",
            security = {}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Health status")
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean healthy = bibleService.isHealthy();
        Map<String, Object> result = new HashMap<>();
        result.put("status", healthy ? "UP" : "DEGRADED");
        result.put("service", "BibleAPI");
        return ResponseEntity.ok(result);
    }

    // ─── Private helpers ─────────────────────────────────────────────────

    private BibleReference buildReference(String ref, String book, Integer chapter,
                                          Integer verse, Integer verseEnd, String translation) {
        if (ref != null && !ref.isBlank()) {
            BibleReference parsed = referenceParser.parse(ref);
            if (parsed == null) {
                return null;
            }
            if (translation != null && !translation.isBlank()) {
                parsed.setTranslation(translation.toUpperCase());
            }
            return parsed;
        }

        if (book != null && !book.isBlank() && chapter != null) {
            return BibleReference.builder()
                    .book(book)
                    .chapter(chapter)
                    .verseStart(verse)
                    .verseEnd(verseEnd)
                    .translation(translation)
                    .build();
        }

        return null;
    }

    private VerseResponse toVerseResponse(BibleVerse v) {
        return new VerseResponse(
                v.getReference(),
                stripHtml(v.getText()),
                v.getTranslation(),
                v.getVersionName(),
                v.getBook(),
                v.getChapter(),
                v.getVerse(),
                null
        );
    }

    private static String stripHtml(String text) {
        if (text == null) return null;
        return text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    // ─── Static data: 66 books of the Bible ──────────────────────────────

    private static final List<BookResponse> ALL_BOOKS = List.of(
            // Old Testament
            new BookResponse("Genesis", "GEN", "OLD", 50),
            new BookResponse("Exodus", "EXO", "OLD", 40),
            new BookResponse("Leviticus", "LEV", "OLD", 27),
            new BookResponse("Numbers", "NUM", "OLD", 36),
            new BookResponse("Deuteronomy", "DEU", "OLD", 34),
            new BookResponse("Joshua", "JOS", "OLD", 24),
            new BookResponse("Judges", "JDG", "OLD", 21),
            new BookResponse("Ruth", "RUT", "OLD", 4),
            new BookResponse("1 Samuel", "1SA", "OLD", 31),
            new BookResponse("2 Samuel", "2SA", "OLD", 24),
            new BookResponse("1 Kings", "1KI", "OLD", 22),
            new BookResponse("2 Kings", "2KI", "OLD", 25),
            new BookResponse("1 Chronicles", "1CH", "OLD", 29),
            new BookResponse("2 Chronicles", "2CH", "OLD", 36),
            new BookResponse("Ezra", "EZR", "OLD", 10),
            new BookResponse("Nehemiah", "NEH", "OLD", 13),
            new BookResponse("Esther", "EST", "OLD", 10),
            new BookResponse("Job", "JOB", "OLD", 42),
            new BookResponse("Psalms", "PSA", "OLD", 150),
            new BookResponse("Proverbs", "PRO", "OLD", 31),
            new BookResponse("Ecclesiastes", "ECC", "OLD", 12),
            new BookResponse("Song of Solomon", "SNG", "OLD", 8),
            new BookResponse("Isaiah", "ISA", "OLD", 66),
            new BookResponse("Jeremiah", "JER", "OLD", 52),
            new BookResponse("Lamentations", "LAM", "OLD", 5),
            new BookResponse("Ezekiel", "EZK", "OLD", 48),
            new BookResponse("Daniel", "DAN", "OLD", 12),
            new BookResponse("Hosea", "HOS", "OLD", 14),
            new BookResponse("Joel", "JOL", "OLD", 3),
            new BookResponse("Amos", "AMO", "OLD", 9),
            new BookResponse("Obadiah", "OBA", "OLD", 1),
            new BookResponse("Jonah", "JON", "OLD", 4),
            new BookResponse("Micah", "MIC", "OLD", 7),
            new BookResponse("Nahum", "NAM", "OLD", 3),
            new BookResponse("Habakkuk", "HAB", "OLD", 3),
            new BookResponse("Zephaniah", "ZEP", "OLD", 3),
            new BookResponse("Haggai", "HAG", "OLD", 2),
            new BookResponse("Zechariah", "ZEC", "OLD", 14),
            new BookResponse("Malachi", "MAL", "OLD", 4),
            // New Testament
            new BookResponse("Matthew", "MAT", "NEW", 28),
            new BookResponse("Mark", "MRK", "NEW", 16),
            new BookResponse("Luke", "LUK", "NEW", 24),
            new BookResponse("John", "JHN", "NEW", 21),
            new BookResponse("Acts", "ACT", "NEW", 28),
            new BookResponse("Romans", "ROM", "NEW", 16),
            new BookResponse("1 Corinthians", "1CO", "NEW", 16),
            new BookResponse("2 Corinthians", "2CO", "NEW", 13),
            new BookResponse("Galatians", "GAL", "NEW", 6),
            new BookResponse("Ephesians", "EPH", "NEW", 6),
            new BookResponse("Philippians", "PHP", "NEW", 4),
            new BookResponse("Colossians", "COL", "NEW", 4),
            new BookResponse("1 Thessalonians", "1TH", "NEW", 5),
            new BookResponse("2 Thessalonians", "2TH", "NEW", 3),
            new BookResponse("1 Timothy", "1TI", "NEW", 6),
            new BookResponse("2 Timothy", "2TI", "NEW", 4),
            new BookResponse("Titus", "TIT", "NEW", 3),
            new BookResponse("Philemon", "PHM", "NEW", 1),
            new BookResponse("Hebrews", "HEB", "NEW", 13),
            new BookResponse("James", "JAS", "NEW", 5),
            new BookResponse("1 Peter", "1PE", "NEW", 5),
            new BookResponse("2 Peter", "2PE", "NEW", 3),
            new BookResponse("1 John", "1JN", "NEW", 5),
            new BookResponse("2 John", "2JN", "NEW", 1),
            new BookResponse("3 John", "3JN", "NEW", 1),
            new BookResponse("Jude", "JUD", "NEW", 1),
            new BookResponse("Revelation", "REV", "NEW", 22)
    );

    // ─── Static data: Supported translations ─────────────────────────────

    private static final List<TranslationResponse> SUPPORTED_TRANSLATIONS = List.of(
            new TranslationResponse("KJV", "King James Version", "KJV", "api.bible", true),
            new TranslationResponse("ESV", "English Standard Version", "ESV", "esv", true),
            new TranslationResponse("NIV", "New International Version", "NIV", "api.bible", true),
            new TranslationResponse("NLT", "New Living Translation", "NLT", "youversion", true),
            new TranslationResponse("NKJV", "New King James Version", "NKJV", "api.bible", true),
            new TranslationResponse("AMP", "Amplified Bible", "AMP", "api.bible", true),
            new TranslationResponse("MSG", "The Message", "MSG", "api.bible", true),
            new TranslationResponse("CSB", "Christian Standard Bible", "CSB", "api.bible", true),
            new TranslationResponse("ASV", "American Standard Version", "ASV", "api.bible", true),
            new TranslationResponse("WEB", "World English Bible", "WEB", "api.bible", true),
            new TranslationResponse("BBE", "Bible in Basic English", "BBE", "api.bible", true),
            new TranslationResponse("TPT", "The Passion Translation", "TPT", "youversion", true),
            new TranslationResponse("EASY", "EasyEnglish Bible", "EASY", "youversion", true)
    );
}
