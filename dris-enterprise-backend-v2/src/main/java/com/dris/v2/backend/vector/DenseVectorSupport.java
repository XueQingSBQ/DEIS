package com.dris.v2.backend.vector;

public final class DenseVectorSupport {
    private DenseVectorSupport() {
    }

    public static double cosine(double[] left, double[] right, double rightNorm) {
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

    public static String serialize(double[] vector) {
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

    public static double[] deserialize(String value, int fallbackDimensions) {
        int dimensions = Math.max(0, fallbackDimensions);
        if (value == null || value.trim().isEmpty()) {
            return new double[dimensions];
        }
        String[] parts = value.split(",");
        double[] vector = new double[Math.max(dimensions, parts.length)];
        int size = Math.min(parts.length, vector.length);
        for (int i = 0; i < size; i++) {
            try {
                vector[i] = Double.parseDouble(parts[i]);
            } catch (Exception ignore) {
                vector[i] = 0.0;
            }
        }
        return vector;
    }

    public static double norm(double[] vector) {
        if (vector == null || vector.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : vector) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }
}