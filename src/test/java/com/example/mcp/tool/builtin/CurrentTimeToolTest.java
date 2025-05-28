package com.example.mcp.tool.builtin;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CurrentTimeToolTest {

    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ResourceLimiter mockResourceLimiter;
    @Mock
    private ResourceLimiter.ResourcePermit mockResourcePermit;

    @Spy
    private CurrentTimeTool toolSpy;
    
    // Fixed time for testing
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2023, 10, 26, 10, 30, 0);
    private static final long FIXED_TIMESTAMP = FIXED_NOW.toInstant(ZoneOffset.UTC).toEpochMilli();


    @BeforeEach
    void setUp() throws ToolExecutionException {
        when(mockResourceLimiter.acquireFileOperation("metadata_access")).thenReturn(mockResourcePermit);
        
        CurrentTimeTool realTool = new CurrentTimeTool(mockSecurityContext, mockResourceLimiter);
        toolSpy = spy(realTool);
    }

    private Map<String, Object> createArgs(String format) {
        if (format == null) {
            return Collections.emptyMap();
        }
        return Map.of("format", format);
    }
    
     private Map<String, Object> createArgs(String format, String timezone) {
        return Map.of("format", format, "timezone", timezone);
    }


    @Test
    void testToolInstantiationAndBasicInfo() {
        assertNotNull(toolSpy);
        assertEquals("current_time", toolSpy.getName());
        assertNotNull(toolSpy.getDescription());
        assertTrue(toolSpy.getInputSchema().containsKey("type"));
        verify(mockResourcePermit, never()).close(); 
    }

    // --- validateInputs Tests ---
    @Test
    void testValidateInputs_validFormats() {
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("iso")));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("readable")));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("timestamp")));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs(null))); // Default format
    }

    @Test
    void testValidateInputs_invalidFormat() {
        Map<String, Object> args = createArgs("invalid_format");
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Invalid format: invalid_format"));
    }
    
    @Test
    void testValidateInputs_timezoneServer() {
        // Currently only "server" is supported, so this should pass
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("readable", "server")));
    }

    @Test
    void testValidateInputs_timezoneOther() {
        // If strict validation for timezone (only "server") was added to validateInputs, this would throw
        // As per CurrentTimeTool's validateInputs, it doesn't throw for other timezones,
        // but it's a good place to note if requirements change.
        // For now, this should pass as the current implementation does not strictly validate against "server" only.
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("readable", "UTC")));
    }


    // --- Resource Management Test ---
    @Test
    void testAcquireResourcesAndPermitClosureOnSuccess() throws ToolExecutionException {
         // Mock LocalDateTime.now() for deterministic output
        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class);
             MockedStatic<System> mockedSystem = mockStatic(System.class)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(FIXED_NOW);
            mockedSystem.when(System::currentTimeMillis).thenReturn(FIXED_TIMESTAMP);

            Map<String, Object> args = createArgs("readable");
            toolSpy.execute(args); 
            verify(mockResourceLimiter).acquireFileOperation("metadata_access");
            verify(mockResourcePermit).close();
        }
    }

    // --- Core Logic Tests ---
    @Test
    void testExecuteInternal_formatReadable() throws ToolExecutionException {
        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(FIXED_NOW);
            
            Map<String, Object> args = createArgs("readable");
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

            assertFalse(result.isError);
            String expectedTimeString = FIXED_NOW.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String expectedOutput = String.format("Current server time (readable): %s", expectedTimeString);
            assertEquals(expectedOutput, result.content.get(0).text);
        }
    }

    @Test
    void testExecuteInternal_formatIso() throws ToolExecutionException {
        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(FIXED_NOW);

            Map<String, Object> args = createArgs("iso");
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

            assertFalse(result.isError);
            String expectedTimeString = FIXED_NOW.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String expectedOutput = String.format("Current server time (iso): %s", expectedTimeString);
            assertEquals(expectedOutput, result.content.get(0).text);
        }
    }
    
    @Test
    void testExecuteInternal_formatTimestamp() throws ToolExecutionException {
        // Mock System.currentTimeMillis() for deterministic timestamp
        try (MockedStatic<System> mockedSystem = mockStatic(System.class)) {
            mockedSystem.when(System::currentTimeMillis).thenReturn(FIXED_TIMESTAMP);

            Map<String, Object> args = createArgs("timestamp");
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

            assertFalse(result.isError);
            String expectedOutput = String.format("Current server time (timestamp): %d", FIXED_TIMESTAMP);
            assertEquals(expectedOutput, result.content.get(0).text);
        }
    }
    
    @Test
    void testExecuteInternal_defaultFormatIsReadable() throws ToolExecutionException {
         try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(FIXED_NOW);
            
            Map<String, Object> args = Collections.emptyMap(); // No format specified, should default to "readable"
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

            assertFalse(result.isError);
            String expectedTimeString = FIXED_NOW.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            // The format string in output will be "readable" (the default used)
            String expectedOutput = String.format("Current server time (readable): %s", expectedTimeString);
            assertEquals(expectedOutput, result.content.get(0).text);
        }
    }

    // --- Error Handling ---
    @Test
    void testExecuteInternal_invalidFormat_propagatesException() {
        // This tests if executeInternal correctly re-throws or passes up ToolExecutionException
        // that would have been generated by validateInputs (though validateInputs is called first by BaseMcpTool).
        // Here, we simulate it being passed to executeInternal directly.
        Map<String, Object> args = createArgs("bad_format");
        
        // validateInputs should catch this, but if executeInternal was called directly:
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.executeInternal(args));
        assertTrue(ex.getMessage().contains("Invalid format: bad_format"));
    }
}
