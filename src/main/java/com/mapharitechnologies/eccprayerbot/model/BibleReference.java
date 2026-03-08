package com.mapharitechnologies.eccprayerbot.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed Bible reference (e.g., "John 3:16")
 */
public class BibleReference {

    private String book;           // e.g., "John"
    private Integer chapter;       // e.g., 3
    private Integer verseStart;    // e.g., 16
    private Integer verseEnd;      // e.g., 18 (optional for ranges)
    private String translation;    // e.g., "KJV", "NIV" (optional)
    private List<Integer> specificVerses;  // e.g., [1, 3, 7] for "Rom 8:1,3,7"

    public BibleReference() {
    }

    public BibleReference(String book, Integer chapter, Integer verseStart, Integer verseEnd, String translation) {
        this.book = book;
        this.chapter = chapter;
        this.verseStart = verseStart;
        this.verseEnd = verseEnd;
        this.translation = translation;
    }

    public BibleReference(String book, Integer chapter, Integer verseStart, Integer verseEnd, String translation, List<Integer> specificVerses) {
        this.book = book;
        this.chapter = chapter;
        this.verseStart = verseStart;
        this.verseEnd = verseEnd;
        this.translation = translation;
        this.specificVerses = specificVerses;
    }

    public static BibleReferenceBuilder builder() {
        return new BibleReferenceBuilder();
    }

    public String getBook() {
        return book;
    }

    public void setBook(String book) {
        this.book = book;
    }

    public Integer getChapter() {
        return chapter;
    }

    public void setChapter(Integer chapter) {
        this.chapter = chapter;
    }

    public Integer getVerseStart() {
        return verseStart;
    }

    public void setVerseStart(Integer verseStart) {
        this.verseStart = verseStart;
    }

    public Integer getVerseEnd() {
        return verseEnd;
    }

    public void setVerseEnd(Integer verseEnd) {
        this.verseEnd = verseEnd;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public List<Integer> getSpecificVerses() {
        return specificVerses;
    }

    public void setSpecificVerses(List<Integer> specificVerses) {
        this.specificVerses = specificVerses;
    }

    public boolean hasSpecificVerses() {
        return specificVerses != null && specificVerses.size() > 1;
    }

    public static class BibleReferenceBuilder {
        private String book;
        private Integer chapter;
        private Integer verseStart;
        private Integer verseEnd;
        private String translation;
        private List<Integer> specificVerses;

        public BibleReferenceBuilder book(String book) {
            this.book = book;
            return this;
        }

        public BibleReferenceBuilder chapter(Integer chapter) {
            this.chapter = chapter;
            return this;
        }

        public BibleReferenceBuilder verseStart(Integer verseStart) {
            this.verseStart = verseStart;
            return this;
        }

        public BibleReferenceBuilder verseEnd(Integer verseEnd) {
            this.verseEnd = verseEnd;
            return this;
        }

        public BibleReferenceBuilder translation(String translation) {
            this.translation = translation;
            return this;
        }

        public BibleReferenceBuilder specificVerses(List<Integer> specificVerses) {
            this.specificVerses = specificVerses;
            return this;
        }

        public BibleReference build() {
            return new BibleReference(book, chapter, verseStart, verseEnd, translation, specificVerses);
        }
    }

    /**
     * Returns a normalized key for caching
     */
    public String getCacheKey() {
        StringBuilder key = new StringBuilder()
                .append(book.toLowerCase().replaceAll("\\s+", ""))
                .append("-")
                .append(chapter);

        if (hasSpecificVerses()) {
            List<Integer> sorted = new ArrayList<>(specificVerses);
            sorted.sort(Integer::compareTo);
            for (Integer v : sorted) {
                key.append("-").append(v);
            }
        } else {
            key.append("-").append(verseStart);
            if (verseEnd != null && !verseEnd.equals(verseStart)) {
                key.append("-").append(verseEnd);
            }
        }

        if (translation != null) {
            key.append("-").append(translation.toLowerCase());
        }

        return key.toString();
    }

    /**
     * Returns a human-readable reference string
     */
    public String toDisplayString() {
        StringBuilder display = new StringBuilder()
                .append(book)
                .append(" ")
                .append(chapter);

        if (hasSpecificVerses()) {
            display.append(":");
            List<Integer> sorted = new ArrayList<>(specificVerses);
            sorted.sort(Integer::compareTo);
            for (int i = 0; i < sorted.size(); i++) {
                if (i > 0) display.append(",");
                display.append(sorted.get(i));
            }
        } else if (verseStart != null) {
            display.append(":").append(verseStart);

            if (verseEnd != null && !verseEnd.equals(verseStart)) {
                display.append("-").append(verseEnd);
            }
        }

        return display.toString();
    }

    public boolean isValid() {
        return book != null && !book.isBlank()
                && chapter != null && chapter > 0
                && (verseStart == null || (verseStart > 0 && (verseEnd == null || verseEnd >= verseStart)));
    }
}