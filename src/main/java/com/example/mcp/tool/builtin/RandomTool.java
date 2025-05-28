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
import java.util.Random;

/**
 * Random Number Generator tool
 */
public class RandomTool extends BaseMcpTool { // Changed to extend BaseMcpTool
    // private static final Logger logger = LoggerFactory.getLogger(RandomTool.class); // Logger inherited
    private static final Random random = new Random(); // Stays static

    // Constructor added
    public RandomTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
        // The static 'random' instance can remain.
    }

    @Override
    public String getName() {
        return "random";
    }

    @Override
    public String getDescription() {
        return "Generates random numbers within specified ranges";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of(
                                "type", "string",
                                "description", "Type of random number (integer, decimal, boolean)",
                                "enum", List.of("integer", "decimal", "boolean"),
                                "default", "integer"
                        ),
                        "min", Map.of(
                                "type", "number",
                                "description", "Minimum value (inclusive)",
                                "default", 1
                        ),
                        "max", Map.of(
                                "type", "number",
                                "description", "Maximum value (inclusive for integer, exclusive for decimal)",
                                "default", 100
                        ),
                        "count", Map.of(
                                "type", "integer",
                                "description", "Number of random values to generate (1-20)",
                                "minimum", 1,
                                "maximum", 20,
                                "default", 1
                        )
                )
        );
    }

    @Override
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String type = getOptionalString(arguments, "type", "integer");
        List<String> validTypes = List.of("integer", "decimal", "boolean");
        if (!validTypes.contains(type.toLowerCase())) {
            throw new ToolExecutionException("Unknown random type: " + type + ". Must be one of " + validTypes);
        }

        int count = getOptionalInt(arguments, "count", 1);
        if (count < 1 || count > 20) { // Min/Max from schema
            throw new ToolExecutionException("Count must be between 1 and 20");
        }

        // Min/max validation depends on 'type' and their values.
        if (type.equalsIgnoreCase("integer")) {
            int min = getOptionalInt(arguments, "min", 1);
            int max = getOptionalInt(arguments, "max", 100);
            if (min > max) {
                throw new ToolExecutionException("Min value cannot be greater than max value for type 'integer'");
            }
        } else if (type.equalsIgnoreCase("decimal")) {
            double min = getOptionalNumber(arguments, "min", 0.0).doubleValue();
            double max = getOptionalNumber(arguments, "max", 1.0).doubleValue();
            if (min >= max) { // For decimal, min must be strictly less than max
                throw new ToolExecutionException("Min value must be less than max value for type 'decimal'");
            }
        }
        // No min/max for boolean type
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        // Random tool is purely computational.
        return resourceLimiter.acquireFileOperation("metadata_access"); // Or a CPU-specific or no-op permit
    }

    // Renamed execute to executeInternal
    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String type = getOptionalString(arguments, "type", "integer"); // Use inherited
            int count = getOptionalInt(arguments, "count", 1);

            // Validations for count, type, min/max moved to validateInputs

            StringBuilder result = new StringBuilder();

            for (int i = 0; i < count; i++) {
                if (i > 0) result.append(", ");

                switch (type.toLowerCase()) {
                    case "integer" -> {
                        int min = getOptionalInt(arguments, "min", 1);
                        int max = getOptionalInt(arguments, "max", 100);
                        // min > max check done in validateInputs
                        int value = random.nextInt(max - min + 1) + min;
                        result.append(value);
                    }
                    case "decimal" -> {
                        double min = getOptionalNumber(arguments, "min", 0.0).doubleValue(); // Use inherited
                        double max = getOptionalNumber(arguments, "max", 1.0).doubleValue(); // Use inherited
                        // min >= max check done in validateInputs
                        double value = random.nextDouble() * (max - min) + min;
                        result.append(String.format("%.6f", value));
                    }
                    case "boolean" -> {
                        boolean value = random.nextBoolean();
                        result.append(value);
                    }
                    default -> throw new ToolExecutionException("Unknown random type: " + type); // Should be caught by validateInputs
                }
            }

            String responseText = count == 1 ?
                    String.format("Random %s: %s", type, result.toString()) :
                    String.format("Random %s values (%d): %s", type, count, result.toString());

            logger.debug("Generated {} random {} value(s)", count, type); // Use inherited logger
            return super.createTextResult(responseText); // Use inherited createTextResult

        } catch (Exception e) { // Catch any other unexpected exceptions
            logger.error("Error in RandomTool execution", e);
            if (e instanceof ToolExecutionException) throw (ToolExecutionException) e; // Avoid re-wrapping
            throw new ToolExecutionException("Random number generation failed: " + e.getMessage(), e);
        }
    }

    // Helper methods getOptionalString, getOptionalInt, getOptionalDouble, and createTextResult are removed
    // as they are inherited from BaseMcpTool.
}
