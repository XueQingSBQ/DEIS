package com.dris.v2.backend.integration;

import com.dris.v2.backend.BackendV2Application;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeDrirpMultiNodeIntegrationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<ConfigurableApplicationContext> contexts = new ArrayList<ConfigurableApplicationContext>();

    @AfterEach
    void tearDown() {
        for (int i = contexts.size() - 1; i >= 0; i--) {
            try {
                contexts.get(i).close();
            } catch (Exception ignored) {
            }
        }
        contexts.clear();
    }

    @Test
    void registerAndStartupHello_shouldSyncRouteToCountryNode() throws Exception {
        int countryHttp = freePort();
        int countryDrirp = freePort();
        int provinceHttp = freePort();
        int provinceDrirp = freePort();
        int cityHttp = freePort();
        int cityDrirp = freePort();

        startNode("node-country", "COUNTRY", "1100", "AAAA", countryHttp, countryDrirp, "", "", "", "");
        waitHttpReady(countryHttp, 12000L);

        String countryDrirpUrl = grpcUrl(countryDrirp);
        JsonNode provinceRegister = postJson(
                "http://127.0.0.1:" + countryHttp + "/api/v2/node/register",
                "{\"childNodeName\":\"node-province\",\"regionCode\":\"3700\",\"orgCode\":\"ABCD\",\"childDrirpUrl\":\"" + grpcUrl(provinceDrirp) + "\"}",
                200
        );
        assertTrue(provinceRegister.path("ok").asBoolean());
        assertEquals("PROVINCE", provinceRegister.path("data").path("childLevel").asText());

        startNode("node-province", "PROVINCE", "3700", "ABCD", provinceHttp, provinceDrirp, "node-country", countryDrirpUrl, "1100", "AAAA");
        waitHttpReady(provinceHttp, 12000L);

        waitUntil(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                JsonNode direct = getJson("http://127.0.0.1:" + countryHttp + "/api/v2/node/direct-descendants", 200);
                JsonNode items = direct.path("data");
                if (!items.isArray()) {
                    return Boolean.FALSE;
                }
                for (JsonNode item : items) {
                    if ("3700ABCD".equals(item.path("childCode").asText())
                            && "node-province".equals(item.path("childNodeName").asText())
                            && "ACTIVE".equals(item.path("relationStatus").asText())) {
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }
        }, 12000L, "country did not mark province ACTIVE after startup hello");

        JsonNode cityRegister = postJson(
                "http://127.0.0.1:" + provinceHttp + "/api/v2/node/register",
                "{\"childNodeName\":\"node-city\",\"regionCode\":\"3701\",\"orgCode\":\"WXYZ\",\"childDrirpUrl\":\"" + grpcUrl(cityDrirp) + "\"}",
                200
        );
        assertTrue(cityRegister.path("ok").asBoolean());
        assertEquals("CITY", cityRegister.path("data").path("childLevel").asText());

        startNode("node-city", "CITY", "3701", "WXYZ", cityHttp, cityDrirp, "node-province", grpcUrl(provinceDrirp), "3700", "ABCD");
        waitHttpReady(cityHttp, 12000L);

        waitUntil(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                JsonNode routes = getJson("http://127.0.0.1:" + countryHttp + "/api/v2/node/routes", 200);
                JsonNode items = routes.path("data");
                if (!items.isArray()) {
                    return Boolean.FALSE;
                }
                for (JsonNode item : items) {
                    if ("3701WXYZ".equals(item.path("targetCode").asText())
                            && "3700ABCD".equals(item.path("nextHopCode").asText())) {
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }
        }, 12000L, "country did not receive route insert from province");

        JsonNode duplicate = postJson(
                "http://127.0.0.1:" + provinceHttp + "/api/v2/node/register",
                "{\"childNodeName\":\"node-city-dup\",\"regionCode\":\"3701\",\"orgCode\":\"WXYZ\",\"childDrirpUrl\":\"grpc://127.0.0.1:40099\"}",
                409
        );
        assertEquals("CONFLICT", duplicate.path("code").asText());
    }

    private void startNode(String nodeId,
                           String nodeLevel,
                           String regionCode,
                           String orgCode,
                           int httpPort,
                           int drirpPort,
                           String parentNodeId,
                           String parentDrirpUrl,
                           String parentRegionCode,
                           String parentOrganizationCode) {
        String dbName = "node_" + nodeId.replace('-', '_') + "_" + drirpPort;
        ConfigurableApplicationContext context = new SpringApplicationBuilder(BackendV2Application.class)
                .run(
                        "--server.port=" + httpPort,
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.url=jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.sql.init.mode=always",
                        "--dris.v2.mode=jdbc",
                        "--dris.v2.node-id=" + nodeId,
                        "--dris.v2.node-level=" + nodeLevel,
                        "--dris.v2.region-code=" + regionCode,
                        "--dris.v2.organization-code=" + orgCode,
                        "--dris.v2.drirp-enabled=true",
                        "--dris.v2.drirp-port=" + drirpPort,
                        "--dris.v2.drirp-public-url=" + grpcUrl(drirpPort),
                        "--dris.v2.parent-node-id=" + parentNodeId,
                        "--dris.v2.parent-drirp-url=" + parentDrirpUrl,
                        "--dris.v2.parent-region-code=" + parentRegionCode,
                        "--dris.v2.parent-organization-code=" + parentOrganizationCode,
                        "--dris.v2.startup-hello-enabled=true",
                        "--dris.v2.strict-industry-validation=false",
                        "--dris.v2.require-tool-sha256=false",
                        "--spring.main.banner-mode=off"
                );
        contexts.add(context);
    }

    private JsonNode postJson(String url, String body, int expectedStatus) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        assertEquals(expectedStatus, status);
        String text = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        return objectMapper.readTree(text);
    }

    private JsonNode getJson(String url, int expectedStatus) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        assertEquals(expectedStatus, status);
        String text = readAll(connection.getInputStream());
        return objectMapper.readTree(text);
    }

    private void waitHttpReady(final int port, long timeoutMs) throws Exception {
        waitUntil(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/api/v2/node/routes").openConnection();
                    connection.setRequestMethod("GET");
                    int status = connection.getResponseCode();
                    return status == 200;
                } catch (Exception ignored) {
                    return Boolean.FALSE;
                }
            }
        }, timeoutMs, "http server is not ready, port=" + port);
    }

    private void waitUntil(Callable<Boolean> condition, long timeoutMs, String timeoutMessage) throws Exception {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (Boolean.TRUE.equals(condition.call())) {
                return;
            }
            Thread.sleep(200L);
        }
        throw new AssertionError(timeoutMessage);
    }

    private static String readAll(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
        }
        return out.toString("UTF-8");
    }

    private static String grpcUrl(int port) {
        return "grpc://127.0.0.1:" + port;
    }

    private static int freePort() throws Exception {
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }
}
