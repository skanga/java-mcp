package com.example.mcp.handler;

import com.example.mcp.exception.McpException;
import com.example.mcp.exception.ProtocolException;
import com.example.mcp.model.McpModels;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized error handling for the MCP server
 */
public class ErrorHandler {
    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);

    /**
     * Handle MCP-specific exceptions and return appropriate JSON-RPC error responses
     */
    public static void handleMcpError(Context ctx, Throwable error, Object requestId) {
        McpModels.ErrorResponse response = createErrorResponse(requestId, error);

        // Log the error appropriately
        if (error instanceof McpException) {
            if (((McpException) error).getErrorCode() == -32601) { // Method not found
                logger.debug("Method not found: {}", error.getMessage());
            } else if (((McpException) error).getErrorCode() == -32602) { // Invalid params
                logger.info("Invalid parameters: {}", error.getMessage());
            } else {
                logger.error("MCP error: {}", error.getMessage(), error);
            }
        } else {
            logger.error("Unexpected error in MCP request", error);
        }

        ctx.status(HttpStatus.OK) // JSON-RPC errors are sent with 200 OK
                .json(response);
    }

    /**
     * Handle general HTTP errors
     */
    public static void handleHttpError(Context ctx, Throwable error) {
        logger.error("HTTP error on {} {}", ctx.method(), ctx.path(), error);

        if (error instanceof JsonProcessingException) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(createHttpErrorResponse("Invalid JSON", HttpStatus.BAD_REQUEST.getCode()));
        } else {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .json(createHttpErrorResponse("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR.getCode()));
        }
    }

    /**
     * Create an MCP error response
     */
    private static McpModels.ErrorResponse createErrorResponse(Object requestId, Throwable error) {
        McpModels.ErrorResponse response = new McpModels.ErrorResponse();
        response.id = requestId;

        McpModels.ErrorResponse.Error mcpError = new McpModels.ErrorResponse.Error();

        if (error instanceof McpException) {
            McpException mcpException = (McpException) error;
            mcpError.code = mcpException.getErrorCode();
            mcpError.message = mcpException.getMessage();

            // Include stack trace for internal errors in development
            if (mcpException.getErrorCode() == -32603 && isDebugMode()) {
                mcpError.data = getErrorDetails(error);
            }
        } else if (error instanceof JsonMappingException || error instanceof JsonProcessingException) {
            mcpError.code = -32700; // Parse error
            mcpError.message = "Invalid JSON: " + error.getMessage();
        } else if (error instanceof IllegalArgumentException) {
            mcpError.code = -32602; // Invalid params
            mcpError.message = "Invalid parameters: " + error.getMessage();
        } else {
            mcpError.code = -32603; // Internal error
            mcpError.message = "Internal error: " + error.getMessage();

            if (isDebugMode()) {
                mcpError.data = getErrorDetails(error);
            }
        }

        response.error = mcpError;
        return response;
    }

    /**
     * Create an HTTP error response (for non-MCP endpoints)
     */
    private static Object createHttpErrorResponse(String message, int statusCode) {
        return java.util.Map.of(
                "error", true,
                "message", message,
                "status", statusCode,
                "timestamp", java.time.Instant.now().toString()
        );
    }

    /**
     * Get detailed error information for debugging
     */
    private static Object getErrorDetails(Throwable error) {
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("type", error.getClass().getSimpleName());
        details.put("message", error.getMessage());

        if (error.getCause() != null) {
            details.put("cause", error.getCause().getMessage());
        }

        // Include stack trace in debug mode
        if (isDebugMode()) {
            java.util.List<String> stackTrace = new java.util.ArrayList<>();
            for (StackTraceElement element : error.getStackTrace()) {
                stackTrace.add(element.toString());
                if (stackTrace.size() >= 10) break; // Limit stack trace size
            }
            details.put("stackTrace", stackTrace);
        }

        return details;
    }

    /**
     * Check if debug mode is enabled
     */
    private static boolean isDebugMode() {
        String debugMode = System.getProperty("mcp.debug", System.getenv("MCP_DEBUG"));
        return "true".equalsIgnoreCase(debugMode) || logger.isDebugEnabled();
    }

    /**
     * Validate JSON-RPC request structure
     */
    public static void validateJsonRpcRequest(java.util.Map<String, Object> request) throws ProtocolException {
        if (request == null) {
            throw new ProtocolException("Request cannot be null");
        }

        // Check JSON-RPC version
        Object jsonrpc = request.get("jsonrpc");
        if (!"2.0".equals(jsonrpc)) {
            throw new ProtocolException("Invalid or missing jsonrpc version");
        }

        // Check method
        Object method = request.get("method");
        if (method == null || !(method instanceof String) || ((String) method).trim().isEmpty()) {
            throw new ProtocolException("Invalid or missing method");
        }

        // ID can be string, number, or null, but should be present for requests
        if (!request.containsKey("id")) {
            throw new ProtocolException("Missing request id");
        }
    }

    /**
     * Extract request ID safely from request
     */
    public static Object extractRequestId(java.util.Map<String, Object> request) {
        if (request == null) {
            return null;
        }
        return request.get("id");
    }

    /**
     * Extract method name safely from request
     */
    public static String extractMethod(java.util.Map<String, Object> request) {
        if (request == null) {
            return null;
        }
        Object method = request.get("method");
        return method instanceof String ? (String) method : null;
    }
}
