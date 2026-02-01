package com.mapharitechnologies.eccprayerbot.handler;

import com.mapharitechnologies.eccprayerbot.model.BibleReference;
import com.mapharitechnologies.eccprayerbot.model.BibleVerse;
import com.mapharitechnologies.eccprayerbot.model.BotRequest;
import com.mapharitechnologies.eccprayerbot.service.BibleService;
import com.mapharitechnologies.eccprayerbot.service.RequestLoggingService;
import com.mapharitechnologies.eccprayerbot.util.BibleReferenceParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Telegram Bot Handler
 *
 * Product Principle: ECCPrayerBot must always serve the sermon and never interrupt it.
 *
 * Behavior:
 * - Only responds when mentioned (@botusername)
 * - Fetches Bible verses instantly (target < 2 seconds)
 * - Provides clean, readable formatting
 * - Gives polite, minimal error messages
 * - Never sends unsolicited messages
 */
@Component
public class PrayerBotHandler extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(PrayerBotHandler.class);

    private final String botUsername;
    private final BibleService bibleService;
    private final BibleReferenceParser referenceParser;
    private final RequestLoggingService loggingService;

    public PrayerBotHandler(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            BibleService bibleService,
            BibleReferenceParser referenceParser,
            RequestLoggingService loggingService) {

        super(botToken);
        this.botUsername = botUsername;
        this.bibleService = bibleService;
        this.referenceParser = referenceParser;
        this.loggingService = loggingService;

        clearWebhooks(botToken);
        logger.info("ECCPrayerBot initialized as @{}", botUsername);
    }

    private void clearWebhooks(String botToken) {
        try {
            logger.info("Clearing any existing webhooks before starting long polling...");
            execute(new DeleteWebhook());
        } catch (TelegramApiException e) {
            logger.warn("Failed to clear webhooks: {}", e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message message = null;
        if (update.hasMessage()) {
            message = update.getMessage();
        } else if (update.hasChannelPost()) {
            message = update.getChannelPost();
        }

        if (message == null || !message.hasText()) {
            return;
        }

        String messageText = message.getText();
        Long chatId = message.getChatId();
        User user = message.getFrom();

        // CRITICAL: Only respond when bot is mentioned
        if (!isBotMentioned(messageText)) {
            logger.debug("Bot not mentioned, ignoring message in chat {}", chatId);
            return;
        }

        logger.info("Bot mentioned in chat {}: {}", chatId, messageText);

        // Start timing
        long startTime = System.currentTimeMillis();

        // Create request log
        // Note: For channel posts, message.getFrom() might be null or a "Channel" user
        BotRequest request = BotRequest.createFromMessage(
                chatId,
                user != null ? user.getId() : 0L,
                user != null ? user.getUserName() : "Channel",
                user != null ? user.getFirstName() : "Channel",
                messageText
        );

        try {
            // Parse Bible reference
            BibleReference reference = referenceParser.parse(messageText);

            if (reference == null) {
                handleInvalidReference(chatId, startTime, request);
                return;
            }

            // Fetch verse
            BibleVerse verse = bibleService.getVerse(reference);

            if (verse == null || verse.getText() == null) {
                handleFetchFailure(chatId, reference, startTime, request);
                return;
            }

            // Send verse
            sendVerse(chatId, verse);

            // Log success
            long responseTime = System.currentTimeMillis() - startTime;
            loggingService.logSuccess(request, reference.toDisplayString(), responseTime);

            logger.info("Successfully sent verse {} to chat {} in {}ms",
                    reference.toDisplayString(), chatId, responseTime);

        } catch (Exception e) {
            handleGeneralError(chatId, startTime, request, e);
        }
    }

    /**
     * Check if bot is mentioned in the message
     */
    private boolean isBotMentioned(String messageText) {
        if (messageText == null) return false;

        String mention = "@" + botUsername.toLowerCase();
        return messageText.toLowerCase().contains(mention);
    }

    /**
     * Send a Bible verse to the chat
     */
    private void sendVerse(Long chatId, BibleVerse verse) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(verse.formatForTelegram())
                    .parseMode("Markdown")
                    .disableWebPagePreview(true)
                    .build();

            execute(message);

        } catch (TelegramApiException e) {
            logger.error("Failed to send verse to chat {}", chatId, e);
            throw new RuntimeException("Failed to send message", e);
        }
    }

    /**
     * Send a simple text message
     */
    private void sendMessage(Long chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .disableWebPagePreview(true)
                    .build();

            execute(message);

        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chat {}", chatId, e);
        }
    }

    /**
     * Handle invalid reference format
     */
    private void handleInvalidReference(Long chatId, long startTime, BotRequest request) {
        String errorMsg = "Please provide a valid Bible reference (e.g., John 3:16)";
        sendMessage(chatId, errorMsg);

        long responseTime = System.currentTimeMillis() - startTime;
        loggingService.logFailure(request, "Invalid reference format", responseTime);

        logger.warn("Invalid reference in chat {}", chatId);
    }

    /**
     * Handle verse fetch failure
     */
    private void handleFetchFailure(Long chatId, BibleReference reference,
                                    long startTime, BotRequest request) {
        String errorMsg = "Unable to retrieve " + reference.toDisplayString() + " at this time";
        sendMessage(chatId, errorMsg);

        long responseTime = System.currentTimeMillis() - startTime;
        loggingService.logFailure(request, "Verse fetch failed", responseTime);

        logger.error("Failed to fetch verse {} for chat {}", reference.toDisplayString(), chatId);
    }

    /**
     * Handle general errors
     */
    private void handleGeneralError(Long chatId, long startTime, BotRequest request, Exception e) {
        String errorMsg = "Sorry, I encountered an issue. Please try again";
        sendMessage(chatId, errorMsg);

        long responseTime = System.currentTimeMillis() - startTime;
        loggingService.logFailure(request, e.getMessage(), responseTime);

        logger.error("Error processing message in chat {}", chatId, e);
    }
}