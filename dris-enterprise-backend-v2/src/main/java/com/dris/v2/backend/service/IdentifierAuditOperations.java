package com.dris.v2.backend.service;

public final class IdentifierAuditOperations {
    public static final String REGISTER = "REGISTER";
    public static final String REGISTER_BATCH = "REGISTER_BATCH";
    public static final String UPDATE_METADATA = "UPDATE_METADATA";
    public static final String PARTIAL_UPDATE_METADATA = "PARTIAL_UPDATE_METADATA";
    public static final String UPDATE_STATUS = "UPDATE_STATUS";
    public static final String DELETE_IDENTIFIER = "DELETE_IDENTIFIER";
    public static final String UPSERT_COMPONENT = "UPSERT_COMPONENT";
    public static final String REMOVE_COMPONENT = "REMOVE_COMPONENT";
    public static final String CREATE_UPLOAD_SESSION = "CREATE_UPLOAD_SESSION";
    public static final String COMPLETE_UPLOAD = "COMPLETE_UPLOAD";

    private IdentifierAuditOperations() {
    }
}
