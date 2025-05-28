package com.example.mcp.exception;

/**
 * Exception thrown when tool execution fails
 */
public class ToolExecutionException extends McpException {
    public ToolExecutionException(String message) {
        super(message, -32603); // Internal error
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause, -32603);
    }
}
