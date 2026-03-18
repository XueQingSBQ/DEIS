package org.bdware.drirp.resilience;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DrirpCircuitBreakerTest {
    @Test
    void shouldOpenAfterThreshold() {
        DrirpCircuitBreaker breaker = new DrirpCircuitBreaker(2, 200, 1);
        Assertions.assertTrue(breaker.allowRequest());

        breaker.recordFailure();
        Assertions.assertEquals(DrirpCircuitBreaker.State.CLOSED, breaker.getState());

        breaker.recordFailure();
        Assertions.assertEquals(DrirpCircuitBreaker.State.OPEN, breaker.getState());
        Assertions.assertFalse(breaker.allowRequest());
    }

    @Test
    void shouldHalfOpenThenCloseOnSuccess() throws InterruptedException {
        DrirpCircuitBreaker breaker = new DrirpCircuitBreaker(1, 1000, 1);
        breaker.recordFailure();
        Assertions.assertEquals(DrirpCircuitBreaker.State.OPEN, breaker.getState());
        Assertions.assertFalse(breaker.allowRequest());

        Thread.sleep(1100);
        Assertions.assertTrue(breaker.allowRequest());
        Assertions.assertEquals(DrirpCircuitBreaker.State.HALF_OPEN, breaker.getState());
        Assertions.assertFalse(breaker.allowRequest());

        breaker.recordSuccess();
        Assertions.assertEquals(DrirpCircuitBreaker.State.CLOSED, breaker.getState());
        Assertions.assertTrue(breaker.allowRequest());
    }

    @Test
    void shouldReopenWhenHalfOpenFails() throws InterruptedException {
        DrirpCircuitBreaker breaker = new DrirpCircuitBreaker(1, 1000, 1);
        breaker.recordFailure();
        Thread.sleep(1100);

        Assertions.assertTrue(breaker.allowRequest());
        breaker.recordFailure();

        Assertions.assertEquals(DrirpCircuitBreaker.State.OPEN, breaker.getState());
        Assertions.assertFalse(breaker.allowRequest());
    }
}
