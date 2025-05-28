package com.example.mcp.tool.builtin;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Random Number Generator tool
 */
public class RandomTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(RandomTool.class);
    private static final Random random = new Random();

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
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String type = getOptionalString(arguments, "type", "integer");
            int count = getOptionalInt(arguments, "count", 1);

            if (count < 1 || count > 20) {
                throw new ToolExecutionException("Count must be between 1 and 20");
            }

            StringBuilder result = new StringBuilder();

            for (int i = 0; i < count; i++) {
                if (i > 0) result.append(", ");

                switch (type.toLowerCase()) {
                    case "integer" -> {
                        int min = getOptionalInt(arguments, "min", 1);
                        int max = getOptionalInt(arguments, "max", 100);
                        if (min > max) {
                            throw new ToolExecutionException("Min value cannot be greater than max value");
                        }
                        int value = random.nextInt(max - min + 1) + min;
                        result.append(value);
                    }
                    case "decimal" -> {
                        double min = getOptionalDouble(arguments, "min", 0.0);
                        double max = getOptionalDouble(arguments, "max", 1.0);
                        if (min >= max) {
                            throw new ToolExecutionException("Min value must be less than max value");
                        }
                        double value = random.nextDouble() * (max - min) + min;
                        result.append(String.format("%.6f", value));
                    }
                    case "boolean" -> {
                        boolean value = random.nextBoolean();
                        result.append(value);
                    }
                    default -> throw new ToolExecutionException("Unknown random type: " + type);
                }
            }

            String responseText = count == 1 ?
                    String.format("Random %s: %s", type, result.toString()) :
                    String.format("Random %s values (%d): %s", type, count, result.toString());

            logger.debug("Generated {} random {} value(s)", count, type);
            return createTextResult(responseText);

        } catch (Exception e) {
            logger.error("Error in RandomTool execution", e);
            throw new ToolExecutionException("Random number generation failed: " + e.getMessage(), e);
        }
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

    private double getOptionalDouble(Map<String, Object> arguments, String key, double defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
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
