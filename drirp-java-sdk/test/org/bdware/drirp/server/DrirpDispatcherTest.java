package org.bdware.drirp.server;

import com.google.gson.JsonObject;
import org.bdware.drirp.filter.DrirpRequestFilter;
import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.opcode.DrirpOpcode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class DrirpDispatcherTest {
    @Test
    void shouldHandleRegisteredOpcode() {
        DrirpDispatcher dispatcher = new DrirpDispatcher()
                .register(DrirpOpcode.RESOLVE, request -> {
                    JsonObject out = new JsonObject();
                    out.addProperty("ok", true);
                    return DrirpMessage.ok(request.getCode(), request.getRid(), out);
                });

        DrirpMessage request = new DrirpMessage();
        request.setCode(DrirpOpcode.RESOLVE);
        request.setRid("r1");

        DrirpMessage response = dispatcher.handle(request);
        Assertions.assertNotNull(response);
        Assertions.assertEquals(DrirpOpcode.RESOLVE, response.getCode());
        Assertions.assertEquals("r1", response.getRid());
        Assertions.assertTrue(response.isSuccess());
    }

    @Test
    void shouldShortCircuitByFilter() {
        DrirpRequestFilter blockAll = request -> DrirpMessage.error(request.getCode(), request.getRid(), "blocked");
        DrirpDispatcher dispatcher = new DrirpDispatcher()
                .addFilter(blockAll)
                .register(DrirpOpcode.RESOLVE, request -> DrirpMessage.ok(request.getCode(), request.getRid(), request.getBody()));

        DrirpMessage request = new DrirpMessage();
        request.setCode(DrirpOpcode.RESOLVE);
        request.setRid("r2");
        DrirpMessage response = dispatcher.handle(request);
        Assertions.assertNotNull(response);
        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals("blocked", response.getErrorMessage());
    }

    @Test
    void shouldServeMutationIdempotencyFromCache() {
        AtomicInteger calls = new AtomicInteger(0);
        DrirpDispatcher dispatcher = new DrirpDispatcher()
                .register(DrirpOpcode.UPDATE, request -> {
                    calls.incrementAndGet();
                    JsonObject out = new JsonObject();
                    out.addProperty("counter", calls.get());
                    return DrirpMessage.ok(request.getCode(), request.getRid(), out);
                });

        DrirpMessage req1 = new DrirpMessage();
        req1.setCode(DrirpOpcode.UPDATE);
        req1.setRid("r-idem-1");
        req1.setTs(System.currentTimeMillis());
        req1.setIdempotencyKey("idem-1");

        DrirpMessage resp1 = dispatcher.handle(req1);
        DrirpMessage req2 = req1.copy();
        req2.setRid("r-idem-2");
        DrirpMessage resp2 = dispatcher.handle(req2);

        Assertions.assertTrue(resp1.isSuccess());
        Assertions.assertTrue(resp2.isSuccess());
        Assertions.assertEquals(1, calls.get());
        Assertions.assertNotNull(resp2.getBody());
        Assertions.assertTrue(resp2.getBody().getAsJsonObject().has("counter"));
        Assertions.assertEquals(1, resp2.getBody().getAsJsonObject().get("counter").getAsInt());
    }
}
