package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ClockProducerTest {
    @Inject
    Clock clock;

    @Test
    void providesUtcClock() {
        assertEquals(ZoneOffset.UTC, clock.getZone());
    }
}
