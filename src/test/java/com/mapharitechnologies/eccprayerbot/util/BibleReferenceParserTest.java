package com.mapharitechnologies.eccprayerbot.util;

import com.mapharitechnologies.eccprayerbot.model.BibleReference;
import org.junit.jupiter.api.Test;
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
}
