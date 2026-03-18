package com.dris.v2.backend.config;

import com.dris.sdk.next.api.DrisSdk;
import com.dris.sdk.next.api.DrisSdkFactory;
import com.dris.sdk.next.infrastructure.IdentifierCredentialGenerator;
import com.dris.sdk.next.infrastructure.TimeProvider;
import com.dris.sdk.next.infrastructure.TransactionRunner;
import com.dris.sdk.next.repository.DigitalObjectRepository;
import com.dris.sdk.next.repository.RegistrationDictionary;
import com.dris.v2.backend.vector.AnnVectorIndexGateway;
import com.dris.v2.backend.vector.ChromaHttpVectorIndexGateway;
import com.dris.v2.backend.vector.EmbeddingProvider;
import com.dris.v2.backend.vector.HashTextEmbeddingProvider;
import com.dris.v2.backend.vector.JdbcVectorIndexGateway;
import com.dris.v2.backend.vector.OpenAiCompatibleEmbeddingProvider;
import com.dris.v2.backend.vector.VectorIndexGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(BackendV2Properties.class)
public class BackendV2Configuration {
    @Bean
    public DrisSdk drisSdk(
            DigitalObjectRepository digitalObjectRepository,
            RegistrationDictionary registrationDictionary,
            TransactionRunner transactionRunner,
            TimeProvider timeProvider,
            IdentifierCredentialGenerator identifierCredentialGenerator
    ) {
        return DrisSdkFactory.compose(
                digitalObjectRepository,
                registrationDictionary,
                transactionRunner,
                timeProvider,
                identifierCredentialGenerator
        );
    }

    @Bean
    public EmbeddingProvider embeddingProvider(BackendV2Properties properties) {
        String provider = properties == null || properties.getEmbeddingProvider() == null
                ? ""
                : properties.getEmbeddingProvider().trim();
        if ("openai_compatible".equalsIgnoreCase(provider)) {
            return new OpenAiCompatibleEmbeddingProvider(
                    properties.getEmbeddingApiBaseUrl(),
                    properties.getEmbeddingModel(),
                    properties.getEmbeddingDimensions(),
                    properties.getEmbeddingRequestTimeoutMs(),
                    properties.getEmbeddingApiKey(),
                    properties.getEmbeddingApiKeyHeader()
            );
        }
        return new HashTextEmbeddingProvider(properties == null ? 128 : properties.getEmbeddingDimensions());
    }

    @Bean
    public VectorIndexGateway vectorIndexGateway(
            DataSource dataSource,
            BackendV2Properties properties,
            EmbeddingProvider embeddingProvider
    ) {
        String engine = properties == null || properties.getVectorEngine() == null
                ? ""
                : properties.getVectorEngine().trim();
        if ("ann_lsh".equalsIgnoreCase(engine)) {
            return new AnnVectorIndexGateway(dataSource, embeddingProvider);
        }
        if ("chroma_http".equalsIgnoreCase(engine)) {
            return new ChromaHttpVectorIndexGateway(
                    properties.getVectorServerUrl(),
                    properties.getVectorCollection(),
                    properties.getVectorTenant(),
                    properties.getVectorDatabase(),
                    properties.getVectorRequestTimeoutMs(),
                    properties.getVectorServerApiKey(),
                    properties.getVectorServerApiKeyHeader(),
                    embeddingProvider
            );
        }
        return new JdbcVectorIndexGateway(dataSource, embeddingProvider);
    }
}