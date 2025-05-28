package com.example.mcp.exception;

/**
 * Base exception for MCP-related errors
 */
public class McpException extends Exception {
    private final int errorCode;

    public McpException(String message) {
        this(message, -32603); // Internal error
    }

    public McpException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public McpException(String message, Throwable cause) {
        this(message, cause, -32603);
    }

    public McpException(String message, Throwable cause, int errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
