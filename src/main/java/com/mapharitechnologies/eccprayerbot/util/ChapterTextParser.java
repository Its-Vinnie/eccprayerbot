package com.mapharitechnologies.eccprayerbot.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a full chapter of Bible text (with embedded verse numbers) into individual verses.
 *
 * Input format (from Bible services):
 *   "&lt;b&gt;1&lt;/b&gt; In the beginning... &lt;b&gt;2&lt;/b&gt; And the earth..."
 *
 * Output: list of (verseNumber, verseText) pairs.
 */
public final class ChapterTextParser {

    private ChapterTextParser() {}

    /**
     * A single verse extracted from a chapter.
     */
    public record ParsedVerse(int verseNumber, String text) {}

    /**
     * Parse chapter text into individual verses.
     *
     * @param chapterText the raw chapter text with verse numbers
     * @return ordered list of parsed verses
     */
    public static List<ParsedVerse> parse(String chapterText) {
        if (chapterText == null || chapterText.isBlank()) {
            return List.of();
        }

        // Split on <b>N</b> patterns — Java split with capturing group includes captured text.
        // "text <b>1</b> v1 <b>2</b> v2" → ["text ", "1", " v1 ", "2", " v2"]
        String[] parts = chapterText.split("<b>(\\d+)</b>");

        List<ParsedVerse> verses = new ArrayList<>();

        // parts[0] = text before first verse (header or empty)
        // Then alternating: verseNumber, verseText
        for (int i = 1; i < parts.length - 1; i += 2) {
            try {
                int verseNum = Integer.parseInt(parts[i].trim());
                String text = stripAndNormalize(parts[i + 1]);
                if (!text.isEmpty()) {
                    verses.add(new ParsedVerse(verseNum, text));
                }
            } catch (NumberFormatException e) {
                // Skip malformed verse numbers
            }
        }

        // Handle case where there's no closing <b> tag for the last verse
        // (some API responses end with text after the last verse number)
        if (parts.length >= 2) {
            int lastVerseIndex = parts.length - 1;
            if (lastVerseIndex % 2 == 0) {
                // Even index means there's trailing text after the last verse number
                // The last verse number was at parts[lastVerseIndex - 1]
                try {
                    int lastVerseNum = Integer.parseInt(parts[lastVerseIndex - 1].trim());
                    String trailingText = stripAndNormalize(parts[lastVerseIndex]);
                    // Check if this verse was already added
                    if (!verses.isEmpty() && verses.get(verses.size() - 1).verseNumber() == lastVerseNum) {
                        // Update the last verse with the trailing text
                        ParsedVerse last = verses.remove(verses.size() - 1);
                        if (!trailingText.isEmpty()) {
                            String combined = last.text().isEmpty()
                                    ? trailingText
                                    : last.text() + " " + trailingText;
                            verses.add(new ParsedVerse(lastVerseNum, combined));
                        } else {
                            verses.add(last);
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }

        return verses;
    }

    private static String stripAndNormalize(String text) {
        if (text == null) return "";
        return text
                .replaceAll("<[^>]+>", "")   // strip any remaining HTML tags
                .replaceAll("\\s+", " ")     // collapse whitespace
                .trim();
    }
}
