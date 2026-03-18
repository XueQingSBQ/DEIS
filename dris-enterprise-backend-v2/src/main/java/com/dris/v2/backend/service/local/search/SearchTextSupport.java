package com.dris.v2.backend.service.local.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class SearchTextSupport {
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fff]+");

    private SearchTextSupport() {
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    static List<String> tokenize(String input) {
        List<String> out = new ArrayList<String>();
        if (input == null) {
            return out;
        }
        String normalized = NON_WORD.matcher(input.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        if (normalized.isEmpty()) {
            return out;
        }
        String[] parts = normalized.split("\\s+");
        Set<String> deduped = new LinkedHashSet<String>();
        for (String part : parts) {
            if (part.length() > 1 || isSingleCjk(part)) {
                deduped.add(part);
            }
        }
        out.addAll(deduped);
        return out;
    }

    static String joinTerms(Iterable<String> values) {
        StringBuilder builder = new StringBuilder();
        Set<String> deduped = new LinkedHashSet<String>();
        for (String value : values) {
            String trimmed = trim(value);
            if (trimmed.isEmpty()) {
                continue;
            }
            String normalized = safeLower(trimmed);
            if (!deduped.add(normalized)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(trimmed);
        }
        return builder.toString();
    }

    static boolean containsAllTokens(String text, List<String> tokens) {
        if (isBlank(text) || tokens == null || tokens.isEmpty()) {
            return false;
        }
        String normalized = safeLower(text);
        for (String token : tokens) {
            if (!normalized.contains(safeLower(token))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSingleCjk(String token) {
        if (token == null || token.length() != 1) {
            return false;
        }
        char c = token.charAt(0);
        return c >= 0x4e00 && c <= 0x9fff;
    }
}
