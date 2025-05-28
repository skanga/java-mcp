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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RandomToolTest {

    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ResourceLimiter mockResourceLimiter;
    @Mock
    private ResourceLimiter.ResourcePermit mockResourcePermit;

    @Spy
    private RandomTool toolSpy;

    @BeforeEach
    void setUp() throws ToolExecutionException {
        when(mockResourceLimiter.acquireFileOperation("metadata_access")).thenReturn(mockResourcePermit);
        
        RandomTool realTool = new RandomTool(mockSecurityContext, mockResourceLimiter);
        toolSpy = spy(realTool);
    }

    private Map<String, Object> createArgs(String type) {
        return Map.of("type", type);
    }
    
    private Map<String, Object> createArgs(String type, Map<String, Object> params) {
        Map<String, Object> args = new java.util.HashMap<>(params);
        args.put("type", type);
        return args;
    }

    @Test
    void testToolInstantiationAndBasicInfo() {
        assertNotNull(toolSpy);
        assertEquals("random", toolSpy.getName());
        assertNotNull(toolSpy.getDescription());
        Map<String, Object> schema = toolSpy.getInputSchema();
        assertTrue(schema.containsKey("type"));
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("type"));
        assertTrue(properties.containsKey("min"));
        assertTrue(properties.containsKey("max"));
        assertTrue(properties.containsKey("count"));
        // "required" is not specified in the schema in RandomTool.java
        assertNull(schema.get("required"));
        verify(mockResourcePermit, never()).close(); 
    }

    // --- validateInputs Tests ---
    @Test
    void testValidateInputs_validTypes() {
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("integer")));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("decimal")));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("boolean")));
        assertDoesNotThrow(() -> toolSpy.validateInputs(Collections.emptyMap()))); // Default type is integer
    }

    @Test
    void testValidateInputs_invalidType() {
        Map<String, Object> args = createArgs("string_type");
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Unknown random type: string_type"));
    }

    @Test
    void testValidateInputs_validCount() {
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("integer", Map.of("count", 1))));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("integer", Map.of("count", 10))));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("integer", Map.of("count", 20))));
    }

    @Test
    void testValidateInputs_countTooLow() {
        Map<String, Object> args = createArgs("integer", Map.of("count", 0));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Count must be between 1 and 20", ex.getMessage());
    }

    @Test
    void testValidateInputs_countTooHigh() {
        Map<String, Object> args = createArgs("integer", Map.of("count", 21));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Count must be between 1 and 20", ex.getMessage());
    }

    @Test
    void testValidateInputs_integer_minGreaterThanMax() {
        Map<String, Object> args = createArgs("integer", Map.of("min", 100, "max", 10));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Min value cannot be greater than max value for type 'integer'", ex.getMessage());
    }
    
    @Test
    void testValidateInputs_integer_minEqualsMax() {
         Map<String, Object> args = createArgs("integer", Map.of("min", 10, "max", 10));
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
    }

    @Test
    void testValidateInputs_decimal_minGreaterThanMax() {
        Map<String, Object> args = createArgs("decimal", Map.of("min", 1.0, "max", 0.0));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Min value must be less than max value for type 'decimal'", ex.getMessage());
    }

    @Test
    void testValidateInputs_decimal_minEqualsMax() {
        Map<String, Object> args = createArgs("decimal", Map.of("min", 1.0, "max", 1.0));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Min value must be less than max value for type 'decimal'", ex.getMessage());
    }
    
    @Test
    void testValidateInputs_boolean_minMaxIgnored() {
        // min/max are not validated for boolean type in validateInputs, so this should pass
        Map<String, Object> args = createArgs("boolean", Map.of("min", 100, "max", 0));
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
    }


    // --- Resource Management Test ---
    @Test
    void testAcquireResourcesAndPermitClosureOnSuccess() throws ToolExecutionException {
        Map<String, Object> args = createArgs("integer");
        toolSpy.execute(args); 
        verify(mockResourceLimiter).acquireFileOperation("metadata_access");
        verify(mockResourcePermit).close();
    }

    // --- Core Logic Tests (`executeInternal`) ---
    @Test
    void testExecuteInternal_integer_singleValue_defaultRange() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("integer"));
        assertFalse(result.isError);
        String output = result.content.get(0).text;
        assertTrue(output.startsWith("Random integer: "));
        int value = Integer.parseInt(output.substring("Random integer: ".length()));
        assertTrue(value >= 1 && value <= 100);
    }

    @Test
    void testExecuteInternal_integer_multipleValues_customRange() throws ToolExecutionException {
        int count = 5;
        int min = 10;
        int max = 20;
        Map<String, Object> params = Map.of("count", count, "min", min, "max", max);
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("integer", params));
        
        assertFalse(result.isError);
        String output = result.content.get(0).text;
        String expectedPrefix = String.format("Random integer values (%d): ", count);
        assertTrue(output.startsWith(expectedPrefix));
        
        String[] valuesStr = output.substring(expectedPrefix.length()).split(", ");
        assertEquals(count, valuesStr.length);
        for (String s : valuesStr) {
            int value = Integer.parseInt(s);
            assertTrue(value >= min && value <= max, "Value " + value + " not in range [" + min + "," + max + "]");
        }
    }
    
    @Test
    void testExecuteInternal_integer_minEqualsMax_singleValue() throws ToolExecutionException {
        int minMax = 7;
        Map<String, Object> params = Map.of("min", minMax, "max", minMax);
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("integer", params));
        assertFalse(result.isError);
        String output = result.content.get(0).text;
        assertEquals(String.format("Random integer: %d", minMax), output);
    }


    @Test
    void testExecuteInternal_decimal_singleValue_defaultRange() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("decimal"));
        assertFalse(result.isError);
        String output = result.content.get(0).text;
        assertTrue(output.startsWith("Random decimal: "));
        // Example: Random decimal: 0.123456
        String valueStr = output.substring("Random decimal: ".length());
        double value = Double.parseDouble(valueStr);
        assertTrue(value >= 0.0 && value < 1.0, "Value " + value + " not in range [0.0, 1.0)");
        // Check formatting to 6 decimal places
        assertTrue(valueStr.matches("\\d\\.\\d{6}"), "Decimal format incorrect: " + valueStr);
    }

    @Test
    void testExecuteInternal_decimal_multipleValues_customRange() throws ToolExecutionException {
        int count = 3;
        double min = 5.0;
        double max = 5.5;
         Map<String, Object> params = Map.of("count", count, "min", min, "max", max);
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("decimal", params));
        
        assertFalse(result.isError);
        String output = result.content.get(0).text;
        String expectedPrefix = String.format("Random decimal values (%d): ", count);
        assertTrue(output.startsWith(expectedPrefix));
        
        String[] valuesStr = output.substring(expectedPrefix.length()).split(", ");
        assertEquals(count, valuesStr.length);
        for (String s : valuesStr) {
            double value = Double.parseDouble(s);
            assertTrue(value >= min && value < max, "Value " + value + " not in range [" + min + "," + max + ")");
             assertTrue(s.matches("\\d\\.\\d{6}"), "Decimal format incorrect: " + s);
        }
    }

    @Test
    void testExecuteInternal_boolean_singleValue() throws ToolExecutionException {
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("boolean"));
        assertFalse(result.isError);
        String output = result.content.get(0).text;
        assertTrue(output.equals("Random boolean: true") || output.equals("Random boolean: false"));
    }
    
    @Test
    void testExecuteInternal_boolean_multipleValues() throws ToolExecutionException {
        int count = 10; // Generate a few booleans to increase chance of both true/false
        Map<String, Object> params = Map.of("count", count);
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(createArgs("boolean", params));
        
        assertFalse(result.isError);
        String output = result.content.get(0).text;
        String expectedPrefix = String.format("Random boolean values (%d): ", count);
        assertTrue(output.startsWith(expectedPrefix));
        
        String[] valuesStr = output.substring(expectedPrefix.length()).split(", ");
        assertEquals(count, valuesStr.length);
        boolean foundTrue = false;
        boolean foundFalse = false;
        for (String s : valuesStr) {
            assertTrue(s.equals("true") || s.equals("false"), "Value is not a boolean: " + s);
            if (s.equals("true")) foundTrue = true;
            if (s.equals("false")) foundFalse = true;
        }
        // With 10 values, it's highly probable both true and false appear, but not guaranteed.
        // This part of the assertion is probabilistic. For strictness, mock Random.
        // For now, just checking they are valid booleans is enough.
        // assertTrue(foundTrue && foundFalse, "Expected both true and false values with 10 counts");
    }

    // --- Error Handling ---
    @Test
    void testExecuteInternal_invalidType_propagatesException() {
        Map<String, Object> args = createArgs("unknown_type");
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.executeInternal(args));
        assertTrue(ex.getMessage().contains("Unknown random type: unknown_type"));
    }
}
