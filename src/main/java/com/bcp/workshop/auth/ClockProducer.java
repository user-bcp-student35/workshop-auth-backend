package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

@ApplicationScoped
public class ClockProducer {
    @Produces
    @ApplicationScoped
    Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
