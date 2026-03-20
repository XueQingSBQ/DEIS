package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.dao.SearchDocumentRow;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SearchThemeSupport {
    static final String THEME_NOTICE = "通知公告";
    static final String THEME_PROGRESS = "建设进展";
    static final String THEME_DYNAMIC = "工作动态";
    static final String THEME_LOCAL = "地方信息";
    static final String THEME_STANDARD = "规范文件";
    static final String THEME_BENEFIT = "建设效益";
    static final String THEME_TECH = "技术资料";

    private static final Pattern COLUMN_PATTERN = Pattern.compile("\"column\"\\s*:\\s*\"([^\"]+)\"");

    private SearchThemeSupport() {
    }

    static List<String> detectQueryThemes(SearchQueryAnalysis analysis) {
        if (analysis == null) {
            return Collections.emptyList();
        }
        String text = SearchTextSupport.safeLower(
                SearchTextSupport.joinTerms(analysis.getExpandedTerms()) + " " + analysis.getOriginalQuery()
        );
        if (SearchTextSupport.isBlank(text)) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> out = new LinkedHashSet<String>();
        addIfMatched(out, text, THEME_STANDARD,
                "规范文件", "规范", "标准", "指南", "导则", "规程", "技术要求", "办法", "制度");
        addIfMatched(out, text, THEME_TECH,
                "技术资料", "分析评价", "技术文件", "数据库表结构", "参数计算", "图件表达", "技术方案", "技术文档");
        addIfMatched(out, text, THEME_NOTICE,
                "通知公告", "通知", "公告", "通告", "通报", "函", "征求意见");
        addIfMatched(out, text, THEME_PROGRESS,
                "建设进展", "进展情况", "项目建设", "建设进度", "进展");
        addIfMatched(out, text, THEME_DYNAMIC,
                "工作动态", "动态", "督查", "督导", "检查", "调研", "召开", "部署");
        addIfMatched(out, text, THEME_LOCAL,
                "地方信息", "地方案例", "地方实践", "县", "市", "区", "乡", "街道", "流域管理局");
        addIfMatched(out, text, THEME_BENEFIT,
                "建设效益", "成效", "效益", "效果", "减灾效益", "发挥效益");
        return Arrays.asList(out.toArray(new String[0]));
    }

    static boolean isDocumentCentricQuery(SearchQueryAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        String text = SearchTextSupport.safeLower(
                SearchTextSupport.joinTerms(analysis.getExpandedTerms()) + " " + analysis.getOriginalQuery()
        );
        if (containsAny(text, "附图", "图片", "图像", "照片", "示意图", "截图", "jpg", "jpeg", "png")) {
            return false;
        }
        if (!detectQueryThemes(analysis).isEmpty()) {
            return true;
        }
        return containsAny(text,
                "技术文件", "规范", "指南", "要求", "方案", "文档", "文件", "标准", "报告",
                "通知", "公告", "案例", "动态", "资料", "进展");
    }

    static String extractPrimaryTheme(SearchDocumentRow row) {
        if (row == null) {
            return "";
        }
        String attrs = SearchTextSupport.trim(row.getAdditionalAttributesText());
        Matcher matcher = COLUMN_PATTERN.matcher(attrs);
        if (matcher.find()) {
            String extracted = canonicalTheme(matcher.group(1));
            if (!SearchTextSupport.isBlank(extracted)) {
                return extracted;
            }
        }
        String mergedText = SearchTextSupport.safeLower(
                row.getName() + " " + row.getDescription() + " " + row.getAdditionalAttributesText()
        );
        if (containsAny(mergedText, "通知公告", "通知", "公告", "函")) {
            return THEME_NOTICE;
        }
        if (containsAny(mergedText, "规范文件", "规范", "标准", "指南", "技术要求")) {
            return THEME_STANDARD;
        }
        if (containsAny(mergedText, "技术资料", "分析评价", "数据库表结构", "技术方案")) {
            return THEME_TECH;
        }
        if (containsAny(mergedText, "建设进展", "进展情况", "项目建设")) {
            return THEME_PROGRESS;
        }
        if (containsAny(mergedText, "工作动态", "督查", "督导", "检查", "调研")) {
            return THEME_DYNAMIC;
        }
        if (containsAny(mergedText, "地方信息", "地方案例", "地方实践")) {
            return THEME_LOCAL;
        }
        if (containsAny(mergedText, "建设效益", "成效", "效益")) {
            return THEME_BENEFIT;
        }
        return "";
    }

    static double computeThemeAdjustment(SearchQueryAnalysis analysis, SearchDocumentRow row) {
        List<String> queryThemes = detectQueryThemes(analysis);
        if (queryThemes.isEmpty() || row == null) {
            return 0.0;
        }
        String rowTheme = extractPrimaryTheme(row);
        if (SearchTextSupport.isBlank(rowTheme)) {
            return 0.0;
        }

        String primaryTheme = queryThemes.get(0);
        if (queryThemes.contains(rowTheme)) {
            return primaryTheme.equals(rowTheme) ? 0.22 : 0.16;
        }
        if (isRelatedTheme(primaryTheme, rowTheme)) {
            return 0.07;
        }
        if (queryThemes.size() == 1) {
            return -0.12;
        }
        return -0.05;
    }

    private static void addIfMatched(Set<String> themes, String text, String canonical, String... hints) {
        if (containsAny(text, hints)) {
            themes.add(canonical);
        }
    }

    private static boolean containsAny(String text, String... hints) {
        if (SearchTextSupport.isBlank(text) || hints == null) {
            return false;
        }
        for (String hint : hints) {
            String normalized = SearchTextSupport.safeLower(hint);
            if (!normalized.isEmpty() && text.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String canonicalTheme(String raw) {
        String text = SearchTextSupport.trim(raw);
        if (text.isEmpty()) {
            return "";
        }
        if (THEME_NOTICE.equals(text)) {
            return THEME_NOTICE;
        }
        if (THEME_PROGRESS.equals(text)) {
            return THEME_PROGRESS;
        }
        if (THEME_DYNAMIC.equals(text)) {
            return THEME_DYNAMIC;
        }
        if (THEME_LOCAL.equals(text)) {
            return THEME_LOCAL;
        }
        if (THEME_STANDARD.equals(text)) {
            return THEME_STANDARD;
        }
        if (THEME_BENEFIT.equals(text)) {
            return THEME_BENEFIT;
        }
        if (THEME_TECH.equals(text)) {
            return THEME_TECH;
        }
        return "";
    }

    private static boolean isRelatedTheme(String left, String right) {
        if (SearchTextSupport.isBlank(left) || SearchTextSupport.isBlank(right)) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        if (THEME_STANDARD.equals(left)) {
            return THEME_TECH.equals(right);
        }
        if (THEME_TECH.equals(left)) {
            return THEME_STANDARD.equals(right);
        }
        if (THEME_NOTICE.equals(left)) {
            return THEME_DYNAMIC.equals(right);
        }
        if (THEME_DYNAMIC.equals(left)) {
            return THEME_NOTICE.equals(right) || THEME_PROGRESS.equals(right) || THEME_LOCAL.equals(right);
        }
        if (THEME_LOCAL.equals(left)) {
            return THEME_DYNAMIC.equals(right) || THEME_PROGRESS.equals(right);
        }
        if (THEME_PROGRESS.equals(left)) {
            return THEME_DYNAMIC.equals(right) || THEME_BENEFIT.equals(right) || THEME_LOCAL.equals(right);
        }
        if (THEME_BENEFIT.equals(left)) {
            return THEME_PROGRESS.equals(right) || THEME_DYNAMIC.equals(right);
        }
        return false;
    }
}
