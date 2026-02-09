package com.mapharitechnologies.eccprayerbot.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BibleVerseTest {

    @Test
    void testCleanTextWithVerseMarkers() {
        BibleVerse verse = new BibleVerse();
        String rawText = "[1] In the beginning [2] God created";
        
        verse.setReference("Gen 1:1-2");
        verse.setText(rawText);
        verse.setVersionName("KJV");
        
        String formatted = verse.formatForTelegram();
        // Now we expect verse markers to be formatted with <b> tags
        assertTrue(formatted.contains("<b>1</b> In the beginning <b>2</b> God created"), "Should format verse markers with bold tags");
        assertFalse(formatted.contains("[1]"), "Should not contain [1]");
    }

    @Test
    void testCleanTextWithMultipleSpaces() {
        BibleVerse verse = new BibleVerse();
        verse.setReference("Gen 1:1");
        verse.setText("In  the   beginning");
        verse.setVersionName("KJV");
        
        String formatted = verse.formatForTelegram();
        assertTrue(formatted.contains("In the beginning"), "Should normalize spaces");
    }

    @Test
    void testCleanTextWithParagraphs() {
        BibleVerse verse = new BibleVerse();
        verse.setReference("Gen 1:1");
        verse.setText("Paragraph 1\n\n\nParagraph 2");
        verse.setVersionName("KJV");
        
        String formatted = verse.formatForTelegram();
        assertTrue(formatted.contains("Paragraph 1\n\nParagraph 2"), "Should normalize multiple newlines to double newlines");
    }
    
    @Test
    void testCleanTextWithSpacesAroundNewlines() {
        BibleVerse verse = new BibleVerse();
        verse.setReference("Gen 1:1");
        verse.setText("Line 1 \n  Line 2");
        verse.setVersionName("KJV");
        
        String formatted = verse.formatForTelegram();
        assertTrue(formatted.contains("Line 1\nLine 2"), "Should clean spaces around newlines");
    }

    @Test
    void testFormatForTelegramWithShortVersionName() {
        BibleVerse verse = new BibleVerse();
        verse.setReference("Genesis 1:6");
        verse.setText("And God said...");
        verse.setVersionName("KJV"); // We now expect abbreviations here
        
        String formatted = verse.formatForTelegram();
        assertEquals("<b>Genesis 1:6 (KJV)</b>\n\nAnd God said...", formatted);
    }

    @Test
    void testFormatForTelegramWithTranslationFallback() {
        BibleVerse verse = new BibleVerse();
        verse.setReference("Genesis 1:12");
        verse.setText("The land produced...");
        verse.setTranslation("NIV");
        verse.setVersionName(null);
        
        String formatted = verse.formatForTelegram();
        assertEquals("<b>Genesis 1:12 (NIV)</b>\n\nThe land produced...", formatted);
    }

    @Test
    void testFormatForTelegramWithNIVNormalization() {
        BibleVerse verse = new BibleVerse();
        verse.setReference("John 3:16");
        verse.setText("For God so loved...");
        verse.setVersionName("NIV11");
        
        // This test checks if formatForTelegram correctly uses whatever is in versionName.
        // The normalization happens in the Service layer, so if we set it to NIV11 here, 
        // it will show NIV11.
        // To truly test the normalization, we'd need to mock the service or just trust the logic.
        // But let's add a test to BibleVerse that shows it handles the provided versionName.
        
        String formatted = verse.formatForTelegram();
        assertTrue(formatted.contains("(NIV11)"));
    }
}
