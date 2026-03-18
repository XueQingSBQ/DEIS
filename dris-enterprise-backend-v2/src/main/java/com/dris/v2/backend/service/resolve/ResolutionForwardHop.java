package com.dris.v2.backend.service.resolve;

public final class ResolutionForwardHop {
    private final String nextHopCode;
    private final String nextHopUrl;

    public ResolutionForwardHop(String nextHopCode, String nextHopUrl) {
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
