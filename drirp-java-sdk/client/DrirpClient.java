package org.bdware.drirp.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bdware.drirp.auth.DrirpAuthProvider;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.resilience.DrirpCircuitBreaker;
import org.bdware.drirp.resilience.DrirpRateLimiter;

public interface DrirpClient {
    DrirpMessage resolve(String identifier) throws DrirpClientException;

    DrirpMessage query(String identifier, JsonObject query) throws DrirpClientException;

    DrirpMessage register(String identifier, JsonObject values) throws DrirpClientException;

    DrirpMessage update(String identifier, JsonObject values) throws DrirpClientException;

    DrirpMessage delete(String identifier) throws DrirpClientException;

    DrirpMessage send(int code, JsonElement body) throws DrirpClientException;

    DrirpMessage send(DrirpMessage message) throws DrirpClientException;

    void connect(String url) throws DrirpClientException;

    void reconnect() throws DrirpClientException;

    void close();

    boolean isConnected();

    void setTimeout(long timeoutMs);

    long getTimeout();

    void setAutoRid(boolean enabled);

    boolean isAutoRid();

    void setAutoIdempotencyForMutation(boolean enabled);

    boolean isAutoIdempotencyForMutation();

    void setCircuitBreaker(DrirpCircuitBreaker circuitBreaker);

    DrirpCircuitBreaker getCircuitBreaker();

    void setRateLimiter(DrirpRateLimiter rateLimiter);

    DrirpRateLimiter getRateLimiter();

    void setAuthProvider(DrirpAuthProvider authProvider);

    DrirpAuthProvider getAuthProvider();

    void setRequireResponseAuth(boolean require);

    boolean isRequireResponseAuth();

    void setRetryPolicy(DrirpRetryPolicy retryPolicy);

    DrirpRetryPolicy getRetryPolicy();

    void addInterceptor(DrirpClientInterceptor interceptor);

    void removeInterceptor(DrirpClientInterceptor interceptor);

    void clearInterceptors();

    void setRidGenerator(DrirpRidGenerator ridGenerator);

    DrirpRidGenerator getRidGenerator();
}


