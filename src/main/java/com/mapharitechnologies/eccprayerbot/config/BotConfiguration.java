package com.mapharitechnologies.eccprayerbot.config;

import com.mapharitechnologies.eccprayerbot.handler.PrayerBotHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Configuration for Telegram Bot
 */
@Configuration
public class BotConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BotConfiguration.class);

    private final PrayerBotHandler prayerBotHandler;

    public BotConfiguration(PrayerBotHandler prayerBotHandler) {
        this.prayerBotHandler = prayerBotHandler;
    }

    @Bean
    @org.springframework.context.annotation.Profile("!test")
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(prayerBotHandler);
        log.info("Telegram bot registered successfully");
        return botsApi;
    }
}