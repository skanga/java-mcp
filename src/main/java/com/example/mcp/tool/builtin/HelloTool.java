package com.example.mcp.tool.builtin;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter; // Added
import com.example.mcp.security.SecurityContext; // Added
import com.example.mcp.tool.BaseMcpTool; // Added
// import org.slf4j.Logger; // To be removed
// import org.slf4j.LoggerFactory; // To be removed

// Unused imports: LocalDateTime, DateTimeFormatter, Random
// import java.time.LocalDateTime;
// import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
// import java.util.Random;

/**
 * Hello World tool - returns a friendly greeting
 */
public class HelloTool extends BaseMcpTool { // Changed to extend BaseMcpTool
    // private static final Logger logger = LoggerFactory.getLogger(HelloTool.class); // Logger inherited

    // Constructor added
    public HelloTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

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
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String name = getOptionalString(arguments, "name", "World");
        if (name.length() > 100) { // MaxLength from schema
            throw new ToolExecutionException("Name too long (max 100 characters)");
        }

        String language = getOptionalString(arguments, "language", "en");
        List<String> validLanguages = List.of("en", "es", "fr", "de"); // From schema
        if (!validLanguages.contains(language.toLowerCase())) {
            throw new ToolExecutionException("Invalid language: " + language + ". Must be one of " + validLanguages);
        }
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        // Hello tool is very lightweight.
        return resourceLimiter.acquireFileOperation("metadata_access"); // Or a more fitting light-weight/no-op permit
    }

    // Renamed execute to executeInternal
    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String name = getOptionalString(arguments, "name", "World"); // Use inherited
            String language = getOptionalString(arguments, "language", "en");

            // Validations moved to validateInputs

            String greeting = switch (language.toLowerCase()) {
                case "es" -> "¡Hola, " + name + "! 👋 ¡Saludos desde el servidor MCP!";
                case "fr" -> "Bonjour, " + name + "! 👋 Salutations du serveur MCP!";
                case "de" -> "Hallo, " + name + "! 👋 Grüße vom MCP-Server!";
                default -> "Hello, " + name + "! 👋 Greetings from the MCP Hello World Server!";
            };

            logger.debug("Generated greeting for name='{}' in language='{}'", name, language); // Use inherited logger
            return super.createTextResult(greeting); // Use inherited createTextResult

        } catch (Exception e) { // Catch any other unexpected exceptions
            logger.error("Error in HelloTool execution", e);
            if (e instanceof ToolExecutionException) throw (ToolExecutionException) e; // Avoid re-wrapping
            throw new ToolExecutionException("Failed to generate greeting: " + e.getMessage(), e);
        }
    }

    // Helper methods getOptionalString and createTextResult are removed
    // as they are inherited from BaseMcpTool.
}

