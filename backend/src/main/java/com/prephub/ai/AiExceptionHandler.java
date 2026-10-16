package com.prephub.ai;

import com.prephub.common.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiExceptionHandler {
    @ExceptionHandler(AiExceptions.AiUnavailableException.class)
    ResponseEntity<ApiError> unavailable(AiExceptions.AiUnavailableException e) {
        return ResponseEntity.status(503).body(ApiError.of(503, "AI_NOT_CONFIGURED", e.getMessage(), null));
    }

    @ExceptionHandler(AiExceptions.AiOutputException.class)
    ResponseEntity<ApiError> output(AiExceptions.AiOutputException e) {
        return ResponseEntity.status(502).body(ApiError.of(502, "AI_INVALID_OUTPUT", e.getMessage(), Map.of("problems", e.getProblems())));
    }

    @ExceptionHandler(AiExceptions.AiProviderException.class)
    ResponseEntity<ApiError> provider(AiExceptions.AiProviderException e) {
        return ResponseEntity.status(502).body(ApiError.of(502, "AI_PROVIDER_ERROR", e.getMessage(), null));
    }
}
