package org.bdware.drirp.client;

import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.opcode.DrirpOpcode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DrirpRetryPolicyTest {
    @Test
    void shouldRetryReadOperationByDefault() {
        DrirpRetryPolicy policy = new DrirpRetryPolicy();
        DrirpMessage request = new DrirpMessage();
        request.setCode(DrirpOpcode.RESOLVE);
        Assertions.assertTrue(policy.shouldRetry(request, new RuntimeException("x"), 1));
    }

    @Test
    void shouldNotRetryWriteWithoutIdempotencyKey() {
        DrirpRetryPolicy policy = new DrirpRetryPolicy();
        DrirpMessage request = new DrirpMessage();
        request.setCode(DrirpOpcode.UPDATE);
        Assertions.assertFalse(policy.shouldRetry(request, new RuntimeException("x"), 1));
    }

    @Test
    void shouldRetryWriteWithIdempotencyKey() {
        DrirpRetryPolicy policy = new DrirpRetryPolicy();
        DrirpMessage request = new DrirpMessage();
        request.setCode(DrirpOpcode.UPDATE);
        request.setIdempotencyKey("idem-1");
        Assertions.assertTrue(policy.shouldRetry(request, new RuntimeException("x"), 1));
    }
}
