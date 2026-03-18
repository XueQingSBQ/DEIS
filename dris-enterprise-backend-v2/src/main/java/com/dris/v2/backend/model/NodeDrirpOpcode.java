package com.dris.v2.backend.model;

public final class NodeDrirpOpcode {
    public static final int NODE_UNIQUENESS_CHECK_UP = 7101;
    public static final int NODE_HELLO = 7102;
    public static final int ROUTE_INSERT_UP = 7103;
    public static final int ROUTE_UPDATE_UP = 7104;
    public static final int NODE_HEARTBEAT = 7105;
    public static final int NODE_GOODBYE = 7106;
    public static final int RESOLVE_FORWARD = 7201;
    public static final int RESOLVE_ROUTE_QUERY = 7202;
    public static final int RESOLVE_BATCH_FORWARD = 7203;
    public static final int OP_FORWARD = 7301;
    public static final int SEARCH_FORWARD = 7401;

    private NodeDrirpOpcode() {
    }
}


