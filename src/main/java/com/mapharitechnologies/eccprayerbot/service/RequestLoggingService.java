package com.mapharitechnologies.eccprayerbot.service;

import com.mapharitechnologies.eccprayerbot.model.BotRequest;
import com.mapharitechnologies.eccprayerbot.repository.BotRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service for logging and analyzing bot requests
 */
@Service
public class RequestLoggingService {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingService.class);

    private final BotRequestRepository requestRepository;

    public RequestLoggingService(BotRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    /**
     * Log a successful request asynchronously
     */
    @Async
    public void logSuccess(BotRequest request, String parsedReference, long responseTimeMs) {
        try {
            request.setSuccessful(true);
            request.setParsedReference(parsedReference);
            request.setResponseTimeMs(responseTimeMs);
            request.setRespondedAt(LocalDateTime.now());

            requestRepository.save(request);

            log.info("Request logged - Chat: {}, Reference: {}, Time: {}ms",
                    request.getChatId(), parsedReference, responseTimeMs);

            // Check if response time exceeds target
            if (responseTimeMs > 2000) {
                log.warn("Response time exceeded 2s target: {}ms for reference: {}",
                        responseTimeMs, parsedReference);
            }

        } catch (Exception e) {
            log.error("Failed to log successful request", e);
        }
    }

    /**
     * Log a failed request asynchronously
     */
    @Async
    public void logFailure(BotRequest request, String errorMessage, long responseTimeMs) {
        try {
            request.setSuccessful(false);
            request.setErrorMessage(errorMessage);
            request.setResponseTimeMs(responseTimeMs);
            request.setRespondedAt(LocalDateTime.now());

            requestRepository.save(request);

            log.warn("Failed request logged - Chat: {}, Error: {}",
                    request.getChatId(), errorMessage);

        } catch (Exception e) {
            log.error("Failed to log failed request", e);
        }
    }

    /**
     * Get success rate statistics
     */
    public double getSuccessRate() {
        try {
            long successful = requestRepository.countBySuccessfulTrue();
            long failed = requestRepository.countBySuccessfulFalse();
            long total = successful + failed;

            if (total == 0) return 0.0;

            return (double) successful / total * 100.0;
        } catch (Exception e) {
            log.error("Failed to calculate success rate", e);
            return 0.0;
        }
    }
}