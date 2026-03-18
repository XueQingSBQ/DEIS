package com.dris.v2.backend.vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface EmbeddingProvider {
    int dimensions();

    List<double[]> embedTexts(List<String> texts);

    default double[] embedText(String text) {
        List<String> inputs = new ArrayList<String>(1);
        inputs.add(text == null ? "" : text);
        List<double[]> outputs = embedTexts(inputs);
        if (outputs == null || outputs.isEmpty() || outputs.get(0) == null) {
            return new double[Math.max(0, dimensions())];
        }
        return outputs.get(0);
    }

    default List<double[]> emptyEmbeddings() {
        return Collections.<double[]>emptyList();
    }
}