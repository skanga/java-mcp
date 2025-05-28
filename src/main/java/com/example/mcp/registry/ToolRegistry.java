package com.example.mcp.registry;

import com.example.mcp.exception.McpException;
import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.exception.ToolNotFoundException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Registry for managing MCP tools with robust error handling and validation
 */
public class ToolRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private final long defaultTimeoutSeconds;

    public ToolRegistry() {
        this(10, 30); // 10 threads, 30 second timeout
    }

    public ToolRegistry(int threadPoolSize, long defaultTimeoutSeconds) {
        this.executorService = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "mcp-tool-executor");
            t.setDaemon(true);
            return t;
        });
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        logger.info("ToolRegistry initialized with {} threads and {}s timeout",
                threadPoolSize, defaultTimeoutSeconds);
    }

    /**
     * Register a tool in the registry
     */
    public void registerTool(McpTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool cannot be null");
        }

        String name = tool.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        validateTool(tool);

        tools.put(name, tool);
        logger.info("Registered tool: {} - {}", name, tool.getDescription());
    }

    /**
     * Unregister a tool from the registry
     */
    public boolean unregisterTool(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        McpTool removed = tools.remove(name);
        if (removed != null) {
            logger.info("Unregistered tool: {}", name);
            return true;
        }
        return false;
    }

    /**
     * Get all registered tools
     */
    public List<McpModels.Tool> listTools() {
        List<McpModels.Tool> toolList = new ArrayList<>();

        for (McpTool mcpTool : tools.values()) {
            try {
                McpModels.Tool tool = new McpModels.Tool();
                tool.name = mcpTool.getName();
                tool.description = mcpTool.getDescription();
                tool.inputSchema = mcpTool.getInputSchema();
                toolList.add(tool);
            } catch (Exception e) {
                logger.error("Error converting tool {} to MCP format", mcpTool.getName(), e);
            }
        }

        logger.debug("Listed {} tools", toolList.size());
        return toolList;
    }

    /**
     * Execute a tool with the given arguments
     */
    public McpModels.CallToolResponse.CallToolResult executeTool(String toolName, Map<String, Object> arguments)
            throws McpException {
        return executeTool(toolName, arguments, defaultTimeoutSeconds);
    }

    /**
     * Execute a tool with the given arguments and timeout
     */
    public McpModels.CallToolResponse.CallToolResult executeTool(String toolName, Map<String, Object> arguments,
                                                                 long timeoutSeconds) throws McpException {
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        McpTool tool = tools.get(toolName);
        if (tool == null) {
            throw new ToolNotFoundException("Tool not found: " + toolName);
        }

        if (arguments == null) {
            arguments = new HashMap<>();
        }

        // Make arguments effectively final for lambda
        final Map<String, Object> finalArguments = arguments;
        final McpTool finalTool = tool;

        logger.debug("Executing tool: {} with arguments: {}", toolName, arguments);

        try {
            // Validate arguments against schema if available
            validateArguments(tool, arguments);

            // Execute tool with timeout
            Future<McpModels.CallToolResponse.CallToolResult> future =
                    executorService.submit(() -> {
                        try {
                            return finalTool.execute(finalArguments);
                        } catch (Exception e) {
                            logger.error("Tool execution failed for {}", finalTool.getName(), e);
                            throw new ToolExecutionException("Tool execution failed: " + e.getMessage(), e);
                        }
                    });

            McpModels.CallToolResponse.CallToolResult result =
                    future.get(timeoutSeconds, TimeUnit.SECONDS);

            logger.debug("Tool {} executed successfully", toolName);
            return result;

        } catch (TimeoutException e) {
            logger.error("Tool {} execution timed out after {}s", toolName, timeoutSeconds);
            throw new ToolExecutionException("Tool execution timed out after " + timeoutSeconds + " seconds");
        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error executing tool {}", toolName, e);
            throw new ToolExecutionException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a tool is registered
     */
    public boolean hasTool(String toolName) {
        return toolName != null && tools.containsKey(toolName);
    }

    /**
     * Get the number of registered tools
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * Get tool names
     */
    public Set<String> getToolNames() {
        return new HashSet<>(tools.keySet());
    }

    /**
     * Get tool by name
     */
    public Optional<McpTool> getTool(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    /**
     * Shutdown the registry and cleanup resources
     */
    public void shutdown() {
        logger.info("Shutting down ToolRegistry...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Tool executor service did not terminate within 30 seconds, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for executor service shutdown");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        tools.clear();
        logger.info("ToolRegistry shutdown complete");
    }

    /**
     * Validate tool implementation
     */
    private void validateTool(McpTool tool) {
        try {
            // Check that tool can provide basic metadata
            String name = tool.getName();
            String description = tool.getDescription();
            Map<String, Object> schema = tool.getInputSchema();

            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Tool name cannot be null or empty");
            }

            if (description == null) {
                logger.warn("Tool {} has null description", name);
            }

            if (schema == null) {
                logger.warn("Tool {} has null input schema", name);
            } else {
                // Validate that schema is proper JSON Schema
                validateJsonSchema(schema);
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Tool validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Basic JSON Schema validation
     */
    private void validateJsonSchema(Map<String, Object> schema) {
        if (!schema.containsKey("type")) {
            logger.warn("JSON Schema missing 'type' field");
        }

        // Additional schema validation could be added here
        // For now, we just ensure it's a valid JSON object
        try {
            objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON Schema: " + e.getMessage(), e);
        }
    }

    /**
     * Validate arguments against tool's input schema
     */
    private void validateArguments(McpTool tool, Map<String, Object> arguments) throws McpException {
        Map<String, Object> schema = tool.getInputSchema();
        if (schema == null) {
            return; // No schema to validate against
        }

        try {
            // Basic validation - check required fields
            Object propertiesObj = schema.get("properties");
            Object requiredObj = schema.get("required");

            if (requiredObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> required = (List<String>) requiredObj;

                for (String field : required) {
                    if (!arguments.containsKey(field) || arguments.get(field) == null) {
                        throw new ToolExecutionException("Missing required parameter: " + field);
                    }
                }
            }

            // Additional validation could be implemented here using a proper JSON Schema validator

        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Error validating arguments for tool {}: {}", tool.getName(), e.getMessage());
            // Don't fail validation for minor issues
        }
    }
}