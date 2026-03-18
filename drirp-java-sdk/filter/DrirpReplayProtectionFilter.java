package org.bdware.drirp.filter;

import org.bdware.drirp.DrirpConfig;
import org.bdware.drirp.model.DrirpErrorCodes;
import org.bdware.drirp.model.DrirpMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DrirpReplayProtectionFilter implements DrirpRequestFilter {
    private final long maxSkewMs;
    private final long nonceTtlMs;
    private final int maxEntries;
    private final boolean allowMissingNonce;
    private final Map<String, Long> nonceSeenAt = new ConcurrentHashMap<String, Long>();

    public DrirpReplayProtectionFilter() {
        this(DrirpConfig.DEFAULT_MAX_SKEW_MS, DrirpConfig.DEFAULT_MAX_SKEW_MS, 100000, false);
    }

    public DrirpReplayProtectionFilter(long maxSkewMs, long nonceTtlMs, int maxEntries, boolean allowMissingNonce) {
        this.maxSkewMs = Math.max(0L, maxSkewMs);
        this.nonceTtlMs = Math.max(1000L, nonceTtlMs);
        this.maxEntries = Math.max(1000, maxEntries);
        this.allowMissingNonce = allowMissingNonce;
    }

    @Override
    public DrirpMessage beforeHandle(DrirpMessage request) {
        if (request == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        Long ts = request.getTs();
        if (ts == null || ts <= 0) {
            return error(request, "missing timestamp");
        }
        if (maxSkewMs > 0 && Math.abs(now - ts.longValue()) > maxSkewMs) {
            return error(request, "timestamp skew too large");
        }

        String nonce = request.getNonce();
        if (nonce == null || nonce.trim().isEmpty()) {
            if (allowMissingNonce) {
                return null;
            }
            return error(request, "missing nonce");
        }

        cleanup(now);
        Long previous = nonceSeenAt.putIfAbsent(nonce, now);
        if (previous != null && now - previous.longValue() <= nonceTtlMs) {
            return error(request, "replay detected");
        }
        if (nonceSeenAt.size() > maxEntries) {
            cleanup(now);
        }
        return null;
    }

    private void cleanup(long now) {
        for (Map.Entry<String, Long> entry : nonceSeenAt.entrySet()) {
            Long seenAt = entry.getValue();
            if (seenAt == null || now - seenAt.longValue() > nonceTtlMs) {
                nonceSeenAt.remove(entry.getKey(), seenAt);
            }
        }
    }

    private DrirpMessage error(DrirpMessage request, String msg) {
        DrirpMessage error = DrirpMessage.error(request.getCode(), request.getRid(), msg);
        error.setErrorCode(DrirpErrorCodes.REPLAY_DETECTED);
        return error;
    }
}
