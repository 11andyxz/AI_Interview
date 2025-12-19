package com.aiinterview.model;

public enum ErrorCode {
    // Authentication errors (1000-1099)
    AUTH_FAILED(1000, "Authentication failed"),
    INVALID_TOKEN(1001, "Invalid or expired token"),
    UNAUTHORIZED(1002, "Unauthorized access"),
    USER_NOT_FOUND(1003, "User not found"),
    INVALID_CREDENTIALS(1004, "Invalid username or password"),
    
    // Interview errors (1100-1199)
    INTERVIEW_NOT_FOUND(1100, "Interview not found"),
    INTERVIEW_ALREADY_COMPLETED(1101, "Interview already completed"),
    INTERVIEW_SESSION_EXPIRED(1102, "Interview session expired"),
    
    // Payment errors (1200-1299)
    PAYMENT_FAILED(1200, "Payment processing failed"),
    SUBSCRIPTION_NOT_FOUND(1201, "Subscription not found"),
    SUBSCRIPTION_EXPIRED(1202, "Subscription expired"),
    INSUFFICIENT_PERMISSIONS(1203, "Insufficient subscription permissions"),
    
    // WebSocket errors (1300-1399)
    WEBSOCKET_CONNECTION_FAILED(1300, "WebSocket connection failed"),
    WEBSOCKET_MESSAGE_INVALID(1301, "Invalid WebSocket message"),
    
    // General errors (1400-1499)
    INTERNAL_ERROR(1400, "Internal server error"),
    INVALID_REQUEST(1401, "Invalid request parameters"),
    RESOURCE_NOT_FOUND(1402, "Resource not found"),
    VALIDATION_ERROR(1403, "Validation error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

