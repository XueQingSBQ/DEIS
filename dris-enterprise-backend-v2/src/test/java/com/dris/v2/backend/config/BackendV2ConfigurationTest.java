package com.dris.v2.backend.config;

import com.dris.v2.backend.vector.AnnVectorIndexGateway;
import com.dris.v2.backend.vector.ChromaHttpVectorIndexGateway;
import com.dris.v2.backend.vector.EmbeddingProvider;
import com.dris.v2.backend.vector.HashTextEmbeddingProvider;
import com.dris.v2.backend.vector.JdbcVectorIndexGateway;
import com.dris.v2.backend.vector.OpenAiCompatibleEmbeddingProvider;
import com.dris.v2.backend.vector.VectorIndexGateway;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BackendV2ConfigurationTest {

    @Test
    void embeddingProvider_default_shouldUseHashProvider() {
        BackendV2Configuration configuration = new BackendV2Configuration();
        BackendV2Properties properties = new BackendV2Properties();

        EmbeddingProvider provider = configuration.embeddingProvider(properties);

        assertTrue(provider instanceof HashTextEmbeddingProvider);
    }

    @Test
    void embeddingProvider_openAiCompatible_shouldUseOpenAiProvider() {
        BackendV2Configuration configuration = new BackendV2Configuration();
        BackendV2Properties properties = new BackendV2Properties();
        properties.setEmbeddingProvider("openai_compatible");
        properties.setEmbeddingApiBaseUrl("http://127.0.0.1:8100/v1");
        properties.setEmbeddingModel("text-embedding-3-small");

        EmbeddingProvider provider = configuration.embeddingProvider(properties);

        assertTrue(provider instanceof OpenAiCompatibleEmbeddingProvider);
    }

    @Test
    void vectorIndexGateway_annLsh_shouldUseAnnGateway() {
        BackendV2Configuration configuration = new BackendV2Configuration();
        BackendV2Properties properties = new BackendV2Properties();
        properties.setVectorEngine("ann_lsh");
        DataSource dataSource = mock(DataSource.class);
        EmbeddingProvider provider = configuration.embeddingProvider(properties);

        VectorIndexGateway gateway = configuration.vectorIndexGateway(dataSource, properties, provider);

        assertTrue(gateway instanceof AnnVectorIndexGateway);
    }

    @Test
    void vectorIndexGateway_chromaHttp_shouldUseChromaGateway() {
        BackendV2Configuration configuration = new BackendV2Configuration();
        BackendV2Properties properties = new BackendV2Properties();
        properties.setVectorEngine("chroma_http");
        properties.setVectorServerUrl("http://127.0.0.1:8000/api/v2");
        DataSource dataSource = mock(DataSource.class);
        EmbeddingProvider provider = configuration.embeddingProvider(properties);

        VectorIndexGateway gateway = configuration.vectorIndexGateway(dataSource, properties, provider);

        assertTrue(gateway instanceof ChromaHttpVectorIndexGateway);
    }

    @Test
    void vectorIndexGateway_unknown_shouldFallbackToJdbcGateway() {
        BackendV2Configuration configuration = new BackendV2Configuration();
        BackendV2Properties properties = new BackendV2Properties();
        properties.setVectorEngine("unknown_engine");
        DataSource dataSource = mock(DataSource.class);
        EmbeddingProvider provider = configuration.embeddingProvider(properties);

        VectorIndexGateway gateway = configuration.vectorIndexGateway(dataSource, properties, provider);

        assertTrue(gateway instanceof JdbcVectorIndexGateway);
    }
}