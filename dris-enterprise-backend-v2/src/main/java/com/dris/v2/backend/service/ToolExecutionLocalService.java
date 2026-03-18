package com.dris.v2.backend.service;

import com.dris.v2.backend.contract.DrisResponse;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.sdknext.BackendKernelResponseSupport;
import com.dris.v2.backend.sdknext.LocalDrisKernelFacade;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ToolExecutionLocalService {
    private static final long DEFAULT_TIMEOUT_MS = 5000L;
    private static final long MAX_TIMEOUT_MS = 120000L;

    private final LocalDrisKernelFacade localDrisKernelFacade;

    public ToolExecutionLocalService(LocalDrisKernelFacade localDrisKernelFacade) {
        this.localDrisKernelFacade = localDrisKernelFacade;
    }

    public DrisResponse<ToolExecuteResult> executeToolLocal(String identifier, ToolExecuteRequest request) {
        final DigitalObject object;
        try {
            object = localDrisKernelFacade.getDigitalObject(identifier);
        } catch (RuntimeException ex) {
            return BackendKernelResponseSupport.failure(ex);
        }

        if (object == null || object.getResource() == null) {
            return DrisResponse.failure("NOT_FOUND", "tool resource not found", null);
        }

        Resource resource = object.getResource();
        if (!"tool".equals(resource.getComponentType())) {
            return DrisResponse.failure("VALIDATION_ERROR", "componentType must be tool", null);
        }

        Map<String, Object> args = request == null ? Collections.<String, Object>emptyMap() : safeArgs(request.getArgs());
        long timeoutMs = normalizeTimeout(request == null ? null : request.getTimeoutMs());
        List<String> command = resolveCommand(resource, args);
        if (command.isEmpty()) {
            return DrisResponse.failure("TOOL_COMMAND_MISSING", "tool extra command is missing", null);
        }

        long start = System.currentTimeMillis();
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);
        try {
            Process process = processBuilder.start();
            StreamCollector outCollector = new StreamCollector(process.getInputStream());
            StreamCollector errCollector = new StreamCollector(process.getErrorStream());
            outCollector.start();
            errCollector.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return DrisResponse.failure("TOOL_EXEC_TIMEOUT", "tool execution timeout", null);
            }
            outCollector.join(1000L);
            errCollector.join(1000L);

            int exitCode = process.exitValue();
            ToolExecuteResult result = new ToolExecuteResult();
            result.setIdentifier(identifier);
            result.setOperationName(readOperationName(resource));
            result.setCommand(command);
            result.setSuccess(exitCode == 0);
            result.setExitCode(exitCode);
            result.setDurationMs(System.currentTimeMillis() - start);
            result.setStdout(outCollector.getOutput());
            result.setStderr(errCollector.getOutput());
            return DrisResponse.success(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DrisResponse.failure("TOOL_EXEC_INTERRUPTED", "tool execution interrupted", null);
        } catch (Exception e) {
            return DrisResponse.failure("TOOL_EXEC_ERROR", e.getMessage(), null);
        }
    }

    private Map<String, Object> safeArgs(Map<String, Object> args) {
        if (args == null) {
            return new LinkedHashMap<String, Object>();
        }
        return args;
    }

    private long normalizeTimeout(Long timeoutMs) {
        if (timeoutMs == null || timeoutMs.longValue() <= 0L) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.min(timeoutMs.longValue(), MAX_TIMEOUT_MS);
    }

    private List<String> resolveCommand(Resource resource, Map<String, Object> args) {
        Map<String, Object> extra = resource.getExtra() == null ? Collections.<String, Object>emptyMap() : resource.getExtra();
        boolean windows = isWindows();

        List<String> fromList = toStringList(extra.get(windows ? "commandWindows" : "commandUnix"));
        if (fromList.isEmpty()) {
            fromList = toStringList(extra.get("command"));
        }
        if (!fromList.isEmpty()) {
            return applyArgs(fromList, args);
        }

        String commandText = toString(extra.get("commandTemplate"));
        if (commandText.isEmpty()) {
            commandText = toString(extra.get("commandText"));
        }
        if (commandText.isEmpty()) {
            return new ArrayList<String>();
        }
        String resolved = applyArgs(commandText, args);
        List<String> command = new ArrayList<String>();
        if (windows) {
            command.add("cmd");
            command.add("/c");
            command.add(resolved);
        } else {
            command.add("sh");
            command.add("-lc");
            command.add(resolved);
        }
        return command;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List)) {
            return new ArrayList<String>();
        }
        List<?> list = (List<?>) value;
        List<String> out = new ArrayList<String>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private List<String> applyArgs(List<String> tokens, Map<String, Object> args) {
        List<String> out = new ArrayList<String>();
        for (String token : tokens) {
            out.add(applyArgs(token, args));
        }
        return out;
    }

    private String applyArgs(String input, Map<String, Object> args) {
        String out = input == null ? "" : input;
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            String value = toArgumentString(entry.getValue());
            out = out.replace("${" + key + "}", value);
        }
        return out;
    }

    private String toArgumentString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            Number number = (Number) value;
            double doubleValue = number.doubleValue();
            if (doubleValue == Math.rint(doubleValue)) {
                return String.valueOf((long) doubleValue);
            }
        }
        return String.valueOf(value);
    }

    private String readOperationName(Resource resource) {
        Map<String, Object> extra = resource.getExtra();
        if (extra == null || !extra.containsKey("operationName")) {
            return "";
        }
        Object value = extra.get("operationName");
        return value == null ? "" : String.valueOf(value);
    }

    private String toString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    private static class StreamCollector extends Thread {
        private final InputStream inputStream;
        private volatile String output = "";

        private StreamCollector(InputStream inputStream) {
            this.inputStream = inputStream;
            setDaemon(true);
        }

        @Override
        public void run() {
            try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, n);
                }
                output = new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception ignore) {
                output = "";
            }
        }

        private String getOutput() {
            return output == null ? "" : output;
        }
    }
}
