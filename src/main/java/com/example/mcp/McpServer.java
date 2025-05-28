package com.example.mcp;

import com.example.mcp.exception.McpException;
import com.example.mcp.exception.ProtocolException;
import com.example.mcp.handler.ErrorHandler;
import com.example.mcp.model.McpModels;
import com.example.mcp.registry.ToolRegistry;
import com.example.mcp.tool.builtin.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.jetty.JettyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Robust MCP Hello World Server with proper error handling and tool registry
 */
public class McpServer {
    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);
    private static final String MCP_VERSION = "2024-11-05";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String SERVER_NAME = "MCP Hello World Server";

    private final ToolRegistry toolRegistry;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final ObjectMapper objectMapper;
    private final SystemInterface systemInterface;
    private final long startTime;
    private Javalin app;

    // Interface for system operations to enable testing
    public interface SystemInterface {
        String getEnvironmentVariable(String name);
        String getSystemProperty(String name);
        String getSystemProperty(String name, String defaultValue);
        long currentTimeMillis();
        boolean isDebugLoggerEnabled();
    }

    // Default implementation using real system calls
    public static class DefaultSystemInterface implements SystemInterface {
        @Override
        public String getEnvironmentVariable(String name) {
            return System.getenv(name);
        }

        @Override
        public String getSystemProperty(String name) {
            return System.getProperty(name);
        }

        @Override
        public String getSystemProperty(String name, String defaultValue) {
            return System.getProperty(name, defaultValue);
        }

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public boolean isDebugLoggerEnabled() {
            return LoggerFactory.getLogger(McpServer.class).isDebugEnabled();
        }
    }

    // Default constructor for production use
    public McpServer() {
        this(new DefaultSystemInterface(), new ObjectMapper());
    }

    // Constructor for testing with dependency injection
    public McpServer(SystemInterface systemInterface, ObjectMapper objectMapper) {
        this.systemInterface = systemInterface;
        this.objectMapper = objectMapper;
        this.startTime = systemInterface.currentTimeMillis();
        this.toolRegistry = new ToolRegistry();
        registerBuiltinTools();
    }

    public static void main(String[] args) {
        McpServer server = new McpServer();

        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, stopping server...");
            server.stop();
        }));

        try {
            int port = server.getPort(args);
            server.start(port);
        } catch (Exception e) {
            logger.error("Failed to start server", e);
            System.exit(1);
        }
    }

    public void start(int port) {
        try {
            app = Javalin.create(config -> {
                config.http.defaultContentType = "application/json";
                config.showJavalinBanner = false;
                config.http.maxRequestSize = 10_485_760L; // 10MB
                config.http.asyncTimeout = 30_000L; // 30 seconds
            });

            // Configure global exception handling
            app.exception(Exception.class, (e, ctx) -> {
                logger.error("Unhandled exception on {} {}", ctx.method(), ctx.path(), e);
                ErrorHandler.handleHttpError(ctx, e);
            });

            setupRoutes();
            app.start(port);

            logger.info("{} v{} started successfully on port {}", SERVER_NAME, SERVER_VERSION, port);
            logger.info("MCP Protocol Version: {}", MCP_VERSION);
            logger.info("Registered tools: {}", toolRegistry.getToolCount());

        } catch (Exception e) {
            logger.error("Failed to start server on port {}", port, e);
            throw new RuntimeException("Server startup failed", e);
        }
    }

    public void stop() {
        if (app != null) {
            logger.info("Stopping MCP server...");
            app.stop();
        }

        if (toolRegistry != null) {
            toolRegistry.shutdown();
        }

        logger.info("MCP server stopped");
    }

    private void setupRoutes() {
        // Main MCP endpoint
        app.post("/mcp", this::handleMcpRequest);

        // Health check endpoint
        app.get("/health", ctx -> {
            Map<String, Object> health = Map.of(
                    "status", "healthy",
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "server", SERVER_NAME,
                    "version", SERVER_VERSION,
                    "protocolVersion", MCP_VERSION,
                    "uptime", getUptime(),
                    "toolsRegistered", toolRegistry.getToolCount(),
                    "initialized", initialized.get()
            );
            ctx.json(health);
        });

        // Server information endpoint
        app.get("/", ctx -> {
            Map<String, Object> info = Map.of(
                    "name", SERVER_NAME,
                    "version", SERVER_VERSION,
                    "protocol", "Model Context Protocol",
                    "protocolVersion", MCP_VERSION,
                    "description", "A robust Hello World MCP server implementation with tool registry",
                    "features", java.util.List.of(
                            "Tool Registry System",
                            "Robust Error Handling",
                            "Request Validation",
                            "Concurrent Tool Execution",
                            "Comprehensive Logging"
                    ),
                    "endpoints", Map.of(
                            "mcp", "POST /mcp - MCP protocol endpoint",
                            "health", "GET /health - Health check with detailed status",
                            "info", "GET / - Server information",
                            "tools", "GET /tools - List available tools (debug)"
                    ),
                    "toolsAvailable", toolRegistry.getToolNames()
            );
            ctx.json(info);
        });

        // Debug endpoint to list tools (non-MCP)
        app.get("/tools", ctx -> {
            if (!isDebugMode()) {
                ctx.status(404).result("Not found");
                return;
            }

            Map<String, Object> response = Map.of(
                    "toolCount", toolRegistry.getToolCount(),
                    "tools", toolRegistry.listTools()
            );
            ctx.json(response);
        });
    }

    private void handleMcpRequest(Context ctx) {
        Object requestId = null;

        try {
            String requestBody = ctx.body();
            if (requestBody == null || requestBody.trim().isEmpty()) {
                throw new ProtocolException("Empty request body");
            }

            logger.debug("Received MCP request: {}", requestBody);

            // Parse JSON-RPC request
            Map<String, Object> request;
            try {
                request = objectMapper.readValue(requestBody, Map.class);
            } catch (Exception e) {
                throw new ProtocolException("Invalid JSON: " + e.getMessage());
            }

            // Extract request ID early for error responses
            requestId = ErrorHandler.extractRequestId(request);

            // Validate request structure
            ErrorHandler.validateJsonRpcRequest(request);

            String method = ErrorHandler.extractMethod(request);
            logger.debug("Processing MCP method: {}", method);

            Object response = switch (method) {
                case "initialize" -> handleInitialize(request, requestId);
                case "tools/list" -> handleListTools(requestId);
                case "tools/call" -> handleCallTool(request, requestId);
                default -> throw new McpException("Method not found: " + method, -32601);
            };

            ctx.json(response);
            logger.debug("Successfully processed MCP method: {}", method);

        } catch (Exception e) {
            ErrorHandler.handleMcpError(ctx, e, requestId);
        }
    }

    private Object handleInitialize(Map<String, Object> request, Object id) throws McpException {
        logger.info("Handling initialize request");

        try {
            // Validate initialization parameters
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) request.get("params");
            if (params == null) {
                throw new McpException("Missing initialization parameters", -32602);
            }

            String clientProtocolVersion = (String) params.get("protocolVersion");
            if (!MCP_VERSION.equals(clientProtocolVersion)) {
                logger.warn("Client protocol version {} differs from server version {}",
                        clientProtocolVersion, MCP_VERSION);
            }

            // Build response
            McpModels.InitializeResponse response = new McpModels.InitializeResponse();
            response.id = id;

            McpModels.InitializeResponse.InitializeResult result = new McpModels.InitializeResponse.InitializeResult();
            result.protocolVersion = MCP_VERSION;

            // Server capabilities
            McpModels.InitializeResponse.ServerCapabilities capabilities = new McpModels.InitializeResponse.ServerCapabilities();
            McpModels.InitializeResponse.ToolsCapability toolsCapability = new McpModels.InitializeResponse.ToolsCapability();
            toolsCapability.listChanged = false; // Static tool list for now
            capabilities.tools = toolsCapability;
            result.capabilities = capabilities;

            // Server info
            McpModels.InitializeResponse.ServerInfo serverInfo = new McpModels.InitializeResponse.ServerInfo();
            serverInfo.name = SERVER_NAME;
            serverInfo.version = SERVER_VERSION;
            result.serverInfo = serverInfo;

            response.result = result;

            initialized.set(true);
            logger.info("MCP server initialized successfully");

            return response;

        } catch (Exception e) {
            throw new McpException("Initialization failed: " + e.getMessage(), e);
        }
    }

    private Object handleListTools(Object id) throws McpException {
        logger.debug("Handling list tools request");

        try {
            McpModels.ListToolsResponse response = new McpModels.ListToolsResponse();
            response.id = id;

            McpModels.ListToolsResponse.ListToolsResult result = new McpModels.ListToolsResponse.ListToolsResult();
            result.tools = toolRegistry.listTools();

            response.result = result;

            logger.debug("Listed {} tools", result.tools.size());
            return response;

        } catch (Exception e) {
            throw new McpException("Failed to list tools: " + e.getMessage(), e);
        }
    }

    private Object handleCallTool(Map<String, Object> request, Object id) throws McpException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) request.get("params");
            if (params == null) {
                throw new McpException("Missing tool call parameters", -32602);
            }

            String toolName = (String) params.get("name");
            if (toolName == null || toolName.trim().isEmpty()) {
                throw new McpException("Missing or empty tool name", -32602);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

            logger.info("Calling tool: {} with arguments: {}", toolName, arguments);

            // Execute tool through registry
            McpModels.CallToolResponse.CallToolResult result = toolRegistry.executeTool(toolName, arguments);

            // Build response
            McpModels.CallToolResponse response = new McpModels.CallToolResponse();
            response.id = id;
            response.result = result;

            logger.debug("Tool {} executed successfully", toolName);
            return response;

        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException("Tool call failed: " + e.getMessage(), e);
        }
    }

    private void registerBuiltinTools() {
        logger.info("Registering built-in tools...");

        try {
            // Initialize AI client for agent tools
            com.example.mcp.ai.AIClient aiClient = new com.example.mcp.ai.AIClient();
            logger.info("AI Client initialized with providers: {}", aiClient.getAvailableProviders());

            // Basic/testing tools
            toolRegistry.registerTool(new com.example.mcp.tool.builtin.HelloTool());
            toolRegistry.registerTool(new com.example.mcp.tool.builtin.CurrentTimeTool());
            toolRegistry.registerTool(new com.example.mcp.tool.builtin.EchoTool());
            toolRegistry.registerTool(new com.example.mcp.tool.builtin.MathTool());
            toolRegistry.registerTool(new com.example.mcp.tool.builtin.RandomTool());

            // Filesystem tools
            toolRegistry.registerTool(new com.example.mcp.tool.filesystem.DirectoryTreeTool());
            toolRegistry.registerTool(new com.example.mcp.tool.filesystem.FileReadTool());
            toolRegistry.registerTool(new com.example.mcp.tool.filesystem.FileWriteTool());
            toolRegistry.registerTool(new com.example.mcp.tool.filesystem.FileSearchTool());

            // Cognition tools
            toolRegistry.registerTool(new com.example.mcp.tool.cognitive.ThinkTool());

            // System and development tools
            toolRegistry.registerTool(new com.example.mcp.tool.system.ProcessExecutorTool());
            toolRegistry.registerTool(new com.example.mcp.tool.development.GitOperationsTool());
            toolRegistry.registerTool(new com.example.mcp.tool.development.REPLEvaluationTool());
            toolRegistry.registerTool(new com.example.mcp.tool.development.DependencyLookupTool());
            toolRegistry.registerTool(new com.example.mcp.tool.development.ProjectAnalysisTool());

            // AI Agent tools (ported from clojure-mcp with system prompts)
            if (!aiClient.getAvailableProviders().isEmpty()) {
                toolRegistry.registerTool(new com.example.mcp.tool.ai.ArchitectTool(aiClient));
                toolRegistry.registerTool(new com.example.mcp.tool.ai.CodeCritiqueTool(aiClient));
                toolRegistry.registerTool(new com.example.mcp.tool.ai.DispatchAgentTool(aiClient));
                toolRegistry.registerTool(new com.example.mcp.tool.ai.ThinkTool(aiClient));
                logger.info("AI agent tools registered successfully");
            } else {
                logger.warn("No AI API keys configured - AI agent tools not registered. " +
                        "Set ANTHROPIC_API_KEY, OPENAI_API_KEY, or GEMINI_API_KEY to enable AI tools.");
            }

            logger.info("Successfully registered {} built-in tools", toolRegistry.getToolCount());

        } catch (Exception e) {
            logger.error("Failed to register built-in tools", e);
            throw new RuntimeException("Tool registration failed", e);
        }
    }

    // Made non-static and using injected SystemInterface
    public int getPort(String[] args) {
        // Command line argument takes precedence
        if (args.length > 0) {
            try {
                int port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65535) {
                    logger.warn("Invalid port number '{}', must be between 1-65535", port);
                } else {
                    return port;
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid port number '{}', not a valid integer", args[0]);
            }
        }

        // Environment variable
        String portEnv = systemInterface.getEnvironmentVariable("PORT");
        if (portEnv != null) {
            try {
                int port = Integer.parseInt(portEnv);
                if (port < 1 || port > 65535) {
                    logger.warn("Invalid PORT environment variable '{}', must be between 1-65535", port);
                } else {
                    return port;
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT environment variable '{}', not a valid integer", portEnv);
            }
        }

        // System property
        String portProp = systemInterface.getSystemProperty("server.port");
        if (portProp != null) {
            try {
                int port = Integer.parseInt(portProp);
                if (port < 1 || port > 65535) {
                    logger.warn("Invalid server.port system property '{}', must be between 1-65535", port);
                } else {
                    return port;
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid server.port system property '{}', not a valid integer", portProp);
            }
        }

        // Default port
        return 8080;
    }

    // Made non-static and using injected SystemInterface
    public boolean isDebugMode() {
        String debugMode = systemInterface.getSystemProperty("mcp.debug");
        if (debugMode == null) {
            debugMode = systemInterface.getEnvironmentVariable("MCP_DEBUG");
        }
        if (debugMode == null) {
            debugMode = "";
        }

        return "true".equalsIgnoreCase(debugMode) ||
                "1".equals(debugMode) ||
                systemInterface.isDebugLoggerEnabled();
    }

    // Made non-static and using injected SystemInterface
    public String getUptime() {
        long uptimeMs = systemInterface.currentTimeMillis() - startTime;
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    // Getters for testing and monitoring
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public boolean isRunning() {
        return app != null && app.port() != -1 && app.jettyServer().server().isRunning();
    }

    // Getter for testing
    public SystemInterface getSystemInterface() {
        return systemInterface;
    }

    public long getStartTime() {
        return startTime;
    }
}