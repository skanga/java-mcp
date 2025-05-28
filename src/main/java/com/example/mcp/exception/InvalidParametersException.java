package com.example.mcp.exception;

/**
 * Exception thrown for invalid parameters
 */
public class InvalidParametersException extends McpException {
    public InvalidParametersException(String message) {
        super(message, -32602); // Invalid params
    }
}
