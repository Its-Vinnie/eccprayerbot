package com.mapharitechnologies.eccprayerbot.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BibleVerseInlinePreviewTest {

    @Test
    void chapterPreviewShouldOnlyShowFirstVerse() {
        BibleVerse verse = BibleVerse.builder()
                .reference("Genesis 1")
                .text("[1] In the beginning God created the heaven and the earth. [2] And the earth was without form.")
                .translation("KJV")
                .versionName("KJV")
                .build();

        String preview = verse.toInlinePreviewText(150);

        assertEquals("1 In the beginning God created the heaven and the earth.", preview);
    }

    @Test
    void singleVersePreviewShouldStaySingleVerseText() {
        BibleVerse verse = BibleVerse.builder()
                .reference("John 3:16")
                .text("For God so loved the world, that he gave his only begotten Son.")
                .translation("KJV")
                .versionName("KJV")
                .verse(16)
                .build();

        String preview = verse.toInlinePreviewText(150);

        assertTrue(preview.startsWith("For God so loved the world"));
    }
}
