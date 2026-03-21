package com.dris.v2.backend.service.local.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.contract.system.KeywordIndexSyncResult;
import com.dris.v2.backend.dao.SearchDocumentRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service("elasticsearchKeywordSearchGateway")
public class ElasticsearchKeywordSearchGateway implements KeywordSearchGateway {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchKeywordSearchGateway.class);
    private static final List<String> SOURCE_FIELDS = Arrays.asList(
            "identifier", "name", "description", "applicationScenario", "owner",
            "industry", "dataCategory", "registrationType", "entityType",
            "theme", "region", "sourceFilename", "bizKeywordsText"
    );

    private final BackendV2Properties properties;
    private final SearchDocumentTextBuilder textBuilder;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ElasticsearchKeywordSearchGateway(
            BackendV2Properties properties,
            SearchDocumentTextBuilder textBuilder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties == null ? new BackendV2Properties() : properties;
        this.textBuilder = textBuilder;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.restTemplate = new RestTemplate(buildRequestFactory(this.properties));
    }

    @Override
    public String engine() {
        return "elasticsearch";
    }

    @Override
    public List<SearchDocumentRow> search(List<String> tokens, List<String> fields, Map<String, String> filters, int limit, int offset) {
        if (SearchTextSupport.isBlank(properties.getSearchEsUrl()) || SearchTextSupport.isBlank(properties.getSearchEsIndex())) {
            return Collections.emptyList();
        }
        String queryText = buildQueryText(tokens);
        if (queryText.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("from", Integer.valueOf(Math.max(0, offset)));
        body.put("size", Integer.valueOf(Math.max(0, limit)));
        body.put("_source", SOURCE_FIELDS);
        body.put("query", buildQuery(queryText, fields, filters));
        body.put("sort", Arrays.asList(
                Collections.singletonMap("_score", "desc"),
                Collections.singletonMap("identifier", "asc")
        ));
        JsonNode root = exchangeJson(HttpMethod.POST, "/" + properties.getSearchEsIndex() + "/_search", body, true);
        return parseSearchHits(root == null ? null : root.path("hits").path("hits"));
    }

    @Override
    public KeywordIndexSyncResult initializeIndex() {
        KeywordIndexSyncResult result = baseResult();
        if (existsIndex()) {
            result.setSkipped(true);
            result.setMessage("index already exists");
            return result;
        }
        exchangeJson(HttpMethod.PUT, "/" + properties.getSearchEsIndex(), buildCreateIndexBody(), false);
        result.setMessage("index created");
        return result;
    }

    @Override
    public KeywordIndexSyncResult rebuildIndex(List<SearchDocumentRow> documents) {
        KeywordIndexSyncResult result = baseResult();
        deleteIndexIfExists();
        initializeIndex();
        KeywordIndexSyncResult upsert = upsertBatch(documents);
        result.setProcessed(upsert.getProcessed());
        result.setIndexed(upsert.getIndexed());
        result.setDeleted(upsert.getDeleted());
        result.setFailed(upsert.getFailed());
        result.setMessage(upsert.getMessage());
        return result;
    }

    @Override
    public KeywordIndexSyncResult upsertBatch(List<SearchDocumentRow> documents) {
        KeywordIndexSyncResult result = baseResult();
        List<SearchDocumentRow> safeDocuments = documents == null ? Collections.<SearchDocumentRow>emptyList() : documents;
        result.setProcessed(safeDocuments.size());
        if (safeDocuments.isEmpty()) {
            result.setSkipped(true);
            result.setMessage("no documents to index");
            return result;
        }
        if (!existsIndex()) {
            initializeIndex();
        }
        StringBuilder ndjson = new StringBuilder(safeDocuments.size() * 256);
        int eligible = 0;
        for (SearchDocumentRow row : safeDocuments) {
            if (row == null || SearchTextSupport.isBlank(row.getIdentifier())) {
                continue;
            }
            try {
                Map<String, Object> source = toSource(row);
                Map<String, Object> meta = new LinkedHashMap<String, Object>();
                meta.put("_index", properties.getSearchEsIndex());
                meta.put("_id", row.getIdentifier());
                Map<String, Object> action = new LinkedHashMap<String, Object>();
                action.put("index", meta);
                ndjson.append(objectMapper.writeValueAsString(action)).append('\n');
                ndjson.append(objectMapper.writeValueAsString(source)).append('\n');
                eligible++;
            } catch (Exception exception) {
                log.warn("keyword index serialize failed, identifier={}, err={}", row.getIdentifier(), exception.getMessage());
            }
        }
        if (eligible <= 0) {
            result.setSkipped(true);
            result.setMessage("no eligible documents to index");
            return result;
        }
        JsonNode root = exchangeNdjson("/_bulk?refresh=true", ndjson.toString());
        fillBulkResult(result, root, eligible, true);
        return result;
    }

    @Override
    public KeywordIndexSyncResult deleteBatch(List<String> identifiers) {
        KeywordIndexSyncResult result = baseResult();
        List<String> safeIdentifiers = sanitizeIdentifiers(identifiers);
        result.setProcessed(safeIdentifiers.size());
        if (safeIdentifiers.isEmpty()) {
            result.setSkipped(true);
            result.setMessage("no identifiers to delete");
            return result;
        }
        if (!existsIndex()) {
            result.setSkipped(true);
            result.setMessage("index does not exist");
            return result;
        }
        StringBuilder ndjson = new StringBuilder(safeIdentifiers.size() * 128);
        for (String identifier : safeIdentifiers) {
            try {
                Map<String, Object> meta = new LinkedHashMap<String, Object>();
                meta.put("_index", properties.getSearchEsIndex());
                meta.put("_id", identifier);
                Map<String, Object> action = new LinkedHashMap<String, Object>();
                action.put("delete", meta);
                ndjson.append(objectMapper.writeValueAsString(action)).append('\n');
            } catch (Exception exception) {
                log.warn("keyword delete serialize failed, identifier={}, err={}", identifier, exception.getMessage());
            }
        }
        JsonNode root = exchangeNdjson("/_bulk?refresh=true", ndjson.toString());
        fillBulkResult(result, root, safeIdentifiers.size(), false);
        return result;
    }

    private KeywordIndexSyncResult baseResult() {
        KeywordIndexSyncResult result = new KeywordIndexSyncResult();
        result.setEngine(engine());
        result.setIndexName(properties.getSearchEsIndex());
        return result;
    }

    private boolean existsIndex() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    buildUrl("/" + properties.getSearchEsIndex()),
                    HttpMethod.HEAD,
                    new HttpEntity<String>(buildHeaders(MediaType.APPLICATION_JSON)),
                    String.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException exception) {
            return false;
        } catch (RestClientException exception) {
            log.warn("check elasticsearch index failed: {}", exception.getMessage());
            return false;
        }
    }

    private void deleteIndexIfExists() {
        if (!existsIndex()) {
            return;
        }
        try {
            restTemplate.exchange(
                    buildUrl("/" + properties.getSearchEsIndex()),
                    HttpMethod.DELETE,
                    new HttpEntity<String>(buildHeaders(MediaType.APPLICATION_JSON)),
                    String.class
            );
        } catch (RestClientException exception) {
            log.warn("delete elasticsearch index failed: {}", exception.getMessage());
        }
    }

    private JsonNode exchangeJson(HttpMethod method, String path, Object body, boolean allowNotFound) {
        try {
            HttpHeaders headers = buildHeaders(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<String>(body == null ? null : objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(buildUrl(path), method, entity, String.class);
            String content = response.getBody();
            return SearchTextSupport.isBlank(content) ? objectMapper.createObjectNode() : objectMapper.readTree(content);
        } catch (HttpClientErrorException exception) {
            if (allowNotFound && exception.getStatusCode().value() == 404) {
                return objectMapper.createObjectNode();
            }
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("elasticsearch request failed: " + exception.getMessage(), exception);
        }
    }

    private JsonNode exchangeNdjson(String path, String ndjsonBody) {
        try {
            HttpHeaders headers = buildHeaders(MediaType.parseMediaType("application/x-ndjson"));
            HttpEntity<String> entity = new HttpEntity<String>(ndjsonBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(buildUrl(path), HttpMethod.POST, entity, String.class);
            String content = response.getBody();
            return SearchTextSupport.isBlank(content) ? objectMapper.createObjectNode() : objectMapper.readTree(content);
        } catch (Exception exception) {
            throw new IllegalStateException("elasticsearch bulk request failed: " + exception.getMessage(), exception);
        }
    }

    private HttpHeaders buildHeaders(MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(mediaType, StandardCharsets.UTF_8));
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        String username = SearchTextSupport.trim(properties.getSearchEsUsername());
        if (!username.isEmpty()) {
            headers.setBasicAuth(username, SearchTextSupport.trim(properties.getSearchEsPassword()), StandardCharsets.UTF_8);
        }
        return headers;
    }

    private String buildUrl(String path) {
        String base = SearchTextSupport.trim(properties.getSearchEsUrl());
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private Map<String, Object> buildCreateIndexBody() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        Map<String, Object> settings = new LinkedHashMap<String, Object>();
        settings.put("number_of_shards", Integer.valueOf(1));
        settings.put("number_of_replicas", Integer.valueOf(0));
        body.put("settings", settings);

        Map<String, Object> propertiesMap = new LinkedHashMap<String, Object>();
        propertiesMap.put("identifier", keywordField());
        propertiesMap.put("owner", textFieldWithKeyword());
        propertiesMap.put("industry", keywordField());
        propertiesMap.put("dataCategory", keywordField());
        propertiesMap.put("registrationType", keywordField());
        propertiesMap.put("entityType", keywordField());
        propertiesMap.put("theme", textFieldWithKeyword());
        propertiesMap.put("region", textFieldWithKeyword());
        propertiesMap.put("sourceFilename", textFieldWithKeyword());
        propertiesMap.put("bizKeywordsText", textField());
        propertiesMap.put("name", textFieldWithKeyword());
        propertiesMap.put("description", textField());
        propertiesMap.put("applicationScenario", textField());
        propertiesMap.put("fullText", textField());

        Map<String, Object> mappings = new LinkedHashMap<String, Object>();
        mappings.put("properties", propertiesMap);
        body.put("mappings", mappings);
        return body;
    }

    private Map<String, Object> keywordField() {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "keyword");
        return field;
    }

    private Map<String, Object> textField() {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "text");
        field.put("analyzer", "ik_max_word");
        field.put("search_analyzer", "ik_smart");
        return field;
    }

    private Map<String, Object> textFieldWithKeyword() {
        Map<String, Object> field = textField();
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("keyword", keywordField());
        field.put("fields", fields);
        return field;
    }

    private Map<String, Object> buildQuery(String queryText, List<String> fields, Map<String, String> filters) {
        Map<String, Object> bool = new LinkedHashMap<String, Object>();
        List<Object> should = new ArrayList<Object>();
        should.add(Collections.singletonMap("multi_match", buildMultiMatch(queryText, fields)));
        if (shouldUseField(fields, "identifier")) {
            should.add(Collections.singletonMap("term", Collections.singletonMap("identifier", exactTermQuery(queryText, 12.0))));
            should.add(Collections.singletonMap("prefix", Collections.singletonMap("identifier", exactTermQuery(queryText, 6.0))));
        }
        if (shouldUseField(fields, "theme")) {
            should.add(Collections.singletonMap("match_phrase", Collections.singletonMap("theme", phraseQuery(queryText, 12.0))));
        }
        if (shouldUseField(fields, "region")) {
            should.add(Collections.singletonMap("match_phrase", Collections.singletonMap("region", phraseQuery(queryText, 10.0))));
        }
        if (shouldUseField(fields, "name")) {
            should.add(Collections.singletonMap("match_phrase", Collections.singletonMap("name", phraseQuery(queryText, 8.0))));
        }
        if (shouldUseField(fields, "sourcefilename") || shouldUseField(fields, "source_filename")) {
            should.add(Collections.singletonMap("match_phrase", Collections.singletonMap("sourceFilename", phraseQuery(queryText, 3.0))));
        }
        if (shouldUseField(fields, "bizkeywords") || shouldUseField(fields, "bizkeywordstext")) {
            should.add(Collections.singletonMap("match_phrase", Collections.singletonMap("bizKeywordsText", phraseQuery(queryText, 6.0))));
        }
        if (shouldUseField(fields, "description")) {
            should.add(Collections.singletonMap("match_phrase", Collections.singletonMap("description", phraseQuery(queryText, 4.0))));
        }
        if (shouldUseField(fields, "applicationscenario") || shouldUseField(fields, "application_scenario")) {
            should.add(Collections.singletonMap("match_phrase", Collections.singletonMap("applicationScenario", phraseQuery(queryText, 4.0))));
        }
        bool.put("should", should);
        bool.put("minimum_should_match", Integer.valueOf(1));
        List<Object> filterClauses = buildFilterClauses(filters);
        if (!filterClauses.isEmpty()) {
            bool.put("filter", filterClauses);
        }
        return Collections.<String, Object>singletonMap("bool", bool);
    }

    private Map<String, Object> buildMultiMatch(String queryText, List<String> fields) {
        Map<String, Object> multi = new LinkedHashMap<String, Object>();
        multi.put("query", queryText);
        multi.put("type", "best_fields");
        multi.put("operator", "or");
        multi.put("fields", resolveSearchFields(fields));
        return multi;
    }

    private Map<String, Object> phraseQuery(String queryText, double boost) {
        Map<String, Object> phrase = new LinkedHashMap<String, Object>();
        phrase.put("query", queryText);
        phrase.put("boost", Double.valueOf(boost));
        return phrase;
    }

    private Map<String, Object> exactTermQuery(String queryText, double boost) {
        Map<String, Object> term = new LinkedHashMap<String, Object>();
        term.put("value", queryText);
        term.put("boost", Double.valueOf(boost));
        return term;
    }

    private List<String> resolveSearchFields(List<String> fields) {
        LinkedHashSet<String> resolved = new LinkedHashSet<String>();
        if (fields != null && !fields.isEmpty()) {
            for (String raw : fields) {
                String normalized = SearchTextSupport.safeLower(raw);
                if ("name".equals(normalized)) {
                    resolved.add("name^6");
                } else if ("theme".equals(normalized)) {
                    resolved.add("theme^10");
                } else if ("region".equals(normalized)) {
                    resolved.add("region^8");
                } else if ("identifier".equals(normalized)) {
                    resolved.add("identifier^12");
                } else if ("bizkeywords".equals(normalized) || "bizkeywordstext".equals(normalized)) {
                    resolved.add("bizKeywordsText^5");
                } else if ("description".equals(normalized)) {
                    resolved.add("description^4");
                } else if ("applicationscenario".equals(normalized) || "application_scenario".equals(normalized)) {
                    resolved.add("applicationScenario^4");
                } else if ("sourcefilename".equals(normalized) || "source_filename".equals(normalized)) {
                    resolved.add("sourceFilename^2");
                } else if ("owner".equals(normalized)) {
                    resolved.add("owner^1");
                } else if ("industry".equals(normalized)) {
                    resolved.add("industry");
                } else if ("datacategory".equals(normalized)) {
                    resolved.add("dataCategory");
                } else if ("registrationtype".equals(normalized)) {
                    resolved.add("registrationType");
                } else if ("entitytype".equals(normalized)) {
                    resolved.add("entityType");
                }
            }
        }
        if (resolved.isEmpty()) {
            resolved.add("theme^10");
            resolved.add("region^8");
            resolved.add("name^6");
            resolved.add("bizKeywordsText^5");
            resolved.add("description^4");
            resolved.add("applicationScenario^4");
            resolved.add("sourceFilename^2");
            resolved.add("owner^1");
        }
        return new ArrayList<String>(resolved);
    }

    private List<Object> buildFilterClauses(Map<String, String> filters) {
        List<Object> clauses = new ArrayList<Object>();
        if (filters == null || filters.isEmpty()) {
            return clauses;
        }
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String field = resolveFilterField(entry.getKey());
            String value = SearchTextSupport.trim(entry.getValue());
            if (field == null || value.isEmpty()) {
                continue;
            }
            clauses.add(Collections.singletonMap("term", Collections.singletonMap(field, value)));
        }
        return clauses;
    }

    private String resolveFilterField(String rawField) {
        String normalized = SearchTextSupport.safeLower(rawField);
        if ("industry".equals(normalized)) {
            return "industry";
        }
        if ("identifier".equals(normalized)) {
            return "identifier";
        }
        if ("entitytype".equals(normalized)) {
            return "entityType";
        }
        if ("datacategory".equals(normalized)) {
            return "dataCategory";
        }
        if ("registrationtype".equals(normalized)) {
            return "registrationType";
        }
        return null;
    }

    private boolean shouldUseField(List<String> fields, String target) {
        if (fields == null || fields.isEmpty()) {
            return true;
        }
        String normalizedTarget = SearchTextSupport.safeLower(target);
        for (String field : fields) {
            if (normalizedTarget.equals(SearchTextSupport.safeLower(field))) {
                return true;
            }
        }
        return false;
    }

    private List<SearchDocumentRow> parseSearchHits(JsonNode hitsNode) {
        List<SearchDocumentRow> rows = new ArrayList<SearchDocumentRow>();
        if (hitsNode == null || !hitsNode.isArray()) {
            return rows;
        }
        for (JsonNode hit : hitsNode) {
            JsonNode source = hit.path("_source");
            if (source.isMissingNode()) {
                continue;
            }
            SearchDocumentRow row = new SearchDocumentRow();
            row.setIdentifier(source.path("identifier").asText(""));
            row.setName(source.path("name").asText(""));
            row.setDescription(source.path("description").asText(""));
            row.setApplicationScenario(source.path("applicationScenario").asText(""));
            row.setOwner(source.path("owner").asText(""));
            row.setIndustry(source.path("industry").asText(""));
            row.setDataCategory(source.path("dataCategory").asText(""));
            row.setRegistrationType(source.path("registrationType").asText(""));
            row.setEntityType(source.path("entityType").asText(""));
            row.setTheme(source.path("theme").asText(""));
            row.setRegion(source.path("region").asText(""));
            row.setSourceFilename(source.path("sourceFilename").asText(""));
            row.setBizKeywordsText(source.path("bizKeywordsText").asText(""));
            row.setScore(hit.path("_score").asDouble(0.0));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> toSource(SearchDocumentRow row) {
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("identifier", row.getIdentifier());
        source.put("name", safe(row.getName()));
        source.put("description", safe(row.getDescription()));
        source.put("applicationScenario", safe(row.getApplicationScenario()));
        source.put("owner", safe(row.getOwner()));
        source.put("industry", safe(row.getIndustry()));
        source.put("dataCategory", safe(row.getDataCategory()));
        source.put("registrationType", safe(row.getRegistrationType()));
        source.put("entityType", safe(row.getEntityType()));
        source.put("theme", safe(row.getTheme()));
        source.put("region", safe(row.getRegion()));
        source.put("sourceFilename", safe(row.getSourceFilename()));
        source.put("bizKeywordsText", safe(row.getBizKeywordsText()));
        source.put("fullText", textBuilder.build(row));
        return source;
    }

    private void fillBulkResult(KeywordIndexSyncResult result, JsonNode root, int expected, boolean indexOperation) {
        result.setProcessed(expected);
        boolean hasErrors = root != null && root.path("errors").asBoolean(false);
        int failures = 0;
        JsonNode items = root == null ? null : root.path("items");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                JsonNode op = item.path(indexOperation ? "index" : "delete");
                int status = op.path("status").asInt(500);
                if (status >= 200 && status < 300) {
                    continue;
                }
                if (!indexOperation && status == 404) {
                    continue;
                }
                failures++;
            }
        }
        result.setFailed(failures);
        if (indexOperation) {
            result.setIndexed(Math.max(0, expected - failures));
        } else {
            result.setDeleted(Math.max(0, expected - failures));
        }
        result.setMessage(hasErrors ? "bulk request completed with partial failures" : "ok");
    }

    private String buildQueryText(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            String normalized = SearchTextSupport.trim(token);
            if (normalized.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(normalized);
        }
        return builder.toString();
    }

    private List<String> sanitizeIdentifiers(List<String> identifiers) {
        LinkedHashSet<String> unique = new LinkedHashSet<String>();
        if (identifiers == null) {
            return new ArrayList<String>();
        }
        for (String identifier : identifiers) {
            String value = SearchTextSupport.trim(identifier);
            if (!value.isEmpty()) {
                unique.add(value);
            }
        }
        return new ArrayList<String>(unique);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(BackendV2Properties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, properties == null ? 3000 : properties.getSearchEsConnectTimeoutMs()));
        factory.setReadTimeout(Math.max(1000, properties == null ? 5000 : properties.getSearchEsRequestTimeoutMs()));
        return factory;
    }
}
