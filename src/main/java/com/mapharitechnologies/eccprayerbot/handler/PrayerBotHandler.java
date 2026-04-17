package com.mapharitechnologies.eccprayerbot.handler;

import com.mapharitechnologies.eccprayerbot.model.BibleReference;
import com.mapharitechnologies.eccprayerbot.model.BibleVerse;
import com.mapharitechnologies.eccprayerbot.model.BotRequest;
import com.mapharitechnologies.eccprayerbot.service.BibleService;
import com.mapharitechnologies.eccprayerbot.service.RequestLoggingService;
import com.mapharitechnologies.eccprayerbot.util.BibleReferenceParser;
import com.mapharitechnologies.eccprayerbot.util.MessageSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
    private static final String INLINE_TRUNCATION_NOTE =
            "\n\n<i>Chapter truncated in inline mode. Use /get with this reference to read the full chapter.</i>";

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
        // Handle inline queries
        if (update.hasInlineQuery()) {
            handleInlineQuery(update.getInlineQuery());
            return;
        }

        Message message = null;
        if (update.hasMessage()) {
            message = update.getMessage();
        } else if (update.hasChannelPost()) {
            message = update.getChannelPost();
        }

        if (message == null || !message.hasText()) {
            return;
        }

        // Inline-generated messages arrive back as chat updates. Use that to continue long chapters.
        if (message.getViaBot() != null && botUsername.equalsIgnoreCase(message.getViaBot().getUserName())) {
            handleInlinePostedMessage(message);
            return;
        }

        String messageText = message.getText();
        Long chatId = message.getChatId();
        User user = message.getFrom();

        // Handle /search command
        if (isSearchCommand(messageText)) {
            handleSearchCommand(chatId, messageText, user);
            return;
        }

        // Only respond to direct triggers (@mention, /get, /find, get, find)
        if (!isDirectTrigger(messageText)) {
            return;
        }

        logger.info("Verse request in chat {}: {}", chatId, messageText);

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

            // Fetch verse(s)
            BibleVerse verse;
            if (reference.hasSpecificVerses()) {
                verse = bibleService.getSpecificVerses(reference);
            } else {
                verse = bibleService.getVerse(reference);
            }

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
     * Handle inline queries - users type @botusername followed by a Bible reference
     */
    private void handleInlineQuery(InlineQuery inlineQuery) {
        String queryText = inlineQuery.getQuery().trim();

        if (queryText.isEmpty()) {
            answerInlineWithHint(inlineQuery.getId());
            return;
        }

        logger.info("Inline query from user {}: {}", inlineQuery.getFrom().getId(), queryText);

        long startTime = System.currentTimeMillis();

        try {
            // First try parsing as a Bible reference (e.g. "John 3:16")
            BibleReference reference = referenceParser.parse(queryText);

            if (reference != null) {
                BibleVerse verse;
                if (reference.hasSpecificVerses()) {
                    verse = bibleService.getSpecificVerses(reference);
                } else {
                    verse = bibleService.getVerse(reference);
                }

                if (verse != null && verse.getText() != null) {
                    List<InlineQueryResult> results = new ArrayList<>();
                    results.add(buildInlineArticle(verse, "ref_" + reference.toDisplayString().hashCode()));

                    AnswerInlineQuery answer = AnswerInlineQuery.builder()
                            .inlineQueryId(inlineQuery.getId())
                            .results(results)
                            .cacheTime(300)
                            .isPersonal(false)
                            .build();
                    execute(answer);

                    long responseTime = System.currentTimeMillis() - startTime;
                    logger.info("Inline query answered for {} in {}ms", reference.toDisplayString(), responseTime);
                    return;
                }
            }

            // Not a reference or verse not found — try text search
            if (queryText.length() >= 3) {
                handleInlineSearch(inlineQuery.getId(), queryText, startTime);
            } else {
                answerInlineWithHint(inlineQuery.getId());
            }

        } catch (Exception e) {
            logger.error("Error handling inline query: {}", queryText, e);
            try {
                AnswerInlineQuery answer = AnswerInlineQuery.builder()
                        .inlineQueryId(inlineQuery.getId())
                        .results(new ArrayList<>())
                        .cacheTime(5)
                        .build();
                execute(answer);
            } catch (TelegramApiException ex) {
                logger.error("Failed to send empty inline answer", ex);
            }
        }
    }

    /**
     * Handle inline text search — find verses matching a quote or paraphrase
     */
    private void handleInlineSearch(String inlineQueryId, String query, long startTime) {
        try {
            List<BibleVerse> searchResults = bibleService.searchVerses(query);

            if (searchResults.isEmpty()) {
                AnswerInlineQuery answer = AnswerInlineQuery.builder()
                        .inlineQueryId(inlineQueryId)
                        .results(new ArrayList<>())
                        .switchPmText("No verses found for: " + truncate(query, 40))
                        .switchPmParameter("search")
                        .cacheTime(60)
                        .isPersonal(false)
                        .build();
                execute(answer);
                return;
            }

            List<InlineQueryResult> results = new ArrayList<>();
            for (int i = 0; i < searchResults.size(); i++) {
                BibleVerse verse = searchResults.get(i);
                results.add(buildInlineArticle(verse, "search_" + i + "_" + query.hashCode()));
            }

            AnswerInlineQuery answer = AnswerInlineQuery.builder()
                    .inlineQueryId(inlineQueryId)
                    .results(results)
                    .cacheTime(300)
                    .isPersonal(false)
                    .build();
            execute(answer);

            long responseTime = System.currentTimeMillis() - startTime;
            logger.info("Inline search returned {} results for '{}' in {}ms",
                    results.size(), query, responseTime);

        } catch (Exception e) {
            logger.error("Error in inline search for: {}", query, e);
            try {
                AnswerInlineQuery answer = AnswerInlineQuery.builder()
                        .inlineQueryId(inlineQueryId)
                        .results(new ArrayList<>())
                        .cacheTime(5)
                        .build();
                execute(answer);
            } catch (TelegramApiException ex) {
                logger.error("Failed to send empty inline answer", ex);
            }
        }
    }

    /**
     * Build an InlineQueryResultArticle from a BibleVerse
     */
    private InlineQueryResultArticle buildInlineArticle(BibleVerse verse, String id) {
        InputTextMessageContent messageContent = new InputTextMessageContent();
        messageContent.setMessageText(buildInlineMessageText(verse));
        messageContent.setParseMode("HTML");
        messageContent.setDisableWebPagePreview(true);

        InlineQueryResultArticle article = new InlineQueryResultArticle();
        article.setId(id);
        article.setTitle(verse.getReference());
        article.setDescription(verse.toInlinePreviewText(150));
        article.setInputMessageContent(messageContent);
        return article;
    }

    /**
     * Telegram inline results cannot exceed a single message payload.
     * For long chapter requests, return the first safe chunk with a continuation note.
     */
    private String buildInlineMessageText(BibleVerse verse) {
        return MessageSplitter.toInlineMessage(verse.formatForTelegram(), INLINE_TRUNCATION_NOTE);
    }

    /**
     * When a user inserts one of this bot's inline results into chat, Telegram sends that message back as an update.
     * If the inline content was only the first chunk of a long chapter, send the remaining chunks now.
     */
    private void handleInlinePostedMessage(Message message) {
        try {
            BibleReference reference = extractReferenceFromInlineMessage(message.getText());
            if (reference == null || reference.getVerseStart() != null || reference.hasSpecificVerses()) {
                return;
            }

            BibleVerse verse = bibleService.getVerse(reference);
            if (verse == null || verse.getText() == null || verse.getText().isBlank()) {
                return;
            }

            List<String> chunks = MessageSplitter.split(verse.formatForTelegram());
            if (chunks.size() <= 1) {
                return;
            }

            sendAdditionalChunks(message.getChatId(), chunks, 1);
            logger.info("Sent {} follow-up chunks for inline chapter {}", chunks.size() - 1, reference.toDisplayString());
        } catch (Exception e) {
            logger.warn("Failed to continue inline chapter message for chat {}", message.getChatId(), e);
        }
    }

    private BibleReference extractReferenceFromInlineMessage(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return null;
        }

        String firstLine = messageText.split("\\R", 2)[0]
                .replaceAll("\\s*\\([A-Za-z0-9]{2,10}\\)\\s*", " ")
                .replace("\uD83D\uDCD6", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return referenceParser.parse(firstLine);
    }

    /**
     * Answer inline query with a usage hint when no valid reference is detected
     */
    private void answerInlineWithHint(String inlineQueryId) {
        try {
            AnswerInlineQuery answer = AnswerInlineQuery.builder()
                    .inlineQueryId(inlineQueryId)
                    .results(new ArrayList<>())
                    .switchPmText("Type a reference e.g. John 3:16")
                    .switchPmParameter("help")
                    .cacheTime(300)
                    .isPersonal(false)
                    .build();

            execute(answer);
        } catch (TelegramApiException e) {
            logger.error("Failed to send inline hint", e);
        }
    }

    /**
     * Answer inline query when a verse couldn't be found
     */
    private void answerInlineEmpty(String inlineQueryId, String reference) {
        try {
            AnswerInlineQuery answer = AnswerInlineQuery.builder()
                    .inlineQueryId(inlineQueryId)
                    .results(new ArrayList<>())
                    .switchPmText("Verse not found: " + reference)
                    .switchPmParameter("not_found")
                    .cacheTime(30)
                    .isPersonal(false)
                    .build();

            execute(answer);
        } catch (TelegramApiException e) {
            logger.error("Failed to send empty inline answer for {}", reference, e);
        }
    }

    /**
     * Truncate text to a max length for inline result descriptions
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        // Strip any HTML tags for the description
        String plain = text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        if (plain.length() <= maxLength) return plain;
        return plain.substring(0, maxLength - 3) + "...";
    }

    /**
     * Check if the message is a /search command
     */
    private boolean isSearchCommand(String messageText) {
        if (messageText == null) return false;
        String lower = messageText.toLowerCase().trim();
        return lower.startsWith("/search") || lower.startsWith("search ");
    }

    /**
     * Handle /search command — find verses matching a quote or phrase
     */
    private void handleSearchCommand(Long chatId, String messageText, User user) {
        // Extract the search query
        String query = messageText.replaceAll("(?i)^/?search(@\\w+)?\\s*", "").trim();

        if (query.isEmpty()) {
            sendMessage(chatId, "Please provide a phrase to search for.\n\nExample: <code>/search For God so loved the world</code>");
            return;
        }

        logger.info("Search request in chat {}: {}", chatId, query);
        long startTime = System.currentTimeMillis();

        try {
            List<BibleVerse> results = bibleService.searchVerses(query);

            if (results.isEmpty()) {
                sendMessage(chatId, "No verses found matching: <i>" + escapeHtml(query) + "</i>");
                return;
            }

            // Send the top result as a full verse
            BibleVerse topResult = results.get(0);
            sendVerse(chatId, topResult);

            // If there are more results, show them as a list
            if (results.size() > 1) {
                StringBuilder moreResults = new StringBuilder();
                moreResults.append("<b>Other matches:</b>\n");
                for (int i = 1; i < results.size(); i++) {
                    BibleVerse v = results.get(i);
                    moreResults.append("• <b>").append(v.getReference()).append("</b> — ")
                            .append(truncate(v.getText(), 80)).append("\n");
                }
                moreResults.append("\n<i>Use /get followed by a reference to read any of these.</i>");
                sendMessage(chatId, moreResults.toString());
            }

            long responseTime = System.currentTimeMillis() - startTime;
            logger.info("Search completed for '{}' in chat {} in {}ms ({} results)",
                    query, chatId, responseTime, results.size());

        } catch (Exception e) {
            sendMessage(chatId, "Sorry, I encountered an issue searching. Please try again.");
            logger.error("Error in search for '{}' in chat {}", query, chatId, e);
        }
    }

    /**
     * Escape HTML special characters for Telegram messages
     */
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Check if the message is a direct trigger (@mention, /command, or plain get/find)
     */
    private boolean isDirectTrigger(String messageText) {
        if (messageText == null) return false;

        String lower = messageText.toLowerCase().trim();

        // @eccprayerbot mention
        String mention = "@" + botUsername.toLowerCase();
        if (lower.contains(mention)) return true;

        // Slash commands: /get, /find (handles /get@eccprayerbot in groups)
        if (lower.startsWith("/get") || lower.startsWith("/find")) return true;

        // Plain text: "get/Get/GET John 3:16" or "find/Find/FIND Romans 8:28"
        if (lower.startsWith("get ") || lower.startsWith("get\n")
                || lower.startsWith("find ") || lower.startsWith("find\n")) return true;

        return false;
    }

    /**
     * Send a Bible verse to the chat
     */
    private void sendVerse(Long chatId, BibleVerse verse) {
        try {
            String fullText = verse.formatForTelegram();
            List<String> chunks = MessageSplitter.split(fullText);
            sendAdditionalChunks(chatId, chunks, 0);

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
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .build();

            execute(message);

        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chat {}", chatId, e);
        }
    }

    private void sendAdditionalChunks(Long chatId, List<String> chunks, int startIndex) throws TelegramApiException {
        for (int i = startIndex; i < chunks.size(); i++) {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(chunks.get(i))
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .build();

            execute(message);
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
        String errorMsg = "Unable to retrieve " + reference.toDisplayString() + " please check your scripture's spelling and try again";
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
