package com.dris.v2.backend.vector;

import com.dris.sdk.next.domain.IdentifierMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SimpleTextVectorizer {
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fff]+");
    private final int dimensions;
    private final VectorMetadataDocumentBuilder documentBuilder = new VectorMetadataDocumentBuilder();

    public SimpleTextVectorizer(int dimensions) {
        this.dimensions = Math.max(32, dimensions);
    }

    public int getDimensions() {
        return dimensions;
    }

    public double[] vectorizeMetadata(IdentifierMetadata metadata) {
        return vectorizeText(documentBuilder.buildText(metadata));
    }

    public double[] vectorizeText(String text) {
        double[] vector = new double[dimensions];
        List<String> tokens = tokenize(text);
        for (String token : tokens) {
            add(vector, token, 1.0);
            if (token.length() > 1) {
                for (int i = 0; i < token.length() - 1; i++) {
                    add(vector, token.substring(i, i + 2), 0.5);
                }
            }
        }
        return normalize(vector);
    }

    public double cosine(double[] left, double[] right, double rightNorm) {
        if (left == null || right == null || left.length != right.length || left.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm2 = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm2 += left[i] * left[i];
        }
        if (leftNorm2 <= 0.0 || rightNorm <= 0.0) {
            return 0.0;
        }
        double score = dot / (Math.sqrt(leftNorm2) * rightNorm);
        if (score < 0.0) {
            return 0.0;
        }
        if (score > 1.0) {
            return 1.0;
        }
        return score;
    }

    public String serialize(double[] vector) {
        if (vector == null || vector.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(vector.length * 8);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Double.toString(vector[i]));
        }
        return builder.toString();
    }

    public double[] deserialize(String value) {
        double[] vector = new double[dimensions];
        if (value == null || value.trim().isEmpty()) {
            return vector;
        }
        String[] parts = value.split(",");
        int size = Math.min(parts.length, dimensions);
        for (int i = 0; i < size; i++) {
            try {
                vector[i] = Double.parseDouble(parts[i]);
            } catch (Exception ignore) {
                vector[i] = 0.0;
            }
        }
        return vector;
    }

    public double norm(double[] vector) {
        if (vector == null || vector.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : vector) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private void add(double[] vector, String token, double weight) {
        if (token == null || token.isEmpty()) {
            return;
        }
        vector[index(token)] += weight;
    }

    private int index(String token) {
        int hash = token.hashCode();
        if (hash == Integer.MIN_VALUE) {
            hash = 0;
        }
        hash = Math.abs(hash);
        return hash % dimensions;
    }

    private double[] normalize(double[] vector) {
        double currentNorm = norm(vector);
        if (currentNorm <= 0.0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / currentNorm;
        }
        return vector;
    }

    private List<String> tokenize(String input) {
        List<String> out = new ArrayList<String>();
        if (input == null) {
            return out;
        }
        String normalized = NON_WORD.matcher(input.toLowerCase()).replaceAll(" ").trim();
        if (normalized.isEmpty()) {
            return out;
        }
        String[] parts = normalized.split("\\s+");
        for (String part : parts) {
            if (part.length() > 1 || isSingleCjk(part)) {
                out.add(part);
            }
        }
        return out;
    }

    private boolean isSingleCjk(String token) {
        if (token == null || token.length() != 1) {
            return false;
        }
        char value = token.charAt(0);
        return value >= 0x4e00 && value <= 0x9fff;
    }
}
