package com.dris.sdk.next.adapter.memory;

import com.dris.sdk.next.infrastructure.TimeProvider;

import java.time.Instant;

public class SystemTimeProvider implements TimeProvider {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
