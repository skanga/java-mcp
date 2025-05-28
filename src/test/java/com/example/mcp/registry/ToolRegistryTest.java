package com.example.mcp.registry;

import com.example.mcp.exception.McpException;
import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.exception.ToolNotFoundException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolRegistryTest {

    @Mock
    private McpTool mockTool;

    @Mock
    private McpTool anotherMockTool;

    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
    }

    @AfterEach
    void tearDown() {
        if (toolRegistry != null) {
            toolRegistry.shutdown();
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create registry with default parameters")
        void shouldCreateRegistryWithDefaults() {
            ToolRegistry registry = new ToolRegistry();

            assertNotNull(registry);
            assertEquals(0, registry.getToolCount());

            registry.shutdown();
        }

        @Test
        @DisplayName("Should create registry with custom parameters")
        void shouldCreateRegistryWithCustomParameters() {
            ToolRegistry registry = new ToolRegistry(5, 60);

            assertNotNull(registry);
            assertEquals(0, registry.getToolCount());

            registry.shutdown();
        }
    }

    @Nested
    @DisplayName("Tool Registration Tests")
    class ToolRegistrationTests {

        @Test
        @DisplayName("Should register valid tool successfully")
        void shouldRegisterValidTool() {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test tool description");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            // When
            toolRegistry.registerTool(mockTool);

            // Then
            assertTrue(toolRegistry.hasTool("test-tool"));
            assertEquals(1, toolRegistry.getToolCount());
            assertTrue(toolRegistry.getToolNames().contains("test-tool"));
        }

        @Test
        @DisplayName("Should throw exception when registering null tool")
        void shouldThrowExceptionForNullTool() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> toolRegistry.registerTool(null)
            );
            assertEquals("Tool cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when tool name is null")
        void shouldThrowExceptionForNullToolName() {
            when(mockTool.getName()).thenReturn(null);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> toolRegistry.registerTool(mockTool)
            );
            assertEquals("Tool name cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when tool name is empty")
        void shouldThrowExceptionForEmptyToolName() {
            when(mockTool.getName()).thenReturn("   ");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> toolRegistry.registerTool(mockTool)
            );
            assertEquals("Tool name cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should handle tool with null description")
        void shouldHandleToolWithNullDescription() {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn(null);
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            // When & Then - should not throw exception
            assertDoesNotThrow(() -> toolRegistry.registerTool(mockTool));
            assertTrue(toolRegistry.hasTool("test-tool"));
        }

        @Test
        @DisplayName("Should handle tool with null schema")
        void shouldHandleToolWithNullSchema() {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test description");
            when(mockTool.getInputSchema()).thenReturn(null);

            // When & Then - should not throw exception
            assertDoesNotThrow(() -> toolRegistry.registerTool(mockTool));
            assertTrue(toolRegistry.hasTool("test-tool"));
        }

        @Test
        @DisplayName("Should replace existing tool when registering with same name")
        void shouldReplaceExistingTool() {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("First tool");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            when(anotherMockTool.getName()).thenReturn("test-tool");
            when(anotherMockTool.getDescription()).thenReturn("Second tool");
            when(anotherMockTool.getInputSchema()).thenReturn(createValidSchema());

            // When
            toolRegistry.registerTool(mockTool);
            toolRegistry.registerTool(anotherMockTool);

            // Then
            assertEquals(1, toolRegistry.getToolCount());
            Optional<McpTool> retrievedTool = toolRegistry.getTool("test-tool");
            assertTrue(retrievedTool.isPresent());
            assertEquals(anotherMockTool, retrievedTool.get());
        }

        @Test
        @DisplayName("Should throw exception for invalid JSON schema")
        void shouldThrowExceptionForInvalidSchema() {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test description");

            // Create a schema with circular reference that can't be serialized
            Map<String, Object> invalidSchema = new HashMap<>();
            invalidSchema.put("self", invalidSchema); // Circular reference
            when(mockTool.getInputSchema()).thenReturn(invalidSchema);

            // When & Then
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> toolRegistry.registerTool(mockTool)
            );
            assertTrue(exception.getMessage().contains("Tool validation failed"));
        }
    }

    @Nested
    @DisplayName("Tool Unregistration Tests")
    class ToolUnregistrationTests {

        @Test
        @DisplayName("Should unregister existing tool successfully")
        void shouldUnregisterExistingTool() {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test description");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            toolRegistry.registerTool(mockTool);

            // When
            boolean result = toolRegistry.unregisterTool("test-tool");

            // Then
            assertTrue(result);
            assertFalse(toolRegistry.hasTool("test-tool"));
            assertEquals(0, toolRegistry.getToolCount());
        }

        @Test
        @DisplayName("Should return false when unregistering non-existent tool")
        void shouldReturnFalseForNonExistentTool() {
            boolean result = toolRegistry.unregisterTool("non-existent-tool");
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when unregistering null tool name")
        void shouldReturnFalseForNullToolName() {
            boolean result = toolRegistry.unregisterTool(null);
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when unregistering empty tool name")
        void shouldReturnFalseForEmptyToolName() {
            boolean result = toolRegistry.unregisterTool("   ");
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Tool Listing Tests")
    class ToolListingTests {

        @Test
        @DisplayName("Should return empty list when no tools registered")
        void shouldReturnEmptyListWhenNoTools() {
            List<McpModels.Tool> tools = toolRegistry.listTools();
            assertTrue(tools.isEmpty());
        }

        @Test
        @DisplayName("Should list all registered tools")
        void shouldListAllRegisteredTools() {
            // Given
            when(mockTool.getName()).thenReturn("tool1");
            when(mockTool.getDescription()).thenReturn("First tool");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            when(anotherMockTool.getName()).thenReturn("tool2");
            when(anotherMockTool.getDescription()).thenReturn("Second tool");
            when(anotherMockTool.getInputSchema()).thenReturn(createValidSchema());

            toolRegistry.registerTool(mockTool);
            toolRegistry.registerTool(anotherMockTool);

            // When
            List<McpModels.Tool> tools = toolRegistry.listTools();

            // Then
            assertEquals(2, tools.size());

            Set<String> toolNames = new HashSet<>();
            for (McpModels.Tool tool : tools) {
                toolNames.add(tool.name);
            }
            assertTrue(toolNames.contains("tool1"));
            assertTrue(toolNames.contains("tool2"));
        }

        @Test
        @DisplayName("Should handle exception during tool conversion")
        void shouldHandleExceptionDuringToolConversion() {
            // Given - Register a tool that will work during registration but fail during listing
            when(mockTool.getName()).thenReturn("problematic-tool");
            when(mockTool.getDescription()).thenReturn("Valid description"); // Works during registration
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            toolRegistry.registerTool(mockTool);

            // Now make getDescription throw exception for listing
            when(mockTool.getDescription()).thenThrow(new RuntimeException("Conversion error"));

            // When
            List<McpModels.Tool> tools = toolRegistry.listTools();

            // Then - should handle the exception and exclude the problematic tool
            assertEquals(0, tools.size());
        }
    }

    @Nested
    @DisplayName("Tool Execution Tests")
    class ToolExecutionTests {

        @Test
        @DisplayName("Should execute tool successfully with default timeout")
        void shouldExecuteToolSuccessfully() throws Exception {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test description");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            McpModels.CallToolResponse.CallToolResult expectedResult = createSuccessResult();
            when(mockTool.execute(any())).thenReturn(expectedResult);

            toolRegistry.registerTool(mockTool);

            Map<String, Object> arguments = new HashMap<>();
            arguments.put("param1", "value1");

            // When
            McpModels.CallToolResponse.CallToolResult result =
                    toolRegistry.executeTool("test-tool", arguments);

            // Then
            assertNotNull(result);
            assertEquals(expectedResult, result);
            verify(mockTool).execute(arguments);
        }

        @Test
        @DisplayName("Should execute tool successfully with custom timeout")
        void shouldExecuteToolWithCustomTimeout() throws Exception {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test description");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            McpModels.CallToolResponse.CallToolResult expectedResult = createSuccessResult();
            when(mockTool.execute(any())).thenReturn(expectedResult);

            toolRegistry.registerTool(mockTool);

            Map<String, Object> arguments = new HashMap<>();

            // When
            McpModels.CallToolResponse.CallToolResult result =
                    toolRegistry.executeTool("test-tool", arguments, 60);

            // Then
            assertNotNull(result);
            assertEquals(expectedResult, result);
        }

        @Test
        @DisplayName("Should throw exception for null tool name")
        void shouldThrowExceptionForNullToolNameInExecution() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> toolRegistry.executeTool(null, new HashMap<>())
            );
            assertEquals("Tool name cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for empty tool name")
        void shouldThrowExceptionForEmptyToolNameInExecution() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> toolRegistry.executeTool("   ", new HashMap<>())
            );
            assertEquals("Tool name cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw ToolNotFoundException for non-existent tool")
        void shouldThrowToolNotFoundExceptionForNonExistentTool() {
            ToolNotFoundException exception = assertThrows(
                    ToolNotFoundException.class,
                    () -> toolRegistry.executeTool("non-existent-tool", new HashMap<>())
            );
            assertEquals("Tool not found: non-existent-tool", exception.getMessage());
        }

        @Test
        @DisplayName("Should handle null arguments by providing empty map")
        void shouldHandleNullArguments() throws Exception {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test description");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            McpModels.CallToolResponse.CallToolResult expectedResult = createSuccessResult();
            when(mockTool.execute(any())).thenReturn(expectedResult);

            toolRegistry.registerTool(mockTool);

            // When
            McpModels.CallToolResponse.CallToolResult result =
                    toolRegistry.executeTool("test-tool", null);

            // Then
            assertNotNull(result);
            verify(mockTool).execute(argThat(args -> args != null && args.isEmpty()));
        }

        @Test
        @DisplayName("Should throw ToolExecutionException when tool execution fails")
        void shouldThrowToolExecutionExceptionWhenToolFails() throws Exception {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test description");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());
            when(mockTool.execute(any())).thenThrow(new RuntimeException("Tool execution failed"));

            toolRegistry.registerTool(mockTool);

            // When & Then
            ToolExecutionException exception = assertThrows(
                    ToolExecutionException.class,
                    () -> toolRegistry.executeTool("test-tool", new HashMap<>())
            );
            assertTrue(exception.getMessage().contains("Tool execution failed"));
        }

        @Test
        @DisplayName("Should throw ToolExecutionException when execution times out")
        void shouldThrowExceptionWhenExecutionTimesOut() throws Exception {
            // Given
            when(mockTool.getName()).thenReturn("slow-tool");
            when(mockTool.getDescription()).thenReturn("Slow tool");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            // Simulate slow execution
            when(mockTool.execute(any())).thenAnswer(invocation -> {
                Thread.sleep(2000); // Sleep for 2 seconds
                return createSuccessResult();
            });

            toolRegistry.registerTool(mockTool);

            // When & Then
            ToolExecutionException exception = assertThrows(
                    ToolExecutionException.class,
                    () -> toolRegistry.executeTool("slow-tool", new HashMap<>(), 1) // 1 second timeout
            );
            assertTrue(exception.getMessage().contains("timed out"));
        }

        @Test
        @DisplayName("Should validate required parameters")
        void shouldValidateRequiredParameters() throws Exception {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test description");

            Map<String, Object> schema = createSchemaWithRequiredFields("param1", "param2");
            when(mockTool.getInputSchema()).thenReturn(schema);

            toolRegistry.registerTool(mockTool);

            Map<String, Object> incompleteArguments = new HashMap<>();
            incompleteArguments.put("param1", "value1");
            // Missing param2

            // When & Then
            ToolExecutionException exception = assertThrows(
                    ToolExecutionException.class,
                    () -> toolRegistry.executeTool("test-tool", incompleteArguments)
            );
            assertTrue(exception.getMessage().contains("Missing required parameter: param2"));
        }

        @Test
        @DisplayName("Should execute successfully when all required parameters are provided")
        void shouldExecuteWhenAllRequiredParametersProvided() throws Exception {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test description");

            Map<String, Object> schema = createSchemaWithRequiredFields("param1", "param2");
            when(mockTool.getInputSchema()).thenReturn(schema);

            McpModels.CallToolResponse.CallToolResult expectedResult = createSuccessResult();
            when(mockTool.execute(any())).thenReturn(expectedResult);

            toolRegistry.registerTool(mockTool);

            Map<String, Object> completeArguments = new HashMap<>();
            completeArguments.put("param1", "value1");
            completeArguments.put("param2", "value2");

            // When
            McpModels.CallToolResponse.CallToolResult result =
                    toolRegistry.executeTool("test-tool", completeArguments);

            // Then
            assertNotNull(result);
            assertEquals(expectedResult, result);
        }
    }

    @Nested
    @DisplayName("Query Methods Tests")
    class QueryMethodsTests {

        @Test
        @DisplayName("Should check if tool exists correctly")
        void shouldCheckToolExistence() {
            // Given
            when(mockTool.getName()).thenReturn("existing-tool");
            when(mockTool.getDescription()).thenReturn("Test description");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            toolRegistry.registerTool(mockTool);

            // When & Then
            assertTrue(toolRegistry.hasTool("existing-tool"));
            assertFalse(toolRegistry.hasTool("non-existing-tool"));
            assertFalse(toolRegistry.hasTool(null));
        }

        @Test
        @DisplayName("Should return correct tool count")
        void shouldReturnCorrectToolCount() {
            // Initially empty
            assertEquals(0, toolRegistry.getToolCount());

            // After adding one tool
            when(mockTool.getName()).thenReturn("tool1");
            when(mockTool.getDescription()).thenReturn("Test description");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());
            toolRegistry.registerTool(mockTool);
            assertEquals(1, toolRegistry.getToolCount());

            // After adding another tool
            when(anotherMockTool.getName()).thenReturn("tool2");
            when(anotherMockTool.getDescription()).thenReturn("Test description");
            when(anotherMockTool.getInputSchema()).thenReturn(createValidSchema());
            toolRegistry.registerTool(anotherMockTool);
            assertEquals(2, toolRegistry.getToolCount());

            // After removing one tool
            toolRegistry.unregisterTool("tool1");
            assertEquals(1, toolRegistry.getToolCount());
        }

        @Test
        @DisplayName("Should return correct tool names")
        void shouldReturnCorrectToolNames() {
            // Given
            when(mockTool.getName()).thenReturn("tool1");
            when(mockTool.getDescription()).thenReturn("Test description");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            when(anotherMockTool.getName()).thenReturn("tool2");
            when(anotherMockTool.getDescription()).thenReturn("Test description");
            when(anotherMockTool.getInputSchema()).thenReturn(createValidSchema());

            toolRegistry.registerTool(mockTool);
            toolRegistry.registerTool(anotherMockTool);

            // When
            Set<String> toolNames = toolRegistry.getToolNames();

            // Then
            assertEquals(2, toolNames.size());
            assertTrue(toolNames.contains("tool1"));
            assertTrue(toolNames.contains("tool2"));
        }

        @Test
        @DisplayName("Should get tool by name")
        void shouldGetToolByName() {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test description");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());

            toolRegistry.registerTool(mockTool);

            // When
            Optional<McpTool> retrievedTool = toolRegistry.getTool("test-tool");
            Optional<McpTool> nonExistentTool = toolRegistry.getTool("non-existent");

            // Then
            assertTrue(retrievedTool.isPresent());
            assertEquals(mockTool, retrievedTool.get());
            assertFalse(nonExistentTool.isPresent());
        }
    }

    @Nested
    @DisplayName("Shutdown Tests")
    class ShutdownTests {

        @Test
        @DisplayName("Should shutdown gracefully")
        void shouldShutdownGracefully() {
            // Given
            when(mockTool.getName()).thenReturn("test-tool");
            when(mockTool.getDescription()).thenReturn("Test description");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());
            toolRegistry.registerTool(mockTool);

            // When
            assertDoesNotThrow(() -> toolRegistry.shutdown());

            // Then
            assertEquals(0, toolRegistry.getToolCount());
        }

        @Test
        @DisplayName("Should handle multiple shutdown calls")
        void shouldHandleMultipleShutdownCalls() {
            assertDoesNotThrow(() -> {
                toolRegistry.shutdown();
                toolRegistry.shutdown(); // Second call should not cause issues
            });
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent tool registrations")
        void shouldHandleConcurrentRegistrations() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            try {
                for (int i = 0; i < threadCount; i++) {
                    final int toolIndex = i;
                    executorService.submit(() -> {
                        try {
                            McpTool concurrentTool = mock(McpTool.class);
                            when(concurrentTool.getName()).thenReturn("tool-" + toolIndex);
                            when(concurrentTool.getDescription()).thenReturn("Tool " + toolIndex);
                            when(concurrentTool.getInputSchema()).thenReturn(createValidSchema());

                            toolRegistry.registerTool(concurrentTool);
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertTrue(latch.await(5, TimeUnit.SECONDS));
                assertEquals(threadCount, toolRegistry.getToolCount());

            } finally {
                executorService.shutdown();
            }
        }

        @Test
        @DisplayName("Should handle concurrent tool executions")
        void shouldHandleConcurrentExecutions() throws Exception {
            // Given
            when(mockTool.getName()).thenReturn("concurrent-tool");
            when(mockTool.getDescription()).thenReturn("Concurrent tool");
            when(mockTool.getInputSchema()).thenReturn(createValidSchema());
            when(mockTool.execute(any())).thenReturn(createSuccessResult());

            toolRegistry.registerTool(mockTool);

            int threadCount = 5;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

            try {
                for (int i = 0; i < threadCount; i++) {
                    executorService.submit(() -> {
                        try {
                            Map<String, Object> arguments = new HashMap<>();
                            arguments.put("param", "value");
                            toolRegistry.executeTool("concurrent-tool", arguments);
                        } catch (Exception e) {
                            exceptions.add(e);
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertTrue(latch.await(10, TimeUnit.SECONDS));
                assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent execution");
                verify(mockTool, times(threadCount)).execute(any());

            } finally {
                executorService.shutdown();
            }
        }
    }

    // Helper methods
    private Map<String, Object> createValidSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<>());
        return schema;
    }

    private Map<String, Object> createSchemaWithRequiredFields(String... requiredFields) {
        Map<String, Object> schema = createValidSchema();
        schema.put("required", Arrays.asList(requiredFields));
        return schema;
    }

    private McpModels.CallToolResponse.CallToolResult createSuccessResult() {
        McpModels.CallToolResponse.CallToolResult result = new McpModels.CallToolResponse.CallToolResult();

        McpModels.Content content = new McpModels.Content();
        content.type = "text";
        content.text = "Tool executed successfully";

        result.content = Arrays.asList(content);
        result.isError = false;
        return result;
    }
}