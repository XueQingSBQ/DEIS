package com.dris.v2.backend.vector;

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
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingProvider.class);
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final String DEFAULT_API_KEY_HEADER = "Authorization";

    private final RestOperations restOperations;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final String apiKeyHeader;
    private final int configuredDimensions;
    private volatile int actualDimensions;

    public OpenAiCompatibleEmbeddingProvider(
            String baseUrl,
            String model,
            int configuredDimensions,
            int requestTimeoutMs,
            String apiKey,
            String apiKeyHeader
    ) {
        this(createRestTemplate(requestTimeoutMs), baseUrl, model, configuredDimensions, apiKey, apiKeyHeader);
    }

    OpenAiCompatibleEmbeddingProvider(
            RestOperations restOperations,
            String baseUrl,
            String model,
            int configuredDimensions,
            String apiKey,
            String apiKeyHeader
    ) {
        this.restOperations = restOperations;
        this.objectMapper = new ObjectMapper();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.model = normalizeRequired(model, "dris.v2.embedding-model is required when embedding-provider=openai_compatible");
        this.configuredDimensions = Math.max(0, configuredDimensions);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiKeyHeader = normalizeApiKeyHeader(apiKeyHeader);
    }

    @Override
    public int dimensions() {
        int current = actualDimensions;
        return current > 0 ? current : configuredDimensions;
    }

    @Override
    public List<double[]> embedTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.<double[]>emptyList();
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("model", model);
        payload.put("input", sanitizeInputs(texts));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        applyAuth(headers);

        ResponseEntity<String> response = restOperations.exchange(
                baseUrl + "/embeddings",
                HttpMethod.POST,
                new HttpEntity<Object>(payload, headers),
                String.class
        );
        return parseEmbeddings(response.getBody(), texts.size());
    }

    private List<String> sanitizeInputs(List<String> texts) {
        List<String> inputs = new ArrayList<String>(texts.size());
        for (String text : texts) {
            inputs.add(text == null ? "" : text);
        }
        return inputs;
    }

    private List<double[]> parseEmbeddings(String responseBody, int expectedSize) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return Collections.<double[]>emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return Collections.<double[]>emptyList();
            }
            List<IndexedEmbedding> indexed = new ArrayList<IndexedEmbedding>(data.size());
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.path("embedding");
                if (!embeddingNode.isArray()) {
                    continue;
                }
                double[] vector = new double[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = embeddingNode.get(i).asDouble();
                }
                indexed.add(new IndexedEmbedding(item.path("index").asInt(indexed.size()), vector));
            }
            indexed.sort(new Comparator<IndexedEmbedding>() {
                @Override
                public int compare(IndexedEmbedding left, IndexedEmbedding right) {
                    return Integer.compare(left.index, right.index);
                }
            });
            List<double[]> outputs = new ArrayList<double[]>(indexed.size());
            for (IndexedEmbedding embedding : indexed) {
                outputs.add(embedding.vector);
            }
            if (!outputs.isEmpty()) {
                actualDimensions = outputs.get(0).length;
                if (configuredDimensions > 0 && configuredDimensions != actualDimensions) {
                    log.warn("embedding dimensions mismatch, configured={}, actual={}", configuredDimensions, actualDimensions);
                }
            }
            if (outputs.size() != expectedSize) {
                log.warn("embedding response size mismatch, expected={}, actual={}", expectedSize, outputs.size());
            }
            return outputs;
        } catch (IOException exception) {
            throw new IllegalStateException("invalid embedding response", exception);
        }
    }

    private void applyAuth(HttpHeaders headers) {
        if (headers == null || apiKey.isEmpty()) {
            return;
        }
        if ("Authorization".equalsIgnoreCase(apiKeyHeader)) {
            headers.setBearerAuth(apiKey);
            return;
        }
        headers.set(apiKeyHeader, apiKey);
    }

    private static RestTemplate createRestTemplate(int requestTimeoutMs) {
        int timeout = requestTimeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : requestTimeoutMs;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private String normalizeBaseUrl(String value) {
        String normalized = normalizeRequired(value, "dris.v2.embedding-api-base-url is required when embedding-provider=openai_compatible");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeApiKeyHeader(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? DEFAULT_API_KEY_HEADER : normalized;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static final class IndexedEmbedding {
        private final int index;
        private final double[] vector;

        private IndexedEmbedding(int index, double[] vector) {
            this.index = index;
            this.vector = vector == null ? new double[0] : vector;
        }
    }
}