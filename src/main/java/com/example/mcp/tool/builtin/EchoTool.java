package com.example.mcp.tool.builtin;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter; // Added
import com.example.mcp.security.SecurityContext; // Added
import com.example.mcp.tool.BaseMcpTool; // Added
// import org.slf4j.Logger; // To be removed
// import org.slf4j.LoggerFactory; // To be removed

import java.util.List;
import java.util.Map;

/**
 * Echo tool - echoes back the provided message with optional transformations
 */
public class EchoTool extends BaseMcpTool { // Changed to extend BaseMcpTool
    // private static final Logger logger = LoggerFactory.getLogger(EchoTool.class); // Logger inherited

    // Constructor added
    public EchoTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

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
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String message = getRequiredString(arguments, "message");
        if (message.length() > 1000) { // MaxLength from schema
            throw new ToolExecutionException("Message too long (max 1000 characters)");
        }

        String transform = getOptionalString(arguments, "transform", "none");
        List<String> validTransforms = List.of("none", "uppercase", "lowercase", "reverse");
        if (!validTransforms.contains(transform.toLowerCase())) {
            throw new ToolExecutionException("Invalid transform: " + transform + ". Must be one of " + validTransforms);
        }

        int repeat = getOptionalInt(arguments, "repeat", 1);
        if (repeat < 1 || repeat > 10) { // Min/Max from schema
            throw new ToolExecutionException("Repeat count must be between 1 and 10");
        }
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        // Echo tool is very lightweight, primarily string operations.
        return resourceLimiter.acquireFileOperation("metadata_access"); // Or a more fitting light-weight/no-op permit
    }

    // Renamed execute to executeInternal
    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String message = getRequiredString(arguments, "message"); // Use inherited
            String transform = getOptionalString(arguments, "transform", "none");
            int repeat = getOptionalInt(arguments, "repeat", 1);

            // Validations moved to validateInputs

            // Apply transformation
            String transformedMessage = switch (transform.toLowerCase()) {
                case "uppercase" -> message.toUpperCase();
                case "lowercase" -> message.toLowerCase();
                case "reverse" -> new StringBuilder(message).reverse().toString();
                case "none" -> message;
                default -> throw new ToolExecutionException("Invalid transform: " + transform); // Should be caught by validateInputs
            };

            // Build response with repetition
            StringBuilder response = new StringBuilder();
            for (int i = 0; i < repeat; i++) {
                if (i > 0) response.append(" "); // Add space between repetitions
                response.append("Echo: ").append(transformedMessage);
            }

            logger.debug("Echoed message with transform='{}' and repeat={}", transform, repeat); // Use inherited logger
            return super.createTextResult(response.toString()); // Use inherited createTextResult

        } catch (Exception e) { // Catch any other unexpected exceptions
            logger.error("Error in EchoTool execution", e);
            if (e instanceof ToolExecutionException) throw (ToolExecutionException) e; // Avoid re-wrapping
            throw new ToolExecutionException("Failed to echo message: " + e.getMessage(), e);
        }
    }

    // Helper methods getRequiredString, getOptionalString, getOptionalInt, and createTextResult are removed
    // as they are inherited from BaseMcpTool.
}
