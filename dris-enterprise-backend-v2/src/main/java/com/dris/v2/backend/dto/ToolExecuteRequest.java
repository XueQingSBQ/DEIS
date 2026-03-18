package com.dris.v2.backend.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolExecuteRequest {
    private Map<String, Object> args = new LinkedHashMap<String, Object>();
    private Long timeoutMs;

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args == null ? new LinkedHashMap<String, Object>() : args;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}

