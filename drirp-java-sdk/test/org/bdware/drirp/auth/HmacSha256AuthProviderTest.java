package org.bdware.drirp.auth;

import com.google.gson.JsonObject;
import org.bdware.drirp.model.DrirpMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HmacSha256AuthProviderTest {
    @Test
    void shouldSignAndVerify() {
        HmacSha256AuthProvider provider = new HmacSha256AuthProvider("secret", "kid1", 60000);
        DrirpMessage message = new DrirpMessage();
        message.setCode(10);
        message.setRid("rid");
        JsonObject body = new JsonObject();
        body.addProperty("identifier", "id-1");
        message.setBody(body);

        provider.sign(message);
        Assertions.assertTrue(message.hasAuth());
        Assertions.assertNotNull(message.getTs());
        Assertions.assertTrue(provider.verify(message));
    }
}
