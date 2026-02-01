package com.mapharitechnologies.eccprayerbot.repository;

import com.mapharitechnologies.eccprayerbot.model.BotRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for logging bot requests
 */
@Repository
public interface BotRequestRepository extends MongoRepository<BotRequest, String> {

    /**
     * Find all requests for a specific chat
     */
    List<BotRequest> findByChatIdOrderByRequestedAtDesc(Long chatId);

    /**
     * Find failed requests
     */
    List<BotRequest> findBySuccessfulFalseOrderByRequestedAtDesc();

    /**
     * Find requests within a time range
     */
    List<BotRequest> findByRequestedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Count successful requests
     */
    Long countBySuccessfulTrue();

    /**
     * Count failed requests
     */
    Long countBySuccessfulFalse();
}