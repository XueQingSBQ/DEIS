package com.dris.v2.backend.risk;

public class RiskDecisionResult {
    private final RiskDecision decision;
    private final String reason;

    public RiskDecisionResult(RiskDecision decision, String reason) {
        this.decision = decision == null ? RiskDecision.DENY : decision;
        this.reason = reason == null ? "" : reason;
    }

    public static RiskDecisionResult allow(String reason) {
        return new RiskDecisionResult(RiskDecision.ALLOW, reason);
    }

    public static RiskDecisionResult deny(String reason) {
        return new RiskDecisionResult(RiskDecision.DENY, reason);
    }

    public RiskDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public boolean isAllowed() {
        return decision == RiskDecision.ALLOW;
    }
}
