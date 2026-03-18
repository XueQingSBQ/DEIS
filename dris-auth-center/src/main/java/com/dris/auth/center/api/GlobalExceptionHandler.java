package com.dris.auth.center.api;

import com.dris.auth.center.auth.AuthException;
import com.dris.auth.center.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuth(AuthException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() == null
                ? ex.getMessage()
                : ex.getBindingResult().getFieldError().getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAny(Exception ex) {
        return ResponseEntity.internalServerError().body(ApiResponse.fail("INTERNAL_ERROR", ex.getMessage()));
    }
}
