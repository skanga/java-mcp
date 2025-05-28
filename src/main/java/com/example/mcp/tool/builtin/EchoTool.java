package com.example.mcp.tool.builtin;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Echo tool - echoes back the provided message with optional transformations
 */
public class EchoTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(EchoTool.class);

    @Override
    public String getName() {
        return "echo";
    }

    @Override
    public String getDescription() {
        return "Echoes back the provided message with optional transformations";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "message", Map.of(
                                "type", "string",
                                "description", "Message to echo back",
                                "maxLength", 1000
                        ),
                        "transform", Map.of(
                                "type", "string",
                                "description", "Transformation to apply (none, uppercase, lowercase, reverse)",
                                "enum", List.of("none", "uppercase", "lowercase", "reverse"),
                                "default", "none"
                        ),
                        "repeat", Map.of(
                                "type", "integer",
                                "description", "Number of times to repeat the message (1-10)",
                                "minimum", 1,
                                "maximum", 10,
                                "default", 1
                        )
                ),
                "required", List.of("message")
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String message = getRequiredString(arguments, "message");
            String transform = getOptionalString(arguments, "transform", "none");
            int repeat = getOptionalInt(arguments, "repeat", 1);

            // Validate inputs
            if (message.length() > 1000) {
                throw new ToolExecutionException("Message too long (max 1000 characters)");
            }

            if (repeat < 1 || repeat > 10) {
                throw new ToolExecutionException("Repeat count must be between 1 and 10");
            }

            // Apply transformation
            String transformedMessage = switch (transform.toLowerCase()) {
                case "uppercase" -> message.toUpperCase();
                case "lowercase" -> message.toLowerCase();
                case "reverse" -> new StringBuilder(message).reverse().toString();
                case "none" -> message;
                default -> throw new ToolExecutionException("Invalid transform: " + transform);
            };

            // Build response with repetition
            StringBuilder response = new StringBuilder();
            for (int i = 0; i < repeat; i++) {
                if (i > 0) response.append(" ");
                response.append("Echo: ").append(transformedMessage);
            }

            logger.debug("Echoed message with transform='{}' and repeat={}", transform, repeat);
            return createTextResult(response.toString());

        } catch (Exception e) {
            logger.error("Error in EchoTool execution", e);
            throw new ToolExecutionException("Failed to echo message: " + e.getMessage(), e);
        }
    }

    private String getRequiredString(Map<String, Object> arguments, String key) throws ToolExecutionException {
        Object value = arguments.get(key);
        if (value == null) {
            throw new ToolExecutionException("Missing required parameter: " + key);
        }
        return String.valueOf(value).trim();
    }

    private String getOptionalString(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        return value != null ? String.valueOf(value).trim() : defaultValue;
    }

    private int getOptionalInt(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private McpModels.CallToolResponse.CallToolResult createTextResult(String text) {
        McpModels.CallToolResponse.CallToolResult result = new McpModels.CallToolResponse.CallToolResult();
        McpModels.Content content = new McpModels.Content();
        content.type = "text";
        content.text = text;
        result.content = List.of(content);
        return result;
    }
}
