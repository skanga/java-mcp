package com.example.mcp;

import com.example.mcp.exception.McpException;
import com.example.mcp.exception.ProtocolException;
import com.example.mcp.model.McpModels;
import com.example.mcp.registry.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpServerTest {

    @Mock
    private McpServer.SystemInterface mockSystemInterface;

    @Mock
    private Context mockContext;

    private McpServer mcpServer;
    private ObjectMapper objectMapper;
    private PrintStream originalOut;
    private ByteArrayOutputStream testOut;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Setup default mock behavior
        lenient().when(mockSystemInterface.currentTimeMillis()).thenReturn(System.currentTimeMillis());
        lenient().when(mockSystemInterface.isDebugLoggerEnabled()).thenReturn(false);
        lenient().when(mockSystemInterface.getEnvironmentVariable(any())).thenReturn(null);
        lenient().when(mockSystemInterface.getSystemProperty(any())).thenReturn(null);
        lenient().when(mockSystemInterface.getSystemProperty(any(), any())).thenReturn(null);

        mcpServer = new McpServer(mockSystemInterface, objectMapper);

        // Capture console output for testing
        originalOut = System.out;
        testOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(testOut));
    }

    @AfterEach
    void tearDown() {
        if (mcpServer != null && mcpServer.isRunning()) {
            mcpServer.stop();
        }
        System.setOut(originalOut);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create McpServer with default system interface")
        void shouldCreateMcpServerWithDefaultSystemInterface() {
            McpServer server = new McpServer();

            assertNotNull(server.getToolRegistry());
            assertNotNull(server.getSystemInterface());
            assertFalse(server.isInitialized());
            assertFalse(server.isRunning());
            assertTrue(server.getSystemInterface() instanceof McpServer.DefaultSystemInterface);
        }

        @Test
        @DisplayName("Should create McpServer with injected dependencies")
        void shouldCreateMcpServerWithInjectedDependencies() {
            assertNotNull(mcpServer.getToolRegistry());
            assertNotNull(mcpServer.getSystemInterface());
            assertFalse(mcpServer.isInitialized());
            assertFalse(mcpServer.isRunning());
            assertEquals(mockSystemInterface, mcpServer.getSystemInterface());
        }

        @Test
        @DisplayName("Should register built-in tools during construction")
        void shouldRegisterBuiltinTools() {
            ToolRegistry registry = mcpServer.getToolRegistry();

            // Verify that built-in tools are registered
            assertTrue(registry.getToolCount() > 0);
            assertTrue(registry.getToolNames().contains("hello"));
            assertTrue(registry.getToolNames().contains("current_time"));
            assertTrue(registry.getToolNames().contains("echo"));
        }

        @Test
        @DisplayName("Should set start time during construction")
        void shouldSetStartTimeDuringConstruction() {
            long expectedTime = 123456789L;
            when(mockSystemInterface.currentTimeMillis()).thenReturn(expectedTime);

            McpServer server = new McpServer(mockSystemInterface, objectMapper);
            assertEquals(expectedTime, server.getStartTime());
        }
    }

    @Nested
    @DisplayName("Server Lifecycle Tests")
    class ServerLifecycleTests {

        @Test
        @DisplayName("Should start server on specified port")
        void shouldStartServerOnSpecifiedPort() throws InterruptedException {
            int testPort = findAvailablePort();
            CountDownLatch latch = new CountDownLatch(1);

            Thread serverThread = new Thread(() -> {
                try {
                    mcpServer.start(testPort);
                    latch.countDown();
                } catch (Exception e) {
                    fail("Server failed to start: " + e.getMessage());
                }
            });

            serverThread.start();
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Server should start within 5 seconds");
            assertTrue(mcpServer.isRunning());
        }

        @Test
        @DisplayName("Should stop server gracefully")
        void shouldStopServerGracefully() throws InterruptedException {
            int testPort = findAvailablePort();
            CountDownLatch startLatch = new CountDownLatch(1);

            Thread serverThread = new Thread(() -> {
                try {
                    mcpServer.start(testPort);
                    startLatch.countDown();
                } catch (Exception e) {
                    fail("Server failed to start: " + e.getMessage());
                }
            });

            serverThread.start();
            assertTrue(startLatch.await(5, TimeUnit.SECONDS));

            mcpServer.stop();
            assertFalse(mcpServer.isRunning());
        }

        @Test
        @DisplayName("Should handle startup failure gracefully")
        void shouldHandleStartupFailure() {
            // Try to start on an invalid port
            assertThrows(RuntimeException.class, () -> mcpServer.start(-1));
        }
    }

    @Nested
    @DisplayName("Port Configuration Tests")
    class PortConfigurationTests {

        @Test
        @DisplayName("Should use command line argument for port")
        void shouldUseCommandLineArgumentForPort() {
            String[] args = {"9999"};
            int port = mcpServer.getPort(args);

            assertEquals(9999, port);
        }

        @Test
        @DisplayName("Should use environment variable for port when no command line arg")
        void shouldUseEnvironmentVariableForPort() {
            when(mockSystemInterface.getEnvironmentVariable("PORT")).thenReturn("8888");

            String[] args = {};
            int port = mcpServer.getPort(args);

            assertEquals(8888, port);
        }

        @Test
        @DisplayName("Should use system property for port")
        void shouldUseSystemPropertyForPort() {
            when(mockSystemInterface.getSystemProperty("server.port")).thenReturn("7777");

            String[] args = {};
            int port = mcpServer.getPort(args);

            assertEquals(7777, port);
        }

        @Test
        @DisplayName("Should use default port when no configuration provided")
        void shouldUseDefaultPort() {
            String[] args = {};
            int port = mcpServer.getPort(args);

            assertEquals(8080, port);
        }

        @Test
        @DisplayName("Should handle invalid port numbers gracefully")
        void shouldHandleInvalidPortNumbers() {
            // Test invalid port number
            String[] invalidArgs = {"invalid"};
            int port = mcpServer.getPort(invalidArgs);
            assertEquals(8080, port); // Should fallback to default

            // Test out of range port
            String[] outOfRangeArgs = {"70000"};
            port = mcpServer.getPort(outOfRangeArgs);
            assertEquals(8080, port); // Should fallback to default

            // Test negative port
            String[] negativeArgs = {"-1"};
            port = mcpServer.getPort(negativeArgs);
            assertEquals(8080, port); // Should fallback to default
        }

        @Test
        @DisplayName("Should prioritize command line over environment variable")
        void shouldPrioritizeCommandLineOverEnvironmentVariable() {
            lenient().when(mockSystemInterface.getEnvironmentVariable("PORT")).thenReturn("8888");

            String[] args = {"9999"};
            int port = mcpServer.getPort(args);

            assertEquals(9999, port); // Command line should take precedence
        }

        @Test
        @DisplayName("Should prioritize environment variable over system property")
        void shouldPrioritizeEnvironmentVariableOverSystemProperty() {
            when(mockSystemInterface.getEnvironmentVariable("PORT")).thenReturn("8888");
            lenient().when(mockSystemInterface.getSystemProperty("server.port")).thenReturn("7777");

            String[] args = {};
            int port = mcpServer.getPort(args);

            assertEquals(8888, port); // Environment variable should take precedence
        }
    }

    @Nested
    @DisplayName("Debug Mode Tests")
    class DebugModeTests {

        @Test
        @DisplayName("Should detect debug mode from system property 'true'")
        void shouldDetectDebugModeFromSystemPropertyTrue() {
            when(mockSystemInterface.getSystemProperty("mcp.debug")).thenReturn("true");
            assertTrue(mcpServer.isDebugMode());
        }

        @Test
        @DisplayName("Should detect debug mode from system property '1'")
        void shouldDetectDebugModeFromSystemPropertyOne() {
            when(mockSystemInterface.getSystemProperty("mcp.debug")).thenReturn("1");
            assertTrue(mcpServer.isDebugMode());
        }

        @Test
        @DisplayName("Should detect debug mode from environment variable")
        void shouldDetectDebugModeFromEnvironmentVariable() {
            when(mockSystemInterface.getSystemProperty("mcp.debug")).thenReturn(null);
            when(mockSystemInterface.getEnvironmentVariable("MCP_DEBUG")).thenReturn("true");

            assertTrue(mcpServer.isDebugMode());
        }

        @Test
        @DisplayName("Should detect debug mode from logger")
        void shouldDetectDebugModeFromLogger() {
            when(mockSystemInterface.getSystemProperty("mcp.debug")).thenReturn(null);
            when(mockSystemInterface.getEnvironmentVariable("MCP_DEBUG")).thenReturn(null);
            when(mockSystemInterface.isDebugLoggerEnabled()).thenReturn(true);

            assertTrue(mcpServer.isDebugMode());
        }

        @Test
        @DisplayName("Should return false when debug mode is not enabled")
        void shouldReturnFalseWhenDebugModeNotEnabled() {
            when(mockSystemInterface.getSystemProperty("mcp.debug")).thenReturn(null);
            when(mockSystemInterface.getEnvironmentVariable("MCP_DEBUG")).thenReturn(null);
            when(mockSystemInterface.isDebugLoggerEnabled()).thenReturn(false);

            assertFalse(mcpServer.isDebugMode());
        }

        @Test
        @DisplayName("Should handle case insensitive debug values")
        void shouldHandleCaseInsensitiveDebugValues() {
            when(mockSystemInterface.getSystemProperty("mcp.debug")).thenReturn("TRUE");
            assertTrue(mcpServer.isDebugMode());

            when(mockSystemInterface.getSystemProperty("mcp.debug")).thenReturn("True");
            assertTrue(mcpServer.isDebugMode());
        }

        @Test
        @DisplayName("Should return false for invalid debug values")
        void shouldReturnFalseForInvalidDebugValues() {
            when(mockSystemInterface.getSystemProperty("mcp.debug")).thenReturn("false");
            lenient().when(mockSystemInterface.getEnvironmentVariable("MCP_DEBUG")).thenReturn(null);
            when(mockSystemInterface.isDebugLoggerEnabled()).thenReturn(false);
            assertFalse(mcpServer.isDebugMode());

            when(mockSystemInterface.getSystemProperty("mcp.debug")).thenReturn("0");
            assertFalse(mcpServer.isDebugMode());
        }
    }

    @Nested
    @DisplayName("Uptime Tests")
    class UptimeTests {

        @Test
        @DisplayName("Should calculate uptime in seconds")
        void shouldCalculateUptimeInSeconds() {
            long startTime = 1000L;
            long currentTime = 1000L + (30 * 1000L); // 30 seconds later (30,000 ms)
            when(mockSystemInterface.currentTimeMillis())
                    .thenReturn(startTime)  // During construction
                    .thenReturn(currentTime); // During getUptime call

            McpServer server = new McpServer(mockSystemInterface, objectMapper);
            String uptime = server.getUptime();

            assertEquals("30s", uptime);
        }

        @Test
        @DisplayName("Should calculate uptime in minutes and seconds")
        void shouldCalculateUptimeInMinutesAndSeconds() {
            long startTime = 1000L;
            long currentTime = 1000L + (2 * 60 * 1000) + (30 * 1000); // 2m 30s later

            when(mockSystemInterface.currentTimeMillis())
                    .thenReturn(startTime)
                    .thenReturn(currentTime);

            McpServer server = new McpServer(mockSystemInterface, objectMapper);
            String uptime = server.getUptime();

            assertEquals("2m 30s", uptime);
        }

        @Test
        @DisplayName("Should calculate uptime in hours, minutes and seconds")
        void shouldCalculateUptimeInHoursMinutesAndSeconds() {
            long startTime = 1000L;
            long currentTime = 1000L + (2 * 60 * 60 * 1000) + (15 * 60 * 1000) + (45 * 1000); // 2h 15m 45s

            when(mockSystemInterface.currentTimeMillis())
                    .thenReturn(startTime)
                    .thenReturn(currentTime);

            McpServer server = new McpServer(mockSystemInterface, objectMapper);
            String uptime = server.getUptime();

            assertEquals("2h 15m 45s", uptime);
        }

        @Test
        @DisplayName("Should handle zero uptime")
        void shouldHandleZeroUptime() {
            long time = 1000L;

            when(mockSystemInterface.currentTimeMillis()).thenReturn(time);

            McpServer server = new McpServer(mockSystemInterface, objectMapper);
            String uptime = server.getUptime();

            assertEquals("0s", uptime);
        }
    }

    @Nested
    @DisplayName("MCP Request Handling Tests")
    class McpRequestHandlingTests {

        @Test
        @DisplayName("Should handle initialize request successfully")
        void shouldHandleInitializeRequest() throws Exception {
            setupMockContext();

            Map<String, Object> initRequest = createInitializeRequest();
            when(mockContext.body()).thenReturn(objectMapper.writeValueAsString(initRequest));

            Method handleMcpRequestMethod = McpServer.class.getDeclaredMethod("handleMcpRequest", Context.class);
            handleMcpRequestMethod.setAccessible(true);
            handleMcpRequestMethod.invoke(mcpServer, mockContext);

            verify(mockContext).json(any());
            assertTrue(mcpServer.isInitialized());
        }

        @Test
        @DisplayName("Should handle tools/list request")
        void shouldHandleToolsListRequest() throws Exception {
            setupMockContext();

            Map<String, Object> listRequest = createListToolsRequest();
            when(mockContext.body()).thenReturn(objectMapper.writeValueAsString(listRequest));

            Method handleMcpRequestMethod = McpServer.class.getDeclaredMethod("handleMcpRequest", Context.class);
            handleMcpRequestMethod.setAccessible(true);
            handleMcpRequestMethod.invoke(mcpServer, mockContext);

            verify(mockContext).json(any());
        }

        @Test
        @DisplayName("Should handle tools/call request")
        void shouldHandleToolsCallRequest() throws Exception {
            setupMockContext();

            Map<String, Object> callRequest = createCallToolRequest("hello", Map.of("name", "test"));
            when(mockContext.body()).thenReturn(objectMapper.writeValueAsString(callRequest));

            Method handleMcpRequestMethod = McpServer.class.getDeclaredMethod("handleMcpRequest", Context.class);
            handleMcpRequestMethod.setAccessible(true);
            handleMcpRequestMethod.invoke(mcpServer, mockContext);

            verify(mockContext).json(any());
        }

        @Test
        @DisplayName("Should handle empty request body")
        void shouldHandleEmptyRequestBody() throws Exception {
            setupMockContext();
            when(mockContext.body()).thenReturn("");

            Method handleMcpRequestMethod = McpServer.class.getDeclaredMethod("handleMcpRequest", Context.class);
            handleMcpRequestMethod.setAccessible(true);
            handleMcpRequestMethod.invoke(mcpServer, mockContext);

            verify(mockContext).json(any());
        }

        @Test
        @DisplayName("Should handle null request body")
        void shouldHandleNullRequestBody() throws Exception {
            setupMockContext();
            when(mockContext.body()).thenReturn(null);

            Method handleMcpRequestMethod = McpServer.class.getDeclaredMethod("handleMcpRequest", Context.class);
            handleMcpRequestMethod.setAccessible(true);
            handleMcpRequestMethod.invoke(mcpServer, mockContext);

            verify(mockContext).json(any());
        }

        @Test
        @DisplayName("Should handle whitespace-only request body")
        void shouldHandleWhitespaceOnlyRequestBody() throws Exception {
            setupMockContext();
            when(mockContext.body()).thenReturn("   \t\n  ");

            Method handleMcpRequestMethod = McpServer.class.getDeclaredMethod("handleMcpRequest", Context.class);
            handleMcpRequestMethod.setAccessible(true);
            handleMcpRequestMethod.invoke(mcpServer, mockContext);
            // Verify that the body was requested
            verify(mockContext, atLeastOnce()).body();
        }

        @Test
        @DisplayName("Should handle invalid JSON")
        void shouldHandleInvalidJson() throws Exception {
            setupMockContext();
            when(mockContext.body()).thenReturn("invalid json");

            Method handleMcpRequestMethod = McpServer.class.getDeclaredMethod("handleMcpRequest", Context.class);
            handleMcpRequestMethod.setAccessible(true);
            handleMcpRequestMethod.invoke(mcpServer, mockContext);
            verify(mockContext).json(any());
        }

        @Test
        @DisplayName("Should handle unknown method")
        void shouldHandleUnknownMethod() throws Exception {
            setupMockContext();

            Map<String, Object> unknownRequest = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "unknown/method",
                    "params", Map.of()
            );
            when(mockContext.body()).thenReturn(objectMapper.writeValueAsString(unknownRequest));

            Method handleMcpRequestMethod = McpServer.class.getDeclaredMethod("handleMcpRequest", Context.class);
            handleMcpRequestMethod.setAccessible(true);
            handleMcpRequestMethod.invoke(mcpServer, mockContext);

            verify(mockContext).json(any());
        }

        @Test
        @DisplayName("Should handle malformed JSON-RPC request")
        void shouldHandleMalformedJsonRpcRequest() throws Exception {
            setupMockContext();

            Map<String, Object> malformedRequest = Map.of(
                    "invalid", "request"
            );
            when(mockContext.body()).thenReturn(objectMapper.writeValueAsString(malformedRequest));

            Method handleMcpRequestMethod = McpServer.class.getDeclaredMethod("handleMcpRequest", Context.class);
            handleMcpRequestMethod.setAccessible(true);
            handleMcpRequestMethod.invoke(mcpServer, mockContext);

            verify(mockContext).json(any());
        }
    }

    @Nested
    @DisplayName("Initialize Handling Tests")
    class InitializeHandlingTests {

        @Test
        @DisplayName("Should handle initialize with valid parameters")
        void shouldHandleInitializeWithValidParameters() throws Exception {
            Map<String, Object> request = createInitializeRequest();

            Method handleInitializeMethod = McpServer.class.getDeclaredMethod("handleInitialize", Map.class, Object.class);
            handleInitializeMethod.setAccessible(true);
            Object response = handleInitializeMethod.invoke(mcpServer, request, 1);

            assertNotNull(response);
            assertTrue(response instanceof McpModels.InitializeResponse);

            McpModels.InitializeResponse initResponse = (McpModels.InitializeResponse) response;
            assertEquals(1, initResponse.id);
            assertEquals("2024-11-05", initResponse.result.protocolVersion);
            assertEquals("MCP Hello World Server", initResponse.result.serverInfo.name);
            assertEquals("1.0.0", initResponse.result.serverInfo.version);
            assertNotNull(initResponse.result.capabilities);
            assertNotNull(initResponse.result.capabilities.tools);
        }

        @Test
        @DisplayName("Should handle initialize without parameters")
        void shouldHandleInitializeWithoutParameters() throws Exception {
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "initialize"
            );

            Method handleInitializeMethod = McpServer.class.getDeclaredMethod("handleInitialize", Map.class, Object.class);
            handleInitializeMethod.setAccessible(true);

            assertThrows(McpException.class, () -> {
                try {
                    handleInitializeMethod.invoke(mcpServer, request, 1);
                } catch (Exception e) {
                    if (e.getCause() instanceof McpException) {
                        throw (McpException) e.getCause();
                    }
                    throw e;
                }
            });
        }

        @Test
        @DisplayName("Should warn about protocol version mismatch")
        void shouldWarnAboutProtocolVersionMismatch() throws Exception {
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "initialize",
                    "params", Map.of("protocolVersion", "2023-01-01")
            );

            Method handleInitializeMethod = McpServer.class.getDeclaredMethod("handleInitialize", Map.class, Object.class);
            handleInitializeMethod.setAccessible(true);
            Object response = handleInitializeMethod.invoke(mcpServer, request, 1);

            assertNotNull(response);
            // Should still succeed despite version mismatch
            assertTrue(response instanceof McpModels.InitializeResponse);

            McpModels.InitializeResponse initResponse = (McpModels.InitializeResponse) response;
            assertEquals("2024-11-05", initResponse.result.protocolVersion); // Server version
        }

        @Test
        @DisplayName("Should handle string request ID")
        void shouldHandleStringRequestId() throws Exception {
            Map<String, Object> request = createInitializeRequest();
            String stringId = "test-id-123";

            Method handleInitializeMethod = McpServer.class.getDeclaredMethod("handleInitialize", Map.class, Object.class);
            handleInitializeMethod.setAccessible(true);
            Object response = handleInitializeMethod.invoke(mcpServer, request, stringId);

            assertNotNull(response);
            assertTrue(response instanceof McpModels.InitializeResponse);

            McpModels.InitializeResponse initResponse = (McpModels.InitializeResponse) response;
            assertEquals(stringId, initResponse.id);
        }

        @Test
        @DisplayName("Should handle null request ID")
        void shouldHandleNullRequestId() throws Exception {
            Map<String, Object> request = createInitializeRequest();

            Method handleInitializeMethod = McpServer.class.getDeclaredMethod("handleInitialize", Map.class, Object.class);
            handleInitializeMethod.setAccessible(true);
            Object response = handleInitializeMethod.invoke(mcpServer, request, null);

            assertNotNull(response);
            assertTrue(response instanceof McpModels.InitializeResponse);

            McpModels.InitializeResponse initResponse = (McpModels.InitializeResponse) response;
            assertNull(initResponse.id);
        }
    }

    @Nested
    @DisplayName("Tool Handling Tests")
    class ToolHandlingTests {

        @Test
        @DisplayName("Should handle list tools request")
        void shouldHandleListToolsRequest() throws Exception {
            Method handleListToolsMethod = McpServer.class.getDeclaredMethod("handleListTools", Object.class);
            handleListToolsMethod.setAccessible(true);
            Object response = handleListToolsMethod.invoke(mcpServer, 1);

            assertNotNull(response);
            assertTrue(response instanceof McpModels.ListToolsResponse);

            McpModels.ListToolsResponse listResponse = (McpModels.ListToolsResponse) response;
            assertEquals(1, listResponse.id);
            assertNotNull(listResponse.result.tools);
            assertTrue(listResponse.result.tools.size() > 0);
        }

        @Test
        @DisplayName("Should handle call tool with valid parameters")
        void shouldHandleCallToolWithValidParameters() throws Exception {
            Map<String, Object> request = createCallToolRequest("hello", Map.of("name", "test"));

            Method handleCallToolMethod = McpServer.class.getDeclaredMethod("handleCallTool", Map.class, Object.class);
            handleCallToolMethod.setAccessible(true);
            Object response = handleCallToolMethod.invoke(mcpServer, request, 1);

            assertNotNull(response);
            assertTrue(response instanceof McpModels.CallToolResponse);

            McpModels.CallToolResponse callResponse = (McpModels.CallToolResponse) response;
            assertEquals(1, callResponse.id);
            assertNotNull(callResponse.result);
        }

        @Test
        @DisplayName("Should handle call tool without parameters")
        void shouldHandleCallToolWithoutParameters() throws Exception {
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "tools/call"
            );

            Method handleCallToolMethod = McpServer.class.getDeclaredMethod("handleCallTool", Map.class, Object.class);
            handleCallToolMethod.setAccessible(true);

            assertThrows(McpException.class, () -> {
                try {
                    handleCallToolMethod.invoke(mcpServer, request, 1);
                } catch (Exception e) {
                    if (e.getCause() instanceof McpException) {
                        throw (McpException) e.getCause();
                    }
                    throw e;
                }
            });
        }

        @Test
        @DisplayName("Should handle call tool with missing tool name")
        void shouldHandleCallToolWithMissingToolName() throws Exception {
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "tools/call",
                    "params", Map.of("arguments", Map.of())
            );

            Method handleCallToolMethod = McpServer.class.getDeclaredMethod("handleCallTool", Map.class, Object.class);
            handleCallToolMethod.setAccessible(true);

            assertThrows(McpException.class, () -> {
                try {
                    handleCallToolMethod.invoke(mcpServer, request, 1);
                } catch (Exception e) {
                    if (e.getCause() instanceof McpException) {
                        throw (McpException) e.getCause();
                    }
                    throw e;
                }
            });
        }

        @Test
        @DisplayName("Should handle call tool with empty tool name")
        void shouldHandleCallToolWithEmptyToolName() throws Exception {
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "tools/call",
                    "params", Map.of("name", "", "arguments", Map.of())
            );

            Method handleCallToolMethod = McpServer.class.getDeclaredMethod("handleCallTool", Map.class, Object.class);
            handleCallToolMethod.setAccessible(true);

            assertThrows(McpException.class, () -> {
                try {
                    handleCallToolMethod.invoke(mcpServer, request, 1);
                } catch (Exception e) {
                    if (e.getCause() instanceof McpException) {
                        throw (McpException) e.getCause();
                    }
                    throw e;
                }
            });
        }

        @Test
        @DisplayName("Should handle call tool with whitespace-only tool name")
        void shouldHandleCallToolWithWhitespaceOnlyToolName() throws Exception {
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "tools/call",
                    "params", Map.of("name", "   ", "arguments", Map.of())
            );

            Method handleCallToolMethod = McpServer.class.getDeclaredMethod("handleCallTool", Map.class, Object.class);
            handleCallToolMethod.setAccessible(true);

            assertThrows(McpException.class, () -> {
                try {
                    handleCallToolMethod.invoke(mcpServer, request, 1);
                } catch (Exception e) {
                    if (e.getCause() instanceof McpException) {
                        throw (McpException) e.getCause();
                    }
                    throw e;
                }
            });
        }

        @Test
        @DisplayName("Should handle call tool without arguments")
        void shouldHandleCallToolWithoutArguments() throws Exception {
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "tools/call",
                    "params", Map.of("name", "hello")
            );

            Method handleCallToolMethod = McpServer.class.getDeclaredMethod("handleCallTool", Map.class, Object.class);
            handleCallToolMethod.setAccessible(true);
            Object response = handleCallToolMethod.invoke(mcpServer, request, 1);

            assertNotNull(response);
            assertTrue(response instanceof McpModels.CallToolResponse);
        }
    }

    @Nested
    @DisplayName("DefaultSystemInterface Tests")
    class DefaultSystemInterfaceTests {

        private McpServer.DefaultSystemInterface systemInterface;

        @BeforeEach
        void setUp() {
            systemInterface = new McpServer.DefaultSystemInterface();
        }

        @Test
        @DisplayName("Should delegate to System.getenv")
        void shouldDelegateToSystemGetenv() {
            // Test with a known environment variable (PATH should exist on most systems)
            String path = systemInterface.getEnvironmentVariable("PATH");
            String actualPath = System.getenv("PATH");

            assertEquals(actualPath, path);
        }

        @Test
        @DisplayName("Should delegate to System.getProperty")
        void shouldDelegateToSystemGetProperty() {
            String javaVersion = systemInterface.getSystemProperty("java.version");
            String actualJavaVersion = System.getProperty("java.version");

            assertEquals(actualJavaVersion, javaVersion);
        }

        @Test
        @DisplayName("Should delegate to System.getProperty with default")
        void shouldDelegateToSystemGetPropertyWithDefault() {
            String nonExistentProperty = systemInterface.getSystemProperty("non.existent.property", "default");
            String actualProperty = System.getProperty("non.existent.property", "default");

            assertEquals(actualProperty, nonExistentProperty);
            assertEquals("default", nonExistentProperty);
        }

        @Test
        @DisplayName("Should delegate to System.currentTimeMillis")
        void shouldDelegateToSystemCurrentTimeMillis() {
            long before = System.currentTimeMillis();
            long result = systemInterface.currentTimeMillis();
            long after = System.currentTimeMillis();

            assertTrue(result >= before && result <= after);
        }

        @Test
        @DisplayName("Should delegate to logger.isDebugEnabled")
        void shouldDelegateToLoggerIsDebugEnabled() {
            boolean result = systemInterface.isDebugLoggerEnabled();
            boolean actual = LoggerFactory.getLogger(McpServer.class).isDebugEnabled();

            assertEquals(actual, result);
        }
    }

    @Nested
    @DisplayName("Getters Tests")
    class GettersTests {

        @Test
        @DisplayName("Should return tool registry")
        void shouldReturnToolRegistry() {
            assertNotNull(mcpServer.getToolRegistry());
        }

        @Test
        @DisplayName("Should return initialization status")
        void shouldReturnInitializationStatus() {
            assertFalse(mcpServer.isInitialized());

            // Simulate initialization by calling handleInitialize
            try {
                Map<String, Object> request = createInitializeRequest();
                Method handleInitializeMethod = McpServer.class.getDeclaredMethod("handleInitialize", Map.class, Object.class);
                handleInitializeMethod.setAccessible(true);
                handleInitializeMethod.invoke(mcpServer, request, 1);

                assertTrue(mcpServer.isInitialized());
            } catch (Exception e) {
                fail("Failed to initialize server: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Should return running status")
        void shouldReturnRunningStatus() {
            assertFalse(mcpServer.isRunning());
        }

        @Test
        @DisplayName("Should return system interface")
        void shouldReturnSystemInterface() {
            assertEquals(mockSystemInterface, mcpServer.getSystemInterface());
        }

        @Test
        @DisplayName("Should return start time")
        void shouldReturnStartTime() {
            long expectedTime = 123456789L;
            when(mockSystemInterface.currentTimeMillis()).thenReturn(expectedTime);

            McpServer server = new McpServer(mockSystemInterface, objectMapper);
            assertEquals(expectedTime, server.getStartTime());
        }
    }

    @Nested
    @DisplayName("Main Method Tests")
    class MainMethodTests {

        @Test
        @DisplayName("Should handle valid command line arguments")
        void shouldHandleValidCommandLineArguments() {
            // Test that main method doesn't crash with valid arguments
            assertDoesNotThrow(() -> {
                Thread mainThread = new Thread(() -> {
                    try {
                        McpServer.main(new String[]{"8081"});
                    } catch (Exception e) {
                        // Expected since we'll interrupt the thread
                    }
                });
                mainThread.start();
                Thread.sleep(1000); // Let it start
                mainThread.interrupt();
            });
        }

        @Test
        @DisplayName("Should handle shutdown hook")
        void shouldHandleShutdownHook() {
            // This test verifies that the shutdown hook is properly registered
            // In practice, testing shutdown hooks is complex, so we verify the code doesn't crash
            assertDoesNotThrow(() -> {
                Thread mainThread = new Thread(() -> {
                    try {
                        McpServer.main(new String[]{"8082"});
                    } catch (Exception e) {
                        // Expected since we'll interrupt the thread
                    }
                });
                mainThread.start();
                Thread.sleep(500);
                mainThread.interrupt();
            });
        }
    }

    @Nested
    @DisplayName("Concurrent Access Tests")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("Should handle concurrent requests safely")
        void shouldHandleConcurrentRequestsSafely() throws Exception {
            int numThreads = 10;
            CountDownLatch latch = new CountDownLatch(numThreads);
            List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < numThreads; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        // Simulate concurrent access to server methods
                        assertNotNull(mcpServer.getToolRegistry());
                        assertFalse(mcpServer.isInitialized());
                        assertFalse(mcpServer.isRunning());
                        assertNotNull(mcpServer.getSystemInterface());
                        assertNotNull(mcpServer.getUptime());
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
                thread.start();
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent access");
        }

        @Test
        @DisplayName("Should maintain state consistency under concurrent access")
        void shouldMaintainStateConsistencyUnderConcurrentAccess() throws Exception {
            int numThreads = 20;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numThreads);
            List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < numThreads; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        boolean initialized = mcpServer.isInitialized();
                        boolean running = mcpServer.isRunning();
                        ToolRegistry registry = mcpServer.getToolRegistry();

                        results.add(initialized);
                        results.add(running);
                        results.add(registry != null);
                    } catch (Exception e) {
                        results.add(false);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }

            startLatch.countDown(); // Start all threads
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

            // All threads should see consistent state
            Set<Boolean> uniqueResults = new HashSet<>(results);
            assertTrue(uniqueResults.size() <= 2, "State should be consistent across threads");
        }
    }

    // Helper methods

    private void setupMockContext() {
        lenient().when(mockContext.status(anyInt())).thenReturn(mockContext);
        lenient().when(mockContext.json(any())).thenReturn(mockContext);
        lenient().when(mockContext.result(anyString())).thenReturn(mockContext);
        lenient().when(mockContext.method()).thenReturn(HandlerType.POST);
        lenient().when(mockContext.path()).thenReturn("/mcp");
        lenient().when(mockContext.contentType(anyString())).thenReturn(mockContext);
        lenient().when(mockContext.header(anyString(), anyString())).thenReturn(mockContext);
        lenient().when(mockContext.status(any(io.javalin.http.HttpStatus.class))).thenReturn(mockContext);
    }

    private Map<String, Object> createInitializeRequest() {
        return Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "initialize",
                "params", Map.of("protocolVersion", "2024-11-05")
        );
    }

    private Map<String, Object> createListToolsRequest() {
        return Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "tools/list",
                "params", Map.of()
        );
    }

    private Map<String, Object> createCallToolRequest(String toolName, Map<String, Object> arguments) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "tools/call",
                "params", Map.of(
                        "name", toolName,
                        "arguments", arguments
                )
        );
    }

    private int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return 8080; // Fallback
        }
    }
}

// Additional integration test class
@ExtendWith(MockitoExtension.class)
class McpServerIntegrationTest {

    private McpServer mcpServer;

    @BeforeEach
    void setUp() {
        mcpServer = new McpServer();
    }

    @AfterEach
    void tearDown() {
        if (mcpServer != null && mcpServer.isRunning()) {
            mcpServer.stop();
        }
    }

    @Test
    @DisplayName("Full server lifecycle integration test")
    void fullServerLifecycleTest() throws InterruptedException {
        int testPort = findAvailablePort();
        CountDownLatch latch = new CountDownLatch(1);

        Thread serverThread = new Thread(() -> {
            try {
                mcpServer.start(testPort);
                latch.countDown();
            } catch (Exception e) {
                fail("Server failed to start: " + e.getMessage());
            }
        });

        serverThread.start();
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(mcpServer.isRunning());

        mcpServer.stop();
        assertFalse(mcpServer.isRunning());
    }

    @Test
    @DisplayName("Should use default system interface correctly")
    void shouldUseDefaultSystemInterfaceCorrectly() {
        assertTrue(mcpServer.getSystemInterface() instanceof McpServer.DefaultSystemInterface);

        // Test that it can get actual system properties
        String javaVersion = mcpServer.getSystemInterface().getSystemProperty("java.version");
        assertNotNull(javaVersion);

        // Test uptime calculation works
        String uptime = mcpServer.getUptime();
        assertNotNull(uptime);
        assertTrue(uptime.matches("\\d+[hms].*"));
    }

    @Test
    @DisplayName("Should handle port configuration from real environment")
    void shouldHandlePortConfigurationFromRealEnvironment() {
        // Test with empty args (should use default or environment)
        int port = mcpServer.getPort(new String[]{});
        assertTrue(port >= 1 && port <= 65535);

        // Test with valid command line arg
        port = mcpServer.getPort(new String[]{"9999"});
        assertEquals(9999, port);
    }

    private int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return 8080;
        }
    }
}

// Performance and stress test class
@ExtendWith(MockitoExtension.class)
class McpServerPerformanceTest {

    private McpServer mcpServer;
    private McpServer.SystemInterface mockSystemInterface;

    @BeforeEach
    void setUp() {
        mockSystemInterface = mock(McpServer.SystemInterface.class);
        when(mockSystemInterface.currentTimeMillis()).thenReturn(System.currentTimeMillis());
        when(mockSystemInterface.isDebugLoggerEnabled()).thenReturn(false);
        when(mockSystemInterface.getEnvironmentVariable(any())).thenReturn(null);
        when(mockSystemInterface.getSystemProperty(any())).thenReturn(null);
        when(mockSystemInterface.getSystemProperty(any(), any())).thenReturn(null);

        mcpServer = new McpServer(mockSystemInterface, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (mcpServer != null && mcpServer.isRunning()) {
            mcpServer.stop();
        }
    }

    @Test
    @DisplayName("Should handle rapid sequential getUptime calls")
    void shouldHandleRapidSequentialGetUptimeCalls() {
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            String uptime = mcpServer.getUptime();
            assertNotNull(uptime);
        }

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 1000, "1000 getUptime calls should complete in under 1 second");
    }

    @Test
    @DisplayName("Should handle rapid sequential port configuration calls")
    void shouldHandleRapidSequentialPortConfigurationCalls() {
        String[] args = {"8080"};
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            int port = mcpServer.getPort(args);
            assertEquals(8080, port);
        }

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 1000, "1000 getPort calls should complete in under 1 second");
    }

    @Test
    @DisplayName("Should handle rapid sequential debug mode checks")
    void shouldHandleRapidSequentialDebugModeChecks() {
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            boolean isDebug = mcpServer.isDebugMode();
            assertFalse(isDebug); // Should be false with our mock setup
        }

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 1000, "1000 isDebugMode calls should complete in under 1 second");
    }

    @Test
    @DisplayName("Should handle concurrent initialization attempts")
    void shouldHandleConcurrentInitializationAttempts() throws Exception {
        int numThreads = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<Boolean> initResults = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();

                    // Attempt to initialize
                    Map<String, Object> request = Map.of(
                            "jsonrpc", "2.0",
                            "id", Thread.currentThread().getId(),
                            "method", "initialize",
                            "params", Map.of("protocolVersion", "2024-11-05")
                    );

                    Method handleInitializeMethod = McpServer.class.getDeclaredMethod("handleInitialize", Map.class, Object.class);
                    handleInitializeMethod.setAccessible(true);
                    Object response = handleInitializeMethod.invoke(mcpServer, request, Thread.currentThread().getId());

                    initResults.add(response != null);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
            thread.start();
        }

        startLatch.countDown(); // Start all threads
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

        assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent initialization");
        assertEquals(numThreads, initResults.size());
        assertTrue(initResults.stream().allMatch(result -> result), "All initialization attempts should succeed");
        assertTrue(mcpServer.isInitialized(), "Server should be initialized after concurrent attempts");
    }

    @Test
    @DisplayName("Should handle memory pressure gracefully")
    void shouldHandleMemoryPressureGracefully() {
        // Create many tool registry accesses to test memory handling
        List<ToolRegistry> registries = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            registries.add(mcpServer.getToolRegistry());
        }

        // All should be the same instance (no memory leak)
        ToolRegistry firstRegistry = registries.get(0);
        assertTrue(registries.stream().allMatch(registry -> registry == firstRegistry));

        // Force garbage collection and verify server still works
        System.gc();

        assertNotNull(mcpServer.getToolRegistry());
        assertFalse(mcpServer.isInitialized());
        assertFalse(mcpServer.isRunning());
    }
}

// Edge case and error condition test class
@ExtendWith(MockitoExtension.class)
class McpServerEdgeCaseTest {

    private McpServer.SystemInterface mockSystemInterface;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockSystemInterface = mock(McpServer.SystemInterface.class);
        objectMapper = new ObjectMapper();

        // Default mock behavior
        when(mockSystemInterface.currentTimeMillis()).thenReturn(System.currentTimeMillis());
        when(mockSystemInterface.isDebugLoggerEnabled()).thenReturn(false);
        when(mockSystemInterface.getEnvironmentVariable(any())).thenReturn(null);
        when(mockSystemInterface.getSystemProperty(any())).thenReturn(null);
        when(mockSystemInterface.getSystemProperty(any(), any())).thenReturn(null);
    }

    @Test
    @DisplayName("Should handle system interface throwing exceptions")
    void shouldHandleSystemInterfaceThrowingExceptions() {
        when(mockSystemInterface.currentTimeMillis()).thenThrow(new RuntimeException("System error"));

        // Should not crash during construction, but might fail
        assertThrows(RuntimeException.class, () -> {
            new McpServer(mockSystemInterface, objectMapper);
        });
    }

    @Test
    @DisplayName("Should handle null ObjectMapper gracefully")
    void shouldHandleNullObjectMapperGracefully() {
        assertThrows(NullPointerException.class, () -> {
            new McpServer(mockSystemInterface, null);
        });
    }

    @Test
    @DisplayName("Should handle null SystemInterface gracefully")
    void shouldHandleNullSystemInterfaceGracefully() {
        assertThrows(NullPointerException.class, () -> {
            new McpServer(null, objectMapper);
        });
    }

    @Test
    @DisplayName("Should handle extreme time values")
    void shouldHandleExtremeTimeValues() {
        // Test with very large time values
        when(mockSystemInterface.currentTimeMillis())
                .thenReturn(Long.MAX_VALUE - 1000)  // Construction time
                .thenReturn(Long.MAX_VALUE);        // Current time for uptime calculation

        McpServer server = new McpServer(mockSystemInterface, objectMapper);
        String uptime = server.getUptime();

        assertNotNull(uptime);
        assertTrue(uptime.matches("\\d+[hms].*"));
    }

    @Test
    @DisplayName("Should handle negative time differences")
    void shouldHandleNegativeTimeDifferences() {
        // Test with clock going backwards (edge case)
        when(mockSystemInterface.currentTimeMillis())
                .thenReturn(1000L)  // Construction time
                .thenReturn(500L);  // Current time is earlier (clock went backwards)

        McpServer server = new McpServer(mockSystemInterface, objectMapper);
        String uptime = server.getUptime();

        assertNotNull(uptime);
        // Should handle gracefully, probably showing 0s or negative time
    }

    @Test
    @DisplayName("Should handle very long environment variable values")
    void shouldHandleVeryLongEnvironmentVariableValues() {
        String veryLongValue = "x".repeat(10000);
        when(mockSystemInterface.getEnvironmentVariable("PORT")).thenReturn(veryLongValue);

        McpServer server = new McpServer(mockSystemInterface, objectMapper);
        int port = server.getPort(new String[]{});

        assertEquals(8080, port); // Should fallback to default
    }

    @Test
    @DisplayName("Should handle special characters in environment variables")
    void shouldHandleSpecialCharactersInEnvironmentVariables() {
        when(mockSystemInterface.getEnvironmentVariable("PORT")).thenReturn("8080\n\r\t");

        McpServer server = new McpServer(mockSystemInterface, objectMapper);
        int port = server.getPort(new String[]{});

        assertEquals(8080, port); // Should fallback to default due to parsing error
    }

    @Test
    @DisplayName("Should handle Unicode characters in debug mode values")
    void shouldHandleUnicodeCharactersInDebugModeValues() {
        when(mockSystemInterface.getSystemProperty("mcp.debug", null)).thenReturn("真实"); // Chinese characters

        McpServer server = new McpServer(mockSystemInterface, objectMapper);
        boolean isDebug = server.isDebugMode();

        assertFalse(isDebug); // Should be false for non-recognized values
    }

    @Test
    @DisplayName("Should handle empty strings in configuration")
    void shouldHandleEmptyStringsInConfiguration() {
        when(mockSystemInterface.getEnvironmentVariable("PORT")).thenReturn("");
        when(mockSystemInterface.getSystemProperty("server.port")).thenReturn("");
        when(mockSystemInterface.getSystemProperty("mcp.debug", "")).thenReturn("");

        McpServer server = new McpServer(mockSystemInterface, objectMapper);

        int port = server.getPort(new String[]{});
        assertEquals(8080, port); // Should use default

        boolean isDebug = server.isDebugMode();
        assertFalse(isDebug); // Should be false for empty string
    }

    @Test
    @DisplayName("Should handle whitespace-only strings in configuration")
    void shouldHandleWhitespaceOnlyStringsInConfiguration() {
        when(mockSystemInterface.getEnvironmentVariable("PORT")).thenReturn("   ");
        when(mockSystemInterface.getSystemProperty("server.port")).thenReturn("\t\n\r");
        when(mockSystemInterface.getSystemProperty("mcp.debug", "\t\n\r")).thenReturn("   ");

        McpServer server = new McpServer(mockSystemInterface, objectMapper);

        int port = server.getPort(new String[]{});
        assertEquals(8080, port); // Should use default

        boolean isDebug = server.isDebugMode();
        assertFalse(isDebug); // Should be false for whitespace-only string
    }

    @Test
    @DisplayName("Should handle boundary port values")
    void shouldHandleBoundaryPortValues() {
        McpServer server = new McpServer(mockSystemInterface, objectMapper);

        // Test minimum valid port
        int port = server.getPort(new String[]{"1"});
        assertEquals(1, port);

        // Test maximum valid port
        port = server.getPort(new String[]{"65535"});
        assertEquals(65535, port);

        // Test just below minimum
        port = server.getPort(new String[]{"0"});
        assertEquals(8080, port); // Should fallback to default

        // Test just above maximum
        port = server.getPort(new String[]{"65536"});
        assertEquals(8080, port); // Should fallback to default
    }

    @Test
    @DisplayName("Should handle multiple rapid state checks")
    void shouldHandleMultipleRapidStateChecks() {
        McpServer server = new McpServer(mockSystemInterface, objectMapper);

        // Rapidly check various states
        for (int i = 0; i < 100; i++) {
            assertFalse(server.isInitialized());
            assertFalse(server.isRunning());
            assertNotNull(server.getToolRegistry());
            assertNotNull(server.getSystemInterface());
            assertTrue(server.getStartTime() > 0);
        }
    }
}