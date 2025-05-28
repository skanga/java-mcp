package com.example.mcp.tool.builtin;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Hello World tool - returns a friendly greeting
 */
public class HelloTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(HelloTool.class);

    @Override
    public String getName() {
        return "hello";
    }

    @Override
    public String getDescription() {
        return "Returns a friendly greeting message with optional personalization";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of(
                                "type", "string",
                                "description", "Name to greet (optional)",
                                "maxLength", 100
                        ),
                        "language", Map.of(
                                "type", "string",
                                "description", "Language for greeting (en, es, fr, de)",
                                "enum", List.of("en", "es", "fr", "de"),
                                "default", "en"
                        )
                )
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String name = getOptionalString(arguments, "name", "World");
            String language = getOptionalString(arguments, "language", "en");

            // Validate name length
            if (name.length() > 100) {
                throw new ToolExecutionException("Name too long (max 100 characters)");
            }

            String greeting = switch (language.toLowerCase()) {
                case "es" -> "¡Hola, " + name + "! 👋 ¡Saludos desde el servidor MCP!";
                case "fr" -> "Bonjour, " + name + "! 👋 Salutations du serveur MCP!";
                case "de" -> "Hallo, " + name + "! 👋 Grüße vom MCP-Server!";
                default -> "Hello, " + name + "! 👋 Greetings from the MCP Hello World Server!";
            };

            logger.debug("Generated greeting for name='{}' in language='{}'", name, language);
            return createTextResult(greeting);

        } catch (Exception e) {
            logger.error("Error in HelloTool execution", e);
            throw new ToolExecutionException("Failed to generate greeting: " + e.getMessage(), e);
        }
    }

    private String getOptionalString(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        return value != null ? String.valueOf(value).trim() : defaultValue;
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

