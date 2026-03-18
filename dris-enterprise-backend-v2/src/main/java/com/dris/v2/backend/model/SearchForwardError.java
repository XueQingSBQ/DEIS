package com.dris.v2.backend.model;

public class SearchForwardError {
    private String nodeCode;
    private String errorCode;
    private String message;

    public SearchForwardError() {
    }

    public SearchForwardError(String nodeCode, String errorCode, String message) {
        this.nodeCode = nodeCode;
        this.errorCode = errorCode;
        this.message = message;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
