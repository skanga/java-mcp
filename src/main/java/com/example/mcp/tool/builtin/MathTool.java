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
 * Math Calculator tool - performs basic mathematical operations
 */
public class MathTool extends BaseMcpTool { // Changed to extend BaseMcpTool
    // private static final Logger logger = LoggerFactory.getLogger(MathTool.class); // Logger inherited

    // Constructor added
    public MathTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

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
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String operation = getRequiredString(arguments, "operation");
        List<String> validOperations = List.of("add", "subtract", "multiply", "divide", "power", "sqrt", "abs");
        if (!validOperations.contains(operation.toLowerCase())) {
            throw new ToolExecutionException("Unknown operation: " + operation + ". Must be one of " + validOperations);
        }

        // Ensure 'a' is present and is a number.
        getRequiredNumber(arguments, "a");

        // Ensure 'b' is present and is a number if required by the operation.
        if (!operation.equalsIgnoreCase("sqrt") && !operation.equalsIgnoreCase("abs")) {
            getRequiredNumber(arguments, "b");
        }
        
        // Specific validation for sqrt (no negative 'a') and divide (no division by zero 'b')
        // This can also be done in executeInternal just before the operation, as it depends on the value.
        // For now, keeping more complex value-based validation in executeInternal.
        // If 'a' or 'b' were file paths or commands, they'd be validated here using securityContext.
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        // Math tool is purely computational.
        return resourceLimiter.acquireFileOperation("metadata_access"); // Or a CPU-specific or no-op permit
    }

    // Renamed execute to executeInternal
    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String operation = getRequiredString(arguments, "operation"); // Use inherited
            double a = getRequiredNumber(arguments, "a").doubleValue(); // Use inherited

            double result;
            String resultText;

            switch (operation.toLowerCase()) {
                case "add" -> {
                    double b = getRequiredNumber(arguments, "b").doubleValue();
                    result = a + b;
                    resultText = String.format("%.6f + %.6f = %.6f", a, b, result);
                }
                case "subtract" -> {
                    double b = getRequiredNumber(arguments, "b").doubleValue();
                    result = a - b;
                    resultText = String.format("%.6f - %.6f = %.6f", a, b, result);
                }
                case "multiply" -> {
                    double b = getRequiredNumber(arguments, "b").doubleValue();
                    result = a * b;
                    resultText = String.format("%.6f × %.6f = %.6f", a, b, result);
                }
                case "divide" -> {
                    double b = getRequiredNumber(arguments, "b").doubleValue();
                    if (b == 0) {
                        throw new ToolExecutionException("Division by zero is not allowed");
                    }
                    result = a / b;
                    resultText = String.format("%.6f ÷ %.6f = %.6f", a, b, result);
                }
                case "power" -> {
                    double b = getRequiredNumber(arguments, "b").doubleValue();
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
                default -> throw new ToolExecutionException("Unknown operation: " + operation); // Should be caught by validateInputs
            }

            // Clean up the formatting (remove unnecessary trailing zeros)
            resultText = resultText.replaceAll("(\\.\\d*?)0+(?=\\D|$)", "$1").replaceAll("\\.$", "");

            logger.debug("Performed math operation: {}", resultText); // Use inherited logger
            return super.createTextResult(resultText); // Use inherited createTextResult

        } catch (Exception e) { // Catch any other unexpected exceptions
            logger.error("Error in MathTool execution", e);
            if (e instanceof ToolExecutionException) throw (ToolExecutionException) e; // Avoid re-wrapping
            throw new ToolExecutionException("Math calculation failed: " + e.getMessage(), e);
        }
    }

    // Helper methods getRequiredString, getRequiredDouble, and createTextResult are removed
    // as they are inherited from BaseMcpTool.
}
