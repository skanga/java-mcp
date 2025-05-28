package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;
import com.example.mcp.tool.BaseMcpTool;
// import org.slf4j.Logger; // Logger inherited from BaseMcpTool
// import org.slf4j.LoggerFactory; // LoggerFactory inherited from BaseMcpTool

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
// import java.nio.file.Paths; // Path creation will be through validatePath or Paths.get as needed
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Git Operations Tool - Common git commands for development workflow
 */
public class GitOperationsTool extends BaseMcpTool {
    // private static final Logger logger = LoggerFactory.getLogger(GitOperationsTool.class); // Logger inherited
    private static final int DEFAULT_TIMEOUT = 60; // seconds

    public GitOperationsTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

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
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String repositoryPathStr = getOptionalString(arguments, "repository_path", ".");
        Path repoPath = validatePath(repositoryPathStr); // From BaseMcpTool

        if (!Files.isDirectory(repoPath)) { // Check if it's a directory first
            throw new ToolExecutionException("Repository path is not a directory: " + repoPath);
        }
        if (!Files.exists(repoPath.resolve(".git"))) { // Then check if it's a Git repo
            throw new ToolExecutionException("Path is not a git repository: " + repoPath);
        }

        // Validate the main 'git' command itself once
        validateCommand("git");

        // Specific validations based on operation
        String operation = getRequiredString(arguments, "operation");
        switch (operation) {
            case "add":
                List<String> files = getOptionalStringList(arguments, "files", List.of());
                if (files.isEmpty()) {
                    throw new ToolExecutionException("Files parameter is required for 'add' operation.");
                }
                break;
            case "commit":
                String message = getOptionalString(arguments, "message", null);
                if (message == null || message.trim().isEmpty()) {
                    throw new ToolExecutionException("Message parameter is required for 'commit' operation.");
                }
                break;
            case "checkout":
                String branchName = getOptionalString(arguments, "branch_name", null);
                if (branchName == null || branchName.trim().isEmpty()) {
                    throw new ToolExecutionException("Branch name is required for 'checkout' operation.");
                }
                break;
            // Add other operation-specific validations if they are simple and declarative.
            // More complex validation logic can remain within executeInternal or be handled by the git command itself.
        }
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        // As it executes external git commands
        return resourceLimiter.acquireProcessOperation();
    }

    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String operation = getRequiredString(arguments, "operation");
            String repositoryPathStr = getOptionalString(arguments, "repository_path", ".");
            // Path validated in validateInputs, re-retrieve for use here
            Path repoPath = validatePath(repositoryPathStr);

            List<String> files = getOptionalStringList(arguments, "files", List.of());
            String message = getOptionalString(arguments, "message", null);
            String branchName = getOptionalString(arguments, "branch_name", null);
            int limit = getOptionalInt(arguments, "limit", 10);
            String remote = getOptionalString(arguments, "remote", "origin");
            boolean force = getOptionalBoolean(arguments, "force", false);

            // isGitRepository and other path checks are done in validateInputs

            // Execute git operation
            GitResult result = executeGitOperation(operation, repoPath, files, message,
                    branchName, limit, remote, force);

            // Format response
            String response = formatGitResult(operation, result);

            logger.debug("Git operation completed: {} in {}", operation, repoPath);
            return super.createTextResult(response); // Use BaseMcpTool's createTextResult

        } catch (IOException | InterruptedException e) { // Catch specific exceptions from executeGitOperation
            logger.error("Error executing git operation: {} on path {}", operation, getOptionalString(arguments, "repository_path", "."), e);
            throw new ToolExecutionException("Git operation failed: " + e.getMessage(), e);
        } catch (ToolExecutionException e) { // Rethrow ToolExecutionExceptions
            throw e;
        } catch (Exception e) { // Catch any other unexpected exceptions
            logger.error("Unexpected error during git operation", e);
            throw new ToolExecutionException("Unexpected error during git operation: " + e.getMessage(), e);
        }
    }

    // isGitRepository check is now part of validateInputs.

    private GitResult executeGitOperation(String operation, Path repoPath, List<String> files,
                                          String message, String branchName, int limit,
                                          String remote, boolean force) throws IOException, InterruptedException, ToolExecutionException {
        // Validations for required parameters (files for add, message for commit, branchName for checkout)
        // are now handled in validateInputs.

        List<String> command = new ArrayList<>();
        command.add("git"); // 'git' command itself validated in validateInputs

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
                    command.addAll(files); // File paths are data here
                }
            }
            case "add" -> {
                command.add("add");
                // files emptiness already checked in validateInputs
                command.addAll(files); // File paths are data here
            }
            case "commit" -> {
                // message null/empty check already in validateInputs
                command.add("commit");
                command.add("-m");
                command.add(message); // Commit message is data
            }
            case "branch" -> {
                command.add("branch");
                if (branchName != null && !branchName.trim().isEmpty()) { // Check not empty, null already handled by getOptional
                    command.add(branchName); // Branch name is data
                }
            }
            case "checkout" -> {
                // branchName null/empty check already in validateInputs
                command.add("checkout");
                command.add(branchName); // Branch name is data
            }
            case "pull" -> {
                command.add("pull");
                command.add(remote); // Remote name is data
            }
            case "push" -> {
                command.add("push");
                command.add(remote); // Remote name is data
                if (force) {
                    command.add("--force"); // Option
                }
            }
            case "stash" -> {
                command.add("stash");
                if (message != null && !message.trim().isEmpty()) { // Check not empty for message
                    command.add("push"); // Stash sub-command
                    command.add("-m");   // Option
                    command.add(message); // Stash message is data
                }
            }
            default -> throw new ToolExecutionException("Unknown git operation: " + operation); // Should ideally not be reached if enum in schema is exhaustive
        }

        return executeGitCommand(command, repoPath);
    }

    private GitResult executeGitCommand(List<String> command, Path workingDirectory)
            throws IOException, InterruptedException, ToolExecutionException { // Added ToolExecutionException for command validation
        
        // The 'git' command itself is validated in validateInputs.
        // Here we could add finer-grained validation for arguments if necessary,
        // but for git, subcommands and options are numerous.
        // For now, we rely on validateCommand("git") for the base command.

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
            throw new IOException("Git command timed out after " + DEFAULT_TIMEOUT + " seconds: " + String.join(" ", command) );
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
            if (line.contains("*")) { // Simple heuristic, could be improved
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

    // Helper methods getRequiredString, getOptionalString, getOptionalInt, getOptionalBoolean, createTextResult
    // are inherited from BaseMcpTool.

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
