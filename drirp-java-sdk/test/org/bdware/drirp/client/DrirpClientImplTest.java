package org.bdware.drirp.client;

import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.opcode.DrirpOpcode;
import org.bdware.drirp.resilience.DrirpCircuitBreaker;
import org.bdware.drirp.resilience.DrirpRateLimiter;
import org.bdware.drirp.transport.DrirpClientChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

class DrirpClientImplTest {
    @Test
    void shouldFillProtocolDefaultsBeforeSend() throws Exception {
        EchoChannel channel = new EchoChannel();
        DrirpClientImpl client = new DrirpClientImpl("http://127.0.0.1:9999", channel);

        DrirpMessage request = new DrirpMessage();
        request.setCode(DrirpOpcode.QUERY);
        request.setRid("rid-1");
        DrirpMessage response = client.send(request);

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getTs());
        Assertions.assertNotNull(response.getTraceId());
        Assertions.assertNotNull(response.getVersion());
        Assertions.assertNotNull(response.getNonce());
        Assertions.assertNull(response.getIdempotencyKey());
    }

    @Test
    void shouldAutoFillIdempotencyForMutation() throws Exception {
        EchoChannel channel = new EchoChannel();
        DrirpClientImpl client = new DrirpClientImpl("http://127.0.0.1:9999", channel);

        DrirpMessage request = new DrirpMessage();
        request.setCode(DrirpOpcode.UPDATE);
        request.setRid("rid-2");
        DrirpMessage response = client.send(request);

        Assertions.assertNotNull(response.getIdempotencyKey());
        Assertions.assertFalse(response.getIdempotencyKey().isEmpty());
    }

    @Test
    void shouldFastFailWhenRateLimitedWithoutRetry() throws Exception {
        CountingChannel channel = new CountingChannel();
        DrirpClientImpl client = new DrirpClientImpl("http://127.0.0.1:9999", channel);

        DrirpRetryPolicy retryPolicy = new DrirpRetryPolicy();
        retryPolicy.setMaxAttempts(5);
        retryPolicy.setBaseDelayMs(0);
        retryPolicy.setMaxDelayMs(0);
        client.setRetryPolicy(retryPolicy);
        client.setRateLimiter(new DrirpRateLimiter(1.0, 1));

        DrirpMessage first = new DrirpMessage();
        first.setCode(DrirpOpcode.QUERY);
        first.setRid("rid-3");
        client.send(first);

        DrirpMessage second = new DrirpMessage();
        second.setCode(DrirpOpcode.QUERY);
        second.setRid("rid-4");
        DrirpClientException ex = Assertions.assertThrows(DrirpClientException.class, () -> client.send(second));
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("rate limit exceeded"));
        Assertions.assertEquals(1, channel.sendCount);
    }

    @Test
    void shouldFastFailWhenCircuitOpenWithoutRetry() throws Exception {
        CountingChannel channel = new CountingChannel();
        DrirpClientImpl client = new DrirpClientImpl("http://127.0.0.1:9999", channel);

        DrirpRetryPolicy retryPolicy = new DrirpRetryPolicy();
        retryPolicy.setMaxAttempts(5);
        retryPolicy.setBaseDelayMs(0);
        retryPolicy.setMaxDelayMs(0);
        client.setRetryPolicy(retryPolicy);

        DrirpCircuitBreaker breaker = new DrirpCircuitBreaker(1, 5_000, 1);
        breaker.recordFailure();
        client.setCircuitBreaker(breaker);

        DrirpMessage request = new DrirpMessage();
        request.setCode(DrirpOpcode.QUERY);
        request.setRid("rid-5");

        DrirpClientException ex = Assertions.assertThrows(DrirpClientException.class, () -> client.send(request));
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("circuit breaker open"));
        Assertions.assertEquals(0, channel.sendCount);
    }

    private static final class EchoChannel implements DrirpClientChannel {
        private boolean connected;

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
            return request;
        }
    }

    private static final class CountingChannel implements DrirpClientChannel {
        private boolean connected;
        private int sendCount;

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
            return request;
        }
    }
}
