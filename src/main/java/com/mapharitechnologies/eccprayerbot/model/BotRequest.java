package com.mapharitechnologies.eccprayerbot.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Logs all bot requests for monitoring and analytics
 */
@Document(collection = "bot_requests")
public class BotRequest {

    @Id
    private String id;

    private Long chatId;
    private Long userId;
    private String username;
    private String firstName;

    private String messageText;
    private String parsedReference;

    private Boolean successful;
    private Long responseTimeMs;
    private String errorMessage;

    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;

    public BotRequest() {
    }

    public BotRequest(String id, Long chatId, Long userId, String username, String firstName, String messageText, String parsedReference, Boolean successful, Long responseTimeMs, String errorMessage, LocalDateTime requestedAt, LocalDateTime respondedAt) {
        this.id = id;
        this.chatId = chatId;
        this.userId = userId;
        this.username = username;
        this.firstName = firstName;
        this.messageText = messageText;
        this.parsedReference = parsedReference;
        this.successful = successful;
        this.responseTimeMs = responseTimeMs;
        this.errorMessage = errorMessage;
        this.requestedAt = requestedAt;
        this.respondedAt = respondedAt;
    }

    public static BotRequestBuilder builder() {
        return new BotRequestBuilder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getParsedReference() {
        return parsedReference;
    }

    public void setParsedReference(String parsedReference) {
        this.parsedReference = parsedReference;
    }

    public Boolean getSuccessful() {
        return successful;
    }

    public void setSuccessful(Boolean successful) {
        this.successful = successful;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }

    public static class BotRequestBuilder {
        private String id;
        private Long chatId;
        private Long userId;
        private String username;
        private String firstName;
        private String messageText;
        private String parsedReference;
        private Boolean successful;
        private Long responseTimeMs;
        private String errorMessage;
        private LocalDateTime requestedAt;
        private LocalDateTime respondedAt;

        public BotRequestBuilder id(String id) {
            this.id = id;
            return this;
        }

        public BotRequestBuilder chatId(Long chatId) {
            this.chatId = chatId;
            return this;
        }

        public BotRequestBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public BotRequestBuilder username(String username) {
            this.username = username;
            return this;
        }

        public BotRequestBuilder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public BotRequestBuilder messageText(String messageText) {
            this.messageText = messageText;
            return this;
        }

        public BotRequestBuilder parsedReference(String parsedReference) {
            this.parsedReference = parsedReference;
            return this;
        }

        public BotRequestBuilder successful(Boolean successful) {
            this.successful = successful;
            return this;
        }

        public BotRequestBuilder responseTimeMs(Long responseTimeMs) {
            this.responseTimeMs = responseTimeMs;
            return this;
        }

        public BotRequestBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public BotRequestBuilder requestedAt(LocalDateTime requestedAt) {
            this.requestedAt = requestedAt;
            return this;
        }

        public BotRequestBuilder respondedAt(LocalDateTime respondedAt) {
            this.respondedAt = respondedAt;
            return this;
        }

        public BotRequest build() {
            return new BotRequest(id, chatId, userId, username, firstName, messageText, parsedReference, successful, responseTimeMs, errorMessage, requestedAt, respondedAt);
        }
    }

    public static BotRequest createFromMessage(Long chatId, Long userId,
                                               String username, String firstName,
                                               String messageText) {
        return BotRequest.builder()
                .chatId(chatId)
                .userId(userId)
                .username(username)
                .firstName(firstName)
                .messageText(messageText)
                .requestedAt(LocalDateTime.now())
                .build();
    }
}