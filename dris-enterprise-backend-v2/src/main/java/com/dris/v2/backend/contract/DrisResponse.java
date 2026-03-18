package com.dris.v2.backend.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

public class DrisResponse<T> {
    private boolean ok;
    private String code;
    private String message;
    private String traceId;
    private String timestamp;
    private List<String> details = new ArrayList<>();
    private T data;

    public static <T> DrisResponse<T> success(T data) {
        DrisResponse<T> response = new DrisResponse<>();
        response.ok = true;
        response.code = "OK";
        response.data = data;
        response.traceId = UUID.randomUUID().toString();
        response.timestamp = Instant.now().toString();
        return response;
    }

    public static <T> DrisResponse<T> failure(String code, String message, List<String> details) {
        DrisResponse<T> response = new DrisResponse<>();
        response.ok = false;
        response.code = code == null ? "ERROR" : code;
        response.message = message;
        response.details = details == null ? Collections.emptyList() : details;
        response.traceId = UUID.randomUUID().toString();
        response.timestamp = Instant.now().toString();
        return response;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public List<String> getDetails() {
        return details;
    }

    public void setDetails(List<String> details) {
        this.details = details == null ? Collections.emptyList() : details;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
