package com.mapharitechnologies.eccprayerbot.analytics.service;

import com.mapharitechnologies.eccprayerbot.analytics.config.AnalyticsBackfillProperties;
import com.mapharitechnologies.eccprayerbot.model.BotRequest;
import com.mapharitechnologies.eccprayerbot.repository.BotRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Explicit startup backfill for legacy MongoDB request history.
 */
@Component
@ConditionalOnBean({BotRequestRepository.class, LegacyMongoAnalyticsBackfillService.class})
@ConditionalOnProperty(prefix = "analytics.backfill", name = "enabled", havingValue = "true")
public class LegacyMongoAnalyticsBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyMongoAnalyticsBackfillRunner.class);

    private final BotRequestRepository botRequestRepository;
    private final LegacyMongoAnalyticsBackfillService backfillService;
    private final AnalyticsBackfillProperties backfillProperties;

    public LegacyMongoAnalyticsBackfillRunner(BotRequestRepository botRequestRepository,
                                              LegacyMongoAnalyticsBackfillService backfillService,
                                              AnalyticsBackfillProperties backfillProperties) {
        this.botRequestRepository = botRequestRepository;
        this.backfillService = backfillService;
        this.backfillProperties = backfillProperties;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() {
        int batchSize = Math.max(backfillProperties.getBatchSize(), 1);
        int pageNumber = 0;
        long imported = 0;
        long skipped = 0;
        long failed = 0;

        log.info("Starting legacy analytics backfill from MongoDB with batch size {}", batchSize);

        try {
            while (true) {
                Page<BotRequest> page = botRequestRepository.findAll(
                        PageRequest.of(
                                pageNumber,
                                batchSize,
                                Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("id"))
                        )
                );

                if (page.isEmpty()) {
                    break;
                }

                for (BotRequest request : page.getContent()) {
                    LegacyMongoAnalyticsBackfillService.BackfillResult result = backfillService.importRequest(request);
                    switch (result) {
                        case IMPORTED -> imported++;
                        case SKIPPED -> skipped++;
                        case FAILED -> failed++;
                    }
                }

                log.info(
                        "Processed legacy analytics batch {}/{} (running totals: imported={}, skipped={}, failed={})",
                        pageNumber + 1,
                        Math.max(page.getTotalPages(), pageNumber + 1),
                        imported,
                        skipped,
                        failed
                );

                if (!page.hasNext()) {
                    break;
                }
                pageNumber++;
            }

            log.info(
                    "Legacy analytics backfill finished: imported={}, skipped={}, failed={}",
                    imported,
                    skipped,
                    failed
            );
        } catch (Exception e) {
            log.error(
                    "Legacy analytics backfill aborted after imported={}, skipped={}, failed={}",
                    imported,
                    skipped,
                    failed,
                    e
            );
        }
    }
}
