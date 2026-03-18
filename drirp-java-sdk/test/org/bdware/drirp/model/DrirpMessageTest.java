package org.bdware.drirp.model;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DrirpMessageTest {
    @Test
    void shouldBuildErrorEnvelope() {
        DrirpMessage message = DrirpMessage.error(20, "rid-1", "failed");
        Assertions.assertEquals(20, message.getCode());
        Assertions.assertEquals("rid-1", message.getRid());
        Assertions.assertEquals(Boolean.FALSE, message.getOk());
        Assertions.assertEquals("ERROR", message.getErrorCode());
        Assertions.assertEquals("failed", message.getErrorMessage());
        Assertions.assertFalse(message.isSuccess());
    }

    @Test
    void shouldBuildOkEnvelope() {
        JsonObject body = new JsonObject();
        body.addProperty("k", "v");
        DrirpMessage message = DrirpMessage.ok(10, "rid-2", body);
        Assertions.assertEquals(10, message.getCode());
        Assertions.assertEquals("rid-2", message.getRid());
        Assertions.assertEquals(Boolean.TRUE, message.getOk());
        Assertions.assertTrue(message.isSuccess());
        Assertions.assertTrue(message.hasBody());
    }
}
