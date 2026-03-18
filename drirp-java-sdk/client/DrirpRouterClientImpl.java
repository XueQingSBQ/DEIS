package org.bdware.drirp.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.transport.DrirpClientChannel;
import org.bdware.drirp.transport.DrirpClientChannelGenerator;

import java.net.URI;
import java.net.URISyntaxException;
import org.bdware.drirp.DrirpConfig;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DrirpRouterClientImpl extends DrirpClientImpl {
    private final Map<String, DrirpClientChannel> cachedChannels = new ConcurrentHashMap<>();
    private int maxHops = DrirpConfig.DEFAULT_MAX_HOPS;
    private volatile String[] delegateKeys = new String[]{
            "nextHopDrirpUrl", "next_hop_drirp_url", "nextHopUrl",
            "nextUrl", "delegateUrl", "routerUrl", "next", "delegate"
    };

    public DrirpRouterClientImpl() {
        super();
    }

    public DrirpRouterClientImpl(String serverUrl) throws DrirpClientException {
        super();
        connect(serverUrl);
    }

    public void setMaxHops(int maxHops) {
        if (maxHops > 0) {
            this.maxHops = maxHops;
        }
    }

    public void setDelegateKeys(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        this.delegateKeys = keys.clone();
    }

    @Override
    public void connect(String url) throws DrirpClientException {
        super.connect(url);
        if (this.channel != null) {
            cachedChannels.put(url, this.channel);
        }
    }

    @Override
    public void close() {
        for (DrirpClientChannel ch : cachedChannels.values()) {
            ch.close();
        }
        cachedChannels.clear();
        channel = null;
    }

    @Override
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
        String currentUrl = serverUrl;
        if (currentUrl == null || currentUrl.isEmpty()) {
            throw new DrirpClientException("server url not set");
        }
        Set<String> visitedUrls = new HashSet<String>();
        DrirpMessage response = null;
        for (int hop = 0; hop < maxHops; hop++) {
            if (!visitedUrls.add(currentUrl)) {
                throw new DrirpClientException("router loop detected: " + currentUrl);
            }
            channel = getOrCreateChannel(currentUrl);
            int attempts = retryPolicy == null ? 1 : retryPolicy.getMaxAttempts();
            DrirpClientException lastException = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    beforeAttempt();
                    if (!channel.isConnected()) {
                        channel.connect(currentUrl);
                    }
                    response = channel.sendMessage(message, timeoutMs);
                    if (response == null) {
                        throw new DrirpClientException("empty response");
                    }
                    afterReceive(message, response);
                    afterAttemptSuccess();
                    lastException = null;
                    break;
                } catch (URISyntaxException e) {
                    DrirpClientException wrapped = new DrirpClientException("invalid server url: " + currentUrl, e);
                    afterAttemptFailure(wrapped);
                    throw wrapped;
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
            String nextUrl = extractNextUrl(response);
            if (nextUrl == null || nextUrl.isEmpty() || nextUrl.equals(currentUrl)) {
                return response;
            }
            currentUrl = normalizeNextUrl(currentUrl, nextUrl);
            serverUrl = currentUrl;
        }
        throw new DrirpClientException("router hops exceeded: " + maxHops);
    }

    protected DrirpClientChannel getOrCreateChannel(String url) throws DrirpClientException {
        DrirpClientChannel cached = cachedChannels.get(url);
        if (cached != null) {
            return cached;
        }
        DrirpClientChannel created = DrirpClientChannelGenerator.create(url);
        cachedChannels.put(url, created);
        return created;
    }

    private String extractNextUrl(DrirpMessage response) {
        if (response == null) {
            return null;
        }
        JsonElement body = response.getBody();
        if (body == null || !body.isJsonObject()) {
            return null;
        }
        JsonObject obj = body.getAsJsonObject();
        String[] keys = delegateKeys;
        for (String key : keys) {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                return obj.get(key).getAsString();
            }
        }
        if (obj.has("router") && obj.get("router").isJsonObject()) {
            JsonObject router = obj.getAsJsonObject("router");
            if (router.has("url") && router.get("url").isJsonPrimitive()) {
                return router.get("url").getAsString();
            }
        }
        return null;
    }

    private static String normalizeNextUrl(String currentUrl, String nextUrl) {
        if (nextUrl.contains("://")) {
            return nextUrl;
        }
        try {
            URI current = new URI(currentUrl);
            if (current.getScheme() != null) {
                return current.getScheme() + "://" + nextUrl;
            }
        } catch (URISyntaxException e) {
            return nextUrl;
        }
        return nextUrl;
    }
}


