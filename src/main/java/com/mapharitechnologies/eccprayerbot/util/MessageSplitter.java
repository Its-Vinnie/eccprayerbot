package com.mapharitechnologies.eccprayerbot.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageSplitter {

    private static final int MAX_LENGTH = 4000; // Leave some headroom for safety (Telegram limit is 4096)

    /**
     * Splits a text into chunks that are safe for Telegram, preserving HTML tags.
     */
    public static List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        if (text.length() <= MAX_LENGTH) {
            chunks.add(text);
            return chunks;
        }

        String remaining = text;
        while (remaining.length() > MAX_LENGTH) {
            int splitIndex = findSafeSplitIndex(remaining, MAX_LENGTH);
            String chunk = remaining.substring(0, splitIndex);
            
            // Handle balanced tags for this chunk
            chunk = balanceTags(chunk);
            
            chunks.add(chunk);
            remaining = remaining.substring(splitIndex).trim();
            
            // If the next chunk starts with something that should have been preceded by tags,
            // we might need to prepend opening tags, but for Bible verses <b> is usually per-verse.
            // However, the header <b>Reference (Version)</b> is at the top.
            // If we split in the middle of the text, we just need to make sure the current chunk is valid HTML.
        }

        if (!remaining.isBlank()) {
            chunks.add(remaining);
        }

        return chunks;
    }

    private static int findSafeSplitIndex(String text, int maxLength) {
        if (text.length() <= maxLength) return text.length();

        // Try to split at a newline first
        int lastNewline = text.lastIndexOf('\n', maxLength);
        if (lastNewline > maxLength * 0.7) { // Only split at newline if it's reasonably far into the chunk
            return lastNewline + 1;
        }

        // Try to split at a space
        int lastSpace = text.lastIndexOf(' ', maxLength);
        if (lastSpace > maxLength * 0.5) {
            return lastSpace + 1;
        }

        // Fallback to absolute limit
        return maxLength;
    }

    /**
     * Ensures all HTML tags in the chunk are closed.
     * Note: This is a simplified version focusing on common tags used in this bot (like <b>).
     */
    private static String balanceTags(String chunk) {
        Stack<String> openTags = new Stack<>();
        Pattern tagPattern = Pattern.compile("<(/?[a-zA-Z0-9]+)>");
        Matcher matcher = tagPattern.matcher(chunk);

        while (matcher.find()) {
            String tag = matcher.group(1);
            if (tag.startsWith("/")) {
                String tagName = tag.substring(1);
                if (!openTags.isEmpty() && openTags.peek().equals(tagName)) {
                    openTags.pop();
                }
            } else {
                openTags.push(tag);
            }
        }

        StringBuilder balanced = new StringBuilder(chunk);
        while (!openTags.isEmpty()) {
            balanced.append("</").append(openTags.pop()).append(">");
        }

        return balanced.toString();
    }
}
