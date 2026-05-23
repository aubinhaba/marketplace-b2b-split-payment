package com.aubin.commons.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String errorCode,
        String message,
        List<FieldError> fieldErrors,
        Instant timestamp
) {

    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, null, Instant.now());
    }

    public static ErrorResponse ofValidation(String message, List<FieldError> fieldErrors) {
        return new ErrorResponse("VALIDATION_ERROR", message, fieldErrors, Instant.now());
    }

    public record FieldError(String field, String message) {}
}
