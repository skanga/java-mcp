package com.example.mcp.tool;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.security.SecurityContext;
import com.example.mcp.resource.ResourceLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Abstract base class for MCP tools with common functionality and security
 */
public abstract class BaseMcpTool implements McpTool {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final SecurityContext securityContext;
    protected final ResourceLimiter resourceLimiter;

    protected BaseMcpTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        this.securityContext = securityContext;
        this.resourceLimiter = resourceLimiter;
    }

    /**
     * Execute the tool with security and resource controls
     */
    @Override
    public final McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments)
            throws ToolExecutionException {

        // Validate inputs first
        validateInputs(arguments);

        // Execute with resource limiting
        try (ResourceLimiter.ResourcePermit permit = acquireResources()) {
            logger.debug("Executing tool: {} with arguments: {}", getName(), arguments.keySet());

            long startTime = System.currentTimeMillis();
            McpModels.CallToolResponse.CallToolResult result = executeInternal(arguments);
            long executionTime = System.currentTimeMillis() - startTime;

            logger.debug("Tool {} completed in {} ms", getName(), executionTime);
            return result;

        } catch (Exception e) {
            logger.error("Error executing tool {}: {}", getName(), e.getMessage(), e);
            if (e instanceof ToolExecutionException) {
                throw e;
            }
            throw new ToolExecutionException("Tool execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Abstract method for tool-specific execution logic
     */
    protected abstract McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments)
            throws ToolExecutionException;

    /**
     * Acquire necessary resources for this tool
     * Override in subclasses to specify resource requirements
     */
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        // Default to general resource acquisition
        return resourceLimiter.acquireFileOperation("general");
    }

    /**
     * Validate inputs before execution
     * Override in subclasses for tool-specific validation
     */
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        // Default implementation - subclasses can override
    }

    // ==================== Common Parameter Extraction Methods ====================

    protected String getRequiredString(Map<String, Object> arguments, String key) throws ToolExecutionException {
        Object value = arguments.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new ToolExecutionException("Missing required parameter: " + key);
        }
        return String.valueOf(value).trim();
    }

    /**
     * Get an optional string parameter with default
     */
    protected String getOptionalString(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        return value != null ? String.valueOf(value).trim() : defaultValue;
    }

    /**
     * Get an optional integer parameter with default
     */
    protected int getOptionalInt(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    protected long getOptionalLong(Map<String, Object> arguments, String key, long defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get an optional boolean parameter with default
     */
    protected boolean getOptionalBoolean(Map<String, Object> arguments, String key, boolean defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    protected List<String> getOptionalStringList(Map<String, Object> arguments, String key, List<String> defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof List) {
            return ((List<?>) value).stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, String> getOptionalStringMap(Map<String, Object> arguments, String key, Map<String, String> defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Map) {
            return ((Map<?, ?>) value).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    e -> String.valueOf(e.getKey()),
                    e -> String.valueOf(e.getValue())
                ));
        }
        return defaultValue;
    }

    // ==================== Security Helper Methods ====================

    /**
     * Validate and resolve a path with security checks
     */
    protected Path validatePath(String pathString) throws ToolExecutionException {
        return securityContext.validatePath(pathString);
    }

    /**
     * Validate a command for execution
     */
    protected void validateCommand(String command) throws ToolExecutionException {
        securityContext.validateCommand(command);
    }

    /**
     * Validate file size before processing
     */
    protected void validateFileSize(Path file) throws ToolExecutionException {
        securityContext.validateFileSize(file);
    }

    /**
     * Create a safe regex pattern
     */
    protected Pattern createSafePattern(String pattern, boolean caseSensitive) throws ToolExecutionException {
        return securityContext.validateRegexPattern(pattern, caseSensitive);
    }

    /**
     * Check if file is allowed by security policy
     */
    protected boolean isFileAllowed(Path file) {
        return securityContext.isFileAllowed(file);
    }

    // ==================== Resource Helper Methods ====================

    /**
     * Reserve memory for an operation
     */
    protected ResourceLimiter.MemoryReservation reserveMemory(long bytes) throws ToolExecutionException {
        return resourceLimiter.reserveMemory(bytes);
    }

    /**
     * Check memory usage before allocation
     */
    protected void checkMemoryUsage(long additionalBytes) throws ToolExecutionException {
        resourceLimiter.checkMemoryUsage(additionalBytes);
    }

    // ==================== Common Response Creation Methods ====================

    protected McpModels.CallToolResponse.CallToolResult createTextResult(String text) {
        McpModels.CallToolResponse.CallToolResult result = new McpModels.CallToolResponse.CallToolResult();
        McpModels.Content content = new McpModels.Content();
        content.type = "text";
        content.text = text;
        result.content = List.of(content);
        return result;
    }

    protected McpModels.CallToolResponse.CallToolResult createErrorResult(String errorMessage) {
        McpModels.CallToolResponse.CallToolResult result = new McpModels.CallToolResponse.CallToolResult();
        McpModels.Content content = new McpModels.Content();
        content.type = "text";
        content.text = "Error: " + errorMessage;
        result.content = List.of(content);
        result.isError = true;
        return result;
    }

    // ==================== Utility Methods ====================

    protected String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " bytes";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    protected int countLines(String content) {
        if (content == null || content.isEmpty()) return 0;
        return (int) content.chars().filter(ch -> ch == '\n').count() + 1;
    }

    protected String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    protected boolean isTextFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".txt") || lowerName.endsWith(".md") ||
               lowerName.endsWith(".java") || lowerName.endsWith(".js") ||
               lowerName.endsWith(".py") || lowerName.endsWith(".xml") ||
               lowerName.endsWith(".json") || lowerName.endsWith(".yml") ||
               lowerName.endsWith(".yaml") || lowerName.endsWith(".properties");
    }
}
