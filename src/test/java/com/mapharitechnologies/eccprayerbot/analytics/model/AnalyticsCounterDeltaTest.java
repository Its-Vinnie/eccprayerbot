package com.mapharitechnologies.eccprayerbot.analytics.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalyticsCounterDeltaTest {

    @Test
    void shouldIncrementVerseRequestCountersOnSuccess() {
        AnalyticsCounterDelta delta = AnalyticsCounterDelta.from(AnalyticsEventType.VERSE_REQUEST, true);

        assertEquals(1, delta.totalInteractions());
        assertEquals(1, delta.verseRequests());
        assertEquals(0, delta.searches());
        assertEquals(1, delta.successfulInteractions());
        assertEquals(0, delta.failedInteractions());
    }

    @Test
    void shouldIncrementSearchFailureCounters() {
        AnalyticsCounterDelta delta = AnalyticsCounterDelta.from(AnalyticsEventType.SEARCH_REQUEST, false);

        assertEquals(1, delta.totalInteractions());
        assertEquals(0, delta.verseRequests());
        assertEquals(1, delta.searches());
        assertEquals(0, delta.successfulInteractions());
        assertEquals(1, delta.failedInteractions());
    }

    @Test
    void shouldNotIncrementUsageCountersForMembershipEvents() {
        AnalyticsCounterDelta delta = AnalyticsCounterDelta.from(AnalyticsEventType.BOT_ADDED_TO_CHAT, null);

        assertEquals(0, delta.totalInteractions());
        assertEquals(0, delta.verseRequests());
        assertEquals(0, delta.searches());
        assertEquals(0, delta.successfulInteractions());
        assertEquals(0, delta.failedInteractions());
    }

    @Test
    void shouldNotIncrementUsageCountersForGroupMessages() {
        AnalyticsCounterDelta delta = AnalyticsCounterDelta.from(AnalyticsEventType.GROUP_MESSAGE, null);

        assertEquals(0, delta.totalInteractions());
        assertEquals(0, delta.verseRequests());
        assertEquals(0, delta.searches());
        assertEquals(0, delta.successfulInteractions());
        assertEquals(0, delta.failedInteractions());
    }
}
