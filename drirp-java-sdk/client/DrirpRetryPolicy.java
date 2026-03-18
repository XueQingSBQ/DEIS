package org.bdware.drirp.client;

import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.opcode.DrirpOpcode;

public class DrirpRetryPolicy {
    private int maxAttempts = 3;
    private long baseDelayMs = 100L;
    private long maxDelayMs = 2000L;
    private double jitterRatio = 0.2;

    public DrirpRetryPolicy() {
    }

    public DrirpRetryPolicy(int maxAttempts, long baseDelayMs, long maxDelayMs) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseDelayMs = Math.max(0, baseDelayMs);
        this.maxDelayMs = Math.max(0, maxDelayMs);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    public void setBaseDelayMs(long baseDelayMs) {
        this.baseDelayMs = Math.max(0, baseDelayMs);
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(long maxDelayMs) {
        this.maxDelayMs = Math.max(0, maxDelayMs);
    }

    public double getJitterRatio() {
        return jitterRatio;
    }

    public void setJitterRatio(double jitterRatio) {
        if (jitterRatio < 0) {
            this.jitterRatio = 0;
        } else if (jitterRatio > 1) {
            this.jitterRatio = 1;
        } else {
            this.jitterRatio = jitterRatio;
        }
    }

    public long computeDelayMs(int attempt) {
        if (attempt <= 1) {
            return 0L;
        }
        long delay = baseDelayMs * (1L << (attempt - 2));
        if (delay < 0) {
            delay = maxDelayMs;
        }
        delay = Math.min(delay, maxDelayMs);
        if (delay <= 0 || jitterRatio <= 0) {
            return delay;
        }
        double jitter = 1 + ((java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2) - 1) * jitterRatio;
        long jittered = (long) (delay * jitter);
        return Math.max(0, Math.min(jittered, maxDelayMs));
    }

    public boolean shouldRetry(DrirpMessage request, Exception error, int attempt) {
        if (attempt >= maxAttempts) {
            return false;
        }
        if (request == null) {
            return true;
        }
        int code = request.getCode();
        if (code == DrirpOpcode.RESOLVE || code == DrirpOpcode.QUERY || code == DrirpOpcode.ROUTER_QUERY) {
            return true;
        }
        String idempotencyKey = request.getIdempotencyKey();
        return idempotencyKey != null && !idempotencyKey.trim().isEmpty();
    }
}


