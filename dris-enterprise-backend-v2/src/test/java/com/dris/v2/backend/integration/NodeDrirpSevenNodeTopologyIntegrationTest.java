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

class NodeDrirpSevenNodeTopologyIntegrationTest {
    private static final long WAIT_TIMEOUT_MS = 15000L;
    private static final String LOG_PREFIX = "[TOPO-7] ";

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
    void shouldBuildSevenNodeTopologyStepByStep() throws Exception {
        log("=== BEGIN seven-node topology build test ===");
        Node country = new Node("node-country", "COUNTRY", "1100", "AAAA");
        Node provinceA = new Node("node-province-a", "PROVINCE", "3700", "ABBB");
        Node provinceB = new Node("node-province-b", "PROVINCE", "4200", "ACCC");
        Node cityA = new Node("node-city-a", "CITY", "3701", "ADDD");
        Node cityB = new Node("node-city-b", "CITY", "4201", "AEEE");
        Node countyA = new Node("node-county-a", "COUNTY", "3701", "AFFF");
        Node countyB = new Node("node-county-b", "COUNTY", "4201", "AGGG");

        allocatePorts(country, provinceA, provinceB, cityA, cityB, countyA, countyB);

        log("Step-1 start country node");
        startNode(country, null);
        waitHttpReady(country.httpPort, WAIT_TIMEOUT_MS);
        dumpRoutes(country.httpPort, "country routes after startup");

        log("Step-2 register province nodes to country");
        JsonNode provinceARegister = registerChild(country.httpPort, provinceA, 200);
        assertTrue(provinceARegister.path("ok").asBoolean());
        assertEquals("PROVINCE", provinceARegister.path("data").path("childLevel").asText());

        JsonNode provinceBRegister = registerChild(country.httpPort, provinceB, 200);
        assertTrue(provinceBRegister.path("ok").asBoolean());
        assertEquals("PROVINCE", provinceBRegister.path("data").path("childLevel").asText());
        dumpDirectDescendants(country.httpPort, "country descendants after province register");

        log("Step-3 start province nodes");
        startNode(provinceA, country);
        startNode(provinceB, country);
        waitHttpReady(provinceA.httpPort, WAIT_TIMEOUT_MS);
        waitHttpReady(provinceB.httpPort, WAIT_TIMEOUT_MS);

        waitDirectDescendantActive(country.httpPort, code(provinceA), provinceA.nodeId, WAIT_TIMEOUT_MS);
        waitDirectDescendantActive(country.httpPort, code(provinceB), provinceB.nodeId, WAIT_TIMEOUT_MS);
        dumpDirectDescendants(country.httpPort, "country descendants after province hello");

        log("Step-4 register city nodes");
        JsonNode cityARegister = registerChild(provinceA.httpPort, cityA, 200);
        assertTrue(cityARegister.path("ok").asBoolean());
        assertEquals("CITY", cityARegister.path("data").path("childLevel").asText());

        JsonNode cityBRegister = registerChild(provinceB.httpPort, cityB, 200);
        assertTrue(cityBRegister.path("ok").asBoolean());
        assertEquals("CITY", cityBRegister.path("data").path("childLevel").asText());
        dumpDirectDescendants(provinceA.httpPort, "province-a descendants after city register");
        dumpDirectDescendants(provinceB.httpPort, "province-b descendants after city register");

        log("Step-5 start city nodes");
        startNode(cityA, provinceA);
        startNode(cityB, provinceB);
        waitHttpReady(cityA.httpPort, WAIT_TIMEOUT_MS);
        waitHttpReady(cityB.httpPort, WAIT_TIMEOUT_MS);

        waitDirectDescendantActive(provinceA.httpPort, code(cityA), cityA.nodeId, WAIT_TIMEOUT_MS);
        waitDirectDescendantActive(provinceB.httpPort, code(cityB), cityB.nodeId, WAIT_TIMEOUT_MS);
        dumpDirectDescendants(provinceA.httpPort, "province-a descendants after city hello");
        dumpDirectDescendants(provinceB.httpPort, "province-b descendants after city hello");

        waitRoute(country.httpPort, code(cityA), code(provinceA), 2, WAIT_TIMEOUT_MS);
        waitRoute(country.httpPort, code(cityB), code(provinceB), 2, WAIT_TIMEOUT_MS);
        dumpRoutes(country.httpPort, "country routes after city route insert");

        log("Step-6 verify duplicate city registration conflict");
        JsonNode duplicateCityA = registerChild(
                provinceA.httpPort,
                new Node("node-city-a-dup", "CITY", cityA.regionCode, cityA.organizationCode),
                409
        );
        assertEquals("CONFLICT", duplicateCityA.path("code").asText());

        log("Step-7 register county nodes");
        JsonNode countyARegister = registerChild(cityA.httpPort, countyA, 200);
        assertTrue(countyARegister.path("ok").asBoolean());
        assertEquals("COUNTY", countyARegister.path("data").path("childLevel").asText());

        JsonNode countyBRegister = registerChild(cityB.httpPort, countyB, 200);
        assertTrue(countyBRegister.path("ok").asBoolean());
        assertEquals("COUNTY", countyBRegister.path("data").path("childLevel").asText());
        dumpDirectDescendants(cityA.httpPort, "city-a descendants after county register");
        dumpDirectDescendants(cityB.httpPort, "city-b descendants after county register");

        log("Step-8 start county nodes");
        startNode(countyA, cityA);
        startNode(countyB, cityB);
        waitHttpReady(countyA.httpPort, WAIT_TIMEOUT_MS);
        waitHttpReady(countyB.httpPort, WAIT_TIMEOUT_MS);

        waitDirectDescendantActive(cityA.httpPort, code(countyA), countyA.nodeId, WAIT_TIMEOUT_MS);
        waitDirectDescendantActive(cityB.httpPort, code(countyB), countyB.nodeId, WAIT_TIMEOUT_MS);
        dumpDirectDescendants(cityA.httpPort, "city-a descendants after county hello");
        dumpDirectDescendants(cityB.httpPort, "city-b descendants after county hello");

        waitRoute(provinceA.httpPort, code(countyA), code(cityA), 2, WAIT_TIMEOUT_MS);
        waitRoute(provinceB.httpPort, code(countyB), code(cityB), 2, WAIT_TIMEOUT_MS);
        dumpRoutes(provinceA.httpPort, "province-a routes after county route insert");
        dumpRoutes(provinceB.httpPort, "province-b routes after county route insert");

        waitRoute(country.httpPort, code(countyA), code(provinceA), 3, WAIT_TIMEOUT_MS);
        waitRoute(country.httpPort, code(countyB), code(provinceB), 3, WAIT_TIMEOUT_MS);
        dumpRoutes(country.httpPort, "country final routes");
        log("=== END seven-node topology build test ===");
    }

    private void allocatePorts(Node... nodes) throws Exception {
        for (Node node : nodes) {
            node.httpPort = freePort();
            node.drirpPort = freePort();
            log("allocate ports: " + nodeSummary(node));
        }
    }

    private JsonNode registerChild(int parentHttpPort, Node child, int expectedStatus) throws Exception {
        String body = "{\"childNodeName\":\"" + child.nodeId + "\",\"regionCode\":\"" + child.regionCode
                + "\",\"orgCode\":\"" + child.organizationCode + "\",\"childDrirpUrl\":\"" + grpcUrl(child.drirpPort) + "\"}";
        String url = "http://127.0.0.1:" + parentHttpPort + "/api/v2/node/register";
        log("register child request: parentHttpPort=" + parentHttpPort + ", child=" + nodeSummary(child));
        JsonNode response = postJson(url, body, expectedStatus);
        log("register child response: statusExpected=" + expectedStatus + ", body=" + response.toString());
        return response;
    }

    private void startNode(Node node, Node parent) {
        String parentNodeId = parent == null ? "" : parent.nodeId;
        String parentDrirpUrl = parent == null ? "" : grpcUrl(parent.drirpPort);
        String parentRegionCode = parent == null ? "" : parent.regionCode;
        String parentOrganizationCode = parent == null ? "" : parent.organizationCode;
        String dbName = "node_" + node.nodeId.replace('-', '_') + "_" + node.drirpPort;
        log("start node: self=" + nodeSummary(node)
                + ", parentNodeId=" + (parent == null ? "<none>" : parentNodeId)
                + ", parentDrirpUrl=" + (parent == null ? "<none>" : parentDrirpUrl));

        ConfigurableApplicationContext context = new SpringApplicationBuilder(BackendV2Application.class)
                .run(
                        "--server.port=" + node.httpPort,
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.url=jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.sql.init.mode=always",
                        "--dris.v2.mode=jdbc",
                        "--dris.v2.node-id=" + node.nodeId,
                        "--dris.v2.node-level=" + node.level,
                        "--dris.v2.region-code=" + node.regionCode,
                        "--dris.v2.organization-code=" + node.organizationCode,
                        "--dris.v2.drirp-enabled=true",
                        "--dris.v2.drirp-port=" + node.drirpPort,
                        "--dris.v2.drirp-public-url=" + grpcUrl(node.drirpPort),
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
        log("node started: " + nodeSummary(node));
    }

    private void waitHttpReady(final int port, long timeoutMs) throws Exception {
        waitUntil(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/api/v2/node/routes").openConnection();
                    connection.setRequestMethod("GET");
                    return connection.getResponseCode() == 200;
                } catch (Exception ignored) {
                    return Boolean.FALSE;
                }
            }
        }, timeoutMs, "http server is not ready, port=" + port);
        log("http ready: port=" + port);
    }

    private void waitDirectDescendantActive(final int parentHttpPort, final String childCode, final String childNodeName, long timeoutMs) throws Exception {
        waitUntil(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                JsonNode direct = getJson("http://127.0.0.1:" + parentHttpPort + "/api/v2/node/direct-descendants", 200);
                JsonNode items = direct.path("data");
                if (!items.isArray()) {
                    return Boolean.FALSE;
                }
                for (JsonNode item : items) {
                    if (childCode.equals(item.path("childCode").asText())
                            && childNodeName.equals(item.path("childNodeName").asText())
                            && "ACTIVE".equals(item.path("relationStatus").asText())) {
                        log("descendant active observed: parentHttpPort=" + parentHttpPort + ", childCode=" + childCode
                                + ", childNodeName=" + childNodeName + ", row=" + item.toString());
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }
        }, timeoutMs, "descendant not active, parentPort=" + parentHttpPort + ", childCode=" + childCode);
    }

    private void waitRoute(final int parentHttpPort, final String targetCode, final String nextHopCode, final int hopCount, long timeoutMs) throws Exception {
        waitUntil(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                JsonNode routes = getJson("http://127.0.0.1:" + parentHttpPort + "/api/v2/node/routes", 200);
                JsonNode items = routes.path("data");
                if (!items.isArray()) {
                    return Boolean.FALSE;
                }
                for (JsonNode item : items) {
                    if (targetCode.equals(item.path("targetCode").asText())
                            && nextHopCode.equals(item.path("nextHopCode").asText())
                            && hopCount == item.path("hopCount").asInt()) {
                        log("route observed: parentHttpPort=" + parentHttpPort + ", targetCode=" + targetCode
                                + ", nextHopCode=" + nextHopCode + ", hopCount=" + hopCount + ", row=" + item.toString());
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }
        }, timeoutMs, "route not found, parentPort=" + parentHttpPort + ", targetCode=" + targetCode);
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

    private void dumpDirectDescendants(int parentHttpPort, String label) throws Exception {
        JsonNode direct = getJson("http://127.0.0.1:" + parentHttpPort + "/api/v2/node/direct-descendants", 200);
        log(label + " -> " + direct.path("data").toString());
    }

    private void dumpRoutes(int parentHttpPort, String label) throws Exception {
        JsonNode routes = getJson("http://127.0.0.1:" + parentHttpPort + "/api/v2/node/routes", 200);
        log(label + " -> " + routes.path("data").toString());
    }

    private void log(String message) {
        System.out.println(LOG_PREFIX + message);
    }

    private String nodeSummary(Node node) {
        return node.nodeId + "(" + node.level + ", code=" + code(node)
                + ", http=" + node.httpPort + ", drirp=" + node.drirpPort + ")";
    }

    private static String code(Node node) {
        return node.regionCode + node.organizationCode;
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

    private static final class Node {
        private final String nodeId;
        private final String level;
        private final String regionCode;
        private final String organizationCode;
        private int httpPort;
        private int drirpPort;

        private Node(String nodeId, String level, String regionCode, String organizationCode) {
            this.nodeId = nodeId;
            this.level = level;
            this.regionCode = regionCode;
            this.organizationCode = organizationCode;
        }
    }
}
