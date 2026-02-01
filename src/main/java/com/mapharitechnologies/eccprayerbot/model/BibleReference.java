package com.mapharitechnologies.eccprayerbot.model;

/**
 * Represents a parsed Bible reference (e.g., "John 3:16")
 */
public class BibleReference {

    private String book;           // e.g., "John"
    private Integer chapter;       // e.g., 3
    private Integer verseStart;    // e.g., 16
    private Integer verseEnd;      // e.g., 18 (optional for ranges)
    private String translation;    // e.g., "KJV", "NIV" (optional)

    public BibleReference() {
    }

    public BibleReference(String book, Integer chapter, Integer verseStart, Integer verseEnd, String translation) {
        this.book = book;
        this.chapter = chapter;
        this.verseStart = verseStart;
        this.verseEnd = verseEnd;
        this.translation = translation;
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

    public static class BibleReferenceBuilder {
        private String book;
        private Integer chapter;
        private Integer verseStart;
        private Integer verseEnd;
        private String translation;

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

        public BibleReference build() {
            return new BibleReference(book, chapter, verseStart, verseEnd, translation);
        }
    }

    /**
     * Returns a normalized key for caching
     */
    public String getCacheKey() {
        StringBuilder key = new StringBuilder()
                .append(book.toLowerCase().replaceAll("\\s+", ""))
                .append("-")
                .append(chapter)
                .append("-")
                .append(verseStart);

        if (verseEnd != null && !verseEnd.equals(verseStart)) {
            key.append("-").append(verseEnd);
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

        if (verseStart != null) {
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