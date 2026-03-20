package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.contract.system.KeywordIndexSyncResult;
import com.dris.v2.backend.dao.SearchDocumentRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Primary
@Service
public class RoutingKeywordSearchGateway implements KeywordSearchGateway {
    private static final Logger log = LoggerFactory.getLogger(RoutingKeywordSearchGateway.class);

    private final BackendV2Properties properties;
    private final KeywordSearchGateway mysqlGateway;
    private final KeywordSearchGateway elasticsearchGateway;

    public RoutingKeywordSearchGateway(
            BackendV2Properties properties,
            @Qualifier("mysqlKeywordSearchGateway") KeywordSearchGateway mysqlGateway,
            @Qualifier("elasticsearchKeywordSearchGateway") KeywordSearchGateway elasticsearchGateway
    ) {
        this.properties = properties == null ? new BackendV2Properties() : properties;
        this.mysqlGateway = mysqlGateway;
        this.elasticsearchGateway = elasticsearchGateway;
    }

    @Override
    public String engine() {
        return useElasticsearch() ? elasticsearchGateway.engine() : mysqlGateway.engine();
    }

    @Override
    public List<SearchDocumentRow> search(List<String> tokens, List<String> fields, Map<String, String> filters, int limit, int offset) {
        if (!useElasticsearch()) {
            return mysqlGateway.search(tokens, fields, filters, limit, offset);
        }
        try {
            return elasticsearchGateway.search(tokens, fields, filters, limit, offset);
        } catch (RuntimeException exception) {
            log.warn("elasticsearch keyword search failed, fallback to mysql-like search: {}", exception.getMessage());
            return mysqlGateway.search(tokens, fields, filters, limit, offset);
        }
    }

    @Override
    public KeywordIndexSyncResult initializeIndex() {
        return useElasticsearch() ? elasticsearchGateway.initializeIndex() : mysqlGateway.initializeIndex();
    }

    @Override
    public KeywordIndexSyncResult rebuildIndex(List<SearchDocumentRow> documents) {
        return useElasticsearch() ? elasticsearchGateway.rebuildIndex(documents) : mysqlGateway.rebuildIndex(documents);
    }

    @Override
    public KeywordIndexSyncResult upsertBatch(List<SearchDocumentRow> documents) {
        return useElasticsearch() ? elasticsearchGateway.upsertBatch(documents) : mysqlGateway.upsertBatch(documents);
    }

    @Override
    public KeywordIndexSyncResult deleteBatch(List<String> identifiers) {
        return useElasticsearch() ? elasticsearchGateway.deleteBatch(identifiers) : mysqlGateway.deleteBatch(identifiers);
    }

    private boolean useElasticsearch() {
        return "elasticsearch".equalsIgnoreCase(SearchTextSupport.trim(properties.getSearchEngine()));
    }
}
