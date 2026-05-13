package com.practice3.api;

import com.practice3.metrics.AppMetrics;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final AppMetrics metrics;

    public GlobalExceptionHandler(AppMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> notFound(NoSuchElementException ex, HttpServletRequest req) {
        metrics.httpErrors.increment();
        return error(HttpStatus.NOT_FOUND, ex, req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> badRequest(MethodArgumentNotValidException ex, HttpServletRequest req) {
        metrics.httpErrors.increment();
        return error(HttpStatus.BAD_REQUEST, ex, req);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> dbUnavailable(DataAccessException ex, HttpServletRequest req) {
        metrics.httpErrors.increment();
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception ex, HttpServletRequest req) {
        metrics.httpErrors.increment();
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ex, req);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, Exception ex, HttpServletRequest req) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                req.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}

