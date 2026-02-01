package com.mapharitechnologies.eccprayerbot.util;

import com.mapharitechnologies.eccprayerbot.model.BibleReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Bible references from text messages
 * Supports formats like:
 * - John 3:16
 * - John 3:16-18
 * - John 3:16 KJV
 * - 1 John 3:16
 * - Genesis 1:1
 */
@Component
public class BibleReferenceParser {

    private static final Logger log = LoggerFactory.getLogger(BibleReferenceParser.class);

    // Pattern to match Bible references
    // Matches:
    // - Book Chapter (Whole chapter)
    // - Book Chapter:Verse (Single verse)
    // - Book Chapter:Verse-Verse (Verse range)
    // - Book Chapter:Verse Translation (With translation)
    private static final Pattern BIBLE_REF_PATTERN = Pattern.compile(
            "(?i)(\\d?\\s*[a-z]+(?:\\s+[a-z]+)?)\\s+(\\d+)(?::(\\d+)(?:-(\\d+))?)?(?:\\s+([a-z0-9]{2,10}))?",
            Pattern.CASE_INSENSITIVE
    );

    // Common Bible book abbreviations
    private static final Map<String, String> BOOK_ABBREVIATIONS = new HashMap<>();

    static {
        // Old Testament
        BOOK_ABBREVIATIONS.put("gen", "Genesis");
        BOOK_ABBREVIATIONS.put("exod", "Exodus");
        BOOK_ABBREVIATIONS.put("ex", "Exodus");
        BOOK_ABBREVIATIONS.put("lev", "Leviticus");
        BOOK_ABBREVIATIONS.put("num", "Numbers");
        BOOK_ABBREVIATIONS.put("deut", "Deuteronomy");
        BOOK_ABBREVIATIONS.put("josh", "Joshua");
        BOOK_ABBREVIATIONS.put("judg", "Judges");
        BOOK_ABBREVIATIONS.put("ruth", "Ruth");
        BOOK_ABBREVIATIONS.put("1sam", "1 Samuel");
        BOOK_ABBREVIATIONS.put("2sam", "2 Samuel");
        BOOK_ABBREVIATIONS.put("1kgs", "1 Kings");
        BOOK_ABBREVIATIONS.put("2kgs", "2 Kings");
        BOOK_ABBREVIATIONS.put("1chr", "1 Chronicles");
        BOOK_ABBREVIATIONS.put("2chr", "2 Chronicles");
        BOOK_ABBREVIATIONS.put("ezra", "Ezra");
        BOOK_ABBREVIATIONS.put("neh", "Nehemiah");
        BOOK_ABBREVIATIONS.put("esth", "Esther");
        BOOK_ABBREVIATIONS.put("job", "Job");
        BOOK_ABBREVIATIONS.put("ps", "Psalms");
        BOOK_ABBREVIATIONS.put("psa", "Psalms");
        BOOK_ABBREVIATIONS.put("psalm", "Psalms");
        BOOK_ABBREVIATIONS.put("prov", "Proverbs");
        BOOK_ABBREVIATIONS.put("eccl", "Ecclesiastes");
        BOOK_ABBREVIATIONS.put("song", "Song of Solomon");
        BOOK_ABBREVIATIONS.put("isa", "Isaiah");
        BOOK_ABBREVIATIONS.put("jer", "Jeremiah");
        BOOK_ABBREVIATIONS.put("lam", "Lamentations");
        BOOK_ABBREVIATIONS.put("ezek", "Ezekiel");
        BOOK_ABBREVIATIONS.put("dan", "Daniel");
        BOOK_ABBREVIATIONS.put("hos", "Hosea");
        BOOK_ABBREVIATIONS.put("joel", "Joel");
        BOOK_ABBREVIATIONS.put("amos", "Amos");
        BOOK_ABBREVIATIONS.put("obad", "Obadiah");
        BOOK_ABBREVIATIONS.put("jonah", "Jonah");
        BOOK_ABBREVIATIONS.put("mic", "Micah");
        BOOK_ABBREVIATIONS.put("nah", "Nahum");
        BOOK_ABBREVIATIONS.put("hab", "Habakkuk");
        BOOK_ABBREVIATIONS.put("zeph", "Zephaniah");
        BOOK_ABBREVIATIONS.put("hag", "Haggai");
        BOOK_ABBREVIATIONS.put("zech", "Zechariah");
        BOOK_ABBREVIATIONS.put("mal", "Malachi");

        // New Testament
        BOOK_ABBREVIATIONS.put("matt", "Matthew");
        BOOK_ABBREVIATIONS.put("mt", "Matthew");
        BOOK_ABBREVIATIONS.put("mark", "Mark");
        BOOK_ABBREVIATIONS.put("mk", "Mark");
        BOOK_ABBREVIATIONS.put("luke", "Luke");
        BOOK_ABBREVIATIONS.put("lk", "Luke");
        BOOK_ABBREVIATIONS.put("john", "John");
        BOOK_ABBREVIATIONS.put("jn", "John");
        BOOK_ABBREVIATIONS.put("acts", "Acts");
        BOOK_ABBREVIATIONS.put("rom", "Romans");
        BOOK_ABBREVIATIONS.put("1cor", "1 Corinthians");
        BOOK_ABBREVIATIONS.put("2cor", "2 Corinthians");
        BOOK_ABBREVIATIONS.put("gal", "Galatians");
        BOOK_ABBREVIATIONS.put("eph", "Ephesians");
        BOOK_ABBREVIATIONS.put("phil", "Philippians");
        BOOK_ABBREVIATIONS.put("col", "Colossians");
        BOOK_ABBREVIATIONS.put("1thess", "1 Thessalonians");
        BOOK_ABBREVIATIONS.put("2thess", "2 Thessalonians");
        BOOK_ABBREVIATIONS.put("1tim", "1 Timothy");
        BOOK_ABBREVIATIONS.put("2tim", "2 Timothy");
        BOOK_ABBREVIATIONS.put("titus", "Titus");
        BOOK_ABBREVIATIONS.put("phlm", "Philemon");
        BOOK_ABBREVIATIONS.put("heb", "Hebrews");
        BOOK_ABBREVIATIONS.put("jas", "James");
        BOOK_ABBREVIATIONS.put("1pet", "1 Peter");
        BOOK_ABBREVIATIONS.put("2pet", "2 Peter");
        BOOK_ABBREVIATIONS.put("1john", "1 John");
        BOOK_ABBREVIATIONS.put("2john", "2 John");
        BOOK_ABBREVIATIONS.put("3john", "3 John");
        BOOK_ABBREVIATIONS.put("jude", "Jude");
        BOOK_ABBREVIATIONS.put("rev", "Revelation");
    }

    /**
     * Parse a Bible reference from message text
     */
    public BibleReference parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        // Remove bot mentions (e.g., @BotUsername) before parsing
        text = text.replaceAll("@\\w+\\s*", "").trim();

        Matcher matcher = BIBLE_REF_PATTERN.matcher(text);

        if (!matcher.find()) {
            log.debug("No Bible reference pattern found in text: {}", text);
            return null;
        }

        try {
            String bookRaw = matcher.group(1).trim();
            String chapterStr = matcher.group(2);
            String verseStartStr = matcher.group(3);
            String verseEndStr = matcher.group(4);
            String translation = matcher.group(5);

            // Normalize book name
            String book = normalizeBookName(bookRaw);

            // Normalize translation (trim and uppercase for consistency)
            if (translation != null) {
                translation = translation.trim().toUpperCase();
            }

            BibleReference reference = BibleReference.builder()
                    .book(book)
                    .chapter(Integer.parseInt(chapterStr))
                    .verseStart(verseStartStr != null ? Integer.parseInt(verseStartStr) : null)
                    .verseEnd(verseEndStr != null ? Integer.parseInt(verseEndStr) : null)
                    .translation(translation != null && !translation.isEmpty() ? translation : "KJV")
                    .build();

            if (!reference.isValid()) {
                log.warn("Invalid Bible reference parsed: {}", reference);
                return null;
            }

            log.debug("Successfully parsed reference: {}", reference.toDisplayString());
            return reference;

        } catch (Exception e) {
            log.error("Error parsing Bible reference from text: {}", text, e);
            return null;
        }
    }

    /**
     * Normalize book name (expand abbreviations, fix capitalization)
     */
    private String normalizeBookName(String bookRaw) {
        String normalized = bookRaw.trim().toLowerCase().replaceAll("\\s+", "");

        // Check if it's an abbreviation
        String fullName = BOOK_ABBREVIATIONS.get(normalized);
        if (fullName != null) {
            return fullName;
        }

        // Capitalize first letter of each word
        String[] words = bookRaw.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Check if message contains a Bible reference
     */
    public boolean containsReference(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return BIBLE_REF_PATTERN.matcher(text).find();
    }
}