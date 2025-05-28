package com.example.mcp.tool.builtin;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Math Calculator tool - performs basic mathematical operations
 */
public class MathTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(MathTool.class);

    @Override
    public String getName() {
        return "math";
    }

    @Override
    public String getDescription() {
        return "Performs basic mathematical operations (add, subtract, multiply, divide, power, sqrt)";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "operation", Map.of(
                                "type", "string",
                                "description", "Mathematical operation to perform",
                                "enum", List.of("add", "subtract", "multiply", "divide", "power", "sqrt", "abs")
                        ),
                        "a", Map.of(
                                "type", "number",
                                "description", "First number"
                        ),
                        "b", Map.of(
                                "type", "number",
                                "description", "Second number (not required for sqrt, abs)"
                        )
                ),
                "required", List.of("operation", "a")
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String operation = getRequiredString(arguments, "operation");
            double a = getRequiredDouble(arguments, "a");

            double result;
            String resultText;

            switch (operation.toLowerCase()) {
                case "add" -> {
                    double b = getRequiredDouble(arguments, "b");
                    result = a + b;
                    resultText = String.format("%.6f + %.6f = %.6f", a, b, result);
                }
                case "subtract" -> {
                    double b = getRequiredDouble(arguments, "b");
                    result = a - b;
                    resultText = String.format("%.6f - %.6f = %.6f", a, b, result);
                }
                case "multiply" -> {
                    double b = getRequiredDouble(arguments, "b");
                    result = a * b;
                    resultText = String.format("%.6f × %.6f = %.6f", a, b, result);
                }
                case "divide" -> {
                    double b = getRequiredDouble(arguments, "b");
                    if (b == 0) {
                        throw new ToolExecutionException("Division by zero is not allowed");
                    }
                    result = a / b;
                    resultText = String.format("%.6f ÷ %.6f = %.6f", a, b, result);
                }
                case "power" -> {
                    double b = getRequiredDouble(arguments, "b");
                    result = Math.pow(a, b);
                    resultText = String.format("%.6f ^ %.6f = %.6f", a, b, result);
                }
                case "sqrt" -> {
                    if (a < 0) {
                        throw new ToolExecutionException("Cannot calculate square root of negative number");
                    }
                    result = Math.sqrt(a);
                    resultText = String.format("√%.6f = %.6f", a, result);
                }
                case "abs" -> {
                    result = Math.abs(a);
                    resultText = String.format("|%.6f| = %.6f", a, result);
                }
                default -> throw new ToolExecutionException("Unknown operation: " + operation);
            }

            // Clean up the formatting (remove unnecessary trailing zeros)
            resultText = resultText.replaceAll("(\\.\\d*?)0+(?=\\D|$)", "$1").replaceAll("\\.$", "");

            logger.debug("Performed math operation: {}", resultText);
            return createTextResult(resultText);

        } catch (Exception e) {
            logger.error("Error in MathTool execution", e);
            throw new ToolExecutionException("Math calculation failed: " + e.getMessage(), e);
        }
    }

    private String getRequiredString(Map<String, Object> arguments, String key) throws ToolExecutionException {
        Object value = arguments.get(key);
        if (value == null) {
            throw new ToolExecutionException("Missing required parameter: " + key);
        }
        return String.valueOf(value).trim();
    }

    private double getRequiredDouble(Map<String, Object> arguments, String key) throws ToolExecutionException {
        Object value = arguments.get(key);
        if (value == null) {
            throw new ToolExecutionException("Missing required parameter: " + key);
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ToolExecutionException("Invalid number for parameter " + key + ": " + value);
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
