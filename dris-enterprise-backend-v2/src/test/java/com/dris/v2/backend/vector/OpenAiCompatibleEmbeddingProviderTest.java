package com.dris.v2.backend.vector;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleEmbeddingProviderTest {
    @Test
    void embedTexts_shouldCallEmbeddingsApiAndKeepOriginalOrder() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OpenAiCompatibleEmbeddingProvider provider = new OpenAiCompatibleEmbeddingProvider(
                restTemplate,
                "http://127.0.0.1:8100/v1",
                "text-embedding-3-small",
                3,
                "test-key",
                "Authorization"
        );

        server.expect(requestTo("http://127.0.0.1:8100/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.input[0]").value("first text"))
                .andExpect(jsonPath("$.input[1]").value("second text"))
                .andRespond(withSuccess("{\"data\":[{\"index\":1,\"embedding\":[0.4,0.5,0.6]},{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}", MediaType.APPLICATION_JSON));

        List<double[]> outputs = provider.embedTexts(Arrays.asList("first text", "second text"));

        assertEquals(2, outputs.size());
        assertArrayEquals(new double[]{0.1d, 0.2d, 0.3d}, outputs.get(0), 0.000001d);
        assertArrayEquals(new double[]{0.4d, 0.5d, 0.6d}, outputs.get(1), 0.000001d);
        assertEquals(3, provider.dimensions());
        server.verify();
    }
}