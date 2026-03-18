package com.dris.v2.backend.service.operation;

public final class OperationForwardHop {
    private final String nextHopCode;
    private final String nextHopUrl;

    public OperationForwardHop(String nextHopCode, String nextHopUrl) {
        this.nextHopCode = nextHopCode == null ? "" : nextHopCode;
        this.nextHopUrl = nextHopUrl;
    }

    public String getNextHopCode() {
        return nextHopCode;
    }

    public String getNextHopUrl() {
        return nextHopUrl;
    }
}
