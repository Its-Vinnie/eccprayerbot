package com.mapharitechnologies.eccprayerbot.util;

import com.mapharitechnologies.eccprayerbot.model.BibleReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BibleReferenceParserTest {

    private final BibleReferenceParser parser = new BibleReferenceParser();

    @Test
    void testParseSingleVerse() {
        BibleReference ref = parser.parse("John 3:16");
        assertNotNull(ref);
        assertEquals("John", ref.getBook());
        assertEquals(3, ref.getChapter());
        assertEquals(16, ref.getVerseStart());
        assertNull(ref.getVerseEnd());
        assertEquals("KJV", ref.getTranslation(), "Should default to KJV");
    }

    @Test
    void testParseVerseRange() {
        BibleReference ref = parser.parse("John 3:16-18");
        assertNotNull(ref);
        assertEquals("John", ref.getBook());
        assertEquals(3, ref.getChapter());
        assertEquals(16, ref.getVerseStart());
        assertEquals(18, ref.getVerseEnd());
        assertEquals("KJV", ref.getTranslation(), "Should default to KJV");
    }

    @Test
    void testParseWithTranslation() {
        BibleReference ref = parser.parse("Romans 8:28 KJV");
        assertNotNull(ref);
        assertEquals("Romans", ref.getBook());
        assertEquals("KJV", ref.getTranslation());
    }

    @Test
    void testParseWithAbbreviation() {
        BibleReference ref = parser.parse("Mt 28:19");
        assertNotNull(ref);
        assertEquals("Matthew", ref.getBook());
    }

    @Test
    void testParseWholeChapter() {
        BibleReference ref = parser.parse("John 3");
        assertNotNull(ref, "Should support whole chapter references");
        assertEquals("John", ref.getBook());
        assertEquals(3, ref.getChapter());
        assertNull(ref.getVerseStart());
        assertEquals("KJV", ref.getTranslation(), "Should default to KJV");
    }

    @Test
    void testParseCaseInsensitiveTranslation() {
        BibleReference ref = parser.parse("John 3:16 niv");
        assertNotNull(ref);
        assertEquals("NIV", ref.getTranslation(), "Should convert lowercase to uppercase");

        ref = parser.parse("John 3:16 NiV");
        assertNotNull(ref);
        assertEquals("NIV", ref.getTranslation(), "Should handle mixed case");
    }

    @Test
    void testParseSpecificVerses() {
        BibleReference ref = parser.parse("Rom 8:1,3,7");
        assertNotNull(ref);
        assertEquals("Romans", ref.getBook());
        assertEquals(8, ref.getChapter());
        assertTrue(ref.hasSpecificVerses());
        assertEquals(List.of(1, 3, 7), ref.getSpecificVerses());
        assertEquals("KJV", ref.getTranslation());
    }

    @Test
    void testParseSpecificVersesWithTranslation() {
        BibleReference ref = parser.parse("John 1:5,6,10 NIV");
        assertNotNull(ref);
        assertEquals("John", ref.getBook());
        assertEquals(1, ref.getChapter());
        assertTrue(ref.hasSpecificVerses());
        assertEquals(List.of(5, 6, 10), ref.getSpecificVerses());
        assertEquals("NIV", ref.getTranslation());
    }

    @Test
    void testParseSpecificVersesOutOfOrder() {
        BibleReference ref = parser.parse("John 1:10,5,6");
        assertNotNull(ref);
        assertTrue(ref.hasSpecificVerses());
        // Should be sorted
        assertEquals(List.of(5, 6, 10), ref.getSpecificVerses());
        assertEquals("John 1:5,6,10", ref.toDisplayString());
    }

    @Test
    void testParseSpecificVersesTwoVerses() {
        BibleReference ref = parser.parse("Genesis 1:1,3");
        assertNotNull(ref);
        assertEquals("Genesis", ref.getBook());
        assertEquals(1, ref.getChapter());
        assertTrue(ref.hasSpecificVerses());
        assertEquals(List.of(1, 3), ref.getSpecificVerses());
    }

    @Test
    void testSingleVerseHasNoSpecificVerses() {
        BibleReference ref = parser.parse("John 3:16");
        assertNotNull(ref);
        assertFalse(ref.hasSpecificVerses());
        assertNull(ref.getSpecificVerses());
    }

    @Test
    void testParseWholeChapterForNumberedBook() {
        BibleReference ref = parser.parse("2 Kings 2");
        assertNotNull(ref, "Should support whole chapter references for numbered books");
        assertEquals("2 Kings", ref.getBook());
        assertEquals(2, ref.getChapter());
        assertNull(ref.getVerseStart());
        assertEquals("KJV", ref.getTranslation());
    }

    @Test
    void testParseSpaceSeparatedVerseForNumberedBook() {
        BibleReference ref = parser.parse("2 Kings 2 1");
        assertNotNull(ref, "Should support space separated verse references");
        assertEquals("2 Kings", ref.getBook());
        assertEquals(2, ref.getChapter());
        assertEquals(1, ref.getVerseStart());
        assertNull(ref.getVerseEnd());
    }

    @Test
    void testParseThreeWordBookName() {
        BibleReference ref = parser.parse("Song of Solomon 2:1");
        assertNotNull(ref, "Should support three-word book names");
        assertEquals("Song of Solomon", ref.getBook());
        assertEquals(2, ref.getChapter());
        assertEquals(1, ref.getVerseStart());
    }

    @Test
    void testParseTelegramCommandWithMention() {
        BibleReference ref = parser.parse("/get@eccprayerbot 2 Kings 2");
        assertNotNull(ref, "Should ignore Telegram command mentions before parsing");
        assertEquals("2 Kings", ref.getBook());
        assertEquals(2, ref.getChapter());
        assertNull(ref.getVerseStart());
    }
}
