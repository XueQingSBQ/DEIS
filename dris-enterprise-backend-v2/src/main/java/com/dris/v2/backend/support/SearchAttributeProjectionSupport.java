package com.dris.v2.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class SearchAttributeProjectionSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SearchAttributeProjectionSupport() {
    }

    public static String extractTheme(String additionalAttributes) {
        return readText(additionalAttributes, "column");
    }

    public static String extractRegion(String additionalAttributes) {
        return readText(additionalAttributes, "region");
    }

    public static String extractNodeTarget(String additionalAttributes) {
        return readText(additionalAttributes, "nodeTarget");
    }

    public static String extractSourceFilename(String additionalAttributes) {
        return readText(additionalAttributes, "sourceFilename");
    }

    public static List<String> extractKeywords(String additionalAttributes) {
        JsonNode root = readJson(additionalAttributes);
        if (root == null) {
            return Collections.emptyList();
        }
        JsonNode node = root.path("keywords");
        if (node.isMissingNode() || node.isNull()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        if (node.isArray()) {
            Iterator<JsonNode> iterator = node.elements();
            while (iterator.hasNext()) {
                String value = trim(iterator.next().asText(""));
                if (!value.isEmpty()) {
                    out.add(value);
                }
            }
            return out;
        }
        String scalar = trim(node.asText(""));
        if (scalar.isEmpty()) {
            return Collections.emptyList();
        }
        for (String part : scalar.split("[,，;；\\s]+")) {
            String value = trim(part);
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    public static String extractKeywordsText(String additionalAttributes) {
        List<String> values = extractKeywords(additionalAttributes);
        if (values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static String readText(String additionalAttributes, String field) {
        JsonNode root = readJson(additionalAttributes);
        if (root == null) {
            return "";
        }
        return trim(root.path(field).asText(""));
    }

    private static JsonNode readJson(String additionalAttributes) {
        String text = trim(additionalAttributes);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(text);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
