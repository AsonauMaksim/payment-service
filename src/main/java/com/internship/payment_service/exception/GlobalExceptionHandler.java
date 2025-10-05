package com.internship.payment_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ApiError build(HttpStatus status, String msg, String path, List<String> errors) {
        return ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(msg)
                .path(path)
                .errors(errors)
                .build();
    }

    private ApiError build(HttpStatus status, String msg, String path) {
        return build(status, msg, path, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(build(HttpStatus.BAD_REQUEST, "Validation error", req.getRequestURI(), errors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(build(HttpStatus.BAD_REQUEST, "Missing parameter: " + ex.getParameterName(), req.getRequestURI()));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            ConversionFailedException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(build(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(RandomApiUnavailableException.class)
    public ResponseEntity<ApiError> handleRandomApi(RandomApiUnavailableException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(build(HttpStatus.BAD_GATEWAY, ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req.getRequestURI()));
    }
}
