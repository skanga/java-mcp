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
public class HelloToolTest {

    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ResourceLimiter mockResourceLimiter;
    @Mock
    private ResourceLimiter.ResourcePermit mockResourcePermit;

    @Spy
    private HelloTool toolSpy;

    @BeforeEach
    void setUp() throws ToolExecutionException {
        when(mockResourceLimiter.acquireFileOperation("metadata_access")).thenReturn(mockResourcePermit);
        
        HelloTool realTool = new HelloTool(mockSecurityContext, mockResourceLimiter);
        toolSpy = spy(realTool);
    }

    private Map<String, Object> createArgs() {
        return Collections.emptyMap(); // For defaults
    }

    private Map<String, Object> createArgsWithName(String name) {
        return Map.of("name", name);
    }

    private Map<String, Object> createArgsWithLanguage(String language) {
        return Map.of("language", language);
    }
    
    private Map<String, Object> createArgs(String name, String language) {
        return Map.of("name", name, "language", language);
    }

    @Test
    void testToolInstantiationAndBasicInfo() {
        assertNotNull(toolSpy);
        assertEquals("hello", toolSpy.getName());
        assertNotNull(toolSpy.getDescription());
        assertTrue(toolSpy.getInputSchema().containsKey("type"));
        assertEquals("object", toolSpy.getInputSchema().get("type"));
        assertTrue(toolSpy.getInputSchema().containsKey("properties"));
        // "name" and "language" are optional, so "required" list should be empty or not present.
        // The schema provided in HelloTool.java does not have a "required" field.
        assertNull(toolSpy.getInputSchema().get("required"));
        verify(mockResourcePermit, never()).close(); 
    }

    // --- validateInputs Tests ---
    @Test
    void testValidateInputs_validNameAndLanguage() {
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("User", "en")));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("Mundo", "es")));
    }

    @Test
    void testValidateInputs_defaultNameAndLanguage() {
        // name is optional (defaults to "World"), language is optional (defaults to "en")
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs())); 
    }

    @Test
    void testValidateInputs_nameTooLong() {
        String longName = "a".repeat(101);
        Map<String, Object> args = createArgsWithName(longName);
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Name too long (max 100 characters)", ex.getMessage());
    }
    
    @Test
    void testValidateInputs_nameMaxLength() {
        String maxLengthName = "a".repeat(100);
        Map<String, Object> args = createArgsWithName(maxLengthName);
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
    }


    @Test
    void testValidateInputs_validLanguages() {
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgsWithLanguage("en")));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgsWithLanguage("es")));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgsWithLanguage("fr")));
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgsWithLanguage("de")));
    }

    @Test
    void testValidateInputs_invalidLanguage() {
        Map<String, Object> args = createArgsWithLanguage("xx");
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Invalid language: xx"));
    }

    // --- Resource Management Test ---
    @Test
    void testAcquireResourcesAndPermitClosureOnSuccess() throws ToolExecutionException {
        Map<String, Object> args = createArgs(); // Default args
        toolSpy.execute(args); 
        verify(mockResourceLimiter).acquireFileOperation("metadata_access");
        verify(mockResourcePermit).close();
    }

    // --- Core Logic Tests (`executeInternal`) ---
    @Test
    void testExecuteInternal_defaultNameAndLanguage() throws ToolExecutionException {
        Map<String, Object> args = createArgs();
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Hello, World! 👋 Greetings from the MCP Hello World Server!", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_customName_defaultLanguage() throws ToolExecutionException {
        Map<String, Object> args = createArgsWithName("Alice");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Hello, Alice! 👋 Greetings from the MCP Hello World Server!", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_languageEnglish_withName() throws ToolExecutionException {
        Map<String, Object> args = createArgs("Bob", "en");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Hello, Bob! 👋 Greetings from the MCP Hello World Server!", result.content.get(0).text);
    }
    
    @Test
    void testExecuteInternal_languageEnglish_defaultName() throws ToolExecutionException {
        Map<String, Object> args = createArgsWithLanguage("en");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Hello, World! 👋 Greetings from the MCP Hello World Server!", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_languageSpanish_withName() throws ToolExecutionException {
        Map<String, Object> args = createArgs("Carlos", "es");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("¡Hola, Carlos! 👋 ¡Saludos desde el servidor MCP!", result.content.get(0).text);
    }
    
    @Test
    void testExecuteInternal_languageSpanish_defaultName() throws ToolExecutionException {
        Map<String, Object> args = createArgsWithLanguage("es");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("¡Hola, World! 👋 ¡Saludos desde el servidor MCP!", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_languageFrench_withName() throws ToolExecutionException {
        Map<String, Object> args = createArgs("Diane", "fr");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Bonjour, Diane! 👋 Salutations du serveur MCP!", result.content.get(0).text);
    }
    
    @Test
    void testExecuteInternal_languageFrench_defaultName() throws ToolExecutionException {
        Map<String, Object> args = createArgsWithLanguage("fr");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Bonjour, World! 👋 Salutations du serveur MCP!", result.content.get(0).text);
    }

    @Test
    void testExecuteInternal_languageGerman_withName() throws ToolExecutionException {
        Map<String, Object> args = createArgs("Eva", "de");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Hallo, Eva! 👋 Grüße vom MCP-Server!", result.content.get(0).text);
    }
    
    @Test
    void testExecuteInternal_languageGerman_defaultName() throws ToolExecutionException {
        Map<String, Object> args = createArgsWithLanguage("de");
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
        assertFalse(result.isError);
        assertEquals("Hallo, World! 👋 Grüße vom MCP-Server!", result.content.get(0).text);
    }

    // --- Error Handling ---
    @Test
    void testExecuteInternal_invalidLanguage_propagatesException() {
        // This tests if executeInternal correctly re-throws or passes up ToolExecutionException
        // that would have been generated by validateInputs.
        Map<String, Object> args = createArgs("Test", "xx"); // "xx" is an invalid language
        
        // validateInputs should catch this, but if executeInternal was called directly:
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.executeInternal(args));
        // The actual error message in executeInternal is more generic for this specific case,
        // as the switch default just uses the "en" greeting.
        // The validation of language enum is in validateInputs.
        // So, if it bypasses validateInputs, it would default to English.
        // To test the ToolExecutionException from the switch default (if it had one), we'd need to modify the switch.
        // The current switch defaults to English, so it won't throw for an "invalid" language *if* validateInputs is bypassed.
        // This test relies on validateInputs being called first by BaseMcpTool.
        // If we want to test executeInternal in isolation with an invalid language that *would* cause its switch to fail:
        // This requires the switch's default to throw an exception.
        // The current SUT's switch default is "default -> English greeting", so it won't throw.
        // The ToolExecutionException for invalid language actually comes from validateInputs.
        
        // Let's adjust to verify the behavior if validateInputs is bypassed:
        // It should default to English.
        McpModels.CallToolResponse.CallToolResult result = toolSpy.executeInternal(args);
        assertFalse(result.isError);
        assertEquals("Hello, Test! 👋 Greetings from the MCP Hello World Server!", result.content.get(0).text);
    }
}
