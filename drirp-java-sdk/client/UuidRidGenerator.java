package org.bdware.drirp.client;

import java.util.UUID;

public class UuidRidGenerator implements DrirpRidGenerator {
    @Override
    public String nextRid() {
        return UUID.randomUUID().toString();
    }
}


