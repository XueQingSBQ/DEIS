package org.bdware.drirp.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bdware.drirp.codec.DrirpMessageCodec;
import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.opcode.DrirpOpcode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

public class DrirpHttpServer {
    private final DrirpDispatcher dispatcher;
    private final HttpServer server;

    public DrirpHttpServer(int port, DrirpDispatcher dispatcher) throws IOException {
        this("0.0.0.0", port, dispatcher, "");
    }

    public DrirpHttpServer(String host, int port, DrirpDispatcher dispatcher, String basePath) throws IOException {
        if (dispatcher == null) {
            throw new IllegalArgumentException("dispatcher is required");
        }
        this.dispatcher = dispatcher;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        String prefix = normalizeBasePath(basePath);
        registerDefaultRoutes(prefix);
        server.createContext(prefix + "/drirp/call", new DrirpHttpHandler(0));
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    public void setExecutor(Executor executor) {
        server.setExecutor(executor);
    }

    public void addRoute(String path, int code) {
        String normalized = normalizePath(path);
        server.createContext(normalized, new DrirpHttpHandler(code));
    }

    private void registerDefaultRoutes(String prefix) {
        addRoute(prefix + "/drirp/resolve", DrirpOpcode.RESOLVE);
        addRoute(prefix + "/drirp/query", DrirpOpcode.QUERY);
        addRoute(prefix + "/drirp/register", DrirpOpcode.REGISTER);
        addRoute(prefix + "/drirp/update", DrirpOpcode.UPDATE);
        addRoute(prefix + "/drirp/delete", DrirpOpcode.DELETE);
        addRoute(prefix + "/drirp/router/query", DrirpOpcode.ROUTER_QUERY);
    }

    private class DrirpHttpHandler implements HttpHandler {
        private final int fixedCode;

        private DrirpHttpHandler(int fixedCode) {
            this.fixedCode = fixedCode;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, DrirpMessage.error(fixedCode, null, "method not allowed"));
                return;
            }
            String body = readAll(exchange.getRequestBody());
            DrirpMessage request;
            try {
                request = (body == null || body.isEmpty()) ? new DrirpMessage() : DrirpMessageCodec.fromJson(body);
            } catch (Exception e) {
                writeResponse(exchange, 400, DrirpMessage.error(fixedCode, null, "invalid json"));
                return;
            }
            if (fixedCode > 0) {
                if (request.getCode() == 0) {
                    request.setCode(fixedCode);
                } else if (request.getCode() != fixedCode) {
                    writeResponse(exchange, 400, DrirpMessage.error(fixedCode, request.getRid(), "opcode mismatch"));
                    return;
                }
            }
            fillProtocolFieldsFromHeaders(exchange, request);
            DrirpMessage response = dispatcher.handle(request);
            writeResponse(exchange, 200, response);
        }
    }

    private void writeResponse(HttpExchange exchange, int status, DrirpMessage response) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        if (response != null) {
            if (response.getTraceId() != null && !response.getTraceId().isEmpty()) {
                headers.set("X-Drirp-TraceId", response.getTraceId());
            }
            if (response.getVersion() != null && !response.getVersion().isEmpty()) {
                headers.set("X-Drirp-Version", response.getVersion());
            }
            if (response.getRid() != null && !response.getRid().isEmpty()) {
                headers.set("X-Drirp-Rid", response.getRid());
            }
        }
        String json = DrirpMessageCodec.toJson(response);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private static void fillProtocolFieldsFromHeaders(HttpExchange exchange, DrirpMessage request) {
        if (request == null || exchange == null) {
            return;
        }
        Headers headers = exchange.getRequestHeaders();
        if ((request.getTraceId() == null || request.getTraceId().isEmpty()) && headers.getFirst("X-Drirp-TraceId") != null) {
            request.setTraceId(headers.getFirst("X-Drirp-TraceId"));
        }
        if ((request.getVersion() == null || request.getVersion().isEmpty()) && headers.getFirst("X-Drirp-Version") != null) {
            request.setVersion(headers.getFirst("X-Drirp-Version"));
        }
        if ((request.getRid() == null || request.getRid().isEmpty()) && headers.getFirst("X-Drirp-Rid") != null) {
            request.setRid(headers.getFirst("X-Drirp-Rid"));
        }
        if (!request.hasIdempotencyKey() && headers.getFirst("X-Drirp-Idempotency-Key") != null) {
            request.setIdempotencyKey(headers.getFirst("X-Drirp-Idempotency-Key"));
        }
    }

    private static String readAll(InputStream inputStream) throws IOException {
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

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            return "/" + path;
        }
        return path;
    }

    private static String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isEmpty()) {
            return "";
        }
        String normalized = normalizePath(basePath);
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}


