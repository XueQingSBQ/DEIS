package org.bdware.drirp;

public final class DrirpConfig {
    public static final long DEFAULT_TIMEOUT_MS = 5000L;
    public static final int DEFAULT_MAX_HOPS = 8;
    public static final String DEFAULT_HTTP_BASE_PATH = "";
    public static final String DEFAULT_PROTOCOL_VERSION = "v1";
    public static final long DEFAULT_MAX_SKEW_MS = 5 * 60 * 1000L;

    private DrirpConfig() {
    }
}


