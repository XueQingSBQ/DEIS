package org.bdware.drirp.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bdware.drirp.DrirpConfig;
import org.bdware.drirp.auth.DrirpAuthProvider;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.opcode.DrirpOpcode;
import org.bdware.drirp.resilience.DrirpCircuitBreaker;
import org.bdware.drirp.resilience.DrirpRateLimiter;
import org.bdware.drirp.transport.DrirpClientChannel;
import org.bdware.drirp.transport.DrirpClientChannelGenerator;

import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class DrirpClientImpl implements DrirpClient {
    protected String serverUrl;
    protected DrirpClientChannel channel;
    protected long timeoutMs = DrirpConfig.DEFAULT_TIMEOUT_MS;
    protected boolean autoRid = true;
    protected boolean autoIdempotencyForMutation = true;
    protected DrirpAuthProvider authProvider;
    protected DrirpCircuitBreaker circuitBreaker;
    protected DrirpRateLimiter rateLimiter;
    protected boolean requireResponseAuth = false;
    protected DrirpRetryPolicy retryPolicy = new DrirpRetryPolicy();
    protected DrirpRidGenerator ridGenerator = new UuidRidGenerator();
    protected final List<DrirpClientInterceptor> interceptors = new CopyOnWriteArrayList<>();

    public DrirpClientImpl() {
    }

    public DrirpClientImpl(String serverUrl) throws DrirpClientException {
        connect(serverUrl);
    }

    public DrirpClientImpl(String serverUrl, DrirpClientChannel channel) throws DrirpClientException {
        this.serverUrl = serverUrl;
        this.channel = channel;
        reconnect();
    }

    @Override
    public DrirpMessage resolve(String identifier) throws DrirpClientException {
        if (identifier == null || identifier.isEmpty()) {
            throw new DrirpClientException("identifier is required");
        }
        JsonObject body = new JsonObject();
        body.addProperty("identifier", identifier);
        return send(DrirpOpcode.RESOLVE, body);
    }

    @Override
    public DrirpMessage query(String identifier, JsonObject query) throws DrirpClientException {
        JsonObject body = new JsonObject();
        if (identifier != null && !identifier.isEmpty()) {
            body.addProperty("identifier", identifier);
        }
        if (query != null) {
            body.add("query", query);
        }
        return send(DrirpOpcode.QUERY, body);
    }

    @Override
    public DrirpMessage register(String identifier, JsonObject values) throws DrirpClientException {
        JsonObject body = new JsonObject();
        if (identifier != null && !identifier.isEmpty()) {
            body.addProperty("identifier", identifier);
        }
        if (values != null) {
            body.add("values", values);
        }
        return send(DrirpOpcode.REGISTER, body);
    }

    @Override
    public DrirpMessage update(String identifier, JsonObject values) throws DrirpClientException {
        JsonObject body = new JsonObject();
        if (identifier == null || identifier.isEmpty()) {
            throw new DrirpClientException("identifier is required");
        }
        body.addProperty("identifier", identifier);
        if (values != null) {
            body.add("values", values);
        }
        return send(DrirpOpcode.UPDATE, body);
    }

    @Override
    public DrirpMessage delete(String identifier) throws DrirpClientException {
        if (identifier == null || identifier.isEmpty()) {
            throw new DrirpClientException("identifier is required");
        }
        JsonObject body = new JsonObject();
        body.addProperty("identifier", identifier);
        return send(DrirpOpcode.DELETE, body);
    }

    @Override
    public DrirpMessage send(int code, JsonElement body) throws DrirpClientException {
        return send(new DrirpMessage(code, body));
    }

    @Override
    public DrirpMessage send(DrirpMessage message) throws DrirpClientException {
        return sendMessageSync(message);
    }

    @Override
    public void connect(String url) throws DrirpClientException {
        if (url == null || url.isEmpty()) {
            throw new DrirpClientException("server url is required");
        }
        this.serverUrl = url;
        this.channel = DrirpClientChannelGenerator.create(url);
        reconnect();
    }

    @Override
    public void reconnect() throws DrirpClientException {
        if (serverUrl == null || serverUrl.isEmpty()) {
            throw new DrirpClientException("server url not set");
        }
        if (channel == null) {
            channel = DrirpClientChannelGenerator.create(serverUrl);
        }
        if (!channel.isConnected()) {
            try {
                channel.connect(serverUrl);
            } catch (URISyntaxException e) {
                throw new DrirpClientException("invalid server url: " + serverUrl, e);
            }
        }
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    @Override
    public boolean isConnected() {
        return channel != null && channel.isConnected();
    }

    @Override
    public void setTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be > 0");
        }
        this.timeoutMs = timeoutMs;
    }

    @Override
    public long getTimeout() {
        return timeoutMs;
    }

    @Override
    public void setAutoRid(boolean enabled) {
        this.autoRid = enabled;
    }

    @Override
    public boolean isAutoRid() {
        return autoRid;
    }

    @Override
    public void setAutoIdempotencyForMutation(boolean enabled) {
        this.autoIdempotencyForMutation = enabled;
    }

    @Override
    public boolean isAutoIdempotencyForMutation() {
        return autoIdempotencyForMutation;
    }

    @Override
    public void setCircuitBreaker(DrirpCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public DrirpCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    @Override
    public void setRateLimiter(DrirpRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public DrirpRateLimiter getRateLimiter() {
        return rateLimiter;
    }

    @Override
    public void setAuthProvider(DrirpAuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    @Override
    public DrirpAuthProvider getAuthProvider() {
        return authProvider;
    }

    @Override
    public void setRequireResponseAuth(boolean require) {
        this.requireResponseAuth = require;
    }

    @Override
    public boolean isRequireResponseAuth() {
        return requireResponseAuth;
    }

    @Override
    public void setRetryPolicy(DrirpRetryPolicy retryPolicy) {
        if (retryPolicy != null) {
            this.retryPolicy = retryPolicy;
        }
    }

    @Override
    public DrirpRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    @Override
    public void addInterceptor(DrirpClientInterceptor interceptor) {
        if (interceptor != null) {
            interceptors.add(interceptor);
        }
    }

    @Override
    public void removeInterceptor(DrirpClientInterceptor interceptor) {
        if (interceptor != null) {
            interceptors.remove(interceptor);
        }
    }

    @Override
    public void clearInterceptors() {
        interceptors.clear();
    }

    @Override
    public void setRidGenerator(DrirpRidGenerator ridGenerator) {
        if (ridGenerator != null) {
            this.ridGenerator = ridGenerator;
        }
    }

    @Override
    public DrirpRidGenerator getRidGenerator() {
        return ridGenerator;
    }

    protected DrirpMessage sendMessageSync(DrirpMessage message) throws DrirpClientException {
        if (message == null) {
            throw new DrirpClientException("message is null");
        }
        if (message.getCode() <= 0) {
            throw new DrirpClientException("invalid opcode: " + message.getCode());
        }
        if (message.getRid() == null && autoRid && ridGenerator != null) {
            message.setRid(ridGenerator.nextRid());
        }
        beforeSend(message);
        int attempts = retryPolicy == null ? 1 : retryPolicy.getMaxAttempts();
        DrirpClientException lastException = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                beforeAttempt();
                reconnect();
                DrirpMessage response = channel.sendMessage(message, timeoutMs);
                if (response == null) {
                    throw new DrirpClientException("empty response");
                }
                afterReceive(message, response);
                afterAttemptSuccess();
                return response;
            } catch (DrirpClientException e) {
                afterAttemptFailure(e);
                lastException = e;
                onError(message, e);
                if (isFastFailException(e)) {
                    throw e;
                }
                if (retryPolicy != null && !retryPolicy.shouldRetry(message, e, attempt)) {
                    throw e;
                }
                long delay = retryPolicy == null ? 0L : retryPolicy.computeDelayMs(attempt);
                if (attempt < attempts && delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new DrirpClientException("request failed");
    }

    protected void beforeSend(DrirpMessage message) {
        message.ensureProtocolDefaults(DrirpConfig.DEFAULT_PROTOCOL_VERSION);
        if (autoIdempotencyForMutation && isMutationOpcode(message.getCode()) && !message.hasIdempotencyKey()) {
            String seed = message.getTraceId() == null ? UUID.randomUUID().toString() : message.getTraceId();
            message.setIdempotencyKey(seed);
        }
        for (DrirpClientInterceptor interceptor : interceptors) {
            interceptor.beforeSend(message);
        }
        if (authProvider != null) {
            authProvider.sign(message);
        }
    }

    protected void afterReceive(DrirpMessage request, DrirpMessage response) throws DrirpClientException {
        if (response == null) {
            throw new DrirpClientException("response is null");
        }
        if (requireResponseAuth && authProvider != null && !authProvider.verify(response)) {
            throw new DrirpClientException("response auth verification failed");
        }
        for (DrirpClientInterceptor interceptor : interceptors) {
            interceptor.afterReceive(request, response);
        }
    }

    protected void onError(DrirpMessage request, Exception error) {
        for (DrirpClientInterceptor interceptor : interceptors) {
            interceptor.onError(request, error);
        }
    }

    protected void beforeAttempt() throws DrirpClientException {
        if (rateLimiter != null && !rateLimiter.tryAcquire()) {
            throw new DrirpClientException("rate limit exceeded");
        }
        if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
            throw new DrirpClientException("circuit breaker open");
        }
    }

    protected void afterAttemptSuccess() {
        if (circuitBreaker != null) {
            circuitBreaker.recordSuccess();
        }
    }

    protected void afterAttemptFailure(Exception ignored) {
        if (circuitBreaker != null) {
            circuitBreaker.recordFailure();
        }
    }

    protected boolean isFastFailException(DrirpClientException e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }
        String message = e.getMessage().toLowerCase();
        return message.contains("rate limit exceeded") || message.contains("circuit breaker open");
    }

    protected boolean isMutationOpcode(int code) {
        return code == DrirpOpcode.REGISTER || code == DrirpOpcode.UPDATE || code == DrirpOpcode.DELETE;
    }
}


