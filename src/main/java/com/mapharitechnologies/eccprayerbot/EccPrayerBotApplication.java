package com.mapharitechnologies.eccprayerbot;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ECCPrayerBot - Main Application Entry Point
 *
 * A Telegram bot designed for online prayer and sermon sessions.
 * Provides instant Bible verse retrieval without disrupting live sermons.
 *
 * Product Principle: ECCPrayerBot must always serve the sermon and never interrupt it.
 *
 * @version 1.0.0-MVP
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableCaching
@EnableAsync
public class EccPrayerBotApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        
        dotenv.entries().forEach(entry -> {
            if (System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });

        // Disable Mongo auto-config if no MongoDB URI is provided.
        String mongoUri = System.getProperty("MONGODB_URI");
        if (mongoUri == null || mongoUri.isBlank()) {
            String exclude = "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration";
            System.setProperty("spring.autoconfigure.exclude", exclude);
        }

        SpringApplication.run(EccPrayerBotApplication.class, args);
    }
}
