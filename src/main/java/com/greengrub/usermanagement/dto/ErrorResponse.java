package com.greengrub.usermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standard error response structure for API errors
 * Provides consistent error format across the application
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    /**
     * HTTP status code
     */
    private int status;

    /**
     * Error type/category
     */
    private String error;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * Detailed error information (optional)
     */
    private String details;

    /**
     * API path where error occurred
     */
    private String path;

    /**
     * Timestamp when error occurred
     */
    private LocalDateTime timestamp;

    /**
     * Validation errors (for field-level validation failures)
     */
    private Map<String, String> validationErrors;

    /**
     * Additional error details (optional)
     */
    private List<String> errors;
}
