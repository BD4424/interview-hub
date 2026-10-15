package com.prephub.common;

import java.time.Instant;
import java.util.Map;

public record ApiError(Instant timestamp, int status, String code, String message, Map<String, Object> details) {
    public static ApiError of(int status, String code, String message, Map<String, Object> details) {
        return new ApiError(Instant.now(), status, code, message, details == null ? Map.of() : details);
    }
}
