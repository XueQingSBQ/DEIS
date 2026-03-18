package com.dris.v2.backend.service.search;

import com.dris.v2.backend.contract.search.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SearchResultSupport {
    private SearchResultSupport() {
    }

    public static SearchResult copy(SearchResult source) {
        if (source == null) {
            return null;
        }
        SearchResult copy = new SearchResult();
        copy.setIdentifier(source.getIdentifier());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setOwner(source.getOwner());
        copy.setIndustry(source.getIndustry());
        copy.setDataCategory(source.getDataCategory());
        copy.setRegistrationType(source.getRegistrationType());
        copy.setEntityType(source.getEntityType());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setComponentType(source.getComponentType());
        copy.setStorageType(source.getStorageType());
        copy.setAccessMethod(source.getAccessMethod());
        copy.setContentType(source.getContentType());
        copy.setFormat(source.getFormat());
        copy.setSource(source.getSource());
        copy.setKeywordScore(source.getKeywordScore());
        copy.setSemanticScore(source.getSemanticScore());
        copy.setLocalScore(source.getLocalScore());
        copy.setFusionScore(source.getFusionScore());
        copy.setDisplayRank(source.getDisplayRank());
        copy.setFinalScore(source.getFinalScore());
        return copy;
    }

    public static List<SearchResult> copyResults(List<SearchResult> source) {
        List<SearchResult> out = new ArrayList<SearchResult>();
        if (source == null) {
            return out;
        }
        for (SearchResult item : source) {
            SearchResult copy = copy(item);
            if (copy != null) {
                out.add(copy);
            }
        }
        return out;
    }

    public static void fillMissing(SearchResult target, SearchResult source) {
        if (target == null || source == null) {
            return;
        }
        if (isBlank(target.getName())) {
            target.setName(source.getName());
        }
        if (isBlank(target.getDescription())) {
            target.setDescription(source.getDescription());
        }
        if (isBlank(target.getOwner())) {
            target.setOwner(source.getOwner());
        }
        if (isBlank(target.getIndustry())) {
            target.setIndustry(source.getIndustry());
        }
        if (isBlank(target.getDataCategory())) {
            target.setDataCategory(source.getDataCategory());
        }
        if (isBlank(target.getRegistrationType())) {
            target.setRegistrationType(source.getRegistrationType());
        }
        if (isBlank(target.getEntityType())) {
            target.setEntityType(source.getEntityType());
        }
        if (isBlank(target.getUpdateTime())) {
            target.setUpdateTime(source.getUpdateTime());
        }
        if (isBlank(target.getComponentType())) {
            target.setComponentType(source.getComponentType());
        }
        if (isBlank(target.getStorageType())) {
            target.setStorageType(source.getStorageType());
        }
        if (isBlank(target.getAccessMethod())) {
            target.setAccessMethod(source.getAccessMethod());
        }
        if (isBlank(target.getContentType())) {
            target.setContentType(source.getContentType());
        }
        if (isBlank(target.getFormat())) {
            target.setFormat(source.getFormat());
        }
    }

    public static int compareByScoreDesc(SearchResult left, SearchResult right) {
        int compare = Double.compare(safeScore(right == null ? 0.0 : right.getFinalScore()), safeScore(left == null ? 0.0 : left.getFinalScore()));
        if (compare != 0) {
            return compare;
        }
        compare = Double.compare(safeScore(right == null ? 0.0 : right.getSemanticScore()), safeScore(left == null ? 0.0 : left.getSemanticScore()));
        if (compare != 0) {
            return compare;
        }
        compare = Double.compare(safeScore(right == null ? 0.0 : right.getKeywordScore()), safeScore(left == null ? 0.0 : left.getKeywordScore()));
        if (compare != 0) {
            return compare;
        }
        return Objects.toString(left == null ? "" : left.getIdentifier(), "").compareTo(Objects.toString(right == null ? "" : right.getIdentifier(), ""));
    }

    public static double safeScore(double score) {
        return Double.isNaN(score) || Double.isInfinite(score) ? 0.0 : score;
    }

    public static void assignDisplayRanks(List<SearchResult> results, int startRank) {
        if (results == null) {
            return;
        }
        int rank = Math.max(1, startRank);
        for (SearchResult result : results) {
            if (result == null) {
                continue;
            }
            result.setDisplayRank(rank++);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
