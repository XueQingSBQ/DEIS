package com.dris.v2.backend.service.search;

public class SearchRateLimiter {
    private final double permitsPerSecond;
    private final double capacity;
    private double tokens;
    private long lastRefillNs;

    public SearchRateLimiter(int permitsPerSecond, int burstCapacity) {
        int safeRate = permitsPerSecond <= 0 ? 1 : permitsPerSecond;
        int safeBurst = burstCapacity <= 0 ? safeRate : burstCapacity;
        this.permitsPerSecond = safeRate;
        this.capacity = safeBurst;
        this.tokens = safeBurst;
        this.lastRefillNs = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (tokens < 1.0) {
            return false;
        }
        tokens -= 1.0;
        return true;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNs = now - lastRefillNs;
        if (elapsedNs <= 0L) {
            return;
        }
        double elapsedSeconds = elapsedNs / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsedSeconds * permitsPerSecond);
        lastRefillNs = now;
    }
}
