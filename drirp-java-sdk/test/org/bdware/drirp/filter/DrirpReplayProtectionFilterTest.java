package org.bdware.drirp.filter;

import org.bdware.drirp.model.DrirpMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DrirpReplayProtectionFilterTest {
    @Test
    void shouldRejectReplayNonce() {
        DrirpReplayProtectionFilter filter = new DrirpReplayProtectionFilter(60000, 60000, 1000, false);

        DrirpMessage first = new DrirpMessage();
        first.setCode(10);
        first.setRid("r1");
        first.setTs(System.currentTimeMillis());
        first.setNonce("n1");
        Assertions.assertNull(filter.beforeHandle(first));

        DrirpMessage second = new DrirpMessage();
        second.setCode(10);
        second.setRid("r1");
        second.setTs(System.currentTimeMillis());
        second.setNonce("n1");
        DrirpMessage blocked = filter.beforeHandle(second);
        Assertions.assertNotNull(blocked);
        Assertions.assertFalse(blocked.isSuccess());
    }
}
