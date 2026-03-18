package org.bdware.drirp.resilience;

public class DrirpCircuitBreaker {
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long openDurationMs;
    private final int halfOpenMaxCalls;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private int halfOpenCalls = 0;
    private long openUntilMs = 0L;

    public DrirpCircuitBreaker() {
        this(5, 30_000L, 1);
    }

    public DrirpCircuitBreaker(int failureThreshold, long openDurationMs, int halfOpenMaxCalls) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(1000L, openDurationMs);
        this.halfOpenMaxCalls = Math.max(1, halfOpenMaxCalls);
    }

    public synchronized boolean allowRequest() {
        long now = System.currentTimeMillis();
        if (state == State.OPEN) {
            if (now < openUntilMs) {
                return false;
            }
            state = State.HALF_OPEN;
            halfOpenCalls = 0;
        }
        if (state == State.HALF_OPEN) {
            if (halfOpenCalls >= halfOpenMaxCalls) {
                return false;
            }
            halfOpenCalls++;
            return true;
        }
        return true;
    }

    public synchronized void recordSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        halfOpenCalls = 0;
        openUntilMs = 0L;
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    private void open() {
        state = State.OPEN;
        openUntilMs = System.currentTimeMillis() + openDurationMs;
        consecutiveFailures = 0;
        halfOpenCalls = 0;
    }
}
