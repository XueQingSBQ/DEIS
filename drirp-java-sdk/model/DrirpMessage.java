package org.bdware.drirp.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DrirpMessage {
    private int code;
    private String rid;
    private String msg;
    private JsonElement body;
    private Long ts;
    private JsonObject auth;
    private Boolean ok;
    private String errorCode;
    private String errorMessage;
    private List<String> details = new ArrayList<String>();
    private String version;
    private String traceId;
    private String nonce;
    private String idempotencyKey;

    public DrirpMessage() {
    }

    public DrirpMessage(int code, JsonElement body) {
        this.code = code;
        this.body = body;
    }

    public DrirpMessage(int code, String rid, String msg, JsonElement body) {
        this.code = code;
        this.rid = rid;
        this.msg = msg;
        this.body = body;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getRid() {
        return rid;
    }

    public void setRid(String rid) {
        this.rid = rid;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public JsonElement getBody() {
        return body;
    }

    public void setBody(JsonElement body) {
        this.body = body;
    }

    public Long getTs() {
        return ts;
    }

    public void setTs(Long ts) {
        this.ts = ts;
    }

    public JsonObject getAuth() {
        return auth;
    }

    public void setAuth(JsonObject auth) {
        this.auth = auth;
    }

    public Boolean getOk() {
        return ok;
    }

    public void setOk(Boolean ok) {
        this.ok = ok;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<String> getDetails() {
        return details;
    }

    public void setDetails(List<String> details) {
        this.details = details == null ? new ArrayList<String>() : details;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public boolean hasAuth() {
        return auth != null && auth.size() > 0;
    }

    public boolean hasBody() {
        return body != null && !body.isJsonNull();
    }

    public boolean hasIdempotencyKey() {
        return idempotencyKey != null && !idempotencyKey.trim().isEmpty();
    }

    public String bodyAsString() {
        if (body == null) {
            return null;
        }
        return body.toString();
    }

    public static DrirpMessage error(int code, String rid, String msg) {
        DrirpMessage out = new DrirpMessage(code, rid, msg, null);
        out.setOk(Boolean.FALSE);
        out.setErrorCode(DrirpErrorCodes.ERROR);
        out.setErrorMessage(msg);
        return out;
    }

    public static DrirpMessage ok(int code, String rid, JsonElement body) {
        DrirpMessage out = new DrirpMessage(code, rid, "ok", body);
        out.setOk(Boolean.TRUE);
        return out;
    }

    public static DrirpMessage of(int code, String rid, String msg, JsonElement body) {
        DrirpMessage out = new DrirpMessage(code, rid, msg, body);
        if (out.getOk() == null) {
            out.setOk(msg == null || !"ok".equalsIgnoreCase(msg) ? null : Boolean.TRUE);
        }
        return out;
    }

    public void ensureTimestamp() {
        if (ts == null || ts <= 0) {
            ts = System.currentTimeMillis();
        }
    }

    public void ensureTraceId() {
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
    }

    public void ensureVersion(String defaultVersion) {
        if (version == null || version.trim().isEmpty()) {
            version = defaultVersion;
        }
    }

    public void ensureNonce() {
        if (nonce == null || nonce.trim().isEmpty()) {
            nonce = UUID.randomUUID().toString().replace("-", "");
        }
    }

    public void ensureProtocolDefaults(String defaultVersion) {
        ensureTimestamp();
        ensureTraceId();
        ensureVersion(defaultVersion);
        ensureNonce();
    }

    public boolean isSuccess() {
        if (ok != null) {
            return ok.booleanValue();
        }
        return errorCode == null || errorCode.trim().isEmpty();
    }

    public DrirpMessage copy() {
        DrirpMessage copy = new DrirpMessage();
        copy.setCode(code);
        copy.setRid(rid);
        copy.setMsg(msg);
        copy.setBody(body == null ? null : body.deepCopy());
        copy.setTs(ts);
        copy.setAuth(auth == null ? null : auth.deepCopy());
        copy.setOk(ok);
        copy.setErrorCode(errorCode);
        copy.setErrorMessage(errorMessage);
        copy.setDetails(details == null ? new ArrayList<String>() : new ArrayList<String>(details));
        copy.setVersion(version);
        copy.setTraceId(traceId);
        copy.setNonce(nonce);
        copy.setIdempotencyKey(idempotencyKey);
        return copy;
    }
}


