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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeDrirpDistributedResolveIntegrationTest {
    private static final long WAIT_TIMEOUT_MS = 15000L;
    private static final String LOG_PREFIX = "[RESOLVE-DIST] ";
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
    void recursiveModeShouldResolveAcrossMultiHops() throws Exception {
        runResolveScenario("recursive");
    }

    @Test
    void iterativeModeShouldResolveAcrossMultiHops() throws Exception {
        runResolveScenario("iterative");
    }

    private void runResolveScenario(String resolveMode) throws Exception {
        log("=== BEGIN distributed resolve scenario, mode=" + resolveMode + " ===");
        Node country = new Node("node-country", "COUNTRY", "1100", "AAAA");
        Node province = new Node("node-province", "PROVINCE", "3700", "ABCD");
        Node city = new Node("node-city", "CITY", "3701", "WXYZ");
        Node county = new Node("node-county", "COUNTY", "3701", "BCDF");
        allocatePorts(country, province, city, county);

        log("Step-1 start country");
        startNode(country, null, resolveMode);
        waitHttpReady(country.httpPort, WAIT_TIMEOUT_MS);
        dumpRoutes(country.httpPort, "country routes after startup");

        log("Step-2 register/start province");
        JsonNode provinceRegister = registerChild(country.httpPort, province, 200);
        assertTrue(provinceRegister.path("ok").asBoolean());
        assertEquals("PROVINCE", provinceRegister.path("data").path("childLevel").asText());
        startNode(province, country, resolveMode);
        waitHttpReady(province.httpPort, WAIT_TIMEOUT_MS);
        waitDirectDescendantActive(country.httpPort, code(province), province.nodeId, WAIT_TIMEOUT_MS);
        dumpDirectDescendants(country.httpPort, "country descendants after province hello");

        log("Step-3 register/start city");
        JsonNode cityRegister = registerChild(province.httpPort, city, 200);
        assertTrue(cityRegister.path("ok").asBoolean());
        assertEquals("CITY", cityRegister.path("data").path("childLevel").asText());
        startNode(city, province, resolveMode);
        waitHttpReady(city.httpPort, WAIT_TIMEOUT_MS);
        waitDirectDescendantActive(province.httpPort, code(city), city.nodeId, WAIT_TIMEOUT_MS);
        waitRoute(country.httpPort, code(city), code(province), 2, WAIT_TIMEOUT_MS);
        dumpRoutes(country.httpPort, "country routes after city route insert");

        log("Step-4 register/start county");
        JsonNode countyRegister = registerChild(city.httpPort, county, 200);
        assertTrue(countyRegister.path("ok").asBoolean());
        assertEquals("COUNTY", countyRegister.path("data").path("childLevel").asText());
        startNode(county, city, resolveMode);
        waitHttpReady(county.httpPort, WAIT_TIMEOUT_MS);
        waitDirectDescendantActive(city.httpPort, code(county), county.nodeId, WAIT_TIMEOUT_MS);
        waitRoute(province.httpPort, code(county), code(city), 2, WAIT_TIMEOUT_MS);
        waitRoute(country.httpPort, code(county), code(province), 3, WAIT_TIMEOUT_MS);
        dumpRoutes(province.httpPort, "province routes after county route insert");
        dumpRoutes(country.httpPort, "country routes after county route insert");

        log("Step-5 register identifier at county and resolve from country");
        String identifier = registerIdentifierOnCounty(county.httpPort, county.regionCode, county.organizationCode);
        log("registered identifier=" + identifier);

        String resolveUrl = "http://127.0.0.1:" + country.httpPort + "/api/v2/identifiers/" + identifier + "/metadata";
        JsonNode metadataFromCountry = getJson(resolveUrl, 200);
        log("resolve metadata response from country: " + metadataFromCountry.toString());
        assertTrue(metadataFromCountry.path("ok").asBoolean());
        assertEquals(identifier, metadataFromCountry.path("data").path("identifier").asText());
        assertEquals(county.regionCode, metadataFromCountry.path("data").path("regionCode").asText());
        assertEquals(county.organizationCode, metadataFromCountry.path("data").path("organizationCode").asText());
        log("=== END distributed resolve scenario, mode=" + resolveMode + " ===");
    }

    private String registerIdentifierOnCounty(int countyHttpPort, String regionCode, String orgCode) throws Exception {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("regionCode", regionCode);
        request.put("organizationCode", orgCode);
        request.put("industry", "energy");
        request.put("dataCategory", DATA_CATEGORY_OTHER);
        request.put("registrationType", REGISTRATION_TYPE_PRODUCT);
        request.put("owner", "county-center");
        request.put("description", "distributed resolve test");
        request.put("applicationScenario", "cross-node");
        request.put("name", "cross-node-sample");
        request.put("entityType", "dataset");

        String url = "http://127.0.0.1:" + countyHttpPort + "/api/v2/identifiers/register";
        String body = objectMapper.writeValueAsString(request);
        log("register identifier request: url=" + url + ", body=" + body);
        JsonNode register = postJson(url, body, 200);
        log("register identifier response: " + register.toString());
        assertTrue(register.path("ok").asBoolean());
        return register.path("data").asText();
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
        log("register child response: " + response.toString());
        return response;
    }

    private void startNode(Node node, Node parent, String resolveMode) {
        String parentNodeId = parent == null ? "" : parent.nodeId;
        String parentDrirpUrl = parent == null ? "" : grpcUrl(parent.drirpPort);
        String parentRegionCode = parent == null ? "" : parent.regionCode;
        String parentOrganizationCode = parent == null ? "" : parent.organizationCode;
        String dbName = "node_" + node.nodeId.replace('-', '_') + "_" + node.drirpPort;
        log("start node: self=" + nodeSummary(node)
                + ", parentNodeId=" + (parent == null ? "<none>" : parentNodeId)
                + ", parentDrirpUrl=" + (parent == null ? "<none>" : parentDrirpUrl)
                + ", resolveMode=" + resolveMode);

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
                        "--dris.v2.resolve-max-hops=16",
                        "--dris.v2.resolve-timeout-ms=5000",
                        "--dris.v2.resolve-allow-parent-fallback=true",
                        "--dris.v2.strict-industry-validation=false",
                        "--dris.v2.require-tool-sha256=false",
                        "--spring.main.banner-mode=off"
                );
        contexts.add(context);
        ensureRegionExists(context, node.regionCode);
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
                        log("descendant active observed: parentHttpPort=" + parentHttpPort
                                + ", childCode=" + childCode + ", childNodeName=" + childNodeName
                                + ", row=" + item.toString());
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
                        log("route observed: parentHttpPort=" + parentHttpPort
                                + ", targetCode=" + targetCode + ", nextHopCode=" + nextHopCode
                                + ", hopCount=" + hopCount + ", row=" + item.toString());
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
        String text = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        log("POST " + url + " -> status=" + status + ", response=" + text);
        assertEquals(expectedStatus, status, "url=" + url + ", response=" + text);
        return objectMapper.readTree(text);
    }

    private JsonNode getJson(String url, int expectedStatus) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        String text = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        log("GET " + url + " -> status=" + status + ", response=" + text);
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

    private void ensureRegionExists(ConfigurableApplicationContext context, String regionCode) {
        DataSource dataSource = context.getBean(DataSource.class);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(1) FROM regions WHERE region_code = ?")) {
                query.setString(1, regionCode);
                try (ResultSet rs = query.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        log("region exists in db: " + regionCode);
                        return;
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO regions (region_code, region_name) VALUES (?, ?)")) {
                insert.setString(1, regionCode);
                insert.setString(2, "test-" + regionCode);
                insert.executeUpdate();
                log("insert region into db: " + regionCode);
            }
        } catch (Exception e) {
            throw new IllegalStateException("ensureRegionExists failed, regionCode=" + regionCode, e);
        }
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
