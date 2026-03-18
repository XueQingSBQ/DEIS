package com.dris.sdk.next.infrastructure;

import java.time.Instant;

public interface TimeProvider {
    Instant now();
}
