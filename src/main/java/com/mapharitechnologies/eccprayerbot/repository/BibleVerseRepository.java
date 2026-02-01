package com.mapharitechnologies.eccprayerbot.repository;

import com.mapharitechnologies.eccprayerbot.model.BibleVerse;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for caching Bible verses in MongoDB
 */
@Repository
public interface BibleVerseRepository extends MongoRepository<BibleVerse, String> {

    /**
     * Find a cached verse by its reference and translation
     */
    Optional<BibleVerse> findByReferenceAndTranslation(String reference, String translation);

    /**
     * Find by book, chapter, and verse
     */
    Optional<BibleVerse> findByBookAndChapterAndVerse(String book, Integer chapter, Integer verse);

    /**
     * Find by reference
     */
    Optional<BibleVerse> findByReference(String reference);
}