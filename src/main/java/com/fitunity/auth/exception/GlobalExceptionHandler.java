package com.fitunity.auth.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailExistsException(EmailExistsException ex) {
        log.warn("Email exists: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), "EMAIL_EXISTS");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentialsException(InvalidCredentialsException ex) {
        log.warn("Invalid credentials: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), "INVALID_CREDENTIALS");
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<Map<String, Object>> handleAccountDisabledException(AccountDisabledException ex) {
        log.warn("Account disabled: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), "ACCOUNT_DISABLED");
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyAttemptsException(TooManyAttemptsException ex) {
        log.warn("Too many attempts: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), "TOO_MANY_ATTEMPTS");
    }

    @ExceptionHandler(ReplayAttackException.class)
    public ResponseEntity<Map<String, Object>> handleReplayAttackException(ReplayAttackException ex) {
        log.error("REPLAY ATTACK DETECTED: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), "REPLAY_ATTACK");
    }

    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleSessionExpiredException(SessionExpiredException ex) {
        log.warn("Session expired: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), "SESSION_EXPIRED");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        StringBuilder message = new StringBuilder();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            if (message.length() > 0) message.append("; ");
            message.append(error.getField()).append(": ").append(error.getDefaultMessage());
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message.toString(), "VALIDATION_ERROR");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Accès refusé", "ACCESS_DENIED");
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationCredentialsNotFoundException(AuthenticationCredentialsNotFoundException ex) {
        log.warn("Authentication required: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentification requise", "AUTHENTICATION_REQUIRED");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_ARGUMENT");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue", "INTERNAL_ERROR");
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status,
            String message,
            String errorCode
    ) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", status.value());
        error.put("error", errorCode);
        error.put("message", message);
        error.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return ResponseEntity.status(status).body(error);
    }
}
