package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.search.SearchRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DefaultSearchQueryAnalyzer implements SearchQueryAnalyzer {
    private static final Pattern REGION_PATTERN = Pattern.compile("([\\p{IsHan}]{2,16}(?:省|市|县|区|乡|镇|村|新区|自治州|自治区|流域))");
    private static final Pattern NODE_TARGET_PATTERN = Pattern.compile("([\\p{IsHan}A-Za-z0-9]{2,20}(?:部|院|所|局|厅|中心))");

    private final SearchSynonymRegistry synonymRegistry;

    public DefaultSearchQueryAnalyzer(SearchSynonymRegistry synonymRegistry) {
        this.synonymRegistry = synonymRegistry;
    }

    @Override
    public SearchQueryAnalysis analyze(SearchRequest request) {
        String originalQuery = request == null ? "" : SearchTextSupport.trim(request.getNaturalQuery());
        LinkedHashSet<String> baseTerms = new LinkedHashSet<String>();
        if (!SearchTextSupport.isBlank(originalQuery)) {
            baseTerms.add(originalQuery);
        }
        if (request != null && request.getKeywords() != null) {
            for (String keyword : request.getKeywords()) {
                String trimmed = SearchTextSupport.trim(keyword);
                if (!trimmed.isEmpty()) {
                    baseTerms.add(trimmed);
                }
            }
        }

        LinkedHashSet<String> coreTokens = new LinkedHashSet<String>();
        for (String baseTerm : baseTerms) {
            coreTokens.addAll(SearchTextSupport.tokenize(baseTerm));
        }

        LinkedHashSet<String> expandedTerms = expandTerms(baseTerms, coreTokens);
        LinkedHashSet<String> retrievalTokens = new LinkedHashSet<String>();
        for (String term : expandedTerms) {
            retrievalTokens.addAll(SearchTextSupport.tokenize(term));
        }
        if (retrievalTokens.isEmpty()) {
            retrievalTokens.addAll(coreTokens);
        }

        Map<String, String> effectiveFilters = new LinkedHashMap<String, String>();
        if (request != null && request.getFilters() != null) {
            effectiveFilters.putAll(request.getFilters());
        }
        inferFilters(baseTerms, coreTokens, effectiveFilters);

        return new SearchQueryAnalysis(
                originalQuery,
                SearchTextSupport.joinTerms(expandedTerms),
                new ArrayList<String>(coreTokens),
                new ArrayList<String>(retrievalTokens),
                new ArrayList<String>(expandedTerms),
                effectiveFilters
        );
    }

    private LinkedHashSet<String> expandTerms(LinkedHashSet<String> baseTerms, LinkedHashSet<String> coreTokens) {
        LinkedHashSet<String> expanded = new LinkedHashSet<String>();
        Deque<String> queue = new ArrayDeque<String>();
        Set<String> seen = new LinkedHashSet<String>();
        for (String value : baseTerms) {
            queue.addLast(value);
        }
        for (String token : coreTokens) {
            queue.addLast(token);
        }

        while (!queue.isEmpty()) {
            String current = SearchTextSupport.trim(queue.removeFirst());
            if (current.isEmpty()) {
                continue;
            }
            String normalized = SearchTextSupport.safeLower(current);
            if (!seen.add(normalized)) {
                continue;
            }
            expanded.add(current);
            for (String knownTerm : synonymRegistry.allTerms()) {
                if (!knownTerm.equals(normalized) && shouldPromoteKnownTerm(knownTerm) && normalized.contains(knownTerm)) {
                    queue.addLast(knownTerm);
                }
            }
            for (String synonym : synonymRegistry.lookup(current)) {
                queue.addLast(synonym);
            }
            for (String token : SearchTextSupport.tokenize(current)) {
                if (!SearchTextSupport.safeLower(token).equals(normalized)) {
                    queue.addLast(token);
                }
            }
        }
        return expanded;
    }

    private boolean shouldPromoteKnownTerm(String knownTerm) {
        String normalized = SearchTextSupport.trim(knownTerm);
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.length() >= 4) {
            return true;
        }
        return normalized.indexOf(' ') >= 0;
    }

    private void inferFilters(Set<String> baseTerms, Set<String> coreTokens, Map<String, String> filters) {
        List<String> values = new ArrayList<String>();
        for (String baseTerm : baseTerms) {
            String normalized = SearchTextSupport.safeLower(baseTerm);
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        for (String token : coreTokens) {
            String normalized = SearchTextSupport.safeLower(token);
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }

        putIfAbsent(filters, "entityType", values, "dataset", Arrays.asList("dataset", "data set"));
        putIfAbsent(filters, "entityType", values, "device", Arrays.asList("device"));
        inferEntityTypeByIntent(filters, values);
    }

    private void inferThemeFilter(String originalQuery, Set<String> expandedTerms, Map<String, String> filters) {
        if (hasFilter(filters, "theme")) {
            return;
        }
        SearchQueryAnalysis analysis = new SearchQueryAnalysis(
                originalQuery,
                SearchTextSupport.joinTerms(expandedTerms),
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                new ArrayList<String>(expandedTerms),
                Collections.<String, String>emptyMap()
        );
        List<String> themes = SearchThemeSupport.detectQueryThemes(analysis);
        if (themes.size() == 1) {
            filters.put("theme", themes.get(0));
        }
    }

    private boolean hasFilter(Map<String, String> filters, String key) {
        return filters.containsKey(key) && !SearchTextSupport.isBlank(filters.get(key));
    }

    private String detectRegion(String text) {
        Matcher matcher = REGION_PATTERN.matcher(SearchTextSupport.trim(text));
        if (matcher.find()) {
            return normalizeRegionCandidate(matcher.group(1));
        }
        return "";
    }

    private String detectNodeTarget(String text) {
        String normalized = SearchTextSupport.trim(text);
        if (normalized.isEmpty()) {
            return "";
        }
        for (String known : Arrays.asList("水利部", "水科院", "减灾所", "水利厅", "水利局", "流域管理局")) {
            if (normalized.contains(known)) {
                return known;
            }
        }
        Matcher matcher = NODE_TARGET_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return SearchTextSupport.trim(matcher.group(1));
        }
        return "";
    }

    private String normalizeRegionCandidate(String value) {
        String normalized = SearchTextSupport.trim(value);
        for (String prefix : Arrays.asList("请检索", "请查询", "请查找", "请搜索", "检索", "查询", "查找", "搜索", "关于", "围绕", "关注", "面向", "针对", "有关")) {
            if (normalized.startsWith(prefix) && normalized.length() > prefix.length()) {
                normalized = SearchTextSupport.trim(normalized.substring(prefix.length()));
            }
        }
        return normalized;
    }

    private void putIfAbsent(Map<String, String> filters, String key, List<String> values, String targetValue, List<String> hints) {
        if (filters.containsKey(key) && !SearchTextSupport.isBlank(filters.get(key))) {
            return;
        }
        for (String value : values) {
            String normalized = SearchTextSupport.safeLower(value);
            for (String hint : hints) {
                String hintValue = SearchTextSupport.safeLower(hint);
                if (!hintValue.isEmpty() && normalized.contains(hintValue)) {
                    filters.put(key, targetValue);
                    return;
                }
            }
        }
    }

    private void inferEntityTypeByIntent(Map<String, String> filters, List<String> values) {
        if (filters.containsKey("entityType") && !SearchTextSupport.isBlank(filters.get("entityType"))) {
            return;
        }
        String merged = SearchTextSupport.joinTerms(values);
        String text = SearchTextSupport.safeLower(merged);
        if (text.isEmpty()) {
            return;
        }
        if (containsAny(text, "附图", "图片", "图像", "照片", "示意图", "截图", "jpg", "jpeg", "png")) {
            filters.put("entityType", "image");
            return;
        }
        if (containsAny(text,
                "通知", "公告", "函", "规范", "标准", "指南", "技术要求", "报告", "方案", "文件", "文档",
                "案例", "动态", "资料", "进展", "地方信息", "建设效益")) {
            filters.put("entityType", "document");
        }
    }

    private boolean containsAny(String text, String... hints) {
        for (String hint : hints) {
            String normalized = SearchTextSupport.safeLower(hint);
            if (!normalized.isEmpty() && text.contains(normalized)) {
                return true;
            }
        }
        return false;
    }
}
