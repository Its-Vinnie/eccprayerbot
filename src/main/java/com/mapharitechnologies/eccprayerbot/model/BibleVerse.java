package com.mapharitechnologies.eccprayerbot.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Represents a Bible verse with its text and metadata
 */
@Document(collection = "verses")
public class BibleVerse {

    @Id
    private String id;

    private String reference;      // e.g., "John 3:16"
    private String text;           // The verse text
    private String translation;    // e.g., "KJV"
    private String book;
    private Integer chapter;
    private Integer verse;
    private String versionName;   // e.g., "NIV"
    private LocalDateTime fetchedAt;
    private LocalDateTime cachedAt;

    public BibleVerse() {
    }

    public BibleVerse(String id, String reference, String text, String translation, String book, Integer chapter, Integer verse, String versionName, LocalDateTime fetchedAt, LocalDateTime cachedAt) {
        this.id = id;
        this.reference = reference;
        this.text = text;
        this.translation = translation;
        this.book = book;
        this.chapter = chapter;
        this.verse = verse;
        this.versionName = versionName;
        this.fetchedAt = fetchedAt;
        this.cachedAt = cachedAt;
    }

    public static BibleVerseBuilder builder() {
        return new BibleVerseBuilder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
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

    public Integer getVerse() {
        return verse;
    }

    public void setVerse(Integer verse) {
        this.verse = verse;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public LocalDateTime getCachedAt() {
        return cachedAt;
    }

    public void setCachedAt(LocalDateTime cachedAt) {
        this.cachedAt = cachedAt;
    }

    public static class BibleVerseBuilder {
        private String id;
        private String reference;
        private String text;
        private String translation;
        private String book;
        private Integer chapter;
        private Integer verse;
        private String versionName;
        private LocalDateTime fetchedAt;
        private LocalDateTime cachedAt;

        public BibleVerseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public BibleVerseBuilder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public BibleVerseBuilder text(String text) {
            this.text = text;
            return this;
        }

        public BibleVerseBuilder translation(String translation) {
            this.translation = translation;
            return this;
        }

        public BibleVerseBuilder book(String book) {
            this.book = book;
            return this;
        }

        public BibleVerseBuilder chapter(Integer chapter) {
            this.chapter = chapter;
            return this;
        }

        public BibleVerseBuilder verse(Integer verse) {
            this.verse = verse;
            return this;
        }

        public BibleVerseBuilder versionName(String versionName) {
            this.versionName = versionName;
            return this;
        }

        public BibleVerseBuilder fetchedAt(LocalDateTime fetchedAt) {
            this.fetchedAt = fetchedAt;
            return this;
        }

        public BibleVerseBuilder cachedAt(LocalDateTime cachedAt) {
            this.cachedAt = cachedAt;
            return this;
        }

        public BibleVerse build() {
            return new BibleVerse(id, reference, text, translation, book, chapter, verse, versionName, fetchedAt, cachedAt);
        }
    }

    /**
     * Formats the verse for Telegram message display
     */
    public String formatForTelegram() {
        StringBuilder formatted = new StringBuilder();

        // Header: Book Chapter:Verse (Translation) 📖
        formatted.append("<b>").append(reference);
        if (versionName != null && !versionName.isBlank()) {
            formatted.append(" (").append(versionName).append(")");
        } else if (translation != null && !translation.isBlank()) {
            formatted.append(" (").append(translation).append(")");
        }
        formatted.append("</b> \uD83D\uDCD6\n\n");

        // Verse text - clean up and format
        String cleanedText = cleanText(text);
        formatted.append(cleanedText);

        return formatted.toString();
    }

    /**
     * Cleans and formats the verse text into readable paragraphs
     */
    private String cleanText(String rawText) {
        if (rawText == null || rawText.isBlank()) return "";

        String cleaned = rawText;

        // Convert bracket verse numbers to bold: [1], [5-10], [11-13], etc.
        cleaned = cleaned.replaceAll("\\[(\\d+(?:-\\d+)?)\\]", "<b>$1</b> ");

        // Collapse all newlines and excess whitespace into single spaces for a clean paragraph
        cleaned = cleaned.replaceAll("\\s+", " ");

        // Add two line breaks before each verse number (except the very first one)
        // Handles single <b>2</b> and range <b>5-10</b> formats
        cleaned = cleaned.replaceAll("(?<!^)\\s*(<b>\\d+(?:-\\d+)?</b>)", "\n\n$1");

        // Trim
        cleaned = cleaned.trim();

        return cleaned;
    }

    /**
     * Creates a simple plain text format
     */
    public String toPlainText() {
        return reference + " - " + text;
    }
}