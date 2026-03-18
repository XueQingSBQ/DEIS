package com.dris.v2.backend.common.cache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
public class LocalTtlCache<K, V> {
    private final boolean enabled;
    private final long ttlMs;
    private final int maxEntries;
    private final Function<V, V> copyFunction;
    private final ConcurrentHashMap<K, CacheEntry<V>> entries = new ConcurrentHashMap<K, CacheEntry<V>>();
    private final AtomicLong writeSequence = new AtomicLong(0L);
    private final AtomicBoolean cleanupRunning = new AtomicBoolean(false);
    public LocalTtlCache(boolean enabled, long ttlMs, int maxEntries, Function<V, V> copyFunction) {
        this.enabled = enabled;
        this.ttlMs = ttlMs <= 0L ? 30000L : ttlMs;
        this.maxEntries = maxEntries <= 0 ? 1024 : maxEntries;
        this.copyFunction = copyFunction;
    }
    public V get(K key) {
        if (!enabled || key == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        CacheEntry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAtMs <= now) {
            entries.remove(key, entry);
            return null;
        }
        return copy(entry.value);
    }
    public void put(K key, V value) {
        if (!enabled || key == null || value == null) {
            return;
        }
        long now = System.currentTimeMillis();
        entries.put(key, new CacheEntry<V>(copy(value), now + ttlMs, writeSequence.incrementAndGet()));
        if (entries.size() > maxEntries) {
            cleanup(now);
        }
    }
    public void invalidate(K key) {
        if (key == null) {
            return;
        }
        entries.remove(key);
    }
    public void clear() {
        entries.clear();
    }
    private V copy(V value) {
        if (value == null || copyFunction == null) {
            return value;
        }
        return copyFunction.apply(value);
    }
    private void cleanup(long nowMs) {
        if (!cleanupRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            for (Map.Entry<K, CacheEntry<V>> entry : entries.entrySet()) {
                CacheEntry<V> value = entry.getValue();
                if (value != null && value.expireAtMs <= nowMs) {
                    entries.remove(entry.getKey(), value);
                }
            }
            int overflow = entries.size() - maxEntries;
            if (overflow <= 0) {
                return;
            }
            List<Map.Entry<K, CacheEntry<V>>> snapshot = new ArrayList<Map.Entry<K, CacheEntry<V>>>(entries.entrySet());
            Collections.sort(snapshot, new Comparator<Map.Entry<K, CacheEntry<V>>>() {
                @Override
                public int compare(Map.Entry<K, CacheEntry<V>> a, Map.Entry<K, CacheEntry<V>> b) {
                    CacheEntry<V> av = a == null ? null : a.getValue();
                    CacheEntry<V> bv = b == null ? null : b.getValue();
                    long as = av == null ? Long.MAX_VALUE : av.sequence;
                    long bs = bv == null ? Long.MAX_VALUE : bv.sequence;
                    return Long.compare(as, bs);
                }
            });
            for (int i = 0; i < overflow && i < snapshot.size(); i++) {
                Map.Entry<K, CacheEntry<V>> entry = snapshot.get(i);
                if (entry != null) {
                    entries.remove(entry.getKey(), entry.getValue());
                }
            }
        } finally {
            cleanupRunning.set(false);
        }
    }
    private static final class CacheEntry<V> {
        private final V value;
        private final long expireAtMs;
        private final long sequence;
        private CacheEntry(V value, long expireAtMs, long sequence) {
            this.value = value;
            this.expireAtMs = expireAtMs;
            this.sequence = sequence;
        }
    }
}