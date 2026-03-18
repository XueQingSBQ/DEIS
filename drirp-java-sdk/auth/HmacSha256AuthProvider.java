package org.bdware.drirp.auth;

import com.google.gson.JsonObject;
import org.bdware.drirp.codec.DrirpMessageCodec;
import org.bdware.drirp.model.DrirpMessage;

import java.nio.charset.StandardCharsets;

public class HmacSha256AuthProvider implements DrirpAuthProvider {
    private final byte[] secret;
    private final String keyId;
    private final long maxSkewMs;

    public HmacSha256AuthProvider(String secret) {
        this(secret, null, 0L);
    }

    public HmacSha256AuthProvider(String secret, String keyId, long maxSkewMs) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.keyId = keyId;
        this.maxSkewMs = maxSkewMs;
    }

    @Override
    public void sign(DrirpMessage message) {
        if (message == null) {
            return;
        }
        long ts = message.getTs() != null ? message.getTs() : System.currentTimeMillis();
        message.setTs(ts);
        String payload = buildPayload(message, ts);
        String signature;
        try {
            signature = DrirpAuthUtils.hmacSha256Base64(secret, payload);
        } catch (Exception e) {
            message.setMsg("auth sign failed: " + e.getMessage());
            return;
        }
        JsonObject auth = new JsonObject();
        auth.addProperty("alg", "HS256");
        auth.addProperty("sig", signature);
        auth.addProperty("ts", ts);
        if (keyId != null && !keyId.isEmpty()) {
            auth.addProperty("kid", keyId);
        }
        message.setAuth(auth);
    }

    @Override
    public boolean verify(DrirpMessage message) {
        if (message == null) {
            return false;
        }
        JsonObject auth = message.getAuth();
        if (auth == null || !auth.has("sig")) {
            return false;
        }
        if (!auth.has("alg") || !"HS256".equalsIgnoreCase(auth.get("alg").getAsString())) {
            return false;
        }
        long ts = message.getTs() != null ? message.getTs() : (auth.has("ts") ? auth.get("ts").getAsLong() : 0L);
        if (ts <= 0) {
            return false;
        }
        if (maxSkewMs > 0) {
            long now = System.currentTimeMillis();
            if (Math.abs(now - ts) > maxSkewMs) {
                return false;
            }
        }
        String payload = buildPayload(message, ts);
        String expected;
        try {
            expected = DrirpAuthUtils.hmacSha256Base64(secret, payload);
        } catch (Exception e) {
            return false;
        }
        String actual = auth.get("sig").getAsString();
        return DrirpAuthUtils.constantTimeEquals(expected, actual);
    }

    private static String buildPayload(DrirpMessage message, long ts) {
        String rid = message.getRid() == null ? "" : message.getRid();
        String body = DrirpMessageCodec.bodyToJson(message.getBody());
        String version = message.getVersion() == null ? "" : message.getVersion();
        String traceId = message.getTraceId() == null ? "" : message.getTraceId();
        String nonce = message.getNonce() == null ? "" : message.getNonce();
        String idempotencyKey = message.getIdempotencyKey() == null ? "" : message.getIdempotencyKey();
        return message.getCode() + "|" + rid + "|" + ts + "|" + version + "|" + traceId + "|" + nonce + "|" + idempotencyKey + "|" + body;
    }
}


