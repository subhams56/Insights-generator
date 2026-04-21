package com.insights.generator.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException e) {
        // Check if the error is specifically a Quota/Rate Limit error from Google
        if (e.getMessage() != null && e.getMessage().contains("429") || e.getMessage().contains("quota")) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "status", "error",
                    "message", "The AI is currently catching its breath (Rate Limit Exceeded). Please try again in a few seconds.",
                    "type", "QUOTA_EXCEEDED"
            ));
        }

        // Fallback for other runtime errors
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "An unexpected error occurred: " + e.getMessage()
        ));
    }
}