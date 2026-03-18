package com.dris.v2.backend.vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HashTextEmbeddingProvider implements EmbeddingProvider {
    private final SimpleTextVectorizer vectorizer;

    public HashTextEmbeddingProvider(int dimensions) {
        this.vectorizer = new SimpleTextVectorizer(dimensions);
    }

    @Override
    public int dimensions() {
        return vectorizer.getDimensions();
    }

    @Override
    public List<double[]> embedTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.<double[]>emptyList();
        }
        List<double[]> outputs = new ArrayList<double[]>(texts.size());
        for (String text : texts) {
            outputs.add(vectorizer.vectorizeText(text));
        }
        return outputs;
    }
}