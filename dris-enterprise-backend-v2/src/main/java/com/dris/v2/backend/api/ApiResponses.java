package com.dris.v2.backend.api;

import com.dris.v2.backend.dto.ApiResponse;
import com.dris.v2.backend.contract.DrisResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ApiResponses {
    private ApiResponses() {
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> fail(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status)
                .body(ApiResponse.<T>fail(code, message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> fromDris(DrisResponse<T> response) {
        if (response == null) {
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "NULL_RESPONSE", "dris response is null");
        }
        if (response.isOk()) {
            return ok(response.getData());
        }
        HttpStatus status = "NOT_FOUND".equalsIgnoreCase(response.getCode()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return fail(status, response.getCode(), response.getMessage());
    }
}
