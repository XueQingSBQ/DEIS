package com.dris.v2.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class ToolExecuteResult {
    private String identifier;
    private String operationName;
    private List<String> command = new ArrayList<String>();
    private boolean success;
    private int exitCode;
    private long durationMs;
    private String stdout;
    private String stderr;
    private String handledByNodeCode;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public List<String> getCommand() {
        return command;
    }

    public void setCommand(List<String> command) {
        this.command = command == null ? new ArrayList<String>() : command;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public String getHandledByNodeCode() {
        return handledByNodeCode;
    }

    public void setHandledByNodeCode(String handledByNodeCode) {
        this.handledByNodeCode = handledByNodeCode;
    }
}
