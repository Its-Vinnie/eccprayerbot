package com.mapharitechnologies.eccprayerbot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageSplitterTest {

    @Test
    void toInlineMessageShouldReturnFullTextWhenWithinLimit() {
        String text = "<b>John 3</b>\n\nShort chapter";
        String inline = MessageSplitter.toInlineMessage(text, "\n\n<i>continued</i>");

        assertTrue(inline.equals(text));
    }

    @Test
    void toInlineMessageShouldTruncateLongTextAndAppendNote() {
        String longText = "<b>Genesis 1 (KJV)</b>\n\n" + "In the beginning ".repeat(500);
        String note = "\n\n<i>Chapter truncated in inline mode.</i>";

        String inline = MessageSplitter.toInlineMessage(longText, note);

        assertTrue(inline.endsWith(note));
        assertTrue(inline.length() < longText.length());
        assertFalse(inline.isBlank());
    }
}
