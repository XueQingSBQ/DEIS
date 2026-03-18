package org.bdware.drirp.client;

import com.google.gson.JsonObject;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.opcode.DrirpOpcode;
import org.bdware.drirp.transport.DrirpClientChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

class DrirpRouterClientImplTest {
    @Test
    void shouldFollowNextHopDrirpUrlByDefault() throws Exception {
        String u1 = "http://127.0.0.1:18081";
        String u2 = "http://127.0.0.1:18082";

        QueueChannel c1 = new QueueChannel(responseWithNextHop(u2));
        QueueChannel c2 = new QueueChannel(responseWithMetadata("1100ABCD011320260000000001"));

        TestRouterClient client = new TestRouterClient(u1);
        client.addChannel(u1, c1);
        client.addChannel(u2, c2);
        client.setMaxHops(8);
        client.setTimeout(2000);

        DrirpMessage request = new DrirpMessage();
        request.setCode(DrirpOpcode.RESOLVE);
        request.setRid("rid-router-1");
        DrirpMessage response = client.send(request);

        Assertions.assertNotNull(response);
        Assertions.assertTrue(response.isSuccess());
        Assertions.assertTrue(response.getBody().isJsonObject());
        Assertions.assertEquals("1100ABCD011320260000000001",
                response.getBody().getAsJsonObject().getAsJsonObject("metadata").get("identifier").getAsString());
        Assertions.assertEquals(1, c1.sendCount);
        Assertions.assertEquals(1, c2.sendCount);
    }

    @Test
    void shouldFailFastWhenRouterLoopDetected() throws Exception {
        String u1 = "http://127.0.0.1:18091";
        String u2 = "http://127.0.0.1:18092";

        QueueChannel c1 = new QueueChannel(responseWithNextHop(u2));
        QueueChannel c2 = new QueueChannel(responseWithNextHop(u1));

        TestRouterClient client = new TestRouterClient(u1);
        client.addChannel(u1, c1);
        client.addChannel(u2, c2);
        client.setMaxHops(8);

        DrirpMessage request = new DrirpMessage();
        request.setCode(DrirpOpcode.RESOLVE);
        request.setRid("rid-router-loop");

        DrirpClientException ex = Assertions.assertThrows(DrirpClientException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                client.send(request);
            }
        });
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("router loop detected"));
    }

    @Test
    void builderShouldCreateRouterClient() throws Exception {
        DrirpRouterClientImpl client = new DrirpClientBuilder()
                .url("http://127.0.0.1:18101")
                .timeoutMs(1234L)
                .autoRid(false)
                .buildRouter();
        Assertions.assertNotNull(client);
        Assertions.assertEquals(1234L, client.getTimeout());
        Assertions.assertFalse(client.isAutoRid());
        client.close();
    }

    private static DrirpMessage responseWithNextHop(String nextHopUrl) {
        JsonObject body = new JsonObject();
        body.addProperty("nextHopDrirpUrl", nextHopUrl);
        DrirpMessage response = DrirpMessage.ok(DrirpOpcode.RESOLVE, "rid-upstream", body);
        response.setOk(Boolean.TRUE);
        return response;
    }

    private static DrirpMessage responseWithMetadata(String identifier) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("identifier", identifier);
        JsonObject body = new JsonObject();
        body.add("metadata", metadata);
        DrirpMessage response = DrirpMessage.ok(DrirpOpcode.RESOLVE, "rid-upstream", body);
        response.setOk(Boolean.TRUE);
        return response;
    }

    private static final class TestRouterClient extends DrirpRouterClientImpl {
        private final Map<String, DrirpClientChannel> channels = new HashMap<String, DrirpClientChannel>();

        private TestRouterClient(String serverUrl) throws DrirpClientException {
            super(serverUrl);
        }

        private void addChannel(String url, DrirpClientChannel channel) {
            channels.put(url, channel);
        }

        @Override
        protected DrirpClientChannel getOrCreateChannel(String url) throws DrirpClientException {
            DrirpClientChannel channel = channels.get(url);
            if (channel == null) {
                throw new DrirpClientException("missing test channel for url: " + url);
            }
            return channel;
        }
    }

    private static final class QueueChannel implements DrirpClientChannel {
        private final DrirpMessage fixedResponse;
        private boolean connected;
        private int sendCount;

        private QueueChannel(DrirpMessage fixedResponse) {
            this.fixedResponse = fixedResponse;
        }

        @Override
        public void connect(String url) throws URISyntaxException {
            connected = true;
        }

        @Override
        public void close() {
            connected = false;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public DrirpMessage sendMessage(DrirpMessage request, long timeoutMs) throws DrirpClientException {
            sendCount++;
            return fixedResponse == null ? null : fixedResponse.copy();
        }
    }
}

