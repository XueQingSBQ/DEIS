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
        String theme = SearchTextSupport.safeLower(row.getTheme());
        String region = SearchTextSupport.safeLower(row.getRegion());
        String name = SearchTextSupport.safeLower(row.getName());
        String sourceFilename = SearchTextSupport.safeLower(row.getSourceFilename());
        String bizKeywords = SearchTextSupport.safeLower(row.getBizKeywordsText());
        String desc = SearchTextSupport.safeLower(row.getDescription());
        String app = SearchTextSupport.safeLower(row.getApplicationScenario());
        String id = SearchTextSupport.safeLower(row.getIdentifier());
        String owner = SearchTextSupport.safeLower(row.getOwner());

        double score = 0.0;
        for (String token : analysis.getRetrievalTokens()) {
            if (fieldSet.contains("theme") && theme.contains(token)) {
                score += 10.0;
            }
            if (fieldSet.contains("region") && region.contains(token)) {
                score += 8.0;
            }
            if (fieldSet.contains("name") && name.contains(token)) {
                score += 6.0;
            }
            if (fieldSet.contains("identifier") && id.contains(token)) {
                score += id.startsWith(token) ? 12.0 : 8.0;
            }
            if ((fieldSet.contains("sourcefilename") || fieldSet.contains("source_filename")) && sourceFilename.contains(token)) {
                score += 2.0;
            }
            if ((fieldSet.contains("bizkeywords") || fieldSet.contains("bizkeywordstext")) && bizKeywords.contains(token)) {
                score += 5.0;
            }
            if (fieldSet.contains("description") && desc.contains(token)) {
                score += 4.0;
            }
            if ((fieldSet.contains("applicationscenario") || fieldSet.contains("application_scenario")) && app.contains(token)) {
                score += 4.0;
            }
            if (fieldSet.contains("owner") && owner.contains(token)) {
                score += 1.0;
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

    public boolean hasPrimaryFieldMatch(SearchQueryAnalysis analysis, SearchDocumentRow row) {
        if (analysis == null || row == null) {
            return false;
        }
        String theme = SearchTextSupport.safeLower(row.getTheme());
        String region = SearchTextSupport.safeLower(row.getRegion());
        String name = SearchTextSupport.safeLower(row.getName());
        for (String token : analysis.getRetrievalTokens()) {
            if (SearchTextSupport.isBlank(token)) {
                continue;
            }
            if (theme.contains(token) || region.contains(token) || name.contains(token)) {
                return true;
            }
        }
        return false;
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
                "theme", "region", "name", "bizkeywords",
                "description", "applicationscenario", "identifier", "sourcefilename", "owner"
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
        String theme = SearchTextSupport.safeLower(row.getTheme());
        String region = SearchTextSupport.safeLower(row.getRegion());
        String desc = SearchTextSupport.safeLower(row.getDescription());
        String app = SearchTextSupport.safeLower(row.getApplicationScenario());
        String keywords = SearchTextSupport.safeLower(row.getBizKeywordsText());
        String query = SearchTextSupport.safeLower(analysis.getOriginalQuery());
        double boost = 0.0;
        if (!SearchTextSupport.isBlank(query) && query.length() >= 2) {
            if (theme.contains(query)) {
                boost += 0.22;
            } else if (region.contains(query)) {
                boost += 0.18;
            } else if (name.contains(query)) {
                boost += 0.15;
            } else if (keywords.contains(query)) {
                boost += 0.10;
            } else if (desc.contains(query) || app.contains(query)) {
                boost += 0.08;
            }
        }
        if (!analysis.getCoreTokens().isEmpty()) {
            if (SearchTextSupport.containsAllTokens(theme, analysis.getCoreTokens())) {
                boost += 0.10;
            } else if (SearchTextSupport.containsAllTokens(region, analysis.getCoreTokens())) {
                boost += 0.08;
            } else if (SearchTextSupport.containsAllTokens(name, analysis.getCoreTokens())) {
                boost += 0.08;
            } else if (SearchTextSupport.containsAllTokens(keywords, analysis.getCoreTokens())) {
                boost += 0.06;
            } else if (SearchTextSupport.containsAllTokens(desc + " " + app, analysis.getCoreTokens())) {
                boost += 0.05;
            }
        }
        return Math.min(0.32, boost);
    }

    private double computeCompletenessBoost(SearchDocumentRow row) {
        if (row == null) {
            return 0.0;
        }
        double boost = 0.0;
        if (!SearchTextSupport.isBlank(row.getTheme())) {
            boost += 0.03;
        }
        if (!SearchTextSupport.isBlank(row.getRegion())) {
            boost += 0.03;
        }
        if (!SearchTextSupport.isBlank(row.getName())) {
            boost += 0.02;
        }
        if (!SearchTextSupport.isBlank(row.getBizKeywordsText())) {
            boost += 0.02;
        }
        if (!SearchTextSupport.isBlank(row.getDescription())) {
            boost += 0.02;
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

