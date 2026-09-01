package com.mapharitechnologies.eccprayerbot.service;

import com.mapharitechnologies.eccprayerbot.handler.PrayerBotHandler;
import com.mapharitechnologies.eccprayerbot.model.BibleVerse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

/**
 * Service for sending Bible verses to Telegram chats via the existing bot.
 * Used by the REST API's forward endpoint to send verses from Shortcuts/Mount Zion.
 */
@Service
public class TelegramMessageService {

    private static final Logger log = LoggerFactory.getLogger(TelegramMessageService.class);

    private final PrayerBotHandler botHandler;

    public TelegramMessageService(PrayerBotHandler botHandler) {
        this.botHandler = botHandler;
    }

    /**
     * Send a Bible verse to a Telegram chat asynchronously.
     * Fire-and-forget — failures are logged but never propagate.
     *
     * @param chatId the Telegram chat ID (user private chat or group)
     * @param verse  the Bible verse to send
     */
    @Async
    public void sendVerse(String chatId, BibleVerse verse) {
        try {
            String text = verse.formatForTelegram();
            List<String> chunks = splitMessage(text);

            for (String chunk : chunks) {
                SendMessage message = SendMessage.builder()
                        .chatId(chatId)
                        .text(chunk)
                        .parseMode("HTML")
                        .disableWebPagePreview(true)
                        .build();
                botHandler.execute(message);
            }

            log.info("Sent verse {} to chat {}", verse.getReference(), chatId);
        } catch (Exception e) {
            log.error("Failed to send verse to chat {}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Send a plain text message to a Telegram chat asynchronously.
     * Fire-and-forget — failures are logged but never propagate.
     *
     * @param chatId the Telegram chat ID
     * @param text   the message text (HTML formatting supported)
     */
    @Async
    public void sendMessage(String chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .build();
            botHandler.execute(message);

            log.info("Sent message to chat {}", chatId);
        } catch (Exception e) {
            log.error("Failed to send message to chat {}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Split a long message into chunks that fit Telegram's 4096 character limit.
     */
    private List<String> splitMessage(String text) {
        if (text == null || text.length() <= 4096) {
            return List.of(text);
        }

        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() + 2 > 4090) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }
                // If a single paragraph exceeds the limit, split by lines
                if (paragraph.length() > 4090) {
                    String[] lines = paragraph.split("\n");
                    for (String line : lines) {
                        if (current.length() + line.length() + 1 > 4090) {
                            if (current.length() > 0) {
                                chunks.add(current.toString());
                                current = new StringBuilder();
                            }
                            current.append(line);
                        } else {
                            if (current.length() > 0) current.append("\n");
                            current.append(line);
                        }
                    }
                } else {
                    current.append(paragraph);
                }
            } else {
                if (current.length() > 0) current.append("\n\n");
                current.append(paragraph);
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        return chunks.isEmpty() ? List.of(text) : chunks;
    }
}
