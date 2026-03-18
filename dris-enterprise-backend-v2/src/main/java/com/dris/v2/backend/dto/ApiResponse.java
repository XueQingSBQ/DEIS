package com.dris.v2.backend.dto;

public class ApiResponse<T> {
    private boolean ok;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> out = new ApiResponse<T>();
        out.ok = true;
        out.code = "OK";
        out.data = data;
        return out;
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> out = new ApiResponse<T>();
        out.ok = false;
        out.code = code == null ? "ERROR" : code;
        out.message = message == null ? "error" : message;
        return out;
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

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
