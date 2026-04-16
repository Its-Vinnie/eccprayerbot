package com.mapharitechnologies.eccprayerbot.util;

import com.mapharitechnologies.eccprayerbot.model.BibleReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Matches the part after the book name:
    // - Chapter
    // - Chapter:Verse
    // - Chapter Verse (space-separated verse)
    // - Chapter:Verse-Verse
    // - Chapter:Verse,Verse,Verse
    // - Optional translation suffix
    private static final Pattern REFERENCE_DETAILS_PATTERN = Pattern.compile(
            "^(\\d+)(?:(?::|\\s+)(\\d+(?:,\\d+)*)(?:-(\\d+))?)?(?:\\s+([a-z0-9]{2,10}))?$",
            Pattern.CASE_INSENSITIVE
    );

    // Canonical book names plus common abbreviations and compact forms.
    private static final Map<String, String> BOOK_ALIASES = new HashMap<>();
    private static final List<String> SORTED_BOOK_ALIASES = new ArrayList<>();

    static {
        // Old Testament
        registerBook("Genesis", "gen");
        registerBook("Exodus", "exod", "ex");
        registerBook("Leviticus", "lev");
        registerBook("Numbers", "num");
        registerBook("Deuteronomy", "deut", "deu");
        registerBook("Joshua", "josh", "jos");
        registerBook("Judges", "judg", "jdg");
        registerBook("Ruth");
        registerBook("1 Samuel", "1sam", "1 sam", "1sa", "1 sa");
        registerBook("2 Samuel", "2sam", "2 sam", "2sa", "2 sa");
        registerBook("1 Kings", "1kgs", "1 kgs", "1kg", "1 kg", "1ki", "1 ki");
        registerBook("2 Kings", "2kgs", "2 kgs", "2kg", "2 kg", "2ki", "2 ki");
        registerBook("1 Chronicles", "1chr", "1 chr", "1ch", "1 ch");
        registerBook("2 Chronicles", "2chr", "2 chr", "2ch", "2 ch");
        registerBook("Ezra", "ezr");
        registerBook("Nehemiah", "neh");
        registerBook("Esther", "esth", "est");
        registerBook("Job");
        registerBook("Psalms", "ps", "psa", "psalm");
        registerBook("Proverbs", "prov", "pro");
        registerBook("Ecclesiastes", "eccl", "ecc");
        registerBook("Song of Solomon", "song", "song of songs", "songs");
        registerBook("Isaiah", "isa");
        registerBook("Jeremiah", "jer");
        registerBook("Lamentations", "lam");
        registerBook("Ezekiel", "ezek", "ezk");
        registerBook("Daniel", "dan");
        registerBook("Hosea", "hos");
        registerBook("Joel", "jol");
        registerBook("Amos", "amo");
        registerBook("Obadiah", "obad", "oba");
        registerBook("Jonah", "jon");
        registerBook("Micah", "mic");
        registerBook("Nahum", "nah", "nam");
        registerBook("Habakkuk", "hab");
        registerBook("Zephaniah", "zeph", "zep");
        registerBook("Haggai", "hag");
        registerBook("Zechariah", "zech", "zec");
        registerBook("Malachi", "mal");

        // New Testament
        registerBook("Matthew", "matt", "mt", "mat");
        registerBook("Mark", "mk", "mrk");
        registerBook("Luke", "lk", "luk");
        registerBook("John", "jn", "jhn");
        registerBook("Acts", "act");
        registerBook("Romans", "rom");
        registerBook("1 Corinthians", "1cor", "1 cor", "1co", "1 co");
        registerBook("2 Corinthians", "2cor", "2 cor", "2co", "2 co");
        registerBook("Galatians", "gal");
        registerBook("Ephesians", "eph");
        registerBook("Philippians", "phil", "php");
        registerBook("Colossians", "col");
        registerBook("1 Thessalonians", "1thess", "1 thess", "1thes", "1 thes", "1th", "1 th");
        registerBook("2 Thessalonians", "2thess", "2 thess", "2thes", "2 thes", "2th", "2 th");
        registerBook("1 Timothy", "1tim", "1 tim", "1ti", "1 ti");
        registerBook("2 Timothy", "2tim", "2 tim", "2ti", "2 ti");
        registerBook("Titus", "tit");
        registerBook("Philemon", "phlm", "phm");
        registerBook("Hebrews", "heb");
        registerBook("James", "jas", "jam");
        registerBook("1 Peter", "1pet", "1 pet", "1pe", "1 pe");
        registerBook("2 Peter", "2pet", "2 pet", "2pe", "2 pe");
        registerBook("1 John", "1john", "1 john", "1jn", "1 jn");
        registerBook("2 John", "2john", "2 john", "2jn", "2 jn");
        registerBook("3 John", "3john", "3 john", "3jn", "3 jn");
        registerBook("Jude", "jud");
        registerBook("Revelation", "rev", "re");

        SORTED_BOOK_ALIASES.addAll(BOOK_ALIASES.keySet());
        SORTED_BOOK_ALIASES.sort(Comparator
                .comparingInt((String alias) -> alias.split(" ").length)
                .thenComparingInt(String::length)
                .reversed());
    }

    /**
     * Parse a Bible reference from message text
     */
    public BibleReference parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String normalizedText = sanitizeInput(text);
        if (normalizedText.isBlank()) {
            return null;
        }

        try {
            BookMatch bookMatch = findBookMatch(normalizedText);
            if (bookMatch == null) {
                log.debug("No known Bible book found in text: {}", normalizedText);
                return null;
            }

            Matcher matcher = REFERENCE_DETAILS_PATTERN.matcher(bookMatch.remainingText());
            if (!matcher.matches()) {
                log.debug("Book matched but reference details were invalid: {}", normalizedText);
                return null;
            }

            String chapterStr = matcher.group(1);
            String verseGroup = matcher.group(2);  // Could be "1" or "1,3,7"
            String verseEndStr = matcher.group(3);
            String translation = matcher.group(4);

            // Normalize translation (trim and uppercase for consistency)
            if (translation != null) {
                translation = translation.trim().toUpperCase();
            }

            // Parse verse numbers - handle comma-separated specific verses
            Integer verseStart = null;
            Integer verseEnd = verseEndStr != null ? Integer.parseInt(verseEndStr) : null;
            List<Integer> specificVerses = null;

            if (verseGroup != null) {
                if (verseGroup.contains(",")) {
                    // Comma-separated verses like "1,3,7"
                    specificVerses = new ArrayList<>();
                    for (String v : verseGroup.split(",")) {
                        specificVerses.add(Integer.parseInt(v.trim()));
                    }
                    // Sort them for ordered display
                    specificVerses.sort(Integer::compareTo);
                    verseStart = specificVerses.get(0);
                } else {
                    verseStart = Integer.parseInt(verseGroup);
                }
            }

            BibleReference reference = BibleReference.builder()
                    .book(bookMatch.book())
                    .chapter(Integer.parseInt(chapterStr))
                    .verseStart(verseStart)
                    .verseEnd(verseEnd)
                    .translation(translation != null && !translation.isEmpty() ? translation : "KJV")
                    .specificVerses(specificVerses)
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
     * Check if message contains a Bible reference
     */
    public boolean containsReference(String text) {
        return parse(text) != null;
    }

    /**
     * Remove Telegram command prefixes and normalize spacing before parsing.
     */
    private String sanitizeInput(String text) {
        String sanitized = text.trim();
        sanitized = sanitized.replaceAll("(?i)^/?(get|find)(@\\w+)?\\s+", "");
        sanitized = sanitized.replaceAll("(?i)^@\\w+\\s*", "");
        sanitized = sanitized.replaceAll("(?i)@\\w+", " ");
        sanitized = sanitized.replace(".", "");
        sanitized = sanitized.replaceAll("\\s+", " ").trim().toLowerCase();
        return sanitized;
    }

    private BookMatch findBookMatch(String text) {
        for (String alias : SORTED_BOOK_ALIASES) {
            if (text.equals(alias)) {
                return null;
            }
            if (text.startsWith(alias + " ")) {
                String book = BOOK_ALIASES.get(alias);
                String remainingText = text.substring(alias.length()).trim();
                return new BookMatch(book, remainingText);
            }
        }
        return null;
    }

    private static void registerBook(String canonicalName, String... aliases) {
        Set<String> allAliases = new LinkedHashSet<>();
        allAliases.add(canonicalName);
        allAliases.add(canonicalName.replace(" ", ""));

        for (String alias : aliases) {
            allAliases.add(alias);
            allAliases.add(alias.replace(" ", ""));
        }

        for (String alias : allAliases) {
            BOOK_ALIASES.put(normalizeAlias(alias), canonicalName);
        }
    }

    private static String normalizeAlias(String alias) {
        return alias.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    private record BookMatch(String book, String remainingText) {
    }
}
