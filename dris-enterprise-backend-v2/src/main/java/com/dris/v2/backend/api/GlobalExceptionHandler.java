package com.dris.v2.backend.api;

import com.dris.v2.backend.dto.ApiResponse;
import com.dris.v2.backend.model.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() == null
                ? ex.getMessage()
                : ex.getBindingResult().getFieldError().getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex == null ? "" : String.valueOf(ex.getName());
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", name + " type is invalid"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAny(Exception ex) {
        return ResponseEntity.internalServerError().body(ApiResponse.fail("INTERNAL_ERROR", ex.getMessage()));
    }
}
