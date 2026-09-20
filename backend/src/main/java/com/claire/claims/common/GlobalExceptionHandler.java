package com.claire.claims.common;

import com.claire.claims.common.ApiExceptions.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaves this application as RFC 9457 application/problem+json.
 * One shape for the client to handle, with field-level detail on validation.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE = "https://claire.example/problems/";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), "not-found", req, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Conflict with current state", ex.getMessage(), "conflict", req, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Business rule violated", ex.getMessage(), "business-rule", req, null);
    }

    @ExceptionHandler(NotImplementedYetException.class)
    public ResponseEntity<ProblemDetail> handleNotImplemented(NotImplementedYetException ex, HttpServletRequest req) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("phase", 2);
        extra.put("handoffRef", ex.getHandoffRef());
        extra.put("specification", "docs/PHASE2_HANDOFF.md#" + ex.getHandoffRef());
        ResponseEntity<ProblemDetail> body = build(HttpStatus.NOT_IMPLEMENTED, "Planned for Phase 2",
                ex.getMessage(), "phase-2-pending", req, extra);
        // Explicit marker so a client can distinguish "not built yet" from "broken".
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .header("X-Phase", "2")
                .body(body.getBody());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParam(MissingServletRequestParameterException ex,
                                                            HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Missing request parameter",
                     "Required parameter '" + ex.getParameterName() + "' is missing.",
                     "missing-parameter", req, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest req) {
        Class<?> required = ex.getRequiredType();
        String expected = required == null ? "the expected type"
                : (required.isEnum() ? "one of " + java.util.Arrays.toString(required.getEnumConstants())
                                     : required.getSimpleName());
        return build(HttpStatus.BAD_REQUEST, "Invalid request parameter",
                     "Parameter '" + ex.getName() + "' must be " + expected + ".",
                     "type-mismatch", req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest req) {
        Map<String, Object> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        for (var oe : ex.getBindingResult().getGlobalErrors()) {
            errors.put(oe.getObjectName(), oe.getDefaultMessage());
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("errors", errors);
        return build(HttpStatus.BAD_REQUEST, "Validation failed",
                     errors.size() + " field(s) failed validation", "validation", req, extra);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                               HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Concurrent modification",
                     "This record was changed by someone else while you were editing it. Reload and try again.",
                     "optimistic-lock", req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex,
                                                          HttpServletRequest req) {
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Data integrity violation",
                     "The request violates a database constraint, most likely a duplicate unique value.",
                     "data-integrity", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Forbidden",
                     "Your role does not permit this action.", "forbidden", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized",
                     "Authentication failed.", "unauthorized", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                     "Something went wrong. The incident has been logged.", "internal", req, null);
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String title, String detail,
                                                String type, HttpServletRequest req,
                                                Map<String, Object> extra) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create(BASE + type));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("timestamp", OffsetDateTime.now().toString());
        if (extra != null) {
            extra.forEach(pd::setProperty);
        }
        return ResponseEntity.status(status).body(pd);
    }
}
