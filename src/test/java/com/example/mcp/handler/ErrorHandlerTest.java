package com.example.mcp.handler;

import com.example.mcp.exception.McpException;
import com.example.mcp.exception.ProtocolException;
import com.example.mcp.model.McpModels;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ErrorHandler Tests")
class ErrorHandlerTest {

    @Mock
    private Context mockContext;

    private String originalDebugProperty;
    private String originalDebugEnv;

    @BeforeEach
    void setUp() {
        // Save original debug settings
        originalDebugProperty = System.getProperty("mcp.debug");
        originalDebugEnv = System.getenv("MCP_DEBUG");

        // Clear debug mode for clean tests
        System.clearProperty("mcp.debug");

        // Set up minimal mock context - only stub what's actually used
        lenient().when(mockContext.status(any(HttpStatus.class))).thenReturn(mockContext);
        lenient().when(mockContext.json(any())).thenReturn(mockContext);
    }

    @AfterEach
    void tearDown() {
        // Restore original debug settings
        if (originalDebugProperty != null) {
            System.setProperty("mcp.debug", originalDebugProperty);
        } else {
            System.clearProperty("mcp.debug");
        }
    }

    @Test
    @DisplayName("Handle MCP Exception with method not found error code")
    void handleMcpError_MethodNotFound() {
        // Given
        Object requestId = "test-123";
        McpException error = new McpException("Method 'unknown' not found", -32601);

        // When
        ErrorHandler.handleMcpError(mockContext, error, requestId);

        // Then
        verify(mockContext).status(HttpStatus.OK);

        ArgumentCaptor<McpModels.ErrorResponse> responseCaptor = ArgumentCaptor.forClass(McpModels.ErrorResponse.class);
        verify(mockContext).json(responseCaptor.capture());

        McpModels.ErrorResponse response = responseCaptor.getValue();
        assertEquals(requestId, response.id);
        assertEquals(-32601, response.error.code);
        assertEquals("Method 'unknown' not found", response.error.message);
        assertNull(response.error.data);
    }

    @Test
    @DisplayName("Handle MCP Exception with invalid params error code")
    void handleMcpError_InvalidParams() {
        // Given
        Object requestId = 42;
        McpException error = new McpException("Missing required parameter", -32602);

        // When
        ErrorHandler.handleMcpError(mockContext, error, requestId);

        // Then
        ArgumentCaptor<McpModels.ErrorResponse> responseCaptor = ArgumentCaptor.forClass(McpModels.ErrorResponse.class);
        verify(mockContext).json(responseCaptor.capture());

        McpModels.ErrorResponse response = responseCaptor.getValue();
        assertEquals(requestId, response.id);
        assertEquals(-32602, response.error.code);
        assertEquals("Missing required parameter", response.error.message);
    }

    @Test
    @DisplayName("Handle MCP Exception with internal error in debug mode")
    void handleMcpError_InternalErrorWithDebug() {
        // Given
        System.setProperty("mcp.debug", "true");
        Object requestId = "debug-test";
        McpException error = new McpException("Internal processing error", -32603);

        // When
        ErrorHandler.handleMcpError(mockContext, error, requestId);

        // Then
        ArgumentCaptor<McpModels.ErrorResponse> responseCaptor = ArgumentCaptor.forClass(McpModels.ErrorResponse.class);
        verify(mockContext).json(responseCaptor.capture());

        McpModels.ErrorResponse response = responseCaptor.getValue();
        assertEquals(-32603, response.error.code);
        assertNotNull(response.error.data); // Should include debug details
    }

    @Test
    @DisplayName("Handle JSON processing exception")
    void handleMcpError_JsonProcessingException() {
        // Given
        Object requestId = "json-error";
        JsonProcessingException error = mock(JsonProcessingException.class);
        when(error.getMessage()).thenReturn("Unexpected character");

        // When
        ErrorHandler.handleMcpError(mockContext, error, requestId);

        // Then
        ArgumentCaptor<McpModels.ErrorResponse> responseCaptor = ArgumentCaptor.forClass(McpModels.ErrorResponse.class);
        verify(mockContext).json(responseCaptor.capture());

        McpModels.ErrorResponse response = responseCaptor.getValue();
        assertEquals(-32700, response.error.code);
        assertTrue(response.error.message.contains("Invalid JSON"));
    }

    @Test
    @DisplayName("Handle JSON mapping exception")
    void handleMcpError_JsonMappingException() {
        // Given
        Object requestId = "mapping-error";
        JsonMappingException error = mock(JsonMappingException.class);
        when(error.getMessage()).thenReturn("Cannot deserialize");

        // When
        ErrorHandler.handleMcpError(mockContext, error, requestId);

        // Then
        ArgumentCaptor<McpModels.ErrorResponse> responseCaptor = ArgumentCaptor.forClass(McpModels.ErrorResponse.class);
        verify(mockContext).json(responseCaptor.capture());

        McpModels.ErrorResponse response = responseCaptor.getValue();
        assertEquals(-32700, response.error.code);
        assertTrue(response.error.message.contains("Invalid JSON"));
    }

    @Test
    @DisplayName("Handle IllegalArgumentException")
    void handleMcpError_IllegalArgumentException() {
        // Given
        Object requestId = "arg-error";
        IllegalArgumentException error = new IllegalArgumentException("Invalid argument value");

        // When
        ErrorHandler.handleMcpError(mockContext, error, requestId);

        // Then
        ArgumentCaptor<McpModels.ErrorResponse> responseCaptor = ArgumentCaptor.forClass(McpModels.ErrorResponse.class);
        verify(mockContext).json(responseCaptor.capture());

        McpModels.ErrorResponse response = responseCaptor.getValue();
        assertEquals(-32602, response.error.code);
        assertTrue(response.error.message.contains("Invalid parameters"));
    }

    @Test
    @DisplayName("Handle generic exception")
    void handleMcpError_GenericException() {
        // Given
        Object requestId = "generic-error";
        RuntimeException error = new RuntimeException("Something went wrong");

        // When
        ErrorHandler.handleMcpError(mockContext, error, requestId);

        // Then
        ArgumentCaptor<McpModels.ErrorResponse> responseCaptor = ArgumentCaptor.forClass(McpModels.ErrorResponse.class);
        verify(mockContext).json(responseCaptor.capture());

        McpModels.ErrorResponse response = responseCaptor.getValue();
        assertEquals(-32603, response.error.code);
        assertTrue(response.error.message.contains("Internal error"));
    }

    @Test
    @DisplayName("Handle HTTP error with JSON processing exception")
    void handleHttpError_JsonProcessingException() {
        // Given
        when(mockContext.method()).thenReturn(HandlerType.POST);
        when(mockContext.path()).thenReturn("/mcp");
        JsonProcessingException error = mock(JsonProcessingException.class);
        lenient().when(error.getMessage()).thenReturn("Malformed JSON");

        // When
        ErrorHandler.handleHttpError(mockContext, error);

        // Then
        verify(mockContext).status(HttpStatus.BAD_REQUEST);
        ArgumentCaptor<Map> responseCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockContext).json(responseCaptor.capture());

        Map<String, Object> response = responseCaptor.getValue();
        assertEquals(true, response.get("error"));
        assertEquals("Invalid JSON", response.get("message"));
        assertEquals(400, response.get("status"));
        assertNotNull(response.get("timestamp"));
    }

    @Test
    @DisplayName("Handle HTTP error with generic exception")
    void handleHttpError_GenericException() {
        // Given
        when(mockContext.method()).thenReturn(HandlerType.POST);
        when(mockContext.path()).thenReturn("/mcp");
        RuntimeException error = new RuntimeException("Database connection failed");

        // When
        ErrorHandler.handleHttpError(mockContext, error);

        // Then
        verify(mockContext).status(HttpStatus.INTERNAL_SERVER_ERROR);
        ArgumentCaptor<Map> responseCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockContext).json(responseCaptor.capture());

        Map<String, Object> response = responseCaptor.getValue();
        assertEquals(true, response.get("error"));
        assertEquals("Internal server error", response.get("message"));
        assertEquals(500, response.get("status"));
    }

    @Test
    @DisplayName("Validate valid JSON-RPC request")
    void validateJsonRpcRequest_ValidRequest() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "tools/list");
        request.put("id", "test-123");

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> ErrorHandler.validateJsonRpcRequest(request));
    }

    @Test
    @DisplayName("Validate JSON-RPC request with null request")
    void validateJsonRpcRequest_NullRequest() {
        // When/Then
        ProtocolException exception = assertThrows(ProtocolException.class,
                () -> ErrorHandler.validateJsonRpcRequest(null));
        assertEquals("Request cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Validate JSON-RPC request with missing jsonrpc version")
    void validateJsonRpcRequest_MissingJsonRpc() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("method", "tools/list");
        request.put("id", "test-123");

        // When/Then
        ProtocolException exception = assertThrows(ProtocolException.class,
                () -> ErrorHandler.validateJsonRpcRequest(request));
        assertEquals("Invalid or missing jsonrpc version", exception.getMessage());
    }

    @Test
    @DisplayName("Validate JSON-RPC request with wrong jsonrpc version")
    void validateJsonRpcRequest_WrongJsonRpcVersion() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "1.0");
        request.put("method", "tools/list");
        request.put("id", "test-123");

        // When/Then
        ProtocolException exception = assertThrows(ProtocolException.class,
                () -> ErrorHandler.validateJsonRpcRequest(request));
        assertEquals("Invalid or missing jsonrpc version", exception.getMessage());
    }

    @Test
    @DisplayName("Validate JSON-RPC request with missing method")
    void validateJsonRpcRequest_MissingMethod() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", "test-123");

        // When/Then
        ProtocolException exception = assertThrows(ProtocolException.class,
                () -> ErrorHandler.validateJsonRpcRequest(request));
        assertEquals("Invalid or missing method", exception.getMessage());
    }

    @Test
    @DisplayName("Validate JSON-RPC request with empty method")
    void validateJsonRpcRequest_EmptyMethod() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "");
        request.put("id", "test-123");

        // When/Then
        ProtocolException exception = assertThrows(ProtocolException.class,
                () -> ErrorHandler.validateJsonRpcRequest(request));
        assertEquals("Invalid or missing method", exception.getMessage());
    }

    @Test
    @DisplayName("Validate JSON-RPC request with whitespace-only method")
    void validateJsonRpcRequest_WhitespaceMethod() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "   ");
        request.put("id", "test-123");

        // When/Then
        ProtocolException exception = assertThrows(ProtocolException.class,
                () -> ErrorHandler.validateJsonRpcRequest(request));
        assertEquals("Invalid or missing method", exception.getMessage());
    }

    @Test
    @DisplayName("Validate JSON-RPC request with non-string method")
    void validateJsonRpcRequest_NonStringMethod() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", 123);
        request.put("id", "test-123");

        // When/Then
        ProtocolException exception = assertThrows(ProtocolException.class,
                () -> ErrorHandler.validateJsonRpcRequest(request));
        assertEquals("Invalid or missing method", exception.getMessage());
    }

    @Test
    @DisplayName("Validate JSON-RPC request with missing id")
    void validateJsonRpcRequest_MissingId() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "tools/list");

        // When/Then
        ProtocolException exception = assertThrows(ProtocolException.class,
                () -> ErrorHandler.validateJsonRpcRequest(request));
        assertEquals("Missing request id", exception.getMessage());
    }

    @Test
    @DisplayName("Validate JSON-RPC request with null id (should be valid)")
    void validateJsonRpcRequest_NullId() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "tools/list");
        request.put("id", null);

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> ErrorHandler.validateJsonRpcRequest(request));
    }

    @Test
    @DisplayName("Extract request ID from valid request")
    void extractRequestId_ValidRequest() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("id", "test-123");

        // When
        Object result = ErrorHandler.extractRequestId(request);

        // Then
        assertEquals("test-123", result);
    }

    @Test
    @DisplayName("Extract request ID from null request")
    void extractRequestId_NullRequest() {
        // When
        Object result = ErrorHandler.extractRequestId(null);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Extract request ID when missing")
    void extractRequestId_MissingId() {
        // Given
        Map<String, Object> request = new HashMap<>();

        // When
        Object result = ErrorHandler.extractRequestId(request);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Extract method from valid request")
    void extractMethod_ValidRequest() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("method", "tools/list");

        // When
        String result = ErrorHandler.extractMethod(request);

        // Then
        assertEquals("tools/list", result);
    }

    @Test
    @DisplayName("Extract method from null request")
    void extractMethod_NullRequest() {
        // When
        String result = ErrorHandler.extractMethod(null);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Extract method when missing")
    void extractMethod_MissingMethod() {
        // Given
        Map<String, Object> request = new HashMap<>();

        // When
        String result = ErrorHandler.extractMethod(request);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Extract method when non-string")
    void extractMethod_NonStringMethod() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("method", 123);

        // When
        String result = ErrorHandler.extractMethod(request);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Debug mode detection with system property")
    void debugMode_SystemProperty() {
        // Given
        System.setProperty("mcp.debug", "true");
        McpException error = new McpException("Test error", -32603);

        // When
        ErrorHandler.handleMcpError(mockContext, error, "test");

        // Then
        ArgumentCaptor<McpModels.ErrorResponse> responseCaptor = ArgumentCaptor.forClass(McpModels.ErrorResponse.class);
        verify(mockContext).json(responseCaptor.capture());

        McpModels.ErrorResponse response = responseCaptor.getValue();
        assertNotNull(response.error.data); // Should include debug details
    }

    @Test
    @DisplayName("Error details include stack trace in debug mode")
    void errorDetails_IncludeStackTrace() {
        // Given
        System.setProperty("mcp.debug", "true");
        RuntimeException error = new RuntimeException("Test error");

        // When
        ErrorHandler.handleMcpError(mockContext, error, "test");

        // Then
        ArgumentCaptor<McpModels.ErrorResponse> responseCaptor = ArgumentCaptor.forClass(McpModels.ErrorResponse.class);
        verify(mockContext).json(responseCaptor.capture());

        McpModels.ErrorResponse response = responseCaptor.getValue();
        assertNotNull(response.error.data);

        // Verify the data contains expected debug information
        if (response.error.data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) response.error.data;
            assertEquals("RuntimeException", details.get("type"));
            assertEquals("Test error", details.get("message"));
            assertNotNull(details.get("stackTrace"));
            assertTrue(details.get("stackTrace") instanceof List);
        }
    }

    @Test
    @DisplayName("Error with cause includes cause information")
    void errorDetails_WithCause() {
        // Given
        System.setProperty("mcp.debug", "true");
        RuntimeException cause = new RuntimeException("Root cause");
        RuntimeException error = new RuntimeException("Wrapper error", cause);

        // When
        ErrorHandler.handleMcpError(mockContext, error, "test");

        // Then
        ArgumentCaptor<McpModels.ErrorResponse> responseCaptor = ArgumentCaptor.forClass(McpModels.ErrorResponse.class);
        verify(mockContext).json(responseCaptor.capture());

        McpModels.ErrorResponse response = responseCaptor.getValue();

        if (response.error.data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) response.error.data;
            assertEquals("Root cause", details.get("cause"));
        }
    }
}
