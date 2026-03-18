package org.bdware.drirp.client;

import org.bdware.drirp.DrirpConfig;
import org.bdware.drirp.auth.DrirpAuthProvider;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.resilience.DrirpCircuitBreaker;
import org.bdware.drirp.resilience.DrirpRateLimiter;

import java.util.ArrayList;
import java.util.List;

public class DrirpClientBuilder {
    private String url;
    private long timeoutMs = DrirpConfig.DEFAULT_TIMEOUT_MS;
    private boolean autoRid = true;
    private boolean autoIdempotencyForMutation = true;
    private boolean requireResponseAuth = false;
    private DrirpAuthProvider authProvider;
    private DrirpCircuitBreaker circuitBreaker;
    private DrirpRateLimiter rateLimiter;
    private DrirpRetryPolicy retryPolicy = new DrirpRetryPolicy();
    private DrirpRidGenerator ridGenerator = new UuidRidGenerator();
    private final List<DrirpClientInterceptor> interceptors = new ArrayList<>();

    public DrirpClientBuilder url(String url) {
        this.url = url;
        return this;
    }

    public DrirpClientBuilder timeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    public DrirpClientBuilder autoRid(boolean autoRid) {
        this.autoRid = autoRid;
        return this;
    }

    public DrirpClientBuilder autoIdempotencyForMutation(boolean autoIdempotencyForMutation) {
        this.autoIdempotencyForMutation = autoIdempotencyForMutation;
        return this;
    }

    public DrirpClientBuilder requireResponseAuth(boolean requireResponseAuth) {
        this.requireResponseAuth = requireResponseAuth;
        return this;
    }

    public DrirpClientBuilder authProvider(DrirpAuthProvider authProvider) {
        this.authProvider = authProvider;
        return this;
    }

    public DrirpClientBuilder circuitBreaker(DrirpCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        return this;
    }

    public DrirpClientBuilder rateLimiter(DrirpRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        return this;
    }

    public DrirpClientBuilder retryPolicy(DrirpRetryPolicy retryPolicy) {
        if (retryPolicy != null) {
            this.retryPolicy = retryPolicy;
        }
        return this;
    }

    public DrirpClientBuilder ridGenerator(DrirpRidGenerator ridGenerator) {
        if (ridGenerator != null) {
            this.ridGenerator = ridGenerator;
        }
        return this;
    }

    public DrirpClientBuilder addInterceptor(DrirpClientInterceptor interceptor) {
        if (interceptor != null) {
            this.interceptors.add(interceptor);
        }
        return this;
    }

    public DrirpClientImpl build() {
        DrirpClientImpl client = new DrirpClientImpl();
        client.setTimeout(timeoutMs);
        client.setAutoRid(autoRid);
        client.setAutoIdempotencyForMutation(autoIdempotencyForMutation);
        client.setAuthProvider(authProvider);
        client.setCircuitBreaker(circuitBreaker);
        client.setRateLimiter(rateLimiter);
        client.setRequireResponseAuth(requireResponseAuth);
        client.setRetryPolicy(retryPolicy);
        client.setRidGenerator(ridGenerator);
        for (DrirpClientInterceptor interceptor : interceptors) {
            client.addInterceptor(interceptor);
        }
        if (url != null && !url.isEmpty()) {
            client.serverUrl = url;
        }
        return client;
    }

    public DrirpClientImpl buildAndConnect() throws DrirpClientException {
        if (url == null || url.isEmpty()) {
            throw new DrirpClientException("server url is required");
        }
        DrirpClientImpl client = build();
        client.connect(url);
        return client;
    }

    public DrirpRouterClientImpl buildRouter() throws DrirpClientException {
        if (url == null || url.isEmpty()) {
            throw new DrirpClientException("server url is required");
        }
        DrirpRouterClientImpl client = new DrirpRouterClientImpl();
        client.setTimeout(timeoutMs);
        client.setAutoRid(autoRid);
        client.setAutoIdempotencyForMutation(autoIdempotencyForMutation);
        client.setAuthProvider(authProvider);
        client.setCircuitBreaker(circuitBreaker);
        client.setRateLimiter(rateLimiter);
        client.setRequireResponseAuth(requireResponseAuth);
        client.setRetryPolicy(retryPolicy);
        client.setRidGenerator(ridGenerator);
        for (DrirpClientInterceptor interceptor : interceptors) {
            client.addInterceptor(interceptor);
        }
        client.serverUrl = url;
        return client;
    }

    public DrirpRouterClientImpl buildRouterAndConnect() throws DrirpClientException {
        DrirpRouterClientImpl client = buildRouter();
        client.connect(url);
        return client;
    }
}


