package org.bdware.drirp.resilience;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DrirpRateLimiterTest {
    @Test
    void shouldConsumeBurstThenReject() {
        DrirpRateLimiter limiter = new DrirpRateLimiter(1.0, 2);
        Assertions.assertTrue(limiter.tryAcquire());
        Assertions.assertTrue(limiter.tryAcquire());
        Assertions.assertFalse(limiter.tryAcquire());
    }

    @Test
    void shouldRefillOverTime() throws InterruptedException {
        DrirpRateLimiter limiter = new DrirpRateLimiter(5.0, 1);
        Assertions.assertTrue(limiter.tryAcquire());
        Assertions.assertFalse(limiter.tryAcquire());

        Thread.sleep(250);
        Assertions.assertTrue(limiter.tryAcquire());
    }

    @Test
    void shouldRejectInvalidConfig() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new DrirpRateLimiter(0, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new DrirpRateLimiter(1, 0));
    }
}
