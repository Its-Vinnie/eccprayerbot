package com.mapharitechnologies.eccprayerbot.service;

import com.mapharitechnologies.eccprayerbot.model.BibleReference;
import com.mapharitechnologies.eccprayerbot.model.BibleVerse;
import com.mapharitechnologies.eccprayerbot.repository.BibleVerseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main Bible service orchestrating cache and API calls
 */
@Service
public class BibleService {

    private static final Logger log = LoggerFactory.getLogger(BibleService.class);

    private final YouVersionApiService youVersionApiService;
    private final ApiBibleService apiBibleService;
    private final EsvApiService esvApiService;
    private final Optional<BibleVerseRepository> verseRepository;

    private static final Set<String> CACHEABLE_TRANSLATIONS = Set.of(
            "KJV", "ASV", "WEB", "WEBBE", "BBE", "FBV", "RSV", "GNT", "DRA", "GNV", "TCNT", "RV"
    );
    private static final int MAX_SEARCH_RESULTS = 5;
    private static final Set<String> SEARCH_STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "he", "her",
            "him", "his", "i", "in", "into", "is", "it", "its", "me", "my", "of", "on", "or",
            "our", "that", "the", "their", "them", "there", "they", "this", "to", "was", "we",
            "were", "will", "with", "you", "your"
    );
    private static final Map<String, String> SEARCH_SYNONYMS = Map.ofEntries(
            Map.entry("afraid", "fear"),
            Map.entry("believeth", "believe"),
            Map.entry("believing", "believe"),
            Map.entry("forgiveness", "forgive"),
            Map.entry("forgiven", "forgive"),
            Map.entry("forgiving", "forgive"),
            Map.entry("loved", "love"),
            Map.entry("loving", "love"),
            Map.entry("loveth", "love"),
            Map.entry("need", "lack"),
            Map.entry("salvation", "save"),
            Map.entry("saved", "save"),
            Map.entry("saving", "save"),
            Map.entry("saves", "save"),
            Map.entry("spoke", "speak"),
            Map.entry("spoken", "speak"),
            Map.entry("want", "lack")
    );

    public BibleService(YouVersionApiService youVersionApiService,
                        ApiBibleService apiBibleService,
                        EsvApiService esvApiService,
                        Optional<BibleVerseRepository> verseRepository) {
        this.youVersionApiService = youVersionApiService;
        this.apiBibleService = apiBibleService;
        this.esvApiService = esvApiService;
        this.verseRepository = verseRepository;
    }

    /**
     * Get a Bible verse - checks cache first, then API
     */
    public BibleVerse getVerse(BibleReference reference) {
        log.debug("Getting verse: {}", reference.toDisplayString());

        // Try to get from cache first
        Optional<BibleVerse> cached = findInCache(reference);
        if (cached.isPresent()) {
            log.debug("Verse found in cache");
            return cached.get();
        }

        // Fetch from API
        log.debug("Verse not in cache, fetching from API");
        BibleVerse verse;
        String translation = reference.getTranslation();
        if (esvApiService.isSupported(translation)) {
            verse = esvApiService.fetchVerse(reference);
        } else if (apiBibleService.isSupported(translation)) {
            if (translation == null) {
                // If no translation specified, create a new reference with default KJV for API.Bible
                BibleReference kjvRef = BibleReference.builder()
                        .book(reference.getBook())
                        .chapter(reference.getChapter())
                        .verseStart(reference.getVerseStart())
                        .verseEnd(reference.getVerseEnd())
                        .translation("KJV")
                        .build();
                verse = apiBibleService.fetchVerse(kjvRef);
            } else {
                verse = apiBibleService.fetchVerse(reference);
            }
        } else if (youVersionApiService.isSupported(translation)) {
            verse = youVersionApiService.fetchVerse(reference);
        } else {
            // Fallback to API.Bible (public domain KJV)
            verse = apiBibleService.fetchVerse(reference);
        }

        // Save to database for future use
        if (verse != null && verse.getText() != null && !verse.getText().isBlank()) {
            saveToCache(verse);
        }

        return verse;
    }

    /**
     * Find verse in local MongoDB cache
     */
    private Optional<BibleVerse> findInCache(BibleReference reference) {
        try {
            if (verseRepository.isEmpty()) {
                return Optional.empty();
            }
            String translation = reference.getTranslation();

            if (translation != null) {
                return verseRepository.get().findByReferenceAndTranslation(
                        reference.toDisplayString(), translation);
            } else if (reference.getVerseStart() != null) {
                return verseRepository.get().findByBookAndChapterAndVerse(
                        reference.getBook(),
                        reference.getChapter(),
                        reference.getVerseStart());
            } else {
                // For whole chapters, we might want a different lookup or just skip cache for now
                return verseRepository.get().findByReference(reference.toDisplayString());
            }
        } catch (Exception e) {
            log.error("Error accessing MongoDB cache for reference: {}", reference.toDisplayString(), e);
            return Optional.empty();
        }
    }

    /**
     * Save verse to MongoDB cache
     */
    private void saveToCache(BibleVerse verse) {
        try {
            if (verseRepository.isEmpty()) {
                return;
            }
            String translation = verse.getTranslation();
            if (translation == null || !isCacheableTranslation(translation)) {
                return;
            }
            verse.setCachedAt(LocalDateTime.now());
            verseRepository.get().save(verse);
            log.debug("Verse saved to cache: {}", verse.getReference());
        } catch (Exception e) {
            log.warn("Failed to cache verse: {}", verse.getReference(), e);
            // Don't throw - caching failure shouldn't break the flow
        }
    }

    private boolean isCacheableTranslation(String translation) {
        return CACHEABLE_TRANSLATIONS.contains(translation.toUpperCase());
    }

    /**
     * Get multiple specific verses (e.g., Rom 8:1,3,7) and combine them into a single BibleVerse.
     * Verses are fetched individually and returned in numerical order.
     */
    public BibleVerse getSpecificVerses(BibleReference reference) {
        List<Integer> verses = new ArrayList<>(reference.getSpecificVerses());
        verses.sort(Integer::compareTo);

        StringBuilder combinedText = new StringBuilder();
        String translation = null;
        String versionName = null;

        for (Integer verseNum : verses) {
            BibleReference singleRef = BibleReference.builder()
                    .book(reference.getBook())
                    .chapter(reference.getChapter())
                    .verseStart(verseNum)
                    .verseEnd(null)
                    .translation(reference.getTranslation())
                    .build();

            BibleVerse fetched = getVerse(singleRef);
            if (fetched != null && fetched.getText() != null && !fetched.getText().isBlank()) {
                if (translation == null) {
                    translation = fetched.getTranslation();
                    versionName = fetched.getVersionName();
                }
                if (combinedText.length() > 0) {
                    combinedText.append(" ");
                }
                // Strip any existing leading verse number to avoid duplication
                // Handles: "[1] ...", "<b>1</b> ...", "1 ..."
                String verseText = fetched.getText().trim()
                        .replaceAll("^\\[\\d+(?:-\\d+)?\\]\\s*", "")
                        .replaceAll("^<b>\\d+(?:-\\d+)?</b>\\s*", "")
                        .replaceAll("^\\d+\\s+", "");
                combinedText.append("[").append(verseNum).append("] ").append(verseText);
            }
        }

        if (combinedText.length() == 0) {
            return null;
        }

        return BibleVerse.builder()
                .reference(reference.toDisplayString())
                .text(combinedText.toString())
                .translation(translation)
                .versionName(versionName)
                .book(reference.getBook())
                .chapter(reference.getChapter())
                .fetchedAt(java.time.LocalDateTime.now())
                .build();
    }

    /**
     * Search for Bible verses by text content (quotes, paraphrases, keywords)
     */
    public List<BibleVerse> searchVerses(String query) {
        log.debug("Searching for verses matching: {}", query);

        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<String> searchQueries = buildSearchQueries(query);
        Map<String, SearchCandidate> mergedResults = new HashMap<>();

        for (String searchQuery : searchQueries) {
            List<BibleVerse> apiResults = apiBibleService.searchVerses(searchQuery);
            if (apiResults == null || apiResults.isEmpty()) {
                continue;
            }
            for (int i = 0; i < apiResults.size(); i++) {
                BibleVerse verse = apiResults.get(i);
                String key = verse.getReference() == null
                        ? searchQuery + "|" + i
                        : verse.getReference().trim().toLowerCase();
                double score = scoreSearchResult(query, searchQuery, verse, i);
                mergedResults.merge(key, new SearchCandidate(verse, score),
                        (existing, candidate) -> candidate.score() > existing.score() ? candidate : existing);
            }
        }

        return mergedResults.values().stream()
                .sorted(Comparator.comparingDouble(SearchCandidate::score).reversed())
                .limit(MAX_SEARCH_RESULTS)
                .map(SearchCandidate::verse)
                .toList();
    }

    /**
     * Check if the service is healthy
     */
    public boolean isHealthy() {
        return youVersionApiService.isApiAvailable() || apiBibleService.isApiAvailable() || esvApiService.isApiAvailable();
    }

    private List<String> buildSearchQueries(String query) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();

        String compactQuery = query.trim().replaceAll("\\s+", " ");
        addSearchVariant(variants, compactQuery);

        String normalizedQuery = normalizeSearchText(query);
        addSearchVariant(variants, normalizedQuery);

        List<String> significantTokens = extractSignificantTokens(query);
        if (!significantTokens.isEmpty()) {
            addSearchVariant(variants, String.join(" ", significantTokens));

            if (significantTokens.size() > 3) {
                addSearchVariant(variants, String.join(" ", significantTokens.subList(0, Math.min(4, significantTokens.size()))));
                addSearchVariant(variants, String.join(" ", significantTokens.subList(Math.max(0, significantTokens.size() - 4), significantTokens.size())));
            }

            if (significantTokens.size() > 4) {
                int middleStart = Math.max(0, (significantTokens.size() - 4) / 2);
                addSearchVariant(variants, String.join(" ", significantTokens.subList(middleStart, middleStart + 4)));
            }
        }

        return variants.stream()
                .limit(6)
                .toList();
    }

    private void addSearchVariant(Set<String> variants, String candidate) {
        if (candidate == null) {
            return;
        }
        String normalized = candidate.trim().replaceAll("\\s+", " ");
        if (normalized.length() >= 3) {
            variants.add(normalized);
        }
    }

    private double scoreSearchResult(String originalQuery, String searchQuery, BibleVerse verse, int apiRank) {
        String verseText = verse.getText() == null ? "" : verse.getText();
        String normalizedVerse = normalizeSearchText(verseText);
        String normalizedOriginalQuery = normalizeSearchText(originalQuery);
        String normalizedSearchQuery = normalizeSearchText(searchQuery);

        List<String> queryTokens = tokenizeSearchText(originalQuery);
        List<String> significantQueryTokens = extractSignificantTokens(originalQuery);
        List<String> verseTokens = tokenizeSearchText(verseText + " " + verse.getReference());
        Set<String> verseTokenSet = new HashSet<>(verseTokens);

        double score = 0;
        if (!normalizedOriginalQuery.isBlank() && normalizedVerse.contains(normalizedOriginalQuery)) {
            score += 60;
        }
        if (!normalizedSearchQuery.isBlank() && normalizedVerse.contains(normalizedSearchQuery)) {
            score += 25;
        }

        score += coverageScore(significantQueryTokens, verseTokenSet) * 35;
        score += coverageScore(queryTokens, verseTokenSet) * 20;
        score += orderedBigramCoverage(significantQueryTokens, verseTokens) * 20;
        score += Math.max(0, 10 - (apiRank * 2));

        return score;
    }

    private double coverageScore(List<String> queryTokens, Set<String> verseTokens) {
        if (queryTokens.isEmpty()) {
            return 0;
        }

        long matches = queryTokens.stream()
                .filter(verseTokens::contains)
                .count();

        return (double) matches / queryTokens.size();
    }

    private double orderedBigramCoverage(List<String> queryTokens, List<String> verseTokens) {
        if (queryTokens.size() < 2 || verseTokens.size() < 2) {
            return 0;
        }

        Set<String> verseBigrams = new HashSet<>();
        for (int i = 0; i < verseTokens.size() - 1; i++) {
            verseBigrams.add(verseTokens.get(i) + " " + verseTokens.get(i + 1));
        }

        int matches = 0;
        int total = 0;
        for (int i = 0; i < queryTokens.size() - 1; i++) {
            String bigram = queryTokens.get(i) + " " + queryTokens.get(i + 1);
            total++;
            if (verseBigrams.contains(bigram)) {
                matches++;
            }
        }

        return total == 0 ? 0 : (double) matches / total;
    }

    private List<String> extractSignificantTokens(String text) {
        return tokenizeSearchText(text).stream()
                .filter(token -> !SEARCH_STOP_WORDS.contains(token))
                .collect(Collectors.toList());
    }

    private List<String> tokenizeSearchText(String text) {
        String normalized = normalizeSearchText(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            String normalizedToken = normalizeSearchToken(token);
            if (!normalizedToken.isBlank()) {
                tokens.add(normalizedToken);
            }
        }
        return tokens;
    }

    private String normalizeSearchText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeSearchToken(String token) {
        String normalized = token == null ? "" : token.toLowerCase().replaceAll("[^a-z0-9]", "");
        normalized = SEARCH_SYNONYMS.getOrDefault(normalized, normalized);

        if (normalized.length() > 5 && normalized.endsWith("eth")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        } else if (normalized.length() > 4 && normalized.endsWith("ies")) {
            normalized = normalized.substring(0, normalized.length() - 3) + "y";
        } else if (normalized.length() > 3 && normalized.endsWith("s")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return SEARCH_SYNONYMS.getOrDefault(normalized, normalized);
    }

    private record SearchCandidate(BibleVerse verse, double score) {
    }
}
