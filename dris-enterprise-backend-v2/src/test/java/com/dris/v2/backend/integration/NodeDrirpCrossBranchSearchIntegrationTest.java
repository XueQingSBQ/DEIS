package com.dris.v2.backend.integration;

import com.dris.v2.backend.BackendV2Application;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeDrirpCrossBranchSearchIntegrationTest {
    private static final long WAIT_TIMEOUT_MS = 18000L;
    private static final String LOG_PREFIX = "[CROSS-BRANCH-SEARCH] ";
    private static final String DATA_CATEGORY_OTHER = "\u5176\u4ed6";
    private static final String REGISTRATION_TYPE_PRODUCT = "\u6570\u636e\u4ea7\u54c1/\u670d\u52a1";

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
    void recursiveShouldSearchAcrossBranches() throws Exception {
        runCrossBranchScenario("RECURSIVE");
    }

    @Test
    void iterativeShouldSearchAcrossBranches() throws Exception {
        runCrossBranchScenario("ITERATIVE");
    }

    private void runCrossBranchScenario(String strategy) throws Exception {
        log("=== BEGIN cross-branch search scenario, strategy=" + strategy + " ===");
        Node country = new Node("node-country", "COUNTRY", "1100", "AAAA");
        Node provinceA = new Node("node-province-a", "PROVINCE", "3700", "ABBB");
        Node provinceB = new Node("node-province-b", "PROVINCE", "4200", "ACCC");
        Node cityA = new Node("node-city-a", "CITY", "3701", "ADDD");
        Node cityB = new Node("node-city-b", "CITY", "4201", "AEEE");
        Node countyA = new Node("node-county-a", "COUNTY", "3701", "AFFF");
        Node countyB = new Node("node-county-b", "COUNTY", "4201", "AGGG");
        allocatePorts(country, provinceA, provinceB, cityA, cityB, countyA, countyB);

        startNode(country, null, "recursive", 16);
        waitHttpReady(country.httpPort, WAIT_TIMEOUT_MS);

        registerAndStartChild(country, provinceA);
        registerAndStartChild(country, provinceB);
        waitDirectDescendantActive(country.httpPort, code(provinceA), provinceA.nodeId, WAIT_TIMEOUT_MS);
        waitDirectDescendantActive(country.httpPort, code(provinceB), provinceB.nodeId, WAIT_TIMEOUT_MS);

        registerAndStartChild(provinceA, cityA);
        registerAndStartChild(provinceB, cityB);
        waitDirectDescendantActive(provinceA.httpPort, code(cityA), cityA.nodeId, WAIT_TIMEOUT_MS);
        waitDirectDescendantActive(provinceB.httpPort, code(cityB), cityB.nodeId, WAIT_TIMEOUT_MS);
        waitRoute(country.httpPort, code(cityA), code(provinceA), 2, WAIT_TIMEOUT_MS);
        waitRoute(country.httpPort, code(cityB), code(provinceB), 2, WAIT_TIMEOUT_MS);

        registerAndStartChild(cityA, countyA);
        registerAndStartChild(cityB, countyB);
        waitDirectDescendantActive(cityA.httpPort, code(countyA), countyA.nodeId, WAIT_TIMEOUT_MS);
        waitDirectDescendantActive(cityB.httpPort, code(countyB), countyB.nodeId, WAIT_TIMEOUT_MS);
        waitRoute(country.httpPort, code(countyA), code(provinceA), 3, WAIT_TIMEOUT_MS);
        waitRoute(country.httpPort, code(countyB), code(provinceB), 3, WAIT_TIMEOUT_MS);

        String identifier = registerIdentifierOnNode(
                countyB.httpPort,
                countyB.regionCode,
                countyB.organizationCode,
                "cross branch global search marker county b"
        );
        log("identifier registered at county-b: " + identifier);

        JsonNode searchOk = postJson(
                "http://127.0.0.1:" + countyA.httpPort + "/api/v2/search/global",
                buildGlobalSearchBody(strategy, code(country), "global search marker county b"),
                200
        );
        log("global search response: " + searchOk.toString());
        assertTrue(searchOk.path("ok").asBoolean());
        JsonNode items = searchOk.path("data").path("items");
        assertTrue(items.isArray());
        boolean found = false;
        for (JsonNode item : items) {
            if (identifier.equals(item.path("identifier").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "target identifier should be found across branch");

        JsonNode countryRoutes = getJson("http://127.0.0.1:" + country.httpPort + "/api/v2/node/routes", 200);
        assertRouteExists(countryRoutes.path("data"), code(countyB), code(provinceB), 3);

        JsonNode routeFail = postJson(
                "http://127.0.0.1:" + countyA.httpPort + "/api/v2/search/global",
                buildGlobalSearchBody(strategy, "9999ABCD", "global search marker county b"),
                400
        );
        log("global search route-fail response: " + routeFail.toString());
        assertTrue(!routeFail.path("ok").asBoolean());
        String failCode = routeFail.path("code").asText();
        assertTrue(
                "ROUTE_NOT_FOUND".equals(failCode) || "UPSTREAM_ERROR".equals(failCode) || "TTL_EXCEEDED".equals(failCode),
                "unexpected fail code: " + failCode
        );
        log("=== END cross-branch search scenario, strategy=" + strategy + " ===");
    }

    private String buildGlobalSearchBody(String strategy, String anchorCode, String query) throws Exception {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("naturalQuery", query);
        body.put("mode", "KEYWORD");
        body.put("strategy", strategy);
        body.put("anchorNodeCode", anchorCode);
        body.put("ttl", 16);
        body.put("maxFanout", 16);
        body.put("perNodeLimit", 20);
        body.put("limit", 20);
        body.put("offset", 0);
        return objectMapper.writeValueAsString(body);
    }

    private void registerAndStartChild(Node parent, Node child) throws Exception {
        JsonNode register = registerChild(parent.httpPort, child, 200);
        assertTrue(register.path("ok").asBoolean());
        startNode(child, parent, "recursive", 16);
        waitHttpReady(child.httpPort, WAIT_TIMEOUT_MS);
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
        return postJson(url, body, expectedStatus);
    }

    private String registerIdentifierOnNode(int httpPort, String regionCode, String orgCode, String marker) throws Exception {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("regionCode", regionCode);
        request.put("organizationCode", orgCode);
        request.put("industry", "energy");
        request.put("dataCategory", DATA_CATEGORY_OTHER);
        request.put("registrationType", REGISTRATION_TYPE_PRODUCT);
        request.put("owner", "county-b-center");
        request.put("description", marker);
        request.put("applicationScenario", "cross-node-search");
        request.put("name", "cross-branch-search-sample");
        request.put("entityType", "dataset");

        JsonNode register = postJson(
                "http://127.0.0.1:" + httpPort + "/api/v2/identifiers/register",
                objectMapper.writeValueAsString(request),
                200
        );
        assertTrue(register.path("ok").asBoolean());
        return register.path("data").asText();
    }

    private void startNode(Node node, Node parent, String resolveMode, int resolveMaxHops) {
        String parentNodeId = parent == null ? "" : parent.nodeId;
        String parentDrirpUrl = parent == null ? "" : grpcUrl(parent.drirpPort);
        String parentRegionCode = parent == null ? "" : parent.regionCode;
        String parentOrganizationCode = parent == null ? "" : parent.organizationCode;
        String dbName = "node_" + node.nodeId.replace('-', '_') + "_" + node.drirpPort;

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
                        "--dris.v2.resolve-mode=" + resolveMode,
                        "--dris.v2.resolve-max-hops=" + resolveMaxHops,
                        "--dris.v2.resolve-timeout-ms=5000",
                        "--dris.v2.resolve-allow-parent-fallback=true",
                        "--dris.v2.strict-industry-validation=false",
                        "--dris.v2.require-tool-sha256=false",
                        "--spring.main.banner-mode=off"
                );
        contexts.add(context);
        ensureRegionExists(context, node.regionCode);
    }

    private void ensureRegionExists(ConfigurableApplicationContext context, String regionCode) {
        DataSource dataSource = context.getBean(DataSource.class);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(1) FROM regions WHERE region_code = ?")) {
                query.setString(1, regionCode);
                try (ResultSet rs = query.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return;
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO regions (region_code, region_name) VALUES (?, ?)")) {
                insert.setString(1, regionCode);
                insert.setString(2, "test-" + regionCode);
                insert.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("ensureRegionExists failed, regionCode=" + regionCode, e);
        }
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
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }
        }, timeoutMs, "route not found, parentPort=" + parentHttpPort + ", targetCode=" + targetCode);
    }

    private void assertRouteExists(JsonNode routes, String targetCode, String nextHopCode, int hopCount) {
        assertTrue(routes.isArray(), "routes must be array");
        for (JsonNode item : routes) {
            if (targetCode.equals(item.path("targetCode").asText())
                    && nextHopCode.equals(item.path("nextHopCode").asText())
                    && hopCount == item.path("hopCount").asInt()) {
                return;
            }
        }
        throw new AssertionError("route not found in route list, targetCode=" + targetCode + ", nextHopCode=" + nextHopCode);
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
        String text = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        assertEquals(expectedStatus, status, "url=" + url + ", response=" + text);
        return objectMapper.readTree(text);
    }

    private JsonNode getJson(String url, int expectedStatus) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        String text = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        assertEquals(expectedStatus, status, "url=" + url + ", response=" + text);
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

    private static String grpcUrl(int port) {
        return "grpc://127.0.0.1:" + port;
    }

    private static int freePort() throws Exception {
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();
        return port;
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

