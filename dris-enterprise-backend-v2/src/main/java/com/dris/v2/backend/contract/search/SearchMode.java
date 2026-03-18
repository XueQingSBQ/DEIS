package com.dris.v2.backend.contract.search;

public enum SearchMode {
    KEYWORD("keyword"),
    SEMANTIC("semantic"),
    HYBRID("hybrid");

    private final String wireValue;

    SearchMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}

