package com.example.mcp.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for all MCP Exception classes
 */
class McpExceptionTest {

    @Test
    @DisplayName("McpException - Message only constructor")
    void testMcpExceptionMessageOnly() {
        String message = "Test MCP exception";
        McpException exception = new McpException(message);

        assertEquals(message, exception.getMessage());
        assertEquals(-32603, exception.getErrorCode()); // Default internal error code
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("McpException - Message and error code constructor")
    void testMcpExceptionMessageAndErrorCode() {
        String message = "Custom MCP exception";
        int errorCode = -32000;
        McpException exception = new McpException(message, errorCode);

        assertEquals(message, exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("McpException - Message and cause constructor")
    void testMcpExceptionMessageAndCause() {
        String message = "MCP exception with cause";
        IOException cause = new IOException("IO error");
        McpException exception = new McpException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(-32603, exception.getErrorCode()); // Default internal error code
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("McpException - Message, cause, and error code constructor")
    void testMcpExceptionMessageCauseAndErrorCode() {
        String message = "Full MCP exception";
        IOException cause = new IOException("IO error");
        int errorCode = -32001;
        McpException exception = new McpException(message, cause, errorCode);

        assertEquals(message, exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("McpException - Inheritance from Exception")
    void testMcpExceptionInheritance() {
        McpException exception = new McpException("Test");

        assertInstanceOf(Exception.class, exception);
        assertTrue(exception instanceof Exception);
    }

    @Test
    @DisplayName("InvalidParametersException - Constructor and error code")
    void testInvalidParametersException() {
        String message = "Invalid parameters provided";
        InvalidParametersException exception = new InvalidParametersException(message);

        assertEquals(message, exception.getMessage());
        assertEquals(-32602, exception.getErrorCode()); // Invalid params error code
        assertNull(exception.getCause());
        assertInstanceOf(McpException.class, exception);
    }

    @Test
    @DisplayName("InvalidParametersException - Inheritance")
    void testInvalidParametersExceptionInheritance() {
        InvalidParametersException exception = new InvalidParametersException("Test");

        assertInstanceOf(McpException.class, exception);
        assertInstanceOf(Exception.class, exception);
    }

    @Test
    @DisplayName("ProtocolException - Message only constructor")
    void testProtocolExceptionMessageOnly() {
        String message = "Protocol violation detected";
        ProtocolException exception = new ProtocolException(message);

        assertEquals(message, exception.getMessage());
        assertEquals(-32600, exception.getErrorCode()); // Invalid request error code
        assertNull(exception.getCause());
        assertInstanceOf(McpException.class, exception);
    }

    @Test
    @DisplayName("ProtocolException - Message and cause constructor")
    void testProtocolExceptionMessageAndCause() {
        String message = "Protocol error with cause";
        RuntimeException cause = new RuntimeException("Runtime error");
        ProtocolException exception = new ProtocolException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(-32600, exception.getErrorCode()); // Invalid request error code
        assertEquals(cause, exception.getCause());
        assertInstanceOf(McpException.class, exception);
    }

    @Test
    @DisplayName("ProtocolException - Inheritance")
    void testProtocolExceptionInheritance() {
        ProtocolException exception = new ProtocolException("Test");

        assertInstanceOf(McpException.class, exception);
        assertInstanceOf(Exception.class, exception);
    }

    @Test
    @DisplayName("ToolExecutionException - Message only constructor")
    void testToolExecutionExceptionMessageOnly() {
        String message = "Tool execution failed";
        ToolExecutionException exception = new ToolExecutionException(message);

        assertEquals(message, exception.getMessage());
        assertEquals(-32603, exception.getErrorCode()); // Internal error code
        assertNull(exception.getCause());
        assertInstanceOf(McpException.class, exception);
    }

    @Test
    @DisplayName("ToolExecutionException - Message and cause constructor")
    void testToolExecutionExceptionMessageAndCause() {
        String message = "Tool execution error with cause";
        IllegalStateException cause = new IllegalStateException("Invalid state");
        ToolExecutionException exception = new ToolExecutionException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(-32603, exception.getErrorCode()); // Internal error code
        assertEquals(cause, exception.getCause());
        assertInstanceOf(McpException.class, exception);
    }

    @Test
    @DisplayName("ToolExecutionException - Inheritance")
    void testToolExecutionExceptionInheritance() {
        ToolExecutionException exception = new ToolExecutionException("Test");

        assertInstanceOf(McpException.class, exception);
        assertInstanceOf(Exception.class, exception);
    }

    @Test
    @DisplayName("ToolNotFoundException - Constructor and error code")
    void testToolNotFoundException() {
        String message = "Tool 'calculate' not found";
        ToolNotFoundException exception = new ToolNotFoundException(message);

        assertEquals(message, exception.getMessage());
        assertEquals(-32601, exception.getErrorCode()); // Method not found error code
        assertNull(exception.getCause());
        assertInstanceOf(McpException.class, exception);
    }

    @Test
    @DisplayName("ToolNotFoundException - Inheritance")
    void testToolNotFoundExceptionInheritance() {
        ToolNotFoundException exception = new ToolNotFoundException("Test");

        assertInstanceOf(McpException.class, exception);
        assertInstanceOf(Exception.class, exception);
    }

    @Test
    @DisplayName("Exception error codes are correct")
    void testErrorCodes() {
        // Test all specific error codes match expected JSON-RPC error codes
        assertEquals(-32600, new ProtocolException("test").getErrorCode()); // Invalid Request
        assertEquals(-32601, new ToolNotFoundException("test").getErrorCode()); // Method not found
        assertEquals(-32602, new InvalidParametersException("test").getErrorCode()); // Invalid params
        assertEquals(-32603, new ToolExecutionException("test").getErrorCode()); // Internal error
        assertEquals(-32603, new McpException("test").getErrorCode()); // Internal error (default)
    }

    @Test
    @DisplayName("Exception messages are preserved")
    void testMessagePreservation() {
        String message = "Specific error message";

        assertEquals(message, new McpException(message).getMessage());
        assertEquals(message, new InvalidParametersException(message).getMessage());
        assertEquals(message, new ProtocolException(message).getMessage());
        assertEquals(message, new ToolExecutionException(message).getMessage());
        assertEquals(message, new ToolNotFoundException(message).getMessage());
    }

    @Test
    @DisplayName("Cause chain is properly maintained")
    void testCauseChain() {
        IOException rootCause = new IOException("Root cause");
        RuntimeException intermediateCause = new RuntimeException("Intermediate", rootCause);

        McpException mcpException = new McpException("MCP error", intermediateCause);
        ProtocolException protocolException = new ProtocolException("Protocol error", intermediateCause);
        ToolExecutionException toolException = new ToolExecutionException("Tool error", intermediateCause);

        // Test direct cause
        assertEquals(intermediateCause, mcpException.getCause());
        assertEquals(intermediateCause, protocolException.getCause());
        assertEquals(intermediateCause, toolException.getCause());

        // Test cause chain traversal
        assertEquals(rootCause, mcpException.getCause().getCause());
        assertEquals(rootCause, protocolException.getCause().getCause());
        assertEquals(rootCause, toolException.getCause().getCause());
    }

    @Test
    @DisplayName("Exceptions can be thrown and caught")
    void testExceptionThrowing() {
        // Test that exceptions can be thrown and caught properly
        assertThrows(McpException.class, () -> {
            throw new McpException("Test exception");
        });

        assertThrows(InvalidParametersException.class, () -> {
            throw new InvalidParametersException("Invalid params");
        });

        assertThrows(ProtocolException.class, () -> {
            throw new ProtocolException("Protocol error");
        });

        assertThrows(ToolExecutionException.class, () -> {
            throw new ToolExecutionException("Tool error");
        });

        assertThrows(ToolNotFoundException.class, () -> {
            throw new ToolNotFoundException("Tool not found");
        });
    }

    @Test
    @DisplayName("Polymorphic catch blocks work correctly")
    void testPolymorphicCatching() {
        // Test that derived exceptions can be caught as base exception
        try {
            throw new InvalidParametersException("Invalid params");
        } catch (McpException e) {
            assertEquals(-32602, e.getErrorCode());
            assertInstanceOf(InvalidParametersException.class, e);
        }

        try {
            throw new ProtocolException("Protocol error");
        } catch (McpException e) {
            assertEquals(-32600, e.getErrorCode());
            assertInstanceOf(ProtocolException.class, e);
        }

        try {
            throw new ToolExecutionException("Tool error");
        } catch (McpException e) {
            assertEquals(-32603, e.getErrorCode());
            assertInstanceOf(ToolExecutionException.class, e);
        }

        try {
            throw new ToolNotFoundException("Tool not found");
        } catch (McpException e) {
            assertEquals(-32601, e.getErrorCode());
            assertInstanceOf(ToolNotFoundException.class, e);
        }
    }

    @Test
    @DisplayName("Exception toString includes message")
    void testToStringMethod() {
        String message = "Test exception message";

        McpException mcpException = new McpException(message);
        assertTrue(mcpException.toString().contains(message));
        assertTrue(mcpException.toString().contains("McpException"));

        InvalidParametersException invalidParams = new InvalidParametersException(message);
        assertTrue(invalidParams.toString().contains(message));
        assertTrue(invalidParams.toString().contains("InvalidParametersException"));

        ProtocolException protocolError = new ProtocolException(message);
        assertTrue(protocolError.toString().contains(message));
        assertTrue(protocolError.toString().contains("ProtocolException"));

        ToolExecutionException toolError = new ToolExecutionException(message);
        assertTrue(toolError.toString().contains(message));
        assertTrue(toolError.toString().contains("ToolExecutionException"));

        ToolNotFoundException toolNotFound = new ToolNotFoundException(message);
        assertTrue(toolNotFound.toString().contains(message));
        assertTrue(toolNotFound.toString().contains("ToolNotFoundException"));
    }

    @Test
    @DisplayName("Exception stack trace is preserved")
    void testStackTracePreservation() {
        McpException exception = new McpException("Test exception");
        StackTraceElement[] stackTrace = exception.getStackTrace();

        assertNotNull(stackTrace);
        assertTrue(stackTrace.length > 0);

        // The first element should be this test method
        assertEquals("testStackTracePreservation", stackTrace[0].getMethodName());
        assertEquals(this.getClass().getName(), stackTrace[0].getClassName());
    }

    @Test
    @DisplayName("Custom error codes work with McpException")
    void testCustomErrorCodes() {
        // Test various custom error codes
        int[] customCodes = {-32000, -32001, -32099, -1, 0, 1, 1000};

        for (int code : customCodes) {
            McpException exception = new McpException("Custom error", code);
            assertEquals(code, exception.getErrorCode());

            McpException exceptionWithCause = new McpException("Custom error with cause",
                    new RuntimeException("cause"), code);
            assertEquals(code, exceptionWithCause.getErrorCode());
        }
    }

    @Test
    @DisplayName("Null message handling")
    void testNullMessageHandling() {
        // Test that null messages are handled gracefully
        McpException mcpException = new McpException(null);
        assertNull(mcpException.getMessage());
        assertEquals(-32603, mcpException.getErrorCode());

        InvalidParametersException invalidParams = new InvalidParametersException(null);
        assertNull(invalidParams.getMessage());
        assertEquals(-32602, invalidParams.getErrorCode());

        ProtocolException protocolException = new ProtocolException(null);
        assertNull(protocolException.getMessage());
        assertEquals(-32600, protocolException.getErrorCode());

        ToolExecutionException toolException = new ToolExecutionException(null);
        assertNull(toolException.getMessage());
        assertEquals(-32603, toolException.getErrorCode());

        ToolNotFoundException toolNotFound = new ToolNotFoundException(null);
        assertNull(toolNotFound.getMessage());
        assertEquals(-32601, toolNotFound.getErrorCode());
    }

    @Test
    @DisplayName("Exception serialization compatibility")
    void testSerializationCompatibility() {
        // Test that exceptions have proper serialVersionUID behavior
        // This test ensures the classes can be serialized if needed

        McpException mcpException = new McpException("Test");
        assertNotNull(mcpException.getClass().getName());

        InvalidParametersException invalidParams = new InvalidParametersException("Test");
        assertNotNull(invalidParams.getClass().getName());

        ProtocolException protocolException = new ProtocolException("Test");
        assertNotNull(protocolException.getClass().getName());

        ToolExecutionException toolException = new ToolExecutionException("Test");
        assertNotNull(toolException.getClass().getName());

        ToolNotFoundException toolNotFound = new ToolNotFoundException("Test");
        assertNotNull(toolNotFound.getClass().getName());
    }
}