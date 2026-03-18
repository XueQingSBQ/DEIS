package com.dris.v2.backend.vector;

import com.dris.sdk.next.domain.IdentifierMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChromaHttpVectorIndexGateway implements VectorIndexGateway {
    private static final Logger log = LoggerFactory.getLogger(ChromaHttpVectorIndexGateway.class);
    private static final int DEFAULT_DIMENSIONS = 128;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final String DEFAULT_COLLECTION = "dris_metadata";
    private static final String DEFAULT_TENANT = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";
    private static final String DEFAULT_API_KEY_HEADER = "X-Chroma-Token";

    private final RestOperations restOperations;
    private final ObjectMapper objectMapper;
    private final EmbeddingProvider embeddingProvider;
    private final VectorMetadataDocumentBuilder documentBuilder;
    private final String serverUrl;
    private final String collectionName;
    private final String tenant;
    private final String database;
    private final String apiKey;
    private final String apiKeyHeader;
    private final Object collectionLock = new Object();
    private volatile String collectionId;

    public ChromaHttpVectorIndexGateway(String serverUrl) {
        this(serverUrl, DEFAULT_COLLECTION, DEFAULT_TENANT, DEFAULT_DATABASE, DEFAULT_TIMEOUT_MS, "", DEFAULT_API_KEY_HEADER);
    }

    public ChromaHttpVectorIndexGateway(
            String serverUrl,
            String collectionName,
            String tenant,
            String database,
            int requestTimeoutMs,
            String apiKey,
            String apiKeyHeader
    ) {
        this(serverUrl, collectionName, tenant, database, requestTimeoutMs, apiKey, apiKeyHeader,
                new HashTextEmbeddingProvider(DEFAULT_DIMENSIONS));
    }

    public ChromaHttpVectorIndexGateway(
            String serverUrl,
            String collectionName,
            String tenant,
            String database,
            int requestTimeoutMs,
            String apiKey,
            String apiKeyHeader,
            EmbeddingProvider embeddingProvider
    ) {
        this(createRestTemplate(requestTimeoutMs), serverUrl, collectionName, tenant, database,
                apiKey, apiKeyHeader, embeddingProvider);
    }

    ChromaHttpVectorIndexGateway(
            RestOperations restOperations,
            String serverUrl,
            String collectionName,
            String tenant,
            String database,
            String apiKey,
            String apiKeyHeader,
            EmbeddingProvider embeddingProvider
    ) {
        this.restOperations = restOperations;
        this.objectMapper = new ObjectMapper();
        this.embeddingProvider = embeddingProvider == null
                ? new HashTextEmbeddingProvider(DEFAULT_DIMENSIONS)
                : embeddingProvider;
        this.documentBuilder = new VectorMetadataDocumentBuilder();
        this.serverUrl = normalizeServerUrl(serverUrl);
        this.collectionName = normalizeOrDefault(collectionName, DEFAULT_COLLECTION);
        this.tenant = normalizeOrDefault(tenant, DEFAULT_TENANT);
        this.database = normalizeOrDefault(database, DEFAULT_DATABASE);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiKeyHeader = normalizeOrDefault(apiKeyHeader, DEFAULT_API_KEY_HEADER);
    }

    @Override
    public boolean upsertBatch(List<IdentifierMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return true;
        }
        List<String> ids = new ArrayList<String>();
        List<Map<String, Object>> metadatas = new ArrayList<Map<String, Object>>();
        List<String> documents = new ArrayList<String>();
        for (IdentifierMetadata metadata : metadataList) {
            String identifier = normalize(metadata == null ? null : metadata.getIdentifier());
            if (identifier.isEmpty()) {
                continue;
            }
            ids.add(identifier);
            metadatas.add(documentBuilder.buildMetadata(metadata));
            String document = documentBuilder.buildText(metadata);
            documents.add(document.isEmpty() ? identifier : document);
        }
        if (ids.isEmpty()) {
            return true;
        }

        List<double[]> vectors = embeddingProvider.embedTexts(documents);
        if (vectors == null || vectors.size() != documents.size()) {
            return false;
        }

        LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("ids", ids);
        payload.put("embeddings", toEmbeddingPayload(vectors));
        payload.put("metadatas", metadatas);
        payload.put("documents", documents);
        payload.put("uris", null);

        try {
            String currentCollectionId = ensureCollectionId(true);
            postUpsert(currentCollectionId, payload);
            return true;
        } catch (HttpClientErrorException.NotFound notFound) {
            clearCollectionId();
            try {
                String currentCollectionId = ensureCollectionId(true);
                postUpsert(currentCollectionId, payload);
                return true;
            } catch (RuntimeException retryException) {
                log.warn("chroma upsert retry failed: {}", retryException.getMessage());
                return false;
            }
        } catch (RuntimeException exception) {
            log.warn("chroma upsert failed: {}", exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteBatch(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return true;
        }
        List<String> ids = new ArrayList<String>();
        for (String identifier : identifiers) {
            String normalized = normalize(identifier);
            if (!normalized.isEmpty()) {
                ids.add(normalized);
            }
        }
        if (ids.isEmpty()) {
            return true;
        }
        try {
            String currentCollectionId = ensureCollectionId(false);
            if (currentCollectionId.isEmpty()) {
                return true;
            }
            LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("ids", ids);
            request(HttpMethod.POST, collectionPath(currentCollectionId, "/delete"), payload);
            return true;
        } catch (HttpClientErrorException.NotFound notFound) {
            clearCollectionId();
            return true;
        } catch (RuntimeException exception) {
            log.warn("chroma delete failed: {}", exception.getMessage());
            return false;
        }
    }

    @Override
    public List<VectorSearchHit> search(String queryText, int topK) {
        int limit = Math.max(0, topK);
        if (limit <= 0 || normalize(queryText).isEmpty()) {
            return Collections.<VectorSearchHit>emptyList();
        }
        try {
            String currentCollectionId = ensureCollectionId(false);
            if (currentCollectionId.isEmpty()) {
                return Collections.<VectorSearchHit>emptyList();
            }
            LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("query_embeddings", Collections.singletonList(toEmbeddingPayload(embeddingProvider.embedText(queryText))));
            payload.put("n_results", Integer.valueOf(limit));
            payload.put("where", null);
            payload.put("where_document", null);
            payload.put("include", Arrays.asList("metadatas", "documents", "distances"));
            JsonNode response = request(HttpMethod.POST, collectionPath(currentCollectionId, "/query"), payload);
            return parseSearchHits(response);
        } catch (HttpClientErrorException.NotFound notFound) {
            clearCollectionId();
            return Collections.<VectorSearchHit>emptyList();
        } catch (RuntimeException exception) {
            log.warn("chroma search failed: {}", exception.getMessage());
            return Collections.<VectorSearchHit>emptyList();
        }
    }

    private void postUpsert(String currentCollectionId, Map<String, Object> payload) {
        request(HttpMethod.POST, collectionPath(currentCollectionId, "/upsert"), payload);
    }

    private List<VectorSearchHit> parseSearchHits(JsonNode response) {
        if (response == null) {
            return Collections.<VectorSearchHit>emptyList();
        }
        JsonNode ids = response.path("ids");
        if (!ids.isArray() || ids.size() == 0 || !ids.get(0).isArray()) {
            return Collections.<VectorSearchHit>emptyList();
        }
        JsonNode distances = response.path("distances");
        JsonNode firstIds = ids.get(0);
        JsonNode firstDistances = distances.isArray() && distances.size() > 0 ? distances.get(0) : null;
        List<VectorSearchHit> hits = new ArrayList<VectorSearchHit>();
        for (int index = 0; index < firstIds.size(); index++) {
            JsonNode identifierNode = firstIds.get(index);
            String identifier = normalize(identifierNode == null ? null : identifierNode.asText());
            if (identifier.isEmpty()) {
                continue;
            }
            double score = 0.0;
            if (firstDistances != null && firstDistances.isArray() && index < firstDistances.size()) {
                score = toScore(firstDistances.get(index).asDouble());
            }
            hits.add(new VectorSearchHit(identifier, score));
        }
        return hits;
    }

    private double toScore(double distance) {
        double score = 1.0 - distance;
        if (score < 0.0) {
            return 0.0;
        }
        if (score > 1.0) {
            return 1.0;
        }
        return score;
    }

    private String ensureCollectionId(boolean createIfMissing) {
        String cached = normalize(collectionId);
        if (!cached.isEmpty()) {
            return cached;
        }
        synchronized (collectionLock) {
            cached = normalize(collectionId);
            if (!cached.isEmpty()) {
                return cached;
            }
            String resolved = fetchCollectionId();
            if (resolved.isEmpty() && createIfMissing) {
                resolved = createCollection();
            }
            collectionId = resolved;
            return normalize(resolved);
        }
    }

    private String fetchCollectionId() {
        try {
            JsonNode response = request(HttpMethod.GET, collectionLookupPath(), null);
            return normalize(readText(response, "id"));
        } catch (HttpClientErrorException.NotFound notFound) {
            return "";
        }
    }

    private String createCollection() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", collectionName);
        payload.put("metadata", Collections.singletonMap("hnsw:space", "cosine"));
        payload.put("get_or_create", Boolean.TRUE);
        JsonNode response = request(HttpMethod.POST, collectionsPath(), payload);
        return normalize(readText(response, "id"));
    }

    private JsonNode request(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        applyApiKey(headers);
        HttpEntity<?> entity = body == null ? new HttpEntity<Object>(headers) : new HttpEntity<Object>(body, headers);
        ResponseEntity<String> response = restOperations.exchange(serverUrl + path, method, entity, String.class);
        String payload = response.getBody();
        if (payload == null || payload.trim().isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(payload);
        } catch (IOException exception) {
            throw new IllegalStateException("invalid chroma response", exception);
        }
    }

    private void applyApiKey(HttpHeaders headers) {
        if (headers == null || apiKey.isEmpty()) {
            return;
        }
        if ("Authorization".equalsIgnoreCase(apiKeyHeader)) {
            headers.setBearerAuth(apiKey);
            return;
        }
        headers.set(apiKeyHeader, apiKey);
    }

    private String collectionLookupPath() {
        return baseCollectionPrefix() + "/collections/" + encode(collectionName);
    }

    private String collectionsPath() {
        return baseCollectionPrefix() + "/collections";
    }

    private String collectionPath(String currentCollectionId, String suffix) {
        return baseCollectionPrefix() + "/collections/" + encode(currentCollectionId) + suffix;
    }

    private String baseCollectionPrefix() {
        return "/tenants/" + encode(tenant) + "/databases/" + encode(database);
    }

    private String encode(String value) {
        return UriUtils.encodePathSegment(normalize(value), StandardCharsets.UTF_8);
    }

    private String readText(JsonNode node, String field) {
        if (node == null || field == null || field.trim().isEmpty()) {
            return "";
        }
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? "" : child.asText("");
    }

    private void clearCollectionId() {
        synchronized (collectionLock) {
            collectionId = "";
        }
    }

    private List<List<Double>> toEmbeddingPayload(List<double[]> vectors) {
        List<List<Double>> payload = new ArrayList<List<Double>>(vectors == null ? 0 : vectors.size());
        if (vectors == null) {
            return payload;
        }
        for (double[] vector : vectors) {
            payload.add(toEmbeddingPayload(vector));
        }
        return payload;
    }

    private List<Double> toEmbeddingPayload(double[] vector) {
        List<Double> out = new ArrayList<Double>(vector == null ? 0 : vector.length);
        if (vector == null) {
            return out;
        }
        for (double value : vector) {
            out.add(Double.valueOf(value));
        }
        return out;
    }

    private static RestTemplate createRestTemplate(int requestTimeoutMs) {
        int timeout = requestTimeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : requestTimeoutMs;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private String normalizeServerUrl(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("dris.v2.vector-server-url is required when vector-engine=chroma_http");
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? defaultValue : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}