package com.park.gate.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Gestionnaire global des exceptions pour l'API REST.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Gere les erreurs de validation (ex: @NotBlank).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error", "Validation failed",
                        "message", ex.getMessage()
                ));
    }
    
    /**
     * Gere les erreurs de conversion de type (ex: enum invalide).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error", "Invalid parameter",
                        "message", "Invalid value for parameter: " + ex.getName()
                ));
    }
}
