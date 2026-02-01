package com.mapharitechnologies.eccprayerbot.service;

import com.mapharitechnologies.eccprayerbot.model.BibleReference;
import com.mapharitechnologies.eccprayerbot.model.BibleVerse;
import com.mapharitechnologies.eccprayerbot.repository.BibleVerseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Main Bible service orchestrating cache and API calls
 */
@Service
public class BibleService {

    private static final Logger log = LoggerFactory.getLogger(BibleService.class);

    private final YouVersionApiService youVersionApiService;
    private final ApiBibleService apiBibleService;
    private final BibleVerseRepository verseRepository;

    public BibleService(YouVersionApiService youVersionApiService, ApiBibleService apiBibleService, BibleVerseRepository verseRepository) {
        this.youVersionApiService = youVersionApiService;
        this.apiBibleService = apiBibleService;
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
        if (youVersionApiService.isSupported(translation)) {
            verse = youVersionApiService.fetchVerse(reference);
        } else if (apiBibleService.isSupported(translation)) {
            verse = apiBibleService.fetchVerse(reference);
        } else {
            // Fallback to API.Bible (public domain)
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
            String translation = reference.getTranslation();

            if (translation != null) {
                return verseRepository.findByReferenceAndTranslation(
                        reference.toDisplayString(), translation);
            } else if (reference.getVerseStart() != null) {
                return verseRepository.findByBookAndChapterAndVerse(
                        reference.getBook(),
                        reference.getChapter(),
                        reference.getVerseStart());
            } else {
                // For whole chapters, we might want a different lookup or just skip cache for now
                return verseRepository.findByReference(reference.toDisplayString());
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
            verse.setCachedAt(LocalDateTime.now());
            verseRepository.save(verse);
            log.debug("Verse saved to cache: {}", verse.getReference());
        } catch (Exception e) {
            log.warn("Failed to cache verse: {}", verse.getReference(), e);
            // Don't throw - caching failure shouldn't break the flow
        }
    }

    /**
     * Check if the service is healthy
     */
    public boolean isHealthy() {
        return youVersionApiService.isApiAvailable() || apiBibleService.isApiAvailable();
    }
}