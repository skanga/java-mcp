package com.example.mcp.tool.builtin;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EchoToolTest {

    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ResourceLimiter mockResourceLimiter;
    @Mock
    private ResourceLimiter.ResourcePermit mockResourcePermit;

    @Spy
    private EchoTool toolSpy;

    @BeforeEach
    void setUp() throws ToolExecutionException {
        when(mockResourceLimiter.acquireFileOperation("metadata_access")).thenReturn(mockResourcePermit);
        
        EchoTool realTool = new EchoTool(mockSecurityContext, mockResourceLimiter);
        toolSpy = spy(realTool);
    }

    private Map<String, Object> createArgs(String message) {
        return Map.of("message", message);
    }

    private Map<String, Object> createArgs(String message, String transform) {
        return Map.of("message", message, "transform", transform);
    }

    private Map<String, Object> createArgs(String message, int repeat) {
        return Map.of("message", message, "repeat", repeat);
    }
    
    private Map<String, Object> createArgs(String message, String transform, int repeat) {
        return Map.of("message", message, "transform", transform, "repeat", repeat);
    }

    @Test
    void testToolInstantiationAndBasicInfo() {
        assertNotNull(toolSpy);
        assertEquals("echo", toolSpy.getName());
        assertNotNull(toolSpy.getDescription());
        assertTrue(toolSpy.getInputSchema().containsKey("type"));
        assertEquals("object", toolSpy.getInputSchema().get("type"));
        assertTrue(toolSpy.getInputSchema().containsKey("properties"));
        assertTrue(toolSpy.getInputSchema().containsKey("required"));
        assertEquals(List.of("message"), toolSpy.getInputSchema().get("required"));
        verify(mockResourcePermit, never()).close(); 
    }

    // --- validateInputs Tests ---
    @Test
    void testValidateInputs_validMessage() {
        Map<String, Object> args = createArgs("Hello");
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
    }

    @Test
    void testValidateInputs_missingMessage() {
        // getRequiredString is from BaseMcpTool, which would throw if "message" is missing
        // and "message" is in "required" list in schema.
        // ToolExecutionException will be thrown by BaseMcpTool.execute() before validateInputs() is called by the tool.
        // So, this specific check in EchoTool.validateInputs might be redundant if BaseMcpTool handles it.
        // However, if validateInputs is called directly:
        Map<String, Object> args = Collections.emptyMap(); // No "message" key
        // Assuming BaseMcpTool's getRequiredString will be called first.
        // If testing validateInputs directly in isolation:
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Missing required parameter: message", ex.getMessage());
    }

    @Test
    void testValidateInputs_messageTooLong() {
        String longMessage = "a".repeat(1001);
        Map<String, Object> args = createArgs(longMessage);
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Message too long (max 1000 characters)", ex.getMessage());
    }

    @Test
    void testValidateInputs_validTransform() {
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("test", "uppercase")));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("test", "none")));
    }

    @Test
    void testValidateInputs_invalidTransform() {
        Map<String, Object> args = createArgs("test", "unknown_transform");
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Invalid transform: unknown_transform"));
    }

    @Test
    void testValidateInputs_validRepeat() {
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("test", 5)));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("test", 1)));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("test", 10)));
    }

    @Test
    void testValidateInputs_repeatTooLow() {
        Map<String, Object> args = createArgs("test", 0);
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Repeat count must be between 1 and 10", ex.getMessage());
    }

    @Test
    void testValidateInputs_repeatTooHigh() {
        Map<String, Object> args = createArgs("test", 11);
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Repeat count must be between 1 and 10", ex.getMessage());
    }

    // --- Resource Management Test ---
    @Test
    void testAcquireResourcesAndPermitClosureOnSuccess() throws ToolExecutionException {
        Map<String, Object> args = createArgs("test");
        toolSpy.execute(args); 
        verify(mockResourceLimiter).acquireFileOperation("metadata_access");
        verify(mockResourcePermit).close();
    }

    // --- Core Logic Tests ---
    @Test
    void testExecuteInternal_transformNone() throws ToolExecutionException {
        Map<String, Object> args = createArgs("Hello World", "none");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Echo: Hello World", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_transformUppercase() throws ToolExecutionException {
        Map<String, Object> args = createArgs("Hello World", "uppercase");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Echo: HELLO WORLD", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_transformLowercase() throws ToolExecutionException {
        Map<String, Object> args = createArgs("Hello World", "lowercase");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Echo: hello world", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_transformReverse() throws ToolExecutionException {
        Map<String, Object> args = createArgs("Hello", "reverse");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Echo: olleH", result.content.get(0).text);
    }
    
    @Test
    void testExecuteInternal_defaultTransformIsNone() throws ToolExecutionException {
        // No transform argument, should default to "none"
        Map<String, Object> args = createArgs("Default Test");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Echo: Default Test", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_repeatOnce_default() throws ToolExecutionException {
        Map<String, Object> args = createArgs("Repeat Once"); // Default repeat is 1
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Echo: Repeat Once", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_repeatMultipleTimes() throws ToolExecutionException {
        Map<String, Object> args = createArgs("Hi", 3);
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Echo: Hi Echo: Hi Echo: Hi", result.content.get(0).text);
    }
    
    @Test
    void testExecuteInternal_transformAndRepeat() throws ToolExecutionException {
        Map<String, Object> args = createArgs("Test Me", "uppercase", 2);
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Echo: TEST ME Echo: TEST ME", result.content.get(0).text);
    }

    // --- Error Handling ---
    @Test
    void testExecuteInternal_invalidTransform_propagatesException() {
        // This tests if executeInternal correctly re-throws or passes up ToolExecutionException
        // that would have been generated by validateInputs.
        Map<String, Object> args = createArgs("test", "bad_transform", 1);
        
        // validateInputs should catch this. If executeInternal was called directly:
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.executeInternal(args));
        assertTrue(ex.getMessage().contains("Invalid transform: bad_transform"));
    }
}
