package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.system.KeywordIndexSyncResult;
import com.dris.v2.backend.dao.IdentifierSearchDao;
import com.dris.v2.backend.dao.SearchDocumentRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service("mysqlKeywordSearchGateway")
public class MysqlKeywordSearchGateway implements KeywordSearchGateway {
    private final IdentifierSearchDao identifierSearchDao;

    public MysqlKeywordSearchGateway(IdentifierSearchDao identifierSearchDao) {
        this.identifierSearchDao = identifierSearchDao;
    }

    @Override
    public String engine() {
        return "mysql_like";
    }

    @Override
    public List<SearchDocumentRow> search(List<String> tokens, List<String> fields, Map<String, String> filters, int limit, int offset) {
        return identifierSearchDao.searchFuzzy(tokens, fields, filters, limit, offset);
    }

    @Override
    public KeywordIndexSyncResult initializeIndex() {
        return skipped("mysql-like search does not require external index");
    }

    @Override
    public KeywordIndexSyncResult rebuildIndex(List<SearchDocumentRow> documents) {
        return skipped("mysql-like search does not require external index");
    }

    @Override
    public KeywordIndexSyncResult upsertBatch(List<SearchDocumentRow> documents) {
        return skipped("mysql-like search does not require external index");
    }

    @Override
    public KeywordIndexSyncResult deleteBatch(List<String> identifiers) {
        return skipped("mysql-like search does not require external index");
    }

    private KeywordIndexSyncResult skipped(String message) {
        KeywordIndexSyncResult result = new KeywordIndexSyncResult();
        result.setEngine(engine());
        result.setSkipped(true);
        result.setMessage(message);
        return result;
    }
}
