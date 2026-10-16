package com.prephub.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(404).body(ApiError.of(404, "NOT_FOUND", e.getMessage(), null));
    }

    @ExceptionHandler(DuplicateQuestionException.class)
    ResponseEntity<ApiError> duplicate(DuplicateQuestionException e) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("matchType", "EXACT_DUPLICATE");
        d.put("matchingQuestionId", e.getMatchingQuestionId());
        d.put("matchingText", e.getMatchingText());
        return ResponseEntity.status(409).body(ApiError.of(409, "DUPLICATE_QUESTION", e.getMessage(), d));
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "BAD_REQUEST", e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, Object> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ApiError.of(400, "VALIDATION_FAILED", "Request validation failed", fields));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiError.of(413, "FILE_TOO_LARGE", "Upload exceeds the size limit", null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(ApiError.of(409, "CONSTRAINT_VIOLATION", "The change conflicts with existing data", null));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> malformed(Exception e) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "MALFORMED_REQUEST", "Malformed request: " + e.getClass().getSimpleName(), null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse er) {
            int st = er.getStatusCode().value();
            return ResponseEntity.status(st).body(ApiError.of(st, "HTTP_" + st, er.getBody().getDetail(), null));
        }
        log.error("Unhandled error: {}", e.getClass().getName(), e);
        log.error("Error msg: {}",e.getMessage());
        return ResponseEntity.status(500).body(ApiError.of(500, "INTERNAL_ERROR", "Unexpected error", null));
    }
}
