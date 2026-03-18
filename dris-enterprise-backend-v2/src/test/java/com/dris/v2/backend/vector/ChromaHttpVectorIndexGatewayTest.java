package com.dris.v2.backend.vector;

import com.dris.sdk.next.domain.IdentifierMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ChromaHttpVectorIndexGatewayTest {
    @Test
    void upsertBatch_shouldCreateCollectionAndSendDocuments() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ChromaHttpVectorIndexGateway gateway = new ChromaHttpVectorIndexGateway(
                restTemplate,
                "http://127.0.0.1:8000/api/v2",
                "dris_metadata",
                "default_tenant",
                "default_database",
                "",
                "X-Chroma-Token",
                new HashTextEmbeddingProvider(128)
        );

        server.expect(requestTo("http://127.0.0.1:8000/api/v2/tenants/default_tenant/databases/default_database/collections/dris_metadata"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{}"));

        server.expect(requestTo("http://127.0.0.1:8000/api/v2/tenants/default_tenant/databases/default_database/collections"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.name").value("dris_metadata"))
                .andExpect(jsonPath("$.get_or_create").value(true))
                .andRespond(withSuccess(collectionJson("col-1"), MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://127.0.0.1:8000/api/v2/tenants/default_tenant/databases/default_database/collections/col-1/upsert"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ids[0]").value("id-1"))
                .andExpect(jsonPath("$.documents[0]").value(org.hamcrest.Matchers.containsString("name: sample meter")))
                .andExpect(jsonPath("$.metadatas[0].industry").value("energy"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        IdentifierMetadata metadata = new IdentifierMetadata();
        metadata.setIdentifier("id-1");
        metadata.setName("sample meter");
        metadata.setDescription("smart meter dataset");
        metadata.setIndustry("energy");
        metadata.setApplicationScenario("lab");

        assertTrue(gateway.upsertBatch(Collections.singletonList(metadata)));
        server.verify();
    }

    @Test
    void search_shouldConvertDistancesToScores() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ChromaHttpVectorIndexGateway gateway = new ChromaHttpVectorIndexGateway(
                restTemplate,
                "http://127.0.0.1:8000/api/v2",
                "dris_metadata",
                "default_tenant",
                "default_database",
                "",
                "X-Chroma-Token",
                new HashTextEmbeddingProvider(128)
        );

        server.expect(requestTo("http://127.0.0.1:8000/api/v2/tenants/default_tenant/databases/default_database/collections/dris_metadata"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(collectionJson("col-2"), MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://127.0.0.1:8000/api/v2/tenants/default_tenant/databases/default_database/collections/col-2/query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.n_results").value(2))
                .andRespond(withSuccess("{\"ids\":[[\"id-1\",\"id-2\"]],\"distances\":[[0.05,0.30]],\"metadatas\":[[{\"name\":\"meter\"},{\"name\":\"grid\"}]],\"documents\":[[\"doc-1\",\"doc-2\"]],\"included\":[\"metadatas\",\"documents\",\"distances\"]}", MediaType.APPLICATION_JSON));

        List<VectorSearchHit> hits = gateway.search("energy meter", 2);

        assertEquals(2, hits.size());
        assertEquals("id-1", hits.get(0).getIdentifier());
        assertEquals(0.95d, hits.get(0).getScore(), 0.000001d);
        assertEquals(0.70d, hits.get(1).getScore(), 0.000001d);
        server.verify();
    }

    @Test
    void deleteBatch_whenCollectionMissing_shouldReturnTrue() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ChromaHttpVectorIndexGateway gateway = new ChromaHttpVectorIndexGateway(
                restTemplate,
                "http://127.0.0.1:8000/api/v2",
                "dris_metadata",
                "default_tenant",
                "default_database",
                "",
                "X-Chroma-Token",
                new HashTextEmbeddingProvider(128)
        );

        server.expect(requestTo("http://127.0.0.1:8000/api/v2/tenants/default_tenant/databases/default_database/collections/dris_metadata"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertTrue(gateway.deleteBatch(Collections.singletonList("id-1")));
        server.verify();
    }

    private static String collectionJson(String collectionId) {
        return "{"
                + "\"id\":\"" + collectionId + "\","
                + "\"name\":\"dris_metadata\","
                + "\"configuration_json\":{},"
                + "\"schema\":null,"
                + "\"metadata\":{\"hnsw:space\":\"cosine\"},"
                + "\"dimension\":128,"
                + "\"tenant\":\"default_tenant\","
                + "\"database\":\"default_database\","
                + "\"version\":0,"
                + "\"log_position\":0"
                + "}";
    }
}