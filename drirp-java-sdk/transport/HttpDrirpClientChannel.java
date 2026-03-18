package org.bdware.drirp.transport;

import org.bdware.drirp.codec.DrirpMessageCodec;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.opcode.DrirpOpcode;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpDrirpClientChannel implements DrirpClientChannel {
    private final Map<Integer, String> routes = new HashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String baseUrl;
    private boolean connected;

    public HttpDrirpClientChannel() {
        routes.put(DrirpOpcode.RESOLVE, "/drirp/resolve");
        routes.put(DrirpOpcode.QUERY, "/drirp/query");
        routes.put(DrirpOpcode.REGISTER, "/drirp/register");
        routes.put(DrirpOpcode.UPDATE, "/drirp/update");
        routes.put(DrirpOpcode.DELETE, "/drirp/delete");
        routes.put(DrirpOpcode.ROUTER_QUERY, "/drirp/router/query");
    }

    public void setRoute(int code, String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        routes.put(code, normalizePath(path));
    }

    public void setHeader(String name, String value) {
        if (name == null || name.isEmpty()) {
            return;
        }
        if (value == null) {
            headers.remove(name);
            return;
        }
        headers.put(name, value);
    }

    @Override
    public void connect(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new URISyntaxException(url, "HTTP channel only supports http/https");
        }
        baseUrl = normalizeBaseUrl(url);
        connected = true;
    }

    @Override
    public void close() {
        connected = false;
        baseUrl = null;
    }

    @Override
    public boolean isConnected() {
        return connected && baseUrl != null;
    }

    @Override
    public DrirpMessage sendMessage(DrirpMessage request, long timeoutMs) throws DrirpClientException {
        if (!isConnected()) {
            throw new DrirpClientException("HTTP channel not connected");
        }
        String path = routes.getOrDefault(request.getCode(), "/drirp/call");
        String url = buildUrl(baseUrl, path);
        String payload = DrirpMessageCodec.toJson(request);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout((int) timeoutMs);
            connection.setReadTimeout((int) timeoutMs);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            applyProtocolHeaders(connection, request);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            String responseBody = readAll(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            if (responseBody == null || responseBody.isEmpty()) {
                return DrirpMessage.error(request.getCode(), request.getRid(), "empty response, http status=" + status);
            }
            try {
                DrirpMessage response = DrirpMessageCodec.fromJson(responseBody);
                if (response.getTraceId() == null) {
                    response.setTraceId(connection.getHeaderField("X-Drirp-TraceId"));
                }
                if (response.getVersion() == null) {
                    response.setVersion(connection.getHeaderField("X-Drirp-Version"));
                }
                return response;
            } catch (Exception e) {
                return DrirpMessage.error(request.getCode(), request.getRid(), "invalid response, http status=" + status);
            }
        } catch (Exception e) {
            throw new DrirpClientException("HTTP request failed: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String normalizeBaseUrl(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String normalizePath(String path) {
        if (!path.startsWith("/")) {
            return "/" + path;
        }
        return path;
    }

    private static String buildUrl(String base, String path) {
        return base + normalizePath(path);
    }

    private static void applyProtocolHeaders(HttpURLConnection connection, DrirpMessage request) {
        if (request == null) {
            return;
        }
        if (request.getTraceId() != null && !request.getTraceId().isEmpty()) {
            connection.setRequestProperty("X-Drirp-TraceId", request.getTraceId());
        }
        if (request.getRid() != null && !request.getRid().isEmpty()) {
            connection.setRequestProperty("X-Drirp-Rid", request.getRid());
        }
        if (request.getVersion() != null && !request.getVersion().isEmpty()) {
            connection.setRequestProperty("X-Drirp-Version", request.getVersion());
        }
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isEmpty()) {
            connection.setRequestProperty("X-Drirp-Idempotency-Key", request.getIdempotencyKey());
        }
    }

    private static String readAll(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}



