package com.mapharitechnologies.eccprayerbot.service;

import com.mapharitechnologies.eccprayerbot.model.BibleVerse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BibleServiceTest {

    @Test
    void searchVersesShouldRankParaphraseMatchesAboveWeakKeywordMatches() {
        ApiBibleService apiBibleService = stubApiBibleService(searchQuery -> {
            BibleVerse psalm231 = verse("Psalm 23:1", "The Lord is my shepherd; I shall not want.");
            BibleVerse john1011 = verse("John 10:11", "I am the good shepherd: the good shepherd giveth his life for the sheep.");
            if (searchQuery.contains("lord shepherd")) {
                return List.of(john1011, psalm231);
            }
            return List.of();
        });
        BibleService service = new BibleService(
                new YouVersionApiService("https://example.com", "test", "111", 1000),
                apiBibleService,
                new EsvApiService("https://example.com", "test", 1000),
                Optional.empty()
        );

        List<BibleVerse> results = service.searchVerses("the lord is my shepherd i will not lack");

        assertFalse(results.isEmpty(), "Expected paraphrase search to return matches");
        assertEquals("Psalm 23:1", results.get(0).getReference(), "Expected Psalm 23:1 to rank first for the paraphrase");
    }

    @Test
    void searchVersesShouldDeduplicateResultsAcrossQueryVariants() {
        BibleVerse john316 = verse("John 3:16", "For God so loved the world, that he gave his only begotten Son.");
        ApiBibleService apiBibleService = stubApiBibleService(searchQuery -> List.of(john316));
        BibleService service = new BibleService(
                new YouVersionApiService("https://example.com", "test", "111", 1000),
                apiBibleService,
                new EsvApiService("https://example.com", "test", 1000),
                Optional.empty()
        );

        List<BibleVerse> results = service.searchVerses("for God so loved the world");

        assertEquals(1, results.size(), "Expected duplicate search hits to be merged by reference");
        assertEquals("John 3:16", results.get(0).getReference());
    }

    private BibleVerse verse(String reference, String text) {
        return BibleVerse.builder()
                .reference(reference)
                .text(text)
                .translation("KJV")
                .versionName("KJV")
                .fetchedAt(LocalDateTime.now())
                .build();
    }

    private ApiBibleService stubApiBibleService(Function<String, List<BibleVerse>> searchHandler) {
        return new ApiBibleService("https://example.com", "test", "de4e12af7f28f599-01", 1000) {
            @Override
            public List<BibleVerse> searchVerses(String query) {
                return searchHandler.apply(query);
            }
        };
    }
}
