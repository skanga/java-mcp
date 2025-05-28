package com.example.mcp.exception;

/**
 * Exception thrown when a tool is not found
 */
public class ToolNotFoundException extends McpException {
    public ToolNotFoundException(String message) {
        super(message, -32601); // Method not found
    }
}
