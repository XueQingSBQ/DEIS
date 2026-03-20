package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.search.SearchMode;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.dao.SearchDocumentRow;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SearchScoreCalculator {
    private final SearchDocumentTextBuilder textBuilder;

    public SearchScoreCalculator(SearchDocumentTextBuilder textBuilder) {
        this.textBuilder = textBuilder;
    }

    public double computeKeywordScore(SearchQueryAnalysis analysis, List<String> fields, SearchDocumentRow row) {
        if (row == null) {
            return 0.0;
        }
        Set<String> fieldSet = resolveFieldSet(fields);
        String name = SearchTextSupport.safeLower(row.getName());
        String desc = SearchTextSupport.safeLower(row.getDescription());
        String app = SearchTextSupport.safeLower(row.getApplicationScenario());
        String id = SearchTextSupport.safeLower(row.getIdentifier());
        String owner = SearchTextSupport.safeLower(row.getOwner());
        String industry = SearchTextSupport.safeLower(row.getIndustry());
        String dataCategory = SearchTextSupport.safeLower(row.getDataCategory());
        String registrationType = SearchTextSupport.safeLower(row.getRegistrationType());
        String entityType = SearchTextSupport.safeLower(row.getEntityType());
        String componentType = SearchTextSupport.safeLower(row.getComponentType());
        String storageType = SearchTextSupport.safeLower(row.getStorageType());
        String accessMethod = SearchTextSupport.safeLower(row.getAccessMethod());
        String contentType = SearchTextSupport.safeLower(row.getContentType());
        String format = SearchTextSupport.safeLower(row.getFormat());
        String locator = SearchTextSupport.safeLower(row.getLocator());
        String additionalAttributes = SearchTextSupport.safeLower(row.getAdditionalAttributesText());

        double score = 0.0;
        for (String token : analysis.getRetrievalTokens()) {
            if (fieldSet.contains("name") && name.contains(token)) {
                score += 3.0;
            }
            if (fieldSet.contains("description") && desc.contains(token)) {
                score += 2.0;
            }
            if ((fieldSet.contains("applicationscenario") || fieldSet.contains("application_scenario")) && app.contains(token)) {
                score += 1.0;
            }
            if (fieldSet.contains("identifier") && id.contains(token)) {
                score += id.startsWith(token) ? 2.0 : 1.5;
            }
            if (fieldSet.contains("owner") && owner.contains(token)) {
                score += 1.2;
            }
            if (fieldSet.contains("industry") && industry.contains(token)) {
                score += 1.2;
            }
            if (fieldSet.contains("datacategory") && dataCategory.contains(token)) {
                score += 1.2;
            }
            if (fieldSet.contains("registrationtype") && registrationType.contains(token)) {
                score += 1.2;
            }
            if (fieldSet.contains("entitytype") && entityType.contains(token)) {
                score += 1.0;
            }
            if (fieldSet.contains("componenttype") && componentType.contains(token)) {
                score += 1.3;
            }
            if (fieldSet.contains("storagetype") && storageType.contains(token)) {
                score += 1.0;
            }
            if (fieldSet.contains("accessmethod") && accessMethod.contains(token)) {
                score += 1.0;
            }
            if (fieldSet.contains("contenttype") && contentType.contains(token)) {
                score += 1.0;
            }
            if (fieldSet.contains("format") && format.contains(token)) {
                score += 1.0;
            }
            if (fieldSet.contains("locator") && locator.contains(token)) {
                score += 0.8;
            }
            if ((fieldSet.contains("additionalattributes") || fieldSet.contains("additional_attributes"))
                    && additionalAttributes.contains(token)) {
                score += 1.6;
            }
        }
        return score;
    }

    public double computeSemanticOverlap(SearchQueryAnalysis analysis, SearchDocumentRow row) {
        Set<String> queryTokens = new LinkedHashSet<String>(analysis.getRetrievalTokens());
        Set<String> corpusTokens = new LinkedHashSet<String>(SearchTextSupport.tokenize(textBuilder.build(row)));
        if (queryTokens.isEmpty() || corpusTokens.isEmpty()) {
            return 0.0;
        }
        int intersect = 0;
        for (String token : queryTokens) {
            if (corpusTokens.contains(token)) {
                intersect++;
            }
        }
        int union = queryTokens.size() + corpusTokens.size() - intersect;
        return union == 0 ? 0.0 : ((double) intersect) / union;
    }

    public double computeLocalScore(SearchRequest request, SearchQueryAnalysis analysis, SearchResult result, SearchDocumentRow row) {
        SearchMode mode = request.getMode() == null ? SearchMode.HYBRID : request.getMode();
        boolean semanticAvailable = result.getSemanticScore() > semanticActivationThreshold(analysis, row);
        double alpha;
        if (mode == SearchMode.KEYWORD) {
            alpha = 0.0;
        } else if (mode == SearchMode.SEMANTIC) {
            alpha = 1.0;
        } else {
            alpha = semanticAvailable ? resolveHybridSemanticWeight(request, result) : 0.0;
        }

        double lexical = normalizeKeywordScore(result.getKeywordScore(), analysis.getCoreTokens().size());
        double semantic = clamp01(result.getSemanticScore());
        double base = (1.0 - alpha) * lexical + alpha * semantic;
        double phraseBoost = computePhraseBoost(analysis, row);
        double completenessBoost = computeCompletenessBoost(row);
        double consistencyAdjustment = computeHybridConsistencyAdjustment(mode, analysis, result, row, lexical, semantic);
        double typeAdjustment = computeTypeAdjustment(mode, analysis, row);
        double themeAdjustment = SearchThemeSupport.computeThemeAdjustment(analysis, row);
        double finalScore = base + phraseBoost + completenessBoost + consistencyAdjustment + typeAdjustment + themeAdjustment;
        return Math.max(0.0, finalScore);
    }

    private Set<String> resolveFieldSet(List<String> fields) {
        Set<String> fieldSet = new LinkedHashSet<String>();
        if (fields != null) {
            for (String field : fields) {
                String normalized = SearchTextSupport.safeLower(field);
                if (!normalized.isEmpty()) {
                    fieldSet.add(normalized);
                }
            }
        }
        if (!fieldSet.isEmpty()) {
            return fieldSet;
        }
        fieldSet.addAll(Arrays.asList(
                "name", "description", "applicationscenario", "identifier",
                "owner", "industry", "datacategory", "registrationtype", "entitytype",
                "componenttype", "storagetype", "accessmethod", "contenttype", "format", "locator",
                "additionalattributes"
        ));
        return fieldSet;
    }

    private double normalizeKeywordScore(double keywordScore, int coreTokenCount) {
        if (keywordScore <= 0.0) {
            return 0.0;
        }
        if (keywordScore <= 1.0) {
            return keywordScore;
        }
        double scale = Math.max(3.0, coreTokenCount <= 0 ? 4.0 : coreTokenCount * 2.5);
        return 1.0 - Math.exp((-1.0 * keywordScore) / scale);
    }

    private double computePhraseBoost(SearchQueryAnalysis analysis, SearchDocumentRow row) {
        if (row == null) {
            return 0.0;
        }
        String name = SearchTextSupport.safeLower(row.getName());
        String desc = SearchTextSupport.safeLower(row.getDescription());
        String app = SearchTextSupport.safeLower(row.getApplicationScenario());
        String query = SearchTextSupport.safeLower(analysis.getOriginalQuery());
        double boost = 0.0;
        if (!SearchTextSupport.isBlank(query) && query.length() >= 2) {
            if (name.contains(query)) {
                boost += 0.18;
            } else if (desc.contains(query) || app.contains(query)) {
                boost += 0.12;
            }
        }
        if (!analysis.getCoreTokens().isEmpty()) {
            if (SearchTextSupport.containsAllTokens(name, analysis.getCoreTokens())) {
                boost += 0.08;
            } else if (SearchTextSupport.containsAllTokens(desc + " " + app, analysis.getCoreTokens())) {
                boost += 0.05;
            }
        }
        return Math.min(0.28, boost);
    }

    private double computeCompletenessBoost(SearchDocumentRow row) {
        if (row == null) {
            return 0.0;
        }
        double boost = 0.0;
        if (!SearchTextSupport.isBlank(row.getName())) {
            boost += 0.01;
        }
        if (!SearchTextSupport.isBlank(row.getDescription())) {
            boost += 0.02;
        }
        if (!SearchTextSupport.isBlank(row.getComponentType())) {
            boost += 0.01;
        }
        if (!SearchTextSupport.isBlank(row.getAccessMethod())) {
            boost += 0.01;
        }
        if (!SearchTextSupport.isBlank(row.getFormat()) || !SearchTextSupport.isBlank(row.getContentType())) {
            boost += 0.02;
        }
        if (!SearchTextSupport.isBlank(row.getLocator())) {
            boost += 0.03;
        }
        return boost;
    }

    private double semanticActivationThreshold(SearchQueryAnalysis analysis, SearchDocumentRow row) {
        double threshold = 0.18;
        if (analysis != null && analysis.getCoreTokens() != null && analysis.getCoreTokens().size() >= 4) {
            threshold += 0.02;
        }
        if (row != null && isImageLike(row) && isDocumentFocusedQuery(analysis)) {
            threshold += 0.08;
        }
        return threshold;
    }

    private double resolveHybridSemanticWeight(SearchRequest request, SearchResult result) {
        double requested = request == null ? 0.7 : request.getSemanticWeight();
        double bounded = Math.min(requested, 0.45);
        if (result == null) {
            return bounded;
        }
        if (result.getKeywordScore() <= 0.0) {
            return Math.min(bounded, 0.22);
        }
        return bounded;
    }

    private double computeHybridConsistencyAdjustment(
            SearchMode mode,
            SearchQueryAnalysis analysis,
            SearchResult result,
            SearchDocumentRow row,
            double lexical,
            double semantic
    ) {
        if (mode != SearchMode.HYBRID) {
            return 0.0;
        }
        double adjustment = 0.0;
        double overlap = row == null ? 0.0 : computeSemanticOverlap(analysis, row);

        if (lexical <= 0.01 && semantic > 0.0) {
            adjustment -= 0.14;
        }
        if (semantic > 0.0 && overlap < 0.03) {
            adjustment -= 0.12;
        } else if (semantic > 0.0 && overlap >= 0.08 && lexical > 0.0) {
            adjustment += 0.05;
        }
        return adjustment;
    }

    private double computeTypeAdjustment(SearchMode mode, SearchQueryAnalysis analysis, SearchDocumentRow row) {
        if (row == null || mode != SearchMode.HYBRID) {
            return 0.0;
        }
        if (!isDocumentFocusedQuery(analysis)) {
            return 0.0;
        }
        if (isImageLike(row)) {
            return -0.16;
        }
        if (isDocumentLike(row)) {
            return 0.08;
        }
        return 0.0;
    }

    private boolean isDocumentFocusedQuery(SearchQueryAnalysis analysis) {
        return SearchThemeSupport.isDocumentCentricQuery(analysis);
    }

    private boolean isImageLike(SearchDocumentRow row) {
        String entityType = SearchTextSupport.safeLower(row.getEntityType());
        String format = SearchTextSupport.safeLower(row.getFormat());
        String contentType = SearchTextSupport.safeLower(row.getContentType());
        String text = SearchTextSupport.safeLower(row.getName() + " " + row.getDescription());
        return "image".equals(entityType)
                || format.contains("png")
                || format.contains("jpg")
                || format.contains("jpeg")
                || contentType.startsWith("image/")
                || text.contains("\u9644\u56fe")
                || text.contains("\u56fe\u7247")
                || text.contains("\u56fe\u50cf");
    }

    private boolean isDocumentLike(SearchDocumentRow row) {
        String entityType = SearchTextSupport.safeLower(row.getEntityType());
        String format = SearchTextSupport.safeLower(row.getFormat());
        String text = SearchTextSupport.safeLower(row.getName() + " " + row.getDescription());
        return "document".equals(entityType)
                || format.contains("pdf")
                || format.contains("doc")
                || format.contains("docx")
                || text.contains("\u6307\u5357")
                || text.contains("\u8981\u6c42")
                || text.contains("\u89c4\u8303")
                || text.contains("\u6280\u672f\u6587\u4ef6")
                || text.contains("\u529e\u6cd5");
    }

    private double clamp01(double value) {
        if (value <= 0.0) {
            return 0.0;
        }
        if (value >= 1.0) {
            return 1.0;
        }
        return value;
    }
}

