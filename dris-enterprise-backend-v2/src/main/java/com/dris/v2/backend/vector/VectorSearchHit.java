package com.dris.v2.backend.vector;

public class VectorSearchHit {
    private String identifier;
    private double score;

    public VectorSearchHit() {
    }

    public VectorSearchHit(String identifier, double score) {
        this.identifier = identifier;
        this.score = score;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
