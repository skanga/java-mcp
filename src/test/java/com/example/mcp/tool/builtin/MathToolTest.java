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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MathToolTest {

    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ResourceLimiter mockResourceLimiter;
    @Mock
    private ResourceLimiter.ResourcePermit mockResourcePermit;

    @Spy
    private MathTool toolSpy;

    @BeforeEach
    void setUp() throws ToolExecutionException {
        when(mockResourceLimiter.acquireFileOperation("metadata_access")).thenReturn(mockResourcePermit);
        
        MathTool realTool = new MathTool(mockSecurityContext, mockResourceLimiter);
        toolSpy = spy(realTool);
    }

    private Map<String, Object> createArgs(String operation, Number a) {
        return Map.of("operation", operation, "a", a);
    }

    private Map<String, Object> createArgs(String operation, Number a, Number b) {
        return Map.of("operation", operation, "a", a, "b", b);
    }
    
    private Map<String, Object> createArgsForValidation(String operation, Object a, Object b) {
        Map<String, Object> args = new java.util.HashMap<>();
        if (operation != null) args.put("operation", operation);
        if (a != null) args.put("a", a);
        if (b != null) args.put("b", b);
        return args;
    }


    @Test
    void testToolInstantiationAndBasicInfo() {
        assertNotNull(toolSpy);
        assertEquals("math", toolSpy.getName());
        assertNotNull(toolSpy.getDescription());
        Map<String, Object> schema = toolSpy.getInputSchema();
        assertTrue(schema.containsKey("type"));
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("operation"));
        assertTrue(properties.containsKey("a"));
        assertTrue(properties.containsKey("b"));
        assertTrue(schema.containsKey("required"));
        assertEquals(List.of("operation", "a"), schema.get("required"));
        verify(mockResourcePermit, never()).close(); 
    }

    // --- validateInputs Tests ---
    @Test
    void testValidateInputs_validOperations() {
        List<String> operations = List.of("add", "subtract", "multiply", "divide", "power", "sqrt", "abs");
        for (String op : operations) {
            Map<String, Object> args;
            if (op.equals("sqrt") || op.equals("abs")) {
                args = createArgsForValidation(op, 5, null);
            } else {
                args = createArgsForValidation(op, 5, 2);
            }
            final Map<String, Object> finalArgs = args; // For lambda
            assertDoesNotThrow(() -> toolSpy.validateInputs(finalArgs), "Validation failed for: " + op);
        }
    }

    @Test
    void testValidateInputs_invalidOperation() {
        Map<String, Object> args = createArgsForValidation("exponentiate", 5, 2);
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Unknown operation: exponentiate"));
    }

    @Test
    void testValidateInputs_missingOperation() {
        Map<String, Object> args = createArgsForValidation(null, 5, 2);
        // This will be caught by BaseMcpTool's getRequiredString
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Missing required parameter: operation", ex.getMessage());
    }

    @Test
    void testValidateInputs_missingA() {
        Map<String, Object> args = createArgsForValidation("add", null, 2);
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Missing required parameter: a", ex.getMessage());
    }

    @Test
    void testValidateInputs_missingB_forRequiredOperations() {
        List<String> opsRequiringB = List.of("add", "subtract", "multiply", "divide", "power");
        for (String op : opsRequiringB) {
            Map<String, Object> args = createArgsForValidation(op, 5, null);
            final Map<String, Object> finalArgs = args; // For lambda
            ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(finalArgs), "Validation failed for missing 'b' in: " + op);
            assertEquals("Missing required parameter: b", ex.getMessage());
        }
    }
    
    @Test
    void testValidateInputs_bNotRequired_forSqrtAndAbs() {
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgsForValidation("sqrt", 5, null)));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgsForValidation("abs", 5, null)));
         // Still valid if 'b' is provided but not used
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgsForValidation("sqrt", 5, 100)));
    }

    @Test
    void testValidateInputs_nonNumericA() {
        Map<String, Object> args = createArgsForValidation("add", "not_a_number", 2);
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Invalid number for parameter a: not_a_number", ex.getMessage());
    }

    @Test
    void testValidateInputs_nonNumericB() {
        Map<String, Object> args = createArgsForValidation("add", 5, "not_a_number");
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Invalid number for parameter b: not_a_number", ex.getMessage());
    }


    // --- Resource Management Test ---
    @Test
    void testAcquireResourcesAndPermitClosureOnSuccess() throws ToolExecutionException {
        Map<String, Object> args = createArgs("add", 10, 5);
        toolSpy.execute(args); 
        verify(mockResourceLimiter).acquireFileOperation("metadata_access");
        verify(mockResourcePermit).close();
    }

    // --- Core Logic Tests (`executeInternal`) ---
    @Test
    void testExecuteInternal_add() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("add", 10, 5));
        assertEquals("10 + 5 = 15", result.content.get(0).text); // .000000 removed by cleanup
    }

    @Test
    void testExecuteInternal_subtract() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("subtract", 10, 5.5));
        assertEquals("10 - 5.5 = 4.5", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_multiply() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("multiply", 10, 2.5));
        assertEquals("10 × 2.5 = 25", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_divide() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("divide", 10, 4));
        assertEquals("10 ÷ 4 = 2.5", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_divide_byZero() {
        Map<String, Object> args = createArgs("divide", 10, 0);
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.execute(args));
        assertEquals("Math calculation failed: Division by zero is not allowed", ex.getMessage());
    }
    
    @Test
    void testExecuteInternal_divide_precisionAndFormatting() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("divide", 1, 3));
        // 1.000000 / 3.000000 = 0.333333
        assertEquals("1 ÷ 3 = 0.333333", result.content.get(0).text); 
    }


    @Test
    void testExecuteInternal_power() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("power", 2, 3));
        assertEquals("2 ^ 3 = 8", result.content.get(0).text);
    }
    
    @Test
    void testExecuteInternal_power_fractional() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("power", 4, 0.5));
        assertEquals("4 ^ 0.5 = 2", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_sqrt() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("sqrt", 9));
        assertEquals("√9 = 3", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_sqrt_negativeNumber() {
        Map<String, Object> args = createArgs("sqrt", -9);
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.execute(args));
        assertEquals("Math calculation failed: Cannot calculate square root of negative number", ex.getMessage());
    }
    
    @Test
    void testExecuteInternal_abs_positive() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("abs", 5.5));
        assertEquals("|5.5| = 5.5", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_abs_negative() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("abs", -5.5));
        assertEquals("|-5.5| = 5.5", result.content.get(0).text);
    }
    
    @Test
    void testExecuteInternal_abs_zero() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("abs", 0));
        assertEquals("|0| = 0", result.content.get(0).text);
    }

    // --- Error Handling ---
    @Test
    void testExecuteInternal_unknownOperation_propagatesException() {
        Map<String, Object> args = createArgs("unknown_op", 5, 2);
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.executeInternal(args));
        assertTrue(ex.getMessage().contains("Unknown operation: unknown_op"));
    }
}
