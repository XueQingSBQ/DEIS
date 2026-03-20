package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.dao.SearchDocumentRow;
import org.springframework.stereotype.Component;

@Component
public class SearchResultFactory {
    public SearchResult fromRow(SearchDocumentRow row) {
        SearchResult result = new SearchResult();
        if (row == null) {
            return result;
        }
        result.setIdentifier(row.getIdentifier());
        result.setName(row.getName());
        result.setDescription(row.getDescription());
        result.setOwner(row.getOwner());
        result.setIndustry(row.getIndustry());
        result.setDataCategory(row.getDataCategory());
        result.setRegistrationType(row.getRegistrationType());
        result.setEntityType(row.getEntityType());
        result.setUpdateTime(row.getUpdateTime());
        result.setComponentType(row.getComponentType());
        result.setStorageType(row.getStorageType());
        result.setAccessMethod(row.getAccessMethod());
        result.setContentType(row.getContentType());
        result.setFormat(row.getFormat());
        result.setSource("local");
        result.setKeywordScore(0.0);
        result.setSemanticScore(0.0);
        result.setLocalScore(0.0);
        result.setFusionScore(0.0);
        result.setDisplayRank(0);
        result.setFinalScore(0.0);
        return result;
    }
}
