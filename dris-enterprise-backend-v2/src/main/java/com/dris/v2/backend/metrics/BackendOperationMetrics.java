package com.dris.v2.backend.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class BackendOperationMetrics {
    private final ConcurrentHashMap<String, Stat> stats = new ConcurrentHashMap<String, Stat>();

    public void record(String operation, long nanos, boolean success) {
        if (operation == null || operation.trim().isEmpty()) {
            return;
        }
        Stat stat = stats.computeIfAbsent(operation, new java.util.function.Function<String, Stat>() {
            @Override
            public Stat apply(String key) {
                return new Stat();
            }
        });
        stat.calls.increment();
        stat.totalNanos.add(Math.max(0L, nanos));
        if (!success) {
            stat.errors.increment();
        }
    }

    public Map<String, Map<String, Long>> snapshot() {
        Map<String, Map<String, Long>> out = new LinkedHashMap<String, Map<String, Long>>();
        for (Map.Entry<String, Stat> entry : stats.entrySet()) {
            Map<String, Long> values = new LinkedHashMap<String, Long>();
            long calls = entry.getValue().calls.sum();
            long totalNanos = entry.getValue().totalNanos.sum();
            long errors = entry.getValue().errors.sum();
            values.put("calls", Long.valueOf(calls));
            values.put("errors", Long.valueOf(errors));
            values.put("totalNanos", Long.valueOf(totalNanos));
            values.put("avgNanos", Long.valueOf(calls <= 0 ? 0L : totalNanos / calls));
            out.put(entry.getKey(), values);
        }
        return out;
    }

    private static final class Stat {
        private final LongAdder calls = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
    }
}