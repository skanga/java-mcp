package com.example.mcp.tool.system;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.BaseMcpTool;
import com.example.mcp.security.SecurityContext;
import com.example.mcp.resource.ResourceLimiter;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Secure Process Executor Tool - Execute system commands safely with proper validation
 */
public class ProcessExecutorTool extends BaseMcpTool {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024; // 1MB

    public ProcessExecutorTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

    @Override
    public String getName() {
        return "execute_command";
    }

    @Override
    public String getDescription() {
        return "Execute system commands with output capture, timeout, and comprehensive security controls";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of(
                                "type", "string",
                                "description", "Command to execute"
                        ),
                        "args", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Command arguments",
                                "default", List.of()
                        ),
                        "working_directory", Map.of(
                                "type", "string",
                                "description", "Working directory for command execution"
                        ),
                        "timeout_seconds", Map.of(
                                "type", "integer",
                                "description", "Command timeout in seconds",
                                "minimum", 1,
                                "maximum", 300,
                                "default", DEFAULT_TIMEOUT_SECONDS
                        ),
                        "capture_output", Map.of(
                                "type", "boolean",
                                "description", "Capture command output",
                                "default", true
                        ),
                        "environment", Map.of(
                                "type", "object",
                                "description", "Additional environment variables",
                                "default", Map.of()
                        )
                ),
                "required", List.of("command")
        );
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        return resourceLimiter.acquireProcessOperation();
    }

    @Override
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String command = getRequiredString(arguments, "command");
        validateCommand(command);

        // Validate working directory if provided
        String workingDir = getOptionalString(arguments, "working_directory", null);
        if (workingDir != null) {
            validatePath(workingDir);
        }

        // Validate timeout
        int timeout = getOptionalInt(arguments, "timeout_seconds", DEFAULT_TIMEOUT_SECONDS);
        if (timeout < 1 || timeout > 300) {
            throw new ToolExecutionException("Timeout must be between 1 and 300 seconds");
        }
    }

    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments)
            throws ToolExecutionException {

        String command = getRequiredString(arguments, "command");
        List<String> args = getOptionalStringList(arguments, "args", List.of());
        String workingDirectory = getOptionalString(arguments, "working_directory", null);
        int timeoutSeconds = getOptionalInt(arguments, "timeout_seconds", DEFAULT_TIMEOUT_SECONDS);
        boolean captureOutput = getOptionalBoolean(arguments, "capture_output", true);
        Map<String, String> environment = getOptionalStringMap(arguments, "environment", Map.of());

        try {
            // Build command list
            List<String> commandList = new ArrayList<>();
            commandList.add(command);
            commandList.addAll(args);

            // Execute command with security controls
            ProcessResult result = executeSecureProcess(commandList, workingDirectory,
                    timeoutSeconds, captureOutput, environment);

            // Format response
            String response = formatProcessResult(result, command, commandList);
            return createTextResult(response);

        } catch (Exception e) {
            logger.error("Error executing command: {}", command, e);
            throw new ToolExecutionException("Command execution failed: " + e.getMessage(), e);
        }
    }

    private ProcessResult executeSecureProcess(List<String> commandList, String workingDirectory,
                                             int timeoutSeconds, boolean captureOutput,
                                             Map<String, String> environment)
            throws IOException, InterruptedException, ToolExecutionException {

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);

        // Set working directory with validation
        if (workingDirectory != null) {
            Path workDir = validatePath(workingDirectory);
            processBuilder.directory(workDir.toFile());
        }

        // Set environment variables (filtered for security)
        if (!environment.isEmpty()) {
            Map<String, String> env = processBuilder.environment();
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // Validate environment variable names and values
                if (isSecureEnvironmentVariable(key, value)) {
                    env.put(key, value);
                } else {
                    logger.warn("Rejected environment variable: {}={}", key, value);
                }
            }
        }

        // Configure output capture
        processBuilder.redirectOutput(captureOutput ?
            ProcessBuilder.Redirect.PIPE : ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

        long startTime = System.currentTimeMillis();
        Process process = processBuilder.start();

        // Reserve memory for output capture
        try (ResourceLimiter.MemoryReservation memReservation =
                reserveMemory(MAX_OUTPUT_SIZE * 2)) { // stdout + stderr

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            // Start output readers
            Thread outputReader = null;
            Thread errorReader = null;

            if (captureOutput) {
                outputReader = new Thread(() ->
                    readStreamSafely(process.getInputStream(), stdout, "stdout"));
                outputReader.start();
            }

            errorReader = new Thread(() ->
                readStreamSafely(process.getErrorStream(), stderr, "stderr"));
            errorReader.start();

            // Wait for process completion with timeout
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Command timed out after " + timeoutSeconds + " seconds");
            }

            // Wait for output readers to finish
            if (outputReader != null) {
                outputReader.join(2000); // 2 second timeout
            }
            if (errorReader != null) {
                errorReader.join(2000);
            }

            long endTime = System.currentTimeMillis();
            return new ProcessResult(
                    process.exitValue(),
                    stdout.toString(),
                    stderr.toString(),
                    endTime - startTime
            );
        }
    }

    private void readStreamSafely(InputStream inputStream, StringBuilder output, String streamName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            int totalLength = 0;

            while ((line = reader.readLine()) != null) {
                totalLength += line.length() + 1; // +1 for newline

                if (totalLength > MAX_OUTPUT_SIZE) {
                    output.append("\n... (").append(streamName)
                          .append(" output truncated - exceeded maximum size) ...");
                    break;
                }

                output.append(line).append("\n");
            }
        } catch (IOException e) {
            output.append("\n... (error reading ").append(streamName)
                  .append(": ").append(e.getMessage()).append(") ...");
        }
    }

    private boolean isSecureEnvironmentVariable(String key, String value) {
        // Block dangerous environment variables
        String[] blockedKeys = {
            "PATH", "LD_LIBRARY_PATH", "DYLD_LIBRARY_PATH",
            "JAVA_OPTS", "MAVEN_OPTS", "GRADLE_OPTS"
        };

        for (String blocked : blockedKeys) {
            if (key.equalsIgnoreCase(blocked)) {
                return false;
            }
        }

        // Block values with suspicious content
        return !value.contains("..") && !value.contains(";") &&
               !value.contains("&&") && !value.contains("||");
    }

    private String formatProcessResult(ProcessResult result, String command, List<String> fullCommand) {
        StringBuilder response = new StringBuilder();

        response.append("🔧 Secure Command Execution\n");
        response.append("═".repeat(50)).append("\n");
        response.append("Command: ").append(String.join(" ", fullCommand)).append("\n");
        response.append("Exit Code: ").append(result.exitCode);

        if (result.exitCode == 0) {
            response.append(" ✅ SUCCESS");
        } else {
            response.append(" ❌ FAILED");
        }
        response.append("\n");
        response.append("Execution Time: ").append(result.executionTimeMs).append(" ms\n\n");

        // Standard Output
        if (!result.stdout.trim().isEmpty()) {
            response.append("📤 Standard Output:\n");
            response.append("─".repeat(30)).append("\n");
            response.append(result.stdout);
            if (!result.stdout.endsWith("\n")) {
                response.append("\n");
            }
            response.append("\n");
        }

        // Standard Error
        if (!result.stderr.trim().isEmpty()) {
            response.append("🔥 Standard Error:\n");
            response.append("─".repeat(30)).append("\n");
            response.append(result.stderr);
            if (!result.stderr.endsWith("\n")) {
                response.append("\n");
            }
        }

        return response.toString();
    }

    /**
     * Result of secure process execution
     */
    private static class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;
        final long executionTimeMs;

        ProcessResult(int exitCode, String stdout, String stderr, long executionTimeMs) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.executionTimeMs = executionTimeMs;
        }
    }
}
