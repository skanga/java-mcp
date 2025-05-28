package com.example.mcp.tool.ai;

import com.example.mcp.ai.AIClient;
import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ArchitectToolTest {

    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ResourceLimiter mockResourceLimiter;
    @Mock
    private ResourceLimiter.ResourcePermit mockResourcePermit;
    @Mock
    private AIClient mockAiClient;
    @Mock
    private AIClient.AIResponse mockAiResponse;
    @Mock
    private JsonNode mockAiUsage; // AIClient.AIUsage seems to be JsonNode based on AIResponse

    @Spy
    private ArchitectTool toolSpy;

    @Captor
    private ArgumentCaptor<AIClient.AIRequest> aiRequestCaptor;

    // ARCHITECT_SYSTEM_PROMPT from ArchitectTool.java (first few lines for assertion)
    private static final String ARCHITECT_SYSTEM_PROMPT_START = "You are an expert software architect";
    private static final int DEFAULT_MAX_TOKENS = 8000;


    @BeforeEach
    void setUp() throws ToolExecutionException {
        when(mockResourceLimiter.acquireNetworkOperation("ai_service_access")).thenReturn(mockResourcePermit);
        
        // toolSpy will be created in each test method that needs custom AIClient behavior,
        // or we can initialize it here if AIClient is always the same mock.
        ArchitectTool realTool = new ArchitectTool(mockSecurityContext, mockResourceLimiter, mockAiClient);
        toolSpy = spy(realTool);
    }

    private Map<String, Object> createArgs(String task) {
        return Map.of("task", task);
    }
    
    private Map<String, Object> createArgs(String task, Map<String, Object> additionalParams) {
        Map<String, Object> args = new java.util.HashMap<>(additionalParams);
        args.put("task", task);
        return args;
    }

    @Test
    void testToolInstantiationAndBasicInfo() {
        assertNotNull(toolSpy);
        assertEquals("architect", toolSpy.getName());
        assertNotNull(toolSpy.getDescription());
        Map<String, Object> schema = toolSpy.getInputSchema();
        assertTrue(schema.containsKey("type"));
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("task"));
        assertTrue(schema.containsKey("required"));
        assertEquals(List.of("task"), schema.get("required"));
        verify(mockResourcePermit, never()).close(); 
    }

    // --- validateInputs Tests ---
    @Test
    void testValidateInputs_validTask() {
        Map<String, Object> args = createArgs("Design a system");
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
    }

    @Test
    void testValidateInputs_missingTask() {
        Map<String, Object> args = Collections.emptyMap();
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("Missing required parameter: task", ex.getMessage());
    }

    @Test
    void testValidateInputs_validSystemType() {
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("Test", Map.of("system_type", "web_application"))));
    }

    @Test
    void testValidateInputs_invalidSystemType() {
        Map<String, Object> args = createArgs("Test", Map.of("system_type", "flying_saucer"));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Invalid system_type: flying_saucer"));
    }
    
    @Test
    void testValidateInputs_validScale() {
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("Test", Map.of("scale", "enterprise"))));
    }

    @Test
    void testValidateInputs_invalidScale() {
        Map<String, Object> args = createArgs("Test", Map.of("scale", "galactic"));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Invalid scale: galactic"));
    }

    @Test
    void testValidateInputs_validAiProvider() {
         assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("Test", Map.of("ai_provider", "openai"))));
    }
    
    @Test
    void testValidateInputs_invalidAiProvider() {
        Map<String, Object> args = createArgs("Test", Map.of("ai_provider", "my_custom_llm"));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Invalid ai_provider: my_custom_llm"));
    }

    @Test
    void testValidateInputs_validDetailLevel() {
        assertDoesNotThrow(() -> toolSpy.validateInputs(createArgs("Test", Map.of("detail_level", "comprehensive"))));
    }

    @Test
    void testValidateInputs_invalidDetailLevel() {
        Map<String, Object> args = createArgs("Test", Map.of("detail_level", "ultra_detailed"));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Invalid detail_level: ultra_detailed"));
    }


    // --- Resource Management Test ---
    @Test
    void testAcquireResourcesAndPermitClosureOnSuccess() throws Exception {
        when(mockAiClient.sendMessage(any(AIClient.AIRequest.class))).thenReturn(mockAiResponse);
        when(mockAiResponse.getContent()).thenReturn("AI response content.");
        when(mockAiResponse.getProvider()).thenReturn("mocked_provider");
        // Optional: mock usage if it's accessed
        // when(mockAiResponse.getUsage()).thenReturn(mockAiUsage);
        // when(mockAiUsage.toString()).thenReturn("Tokens: 100");


        Map<String, Object> args = createArgs("test task");
        toolSpy.execute(args); 
        verify(mockResourceLimiter).acquireNetworkOperation("ai_service_access");
        verify(mockResourcePermit).close();
    }

    // --- Core Logic Tests (`executeInternal`) ---
    @Test
    void testExecuteInternal_promptConstruction_basic() throws Exception {
        when(mockAiClient.sendMessage(aiRequestCaptor.capture())).thenReturn(mockAiResponse);
        when(mockAiResponse.getContent()).thenReturn("Basic AI response.");
        when(mockAiResponse.getProvider()).thenReturn("test_provider");

        String task = "Design a simple login system.";
        toolSpy.execute(createArgs(task));

        AIClient.AIRequest capturedRequest = aiRequestCaptor.getValue();
        assertTrue(capturedRequest.getMessage().contains("**Task**: " + task));
        assertTrue(capturedRequest.getSystemPrompt().startsWith(ARCHITECT_SYSTEM_PROMPT_START));
        assertEquals(DEFAULT_MAX_TOKENS, capturedRequest.getMaxTokens());
        assertEquals("auto", capturedRequest.getPreferredProvider()); // Default
    }

    @Test
    void testExecuteInternal_promptConstruction_allFields() throws Exception {
        when(mockAiClient.sendMessage(aiRequestCaptor.capture())).thenReturn(mockAiResponse);
        when(mockAiResponse.getContent()).thenReturn("Comprehensive AI response.");
        when(mockAiResponse.getProvider()).thenReturn("openai");
        when(mockAiResponse.getUsage()).thenReturn(mockAiUsage); // Assume usage is returned
        when(mockAiUsage.toString()).thenReturn("input: 50, output: 100, total: 150");


        String task = "Develop a scalable e-commerce platform.";
        String context = "Existing inventory system is SQL-based. Need to integrate with Stripe.";
        String systemType = "microservices";
        String scale = "enterprise";
        List<String> preferredTech = List.of("Java", "Kafka", "PostgreSQL");
        List<String> constraints = List.of("Budget < $500k", "Team size: 10 engineers");
        String aiProvider = "openai";
        String detailLevel = "comprehensive";

        Map<String, Object> args = createArgs(task, Map.of(
            "context", context,
            "system_type", systemType,
            "scale", scale,
            "preferred_technologies", preferredTech,
            "constraints", constraints,
            "ai_provider", aiProvider,
            "detail_level", detailLevel
        ));
        McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

        AIClient.AIRequest capturedRequest = aiRequestCaptor.getValue();
        String prompt = capturedRequest.getMessage();

        assertTrue(prompt.contains("**Task**: " + task));
        assertTrue(prompt.contains("**Context**: " + context));
        assertTrue(prompt.contains("**System Type**: microservices")); // underscore replaced
        assertTrue(prompt.contains("**Expected Scale**: enterprise"));
        assertTrue(prompt.contains("**Preferred/Existing Technologies**: Java, Kafka, PostgreSQL"));
        assertTrue(prompt.contains("**Constraints**:"));
        assertTrue(prompt.contains("- Budget < $500k"));
        assertTrue(prompt.contains("- Team size: 10 engineers"));
        assertTrue(prompt.contains("Please provide a comprehensive architectural analysis")); // from detail_level
        assertEquals(aiProvider, capturedRequest.getPreferredProvider());

        String output = result.content.get(0).text;
        assertTrue(output.contains("Comprehensive AI response."));
        assertTrue(output.contains("**AI Provider**: openai"));
        assertTrue(output.contains("**Token Usage**: input: 50, output: 100, total: 150"));
    }
    
    @Test
    void testExecuteInternal_detailLevel_overview() throws Exception {
        when(mockAiClient.sendMessage(aiRequestCaptor.capture())).thenReturn(mockAiResponse);
        // ... mockAiResponse setup ...

        toolSpy.execute(createArgs("task", Map.of("detail_level", "overview")));
        AIClient.AIRequest capturedRequest = aiRequestCaptor.getValue();
        assertTrue(capturedRequest.getMessage().contains("Please provide a high-level architectural overview"));
    }


    // --- Error Handling ---
    @Test
    void testExecuteInternal_aiClientException() throws Exception {
        String task = "Test AI failure.";
        when(mockAiClient.sendMessage(any(AIClient.AIRequest.class)))
            .thenThrow(new AIClient.AIException("AI service unavailable"));

        Map<String, Object> args = createArgs(task);
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.execute(args));
        assertTrue(ex.getMessage().contains("Architecture analysis failed: AI service unavailable"));
    }
    
    @Test
    void testExecuteInternal_generalException() throws Exception {
        // Simulate a different exception, e.g. NullPointerException if AIClient was null
        // For this, we'd need to re-initialize toolSpy with a null AIClient.
        ArchitectTool toolWithNullClient = new ArchitectTool(mockSecurityContext, mockResourceLimiter, null);
        // toolSpy = spy(toolWithNullClient); // This spy setup won't work as it's already spied.
        // Direct instantiation for this specific test:
        ArchitectTool directTool = new ArchitectTool(mockSecurityContext, mockResourceLimiter, null);


        Map<String, Object> args = createArgs("Test general failure.");
        // This will throw NullPointerException when aiClient.sendMessage is called.
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> directTool.execute(args));
        assertTrue(ex.getMessage().startsWith("Architect tool failed:")); // Wraps the NullPointerException
        // The exact message might be "Cannot invoke \"com.example.mcp.ai.AIClient.sendMessage(com.example.mcp.ai.AIClient$AIRequest)\" because \"this.aiClient\" is null"
        // So, checking the prefix is safer.
    }
}
