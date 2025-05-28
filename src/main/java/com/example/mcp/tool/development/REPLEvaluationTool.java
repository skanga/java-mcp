package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REPL Evaluation Tool - Execute code in various languages (Java, JavaScript, Python)
 */
public class REPLEvaluationTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(REPLEvaluationTool.class);
    private static final Map<String, ScriptEngine> engines = new ConcurrentHashMap<>();
    private static final int MAX_CODE_LENGTH = 10000;
    private static final int MAX_OUTPUT_LENGTH = 50000;

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
            logger.debug("Groovy engine not available: {}", e.getMessage());
        }

        // Try Python (Jython) if available
        try {
            ScriptEngine pythonEngine = manager.getEngineByName("python");
            if (pythonEngine != null) {
                engines.put("python", pythonEngine);
            }
        } catch (Exception e) {
            logger.debug("Python engine not available: {}", e.getMessage());
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
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String code = getRequiredString(arguments, "code");
            String language = getOptionalString(arguments, "language", "javascript").toLowerCase();
            int timeoutSeconds = getOptionalInt(arguments, "timeout_seconds", 10);
            boolean captureOutput = getOptionalBoolean(arguments, "capture_output", true);
            @SuppressWarnings("unchecked")
            Map<String, Object> variables = (Map<String, Object>) arguments.getOrDefault("variables", Map.of());

            // Validate code length
            if (code.length() > MAX_CODE_LENGTH) {
                throw new ToolExecutionException("Code too long (max " + MAX_CODE_LENGTH + " characters)");
            }

            // Get script engine
            ScriptEngine engine = engines.get(language);
            if (engine == null) {
                throw new ToolExecutionException("Language not supported: " + language + ". Available: " + engines.keySet());
            }

            // Execute code
            EvaluationResult result = executeCode(engine, code, variables, captureOutput, timeoutSeconds);

            // Format response
            String response = formatEvaluationResult(result, language, code);

            logger.debug("Code evaluation completed for language: {}", language);
            return createTextResult(response);

        } catch (Exception e) {
            logger.error("Error evaluating code", e);
            throw new ToolExecutionException("Code evaluation failed: " + e.getMessage(), e);
        }
    }

    private EvaluationResult executeCode(ScriptEngine engine, String code, Map<String, Object> variables,
                                         boolean captureOutput, int timeoutSeconds) throws ToolExecutionException {

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
            if (executionTime > timeoutSeconds * 1000) {
                throw new ToolExecutionException("Code execution timed out");
            }

            String output = outputWriter.toString();
            String errorOutput = errorWriter.toString();

            // Truncate output if too long
            if (output.length() > MAX_OUTPUT_LENGTH) {
                output = output.substring(0, MAX_OUTPUT_LENGTH) + "\n... (output truncated)";
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
            return new EvaluationResult(
                    null,
                    outputWriter.toString(),
                    errorWriter.toString(),
                    endTime - startTime,
                    false,
                    e.getMessage()
            );
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            throw new ToolExecutionException("Execution error: " + e.getMessage());
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
            logger.debug("Could not setup helper functions: {}", e.getMessage());
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
            if (result.errorMessage != null) {
                response.append("Error: ").append(result.errorMessage).append("\n");
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
            return java.util.Arrays.toString((Object[]) result);
        } else {
            return result.toString();
        }
    }

    private String getRequiredString(Map<String, Object> arguments, String key) throws ToolExecutionException {
        Object value = arguments.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new ToolExecutionException("Missing required parameter: " + key);
        }
        return String.valueOf(value);
    }

    private String getOptionalString(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private int getOptionalInt(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getOptionalBoolean(Map<String, Object> arguments, String key, boolean defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private McpModels.CallToolResponse.CallToolResult createTextResult(String text) {
        McpModels.CallToolResponse.CallToolResult result = new McpModels.CallToolResponse.CallToolResult();
        McpModels.Content content = new McpModels.Content();
        content.type = "text";
        content.text = text;
        result.content = List.of(content);
        return result;
    }

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
