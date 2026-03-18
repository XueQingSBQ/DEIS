package com.dris.v2.backend.common.drirp;

import com.dris.v2.backend.config.DrirpSecuritySupport;
import org.bdware.drirp.client.DrirpClientBuilder;
import org.bdware.drirp.client.DrirpClientImpl;
import org.bdware.drirp.exception.DrirpClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared process-level outbound DRIRP client pool.
 * Reuses connected clients by (url, timeoutMs) to avoid repeated connect/close overhead.
 */
public final class SharedDrirpClientPool {
    private static final Logger log = LoggerFactory.getLogger(SharedDrirpClientPool.class);
    private static final int DEFAULT_MAX_ENTRIES = 64;
    private static final long DEFAULT_MAX_IDLE_MS = 10 * 60 * 1000L;
    private static final long DEFAULT_CLEANUP_INTERVAL_MS = 30 * 1000L;

    private static volatile int maxEntries = DEFAULT_MAX_ENTRIES;
    private static volatile long maxIdleMs = DEFAULT_MAX_IDLE_MS;
    private static volatile long cleanupIntervalMs = DEFAULT_CLEANUP_INTERVAL_MS;

    private static final ConcurrentHashMap<String, PooledClient> CLIENTS = new ConcurrentHashMap<String, PooledClient>();
    private static final AtomicLong LAST_CLEANUP_AT = new AtomicLong(0L);

    private SharedDrirpClientPool() {
    }

    public static void configure(int configuredMaxEntries, long configuredMaxIdleMs, long configuredCleanupIntervalMs) {
        maxEntries = configuredMaxEntries > 0 ? configuredMaxEntries : DEFAULT_MAX_ENTRIES;
        maxIdleMs = configuredMaxIdleMs > 0L ? configuredMaxIdleMs : DEFAULT_MAX_IDLE_MS;
        cleanupIntervalMs = configuredCleanupIntervalMs > 0L ? configuredCleanupIntervalMs : DEFAULT_CLEANUP_INTERVAL_MS;
    }

    public static DrirpClientImpl acquire(
            String url,
            long timeoutMs,
            DrirpSecuritySupport securitySupport
    ) throws DrirpClientException {
        String normalizedUrl = normalizeUrl(url);
        long normalizedTimeoutMs = normalizeTimeoutMs(timeoutMs);
        String key = buildKey(normalizedUrl, normalizedTimeoutMs);
        maybeCleanup(System.currentTimeMillis());

        PooledClient cached = CLIENTS.get(key);
        if (isUsable(cached)) {
            cached.touch();
            return cached.client;
        }

        try {
            PooledClient holder = CLIENTS.compute(key, (k, oldHolder) -> {
                if (isUsable(oldHolder)) {
                    oldHolder.touch();
                    return oldHolder;
                }
                closeQuietly(oldHolder);
                try {
                    DrirpClientBuilder builder = new DrirpClientBuilder()
                            .url(normalizedUrl)
                            .timeoutMs(normalizedTimeoutMs);
                    builder = securitySupport == null ? builder : securitySupport.applyClientSecurity(builder);
                    DrirpClientImpl created = builder.buildAndConnect();
                    return new PooledClient(created);
                } catch (DrirpClientException exception) {
                    throw new ClientCreateRuntimeException(exception);
                }
            });
            holder.touch();
            return holder.client;
        } catch (ClientCreateRuntimeException exception) {
            throw exception.exception;
        }
    }

    public static void invalidate(String url, long timeoutMs, DrirpClientImpl expectedClient) {
        if (isBlank(url)) {
            return;
        }
        String key = buildKey(url.trim(), normalizeTimeoutMs(timeoutMs));
        PooledClient current = CLIENTS.get(key);
        if (current == null) {
            return;
        }
        if (expectedClient != null && current.client != expectedClient) {
            return;
        }
        if (CLIENTS.remove(key, current)) {
            closeQuietly(current);
        }
    }

    public static void closeAll() {
        for (Map.Entry<String, PooledClient> entry : CLIENTS.entrySet()) {
            if (CLIENTS.remove(entry.getKey(), entry.getValue())) {
                closeQuietly(entry.getValue());
            }
        }
    }

    private static void maybeCleanup(long now) {
        long last = LAST_CLEANUP_AT.get();
        long intervalMs = cleanupIntervalMs <= 0L ? DEFAULT_CLEANUP_INTERVAL_MS : cleanupIntervalMs;
        if (now - last < intervalMs) {
            return;
        }
        if (!LAST_CLEANUP_AT.compareAndSet(last, now)) {
            return;
        }

        for (Map.Entry<String, PooledClient> entry : CLIENTS.entrySet()) {
            PooledClient holder = entry.getValue();
            if (holder == null) {
                CLIENTS.remove(entry.getKey(), null);
                continue;
            }
            long idleForMs = now - holder.lastUsedAt;
            if (idleForMs > maxIdleMs || !holder.client.isConnected()) {
                if (CLIENTS.remove(entry.getKey(), holder)) {
                    closeQuietly(holder);
                }
            }
        }

        int size = CLIENTS.size();
        if (size <= maxEntries) {
            return;
        }
        List<Map.Entry<String, PooledClient>> snapshot = new ArrayList<Map.Entry<String, PooledClient>>(CLIENTS.entrySet());
        snapshot.sort(Comparator.comparingLong(e -> e.getValue() == null ? Long.MIN_VALUE : e.getValue().lastUsedAt));
        int removeCount = size - maxEntries;
        for (int i = 0; i < snapshot.size() && removeCount > 0; i++) {
            Map.Entry<String, PooledClient> entry = snapshot.get(i);
            PooledClient holder = entry.getValue();
            if (holder == null) {
                continue;
            }
            if (CLIENTS.remove(entry.getKey(), holder)) {
                closeQuietly(holder);
                removeCount--;
            }
        }
    }

    private static boolean isUsable(PooledClient holder) {
        return holder != null && holder.client != null && holder.client.isConnected();
    }

    private static void closeQuietly(PooledClient holder) {
        if (holder == null || holder.client == null) {
            return;
        }
        try {
            holder.client.close();
        } catch (Exception exception) {
            log.debug("ignore drirp pooled client close error: {}", exception.getMessage());
        }
    }

    private static String normalizeUrl(String url) throws DrirpClientException {
        if (isBlank(url)) {
            throw new DrirpClientException("nextHopUrl is required");
        }
        return url.trim();
    }

    private static long normalizeTimeoutMs(long timeoutMs) {
        return timeoutMs <= 0L ? 5000L : timeoutMs;
    }

    private static String buildKey(String url, long timeoutMs) {
        return url + "|" + timeoutMs;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class PooledClient {
        private final DrirpClientImpl client;
        private volatile long lastUsedAt;

        private PooledClient(DrirpClientImpl client) {
            this.client = client;
            this.lastUsedAt = System.currentTimeMillis();
        }

        private void touch() {
            this.lastUsedAt = System.currentTimeMillis();
        }
    }

    private static final class ClientCreateRuntimeException extends RuntimeException {
        private final DrirpClientException exception;

        private ClientCreateRuntimeException(DrirpClientException exception) {
            super(exception == null ? null : exception.getMessage(), exception);
            this.exception = exception;
        }
    }
}
