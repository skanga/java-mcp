package com.example.mcp.exception;

/**
 * Exception thrown for protocol violations
 */
public class ProtocolException extends McpException {
    public ProtocolException(String message) {
        super(message, -32600); // Invalid request
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause, -32600);
    }
}
