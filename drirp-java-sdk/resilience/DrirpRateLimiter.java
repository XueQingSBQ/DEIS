package org.bdware.drirp.resilience;

public class DrirpRateLimiter {
    private final double permitsPerSecond;
    private final double burstCapacity;

    private double availableTokens;
    private long lastRefillNanos;

    public DrirpRateLimiter(double permitsPerSecond, int burstCapacity) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be > 0");
        }
        if (burstCapacity <= 0) {
            throw new IllegalArgumentException("burstCapacity must be > 0");
        }
        this.permitsPerSecond = permitsPerSecond;
        this.burstCapacity = burstCapacity;
        this.availableTokens = burstCapacity;
        this.lastRefillNanos = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (availableTokens >= 1.0) {
            availableTokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos <= 0) {
            return;
        }
        double tokens = (elapsedNanos / 1_000_000_000.0) * permitsPerSecond;
        if (tokens > 0) {
            availableTokens = Math.min(burstCapacity, availableTokens + tokens);
            lastRefillNanos = now;
        }
    }
}
