package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter; // Added
import com.example.mcp.security.SecurityContext; // Added
import com.example.mcp.tool.BaseMcpTool; // Added
// import org.slf4j.Logger; // To be removed
// import org.slf4j.LoggerFactory; // To be removed

import javax.script.*;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REPL Evaluation Tool - Execute code in various languages (Java, JavaScript, Python)
 */
public class REPLEvaluationTool extends BaseMcpTool { // Changed to extend BaseMcpTool
    // private static final Logger logger = LoggerFactory.getLogger(REPLEvaluationTool.class); // Logger inherited
    private static final Map<String, ScriptEngine> engines = new ConcurrentHashMap<>(); // Stays static
    private static final int MAX_CODE_LENGTH = 10000; // Stays static
    private static final int MAX_OUTPUT_LENGTH = 50000; // Stays static

    // Constructor added
    public REPLEvaluationTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
        // Static 'engines' map initialization remains in static block.
    }

    static {
        // Initialize available script engines
        ScriptEngineManager manager = new ScriptEngineManager();

        // JavaScript (Nashorn/GraalVM)
        ScriptEngine jsEngine = manager.getEngineByName("javascript");
        if (jsEngine != null) {
            engines.put("javascript", jsEngine);
            engines.put("js", jsEngine);
        }

        // Try Groovy if available
        try {
            ScriptEngine groovyEngine = manager.getEngineByName("groovy");
            if (groovyEngine != null) {
                engines.put("groovy", groovyEngine);
            }
        } catch (Exception e) {
            // Static logger is not available here before BaseMcpTool constructor is called for an instance.
            // This debug log might be lost or needs a static logger if critical.
            // For now, assuming this is acceptable as it's a one-time setup warning.
            System.err.println("Groovy engine not available: " + e.getMessage());
        }

        // Try Python (Jython) if available
        try {
            ScriptEngine pythonEngine = manager.getEngineByName("python");
            if (pythonEngine != null) {
                engines.put("python", pythonEngine);
            }
        } catch (Exception e) {
            System.err.println("Python engine not available: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "eval_code";
    }

    @Override
    public String getDescription() {
        return "Execute code in various languages (JavaScript, Groovy, Python) with output capture and error handling";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of(
                                "type", "string",
                                "description", "Code to execute"
                        ),
                        "language", Map.of(
                                "type", "string",
                                "description", "Programming language",
                                "enum", List.of("javascript", "js", "groovy", "python"),
                                "default", "javascript"
                        ),
                        "timeout_seconds", Map.of(
                                "type", "integer",
                                "description", "Execution timeout in seconds",
                                "minimum", 1,
                                "maximum", 30,
                                "default", 10
                        ),
                        "capture_output", Map.of(
                                "type", "boolean",
                                "description", "Capture standard output",
                                "default", true
                        ),
                        "variables", Map.of(
                                "type", "object",
                                "description", "Variables to set in the execution context",
                                "default", Map.of()
                        )
                ),
                "required", List.of("code")
        );
    }

    @Override
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String code = getRequiredString(arguments, "code");
        if (code.length() > MAX_CODE_LENGTH) { 
            throw new ToolExecutionException("Code too long (max " + MAX_CODE_LENGTH + " characters)");
        }

        String language = getOptionalString(arguments, "language", "javascript").toLowerCase();
        if (!engines.containsKey(language)) {
            throw new ToolExecutionException("Language not supported: " + language + ". Available: " + engines.keySet());
        }

        int timeoutSeconds = getOptionalInt(arguments, "timeout_seconds", 10);
        if (timeoutSeconds < 1 || timeoutSeconds > 30) { 
            throw new ToolExecutionException("Timeout must be between 1 and 30 seconds.");
        }
        
        // Optional: Validate variable names or types if security policy dictated it.
        // Map<String, Object> variables = getOptionalMap(arguments, "variables", Map.of());
        // for (String key : variables.keySet()) {
        //    if (!key.matches("[a-zA-Z_][a-zA-Z0-9_]*")) { // Example validation
        //        throw new ToolExecutionException("Invalid variable name: " + key);
        //    }
        // }
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        return resourceLimiter.acquireProcessOperation(); // Or a more specific type like acquireCodeExecutionPermit()
    }

    // Renamed execute to executeInternal
    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String code = getRequiredString(arguments, "code"); // Use inherited
            String language = getOptionalString(arguments, "language", "javascript").toLowerCase();
            int timeoutSeconds = getOptionalInt(arguments, "timeout_seconds", 10);
            boolean captureOutput = getOptionalBoolean(arguments, "capture_output", true);
            // Using getOptionalMap for variables for better type safety, though current cast is acceptable.
            Map<String, Object> variables = getOptionalMap(arguments, "variables", Map.of());


            // Validation for code length and language support already done in validateInputs.
            // Timeout validation also done in validateInputs.

            ScriptEngine engine = engines.get(language);
            // Engine null check already effectively done by validateInputs's engines.containsKey(language)

            // Execute code
            EvaluationResult result = executeCode(engine, code, variables, captureOutput, timeoutSeconds);

            // Format response
            String response = formatEvaluationResult(result, language, code);

            logger.debug("Code evaluation completed for language: {}", language); // Use inherited logger
            return super.createTextResult(response); // Use inherited createTextResult

        } catch (Exception e) { // Catch any exception not already ToolExecutionException
            logger.error("Error evaluating code", e);
            if (e instanceof ToolExecutionException) throw (ToolExecutionException) e; // Avoid re-wrapping
            throw new ToolExecutionException("Code evaluation failed: " + e.getMessage(), e);
        }
    }

    private EvaluationResult executeCode(ScriptEngine engine, String code, Map<String, Object> variables,
                                         boolean captureOutput, int timeoutSeconds) throws ToolExecutionException {
        // Note: The timeout logic remains a post-execution check.
        // True pre-emptive timeout for engine.eval() is complex and platform-dependent.
        long startTime = System.currentTimeMillis();
        StringWriter outputWriter = new StringWriter();
        StringWriter errorWriter = new StringWriter();

        try {
            // Set up output capture
            if (captureOutput) {
                engine.getContext().setWriter(outputWriter);
                engine.getContext().setErrorWriter(errorWriter);
            }

            // Set variables
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                engine.put(entry.getKey(), entry.getValue());
            }

            // Add helper functions
            setupHelperFunctions(engine);

            // Execute code with timeout (simplified - real implementation would use thread interruption)
            Object evalResult = engine.eval(code);

            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            // Check timeout
            if (executionTime > timeoutSeconds * 1000L) { // Ensure long comparison
                throw new ToolExecutionException("Code execution timed out after " + timeoutSeconds + " seconds.");
            }

            String output = outputWriter.toString();
            String errorOutput = errorWriter.toString();

            // Truncate output if too long
            if (output.length() > MAX_OUTPUT_LENGTH) {
                output = output.substring(0, MAX_OUTPUT_LENGTH) + "\n... (output truncated)";
            }
            if (errorOutput.length() > MAX_OUTPUT_LENGTH) {
                errorOutput = errorOutput.substring(0, MAX_OUTPUT_LENGTH) + "\n... (error output truncated)";
            }


            return new EvaluationResult(
                    evalResult,
                    output,
                    errorOutput,
                    executionTime,
                    true,
                    null
            );

        } catch (ScriptException e) {
            long endTime = System.currentTimeMillis();
            // It's important to capture the error output even in case of ScriptException
            String currentOutput = outputWriter.toString();
            String currentErrorOutput = errorWriter.toString();
            if (currentErrorOutput.isEmpty()) { // If error writer is empty, use exception message
                currentErrorOutput = e.getMessage();
            }
            return new EvaluationResult(
                    null,
                    currentOutput,
                    currentErrorOutput,
                    endTime - startTime,
                    false,
                    e.getMessage() // Keep original ScriptException message for clarity
            );
        } catch (Exception e) { // Catch other runtime exceptions from script execution or setup
            long endTime = System.currentTimeMillis();
            // This could be an unexpected error not from the script itself but the setup.
            throw new ToolExecutionException("Execution error: " + e.getMessage(), e);
        }
    }

    private void setupHelperFunctions(ScriptEngine engine) {
        try {
            // Add some helpful functions depending on the engine type
            if (engine.getFactory().getEngineName().toLowerCase().contains("javascript")) {
                // Add console object for JavaScript
                engine.eval("""
                    var console = {
                        log: function() {
                            java.lang.System.out.println(Array.prototype.slice.call(arguments).join(' '));
                        },
                        error: function() {
                            java.lang.System.err.println(Array.prototype.slice.call(arguments).join(' '));
                        }
                    };
                    """);
            }
        } catch (ScriptException e) {
            logger.debug("Could not setup helper functions: {}", e.getMessage()); // Use inherited logger
        }
    }

    private String formatEvaluationResult(EvaluationResult result, String language, String code) {
        StringBuilder response = new StringBuilder();

        response.append("Code Evaluation Result\n");
        response.append("═".repeat(50)).append("\n");
        response.append("Language: ").append(language.toUpperCase()).append("\n");
        response.append("Execution Time: ").append(result.executionTimeMs).append(" ms\n");
        response.append("Status: ");

        if (result.success) {
            response.append("✅ SUCCESS");
        } else {
            response.append("❌ FAILED");
        }
        response.append("\n\n");

        // Code section
        response.append("📝 Code:\n");
        response.append("─".repeat(30)).append("\n");
        response.append(code).append("\n\n");

        // Result section
        if (result.success && result.result != null) {
            response.append("📤 Result:\n");
            response.append("─".repeat(30)).append("\n");
            response.append(formatResult(result.result)).append("\n\n");
        }

        // Output section
        if (!result.output.trim().isEmpty()) {
            response.append("📄 Output:\n");
            response.append("─".repeat(30)).append("\n");
            response.append(result.output).append("\n");
        }

        // Error section
        if (!result.errorOutput.trim().isEmpty() || !result.success) {
            response.append("❌ Errors:\n");
            response.append("─".repeat(30)).append("\n");
            if (result.errorMessage != null && !result.errorMessage.trim().isEmpty() && !result.errorOutput.contains(result.errorMessage)) {
                 // Only append errorMessage if it's not already part of errorOutput (which it often is for ScriptException)
                response.append("Error Message: ").append(result.errorMessage).append("\n");
            }
            if (!result.errorOutput.trim().isEmpty()) {
                response.append(result.errorOutput).append("\n");
            }
        }

        return response.toString();
    }

    private String formatResult(Object result) {
        if (result == null) {
            return "null";
        } else if (result instanceof String) {
            return "\"" + result + "\"";
        } else if (result instanceof Number || result instanceof Boolean) {
            return result.toString();
        } else if (result.getClass().isArray()) {
            // This needs more robust array formatting depending on array type
            if (result instanceof Object[]) return java.util.Arrays.toString((Object[]) result);
            if (result instanceof int[]) return java.util.Arrays.toString((int[]) result);
            if (result instanceof long[]) return java.util.Arrays.toString((long[]) result);
            if (result instanceof double[]) return java.util.Arrays.toString((double[]) result);
            if (result instanceof boolean[]) return java.util.Arrays.toString((boolean[]) result);
            // Add other primitive array types if necessary
            return result.toString();
        } else {
            return result.toString();
        }
    }

    // Helper methods (getRequiredString, etc.) are inherited from BaseMcpTool.

    /**
     * Result of code evaluation
     */
    private static class EvaluationResult {
        final Object result;
        final String output;
        final String errorOutput;
        final long executionTimeMs;
        final boolean success;
        final String errorMessage;

        EvaluationResult(Object result, String output, String errorOutput,
                         long executionTimeMs, boolean success, String errorMessage) {
            this.result = result;
            this.output = output;
            this.errorOutput = errorOutput;
            this.executionTimeMs = executionTimeMs;
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }
}
