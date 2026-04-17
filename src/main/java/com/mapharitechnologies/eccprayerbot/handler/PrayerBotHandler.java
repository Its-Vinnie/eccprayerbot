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
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
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
    private static final String INLINE_CHAPTER_CALLBACK_PREFIX = "chapter";

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
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }

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

        if (message.getViaBot() != null && botUsername.equalsIgnoreCase(message.getViaBot().getUserName())) {
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
        article.setReplyMarkup(buildInlineReplyMarkup(verse, 0));
        article.setInputMessageContent(messageContent);
        return article;
    }

    /**
     * Telegram inline results cannot exceed a single message payload.
     * For long chapter requests, return the first safe chunk with a continuation note.
     */
    private String buildInlineMessageText(BibleVerse verse) {
        String fullText = verse.formatForTelegram();
        List<String> chunks = MessageSplitter.split(fullText);

        if (chunks.isEmpty()) {
            return fullText;
        }

        if (chunks.size() == 1) {
            return chunks.get(0);
        }

        if (isChapterResult(verse)) {
            return chunks.get(0);
        }

        return MessageSplitter.toInlineMessage(fullText, INLINE_TRUNCATION_NOTE);
    }

    /**
     * Telegram inline mode can't send multiple messages to the target chat on one tap,
     * but it can edit the inserted inline message. For long chapter results, page through chunks.
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        try {
            String data = callbackQuery.getData();
            if ("noop".equals(data)) {
                acknowledgeCallback(callbackQuery, null);
                return;
            }
            if (data == null || !data.startsWith(INLINE_CHAPTER_CALLBACK_PREFIX + "|")) {
                return;
            }

            ChapterPage chapterPage = parseChapterPage(data);
            if (chapterPage == null) {
                acknowledgeCallback(callbackQuery, "Unable to open chapter page.");
                return;
            }

            BibleReference reference = chapterPage.reference();
            BibleVerse verse = bibleService.getVerse(reference);
            if (verse == null || verse.getText() == null || verse.getText().isBlank()) {
                acknowledgeCallback(callbackQuery, "Unable to load that chapter.");
                return;
            }

            List<String> chunks = MessageSplitter.split(verse.formatForTelegram());
            if (chunks.isEmpty()) {
                acknowledgeCallback(callbackQuery, null);
                return;
            }

            int page = Math.max(0, Math.min(chapterPage.page(), chunks.size() - 1));
            EditMessageText editMessage = EditMessageText.builder()
                    .inlineMessageId(callbackQuery.getInlineMessageId())
                    .text(chunks.get(page))
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .replyMarkup(buildInlineReplyMarkup(verse, page))
                    .build();

            execute(editMessage);
            acknowledgeCallback(callbackQuery, "Page " + (page + 1) + " of " + chunks.size());
            logger.info("Edited inline chapter {} to page {}", reference.toDisplayString(), page + 1);
        } catch (Exception e) {
            logger.warn("Failed to handle inline chapter pagination", e);
            acknowledgeCallback(callbackQuery, "Unable to open chapter page.");
        }
    }

    private void acknowledgeCallback(CallbackQuery callbackQuery, String text) {
        try {
            AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQuery.getId())
                    .text(text)
                    .build();
            execute(answer);
        } catch (TelegramApiException e) {
            logger.debug("Failed to answer callback query {}", callbackQuery.getId(), e);
        }
    }

    private InlineKeyboardMarkup buildInlineReplyMarkup(BibleVerse verse, int page) {
        if (!isChapterResult(verse)) {
            return null;
        }

        List<String> chunks = MessageSplitter.split(verse.formatForTelegram());
        if (chunks.size() <= 1) {
            return null;
        }

        int safePage = Math.max(0, Math.min(page, chunks.size() - 1));
        List<InlineKeyboardButton> row = new ArrayList<>();

        if (safePage > 0) {
            row.add(InlineKeyboardButton.builder()
                    .text("Prev")
                    .callbackData(buildChapterCallbackData(verse, safePage - 1))
                    .build());
        }

        row.add(InlineKeyboardButton.builder()
                .text((safePage + 1) + "/" + chunks.size())
                .callbackData("noop")
                .build());

        if (safePage < chunks.size() - 1) {
            row.add(InlineKeyboardButton.builder()
                    .text("Next")
                    .callbackData(buildChapterCallbackData(verse, safePage + 1))
                    .build());
        }

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row)
                .build();
    }

    private boolean isChapterResult(BibleVerse verse) {
        return verse != null
                && verse.getBook() != null
                && verse.getChapter() != null
                && verse.getVerse() == null;
    }

    private String buildChapterCallbackData(BibleVerse verse, int page) {
        String translation = verse.getTranslation() == null || verse.getTranslation().isBlank()
                ? "KJV"
                : verse.getTranslation().trim().toUpperCase();
        return INLINE_CHAPTER_CALLBACK_PREFIX + "|"
                + verse.getBook() + "|"
                + verse.getChapter() + "|"
                + translation + "|"
                + page;
    }

    private ChapterPage parseChapterPage(String data) {
        String[] parts = data.split("\\|", 5);
        if (parts.length != 5 || !INLINE_CHAPTER_CALLBACK_PREFIX.equals(parts[0])) {
            return null;
        }

        try {
            BibleReference reference = BibleReference.builder()
                    .book(parts[1])
                    .chapter(Integer.parseInt(parts[2]))
                    .translation(parts[3])
                    .build();
            return new ChapterPage(reference, Integer.parseInt(parts[4]));
        } catch (NumberFormatException e) {
            return null;
        }
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

    private record ChapterPage(BibleReference reference, int page) {
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
