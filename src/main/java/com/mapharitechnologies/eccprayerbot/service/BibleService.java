package com.mapharitechnologies.eccprayerbot.service;

import com.mapharitechnologies.eccprayerbot.model.BibleReference;
import com.mapharitechnologies.eccprayerbot.model.BibleVerse;
import com.mapharitechnologies.eccprayerbot.repository.BibleVerseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
     * Search for Bible verses by text content (quotes, paraphrases, keywords)
     */
    public List<BibleVerse> searchVerses(String query) {
        log.debug("Searching for verses matching: {}", query);
        return apiBibleService.searchVerses(query);
    }

    /**
     * Check if the service is healthy
     */
    public boolean isHealthy() {
        return youVersionApiService.isApiAvailable() || apiBibleService.isApiAvailable() || esvApiService.isApiAvailable();
    }
}
