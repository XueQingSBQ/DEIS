package com.dris.v2.backend.service.search;

public class SearchCircuitBreaker {
    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long openMs;
    private final int halfOpenPermits;
    private State state = State.CLOSED;
    private int failureCount = 0;
    private long openUntilMs = 0L;
    private int halfOpenInFlight = 0;

    public SearchCircuitBreaker(int failureThreshold, long openMs, int halfOpenPermits) {
        this.failureThreshold = failureThreshold <= 0 ? 10 : failureThreshold;
        this.openMs = openMs <= 0 ? 10000L : openMs;
        this.halfOpenPermits = halfOpenPermits <= 0 ? 1 : halfOpenPermits;
    }

    public synchronized boolean allowRequest() {
        long now = System.currentTimeMillis();
        if (state == State.OPEN) {
            if (now < openUntilMs) {
                return false;
            }
            state = State.HALF_OPEN;
            halfOpenInFlight = 0;
        }
        if (state == State.HALF_OPEN) {
            if (halfOpenInFlight >= halfOpenPermits) {
                return false;
            }
            halfOpenInFlight++;
        }
        return true;
    }

    public synchronized void onSuccess() {
        if (state == State.HALF_OPEN) {
            halfOpenInFlight = Math.max(0, halfOpenInFlight - 1);
        }
        state = State.CLOSED;
        failureCount = 0;
        openUntilMs = 0L;
    }

    public synchronized void onFailure() {
        long now = System.currentTimeMillis();
        if (state == State.HALF_OPEN) {
            halfOpenInFlight = Math.max(0, halfOpenInFlight - 1);
            open(now);
            return;
        }
        failureCount++;
        if (failureCount >= failureThreshold) {
            open(now);
        }
    }

    private void open(long nowMs) {
        state = State.OPEN;
        openUntilMs = nowMs + openMs;
        failureCount = 0;
        halfOpenInFlight = 0;
    }
}
