package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Git Operations Tool - Common git commands for development workflow
 */
public class GitOperationsTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(GitOperationsTool.class);
    private static final int DEFAULT_TIMEOUT = 60; // seconds

    @Override
    public String getName() {
        return "git_operations";
    }

    @Override
    public String getDescription() {
        return "Execute common git operations like status, log, diff, add, commit with safety checks";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "operation", Map.of(
                                "type", "string",
                                "description", "Git operation to perform",
                                "enum", List.of("status", "log", "diff", "add", "commit", "branch", "checkout", "pull", "push", "stash")
                        ),
                        "repository_path", Map.of(
                                "type", "string",
                                "description", "Path to git repository (default: current directory)",
                                "default", "."
                        ),
                        "files", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Files to operate on (for add, diff operations)",
                                "default", List.of()
                        ),
                        "message", Map.of(
                                "type", "string",
                                "description", "Commit message (required for commit operation)"
                        ),
                        "branch_name", Map.of(
                                "type", "string",
                                "description", "Branch name (for branch, checkout operations)"
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "Limit number of results (for log operation)",
                                "minimum", 1,
                                "maximum", 100,
                                "default", 10
                        ),
                        "remote", Map.of(
                                "type", "string",
                                "description", "Remote name (for push/pull operations)",
                                "default", "origin"
                        ),
                        "force", Map.of(
                                "type", "boolean",
                                "description", "Force operation (use with caution)",
                                "default", false
                        )
                ),
                "required", List.of("operation")
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String operation = getRequiredString(arguments, "operation");
            String repositoryPath = getOptionalString(arguments, "repository_path", ".");
            @SuppressWarnings("unchecked")
            List<String> files = (List<String>) arguments.getOrDefault("files", List.of());
            String message = getOptionalString(arguments, "message", null);
            String branchName = getOptionalString(arguments, "branch_name", null);
            int limit = getOptionalInt(arguments, "limit", 10);
            String remote = getOptionalString(arguments, "remote", "origin");
            boolean force = getOptionalBoolean(arguments, "force", false);

            // Validate repository path
            Path repoPath = Paths.get(repositoryPath);
            if (!Files.exists(repoPath)) {
                throw new ToolExecutionException("Repository path does not exist: " + repositoryPath);
            }

            if (!isGitRepository(repoPath)) {
                throw new ToolExecutionException("Path is not a git repository: " + repositoryPath);
            }

            // Execute git operation
            GitResult result = executeGitOperation(operation, repoPath, files, message,
                    branchName, limit, remote, force);

            // Format response
            String response = formatGitResult(operation, result);

            logger.debug("Git operation completed: {} in {}", operation, repositoryPath);
            return createTextResult(response);

        } catch (Exception e) {
            logger.error("Error executing git operation", e);
            throw new ToolExecutionException("Git operation failed: " + e.getMessage(), e);
        }
    }

    private boolean isGitRepository(Path path) {
        return Files.exists(path.resolve(".git"));
    }

    private GitResult executeGitOperation(String operation, Path repoPath, List<String> files,
                                          String message, String branchName, int limit,
                                          String remote, boolean force) throws IOException, InterruptedException, ToolExecutionException {

        List<String> command = new ArrayList<>();
        command.add("git");

        switch (operation) {
            case "status" -> {
                command.add("status");
                command.add("--porcelain");
                command.add("--branch");
            }
            case "log" -> {
                command.add("log");
                command.add("--oneline");
                command.add("--graph");
                command.add("--decorate");
                command.add("-n");
                command.add(String.valueOf(limit));
            }
            case "diff" -> {
                command.add("diff");
                if (!files.isEmpty()) {
                    command.add("--");
                    command.addAll(files);
                }
            }
            case "add" -> {
                command.add("add");
                if (files.isEmpty()) {
                    throw new ToolExecutionException("Files parameter required for add operation");
                }
                command.addAll(files);
            }
            case "commit" -> {
                if (message == null || message.trim().isEmpty()) {
                    throw new ToolExecutionException("Message parameter required for commit operation");
                }
                command.add("commit");
                command.add("-m");
                command.add(message);
            }
            case "branch" -> {
                command.add("branch");
                if (branchName != null) {
                    command.add(branchName);
                }
            }
            case "checkout" -> {
                if (branchName == null) {
                    throw new ToolExecutionException("Branch name required for checkout operation");
                }
                command.add("checkout");
                command.add(branchName);
            }
            case "pull" -> {
                command.add("pull");
                command.add(remote);
            }
            case "push" -> {
                command.add("push");
                command.add(remote);
                if (force) {
                    command.add("--force");
                }
            }
            case "stash" -> {
                command.add("stash");
                if (message != null) {
                    command.add("push");
                    command.add("-m");
                    command.add(message);
                }
            }
            default -> throw new ToolExecutionException("Unknown git operation: " + operation);
        }

        return executeGitCommand(command, repoPath);
    }

    private GitResult executeGitCommand(List<String> command, Path workingDirectory)
            throws IOException, InterruptedException {

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);

        long startTime = System.currentTimeMillis();
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Git command timed out after " + DEFAULT_TIMEOUT + " seconds");
        }

        long endTime = System.currentTimeMillis();

        return new GitResult(
                process.exitValue(),
                output.toString(),
                endTime - startTime,
                String.join(" ", command)
        );
    }

    private String formatGitResult(String operation, GitResult result) {
        StringBuilder response = new StringBuilder();

        response.append("Git Operation: ").append(operation.toUpperCase()).append("\n");
        response.append("═".repeat(50)).append("\n");
        response.append("Command: ").append(result.command).append("\n");
        response.append("Exit Code: ").append(result.exitCode);

        if (result.exitCode == 0) {
            response.append(" ✅ SUCCESS");
        } else {
            response.append(" ❌ FAILED");
        }
        response.append("\n");

        response.append("Execution Time: ").append(result.executionTimeMs).append(" ms\n");
        response.append("\n");

        // Format output based on operation type
        if (!result.output.trim().isEmpty()) {
            response.append("Output:\n");
            response.append("─".repeat(30)).append("\n");

            switch (operation) {
                case "status" -> response.append(formatStatusOutput(result.output));
                case "log" -> response.append(formatLogOutput(result.output));
                case "diff" -> response.append(formatDiffOutput(result.output));
                case "branch" -> response.append(formatBranchOutput(result.output));
                default -> response.append(result.output);
            }
        } else if (result.exitCode == 0) {
            response.append("Operation completed successfully with no output.\n");
        }

        return response.toString();
    }

    private String formatStatusOutput(String output) {
        StringBuilder formatted = new StringBuilder();
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line.startsWith("##")) {
                // Branch information
                formatted.append("🌿 ").append(line.substring(2).trim()).append("\n");
            } else if (!line.trim().isEmpty()) {
                // File status
                String status = line.substring(0, 2);
                String file = line.substring(3);
                String emoji = switch (status.trim()) {
                    case "M" -> "📝"; // Modified
                    case "A" -> "➕"; // Added
                    case "D" -> "➖"; // Deleted
                    case "R" -> "🔄"; // Renamed
                    case "C" -> "📋"; // Copied
                    case "??" -> "❓"; // Untracked
                    default -> "📄";
                };
                formatted.append(emoji).append(" ").append(status).append(" ").append(file).append("\n");
            }
        }

        return formatted.toString();
    }

    private String formatLogOutput(String output) {
        StringBuilder formatted = new StringBuilder();
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line.contains("*")) {
                formatted.append("📝 ").append(line).append("\n");
            } else {
                formatted.append(line).append("\n");
            }
        }

        return formatted.toString();
    }

    private String formatDiffOutput(String output) {
        StringBuilder formatted = new StringBuilder();
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line.startsWith("+++") || line.startsWith("---")) {
                formatted.append("📁 ").append(line).append("\n");
            } else if (line.startsWith("+")) {
                formatted.append("➕ ").append(line).append("\n");
            } else if (line.startsWith("-")) {
                formatted.append("➖ ").append(line).append("\n");
            } else if (line.startsWith("@@")) {
                formatted.append("🔍 ").append(line).append("\n");
            } else {
                formatted.append("   ").append(line).append("\n");
            }
        }

        return formatted.toString();
    }

    private String formatBranchOutput(String output) {
        StringBuilder formatted = new StringBuilder();
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line.startsWith("*")) {
                formatted.append("🌟 ").append(line.substring(1).trim()).append(" (current)\n");
            } else {
                formatted.append("🌿 ").append(line.trim()).append("\n");
            }
        }

        return formatted.toString();
    }

    private String getRequiredString(Map<String, Object> arguments, String key) throws ToolExecutionException {
        Object value = arguments.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new ToolExecutionException("Missing required parameter: " + key);
        }
        return String.valueOf(value).trim();
    }

    private String getOptionalString(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        return value != null ? String.valueOf(value).trim() : defaultValue;
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
     * Result of git command execution
     */
    private static class GitResult {
        final int exitCode;
        final String output;
        final long executionTimeMs;
        final String command;

        GitResult(int exitCode, String output, long executionTimeMs, String command) {
            this.exitCode = exitCode;
            this.output = output;
            this.executionTimeMs = executionTimeMs;
            this.command = command;
        }
    }
}
