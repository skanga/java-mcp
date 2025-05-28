package com.example.mcp.security;

import com.example.mcp.exception.ToolExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Security context for validating paths, commands, and other security-sensitive operations
 */
public class SecurityContext {
    private static final Logger logger = LoggerFactory.getLogger(SecurityContext.class);

    private final Set<Path> allowedPaths;
    private final Set<String> allowedCommands;
    private final Set<Pattern> allowedFilePatterns;
    private final Set<Pattern> blockedPatterns;
    private final long maxFileSize;
    private final int maxPathDepth;

    private SecurityContext(Builder builder) {
        this.allowedPaths = Set.copyOf(builder.allowedPaths);
        this.allowedCommands = Set.copyOf(builder.allowedCommands);
        this.allowedFilePatterns = Set.copyOf(builder.allowedFilePatterns);
        this.blockedPatterns = Set.copyOf(builder.blockedPatterns);
        this.maxFileSize = builder.maxFileSize;
        this.maxPathDepth = builder.maxPathDepth;
    }

    /**
     * Validate and resolve a file path to ensure it's within allowed boundaries
     */
    public Path validatePath(String pathString) throws ToolExecutionException {
        if (pathString == null || pathString.trim().isEmpty()) {
            throw new ToolExecutionException("Path cannot be null or empty");
        }

        try {
            // Normalize the path to resolve any .. or . components
            Path path = Paths.get(pathString).normalize().toAbsolutePath();

            // Check path depth
            if (path.getNameCount() > maxPathDepth) {
                throw new ToolExecutionException("Path depth exceeds maximum allowed: " + maxPathDepth);
            }

            // Check against blocked patterns
            String pathStr = path.toString();
            for (Pattern blocked : blockedPatterns) {
                if (blocked.matcher(pathStr).find()) {
                    logger.warn("Blocked path pattern detected: {}", pathString);
                    throw new ToolExecutionException("Path contains blocked pattern");
                }
            }

            // Verify path is within allowed boundaries
            boolean pathAllowed = false;
            for (Path allowedPath : allowedPaths) {
                try {
                    if (path.startsWith(allowedPath.toAbsolutePath().normalize())) {
                        pathAllowed = true;
                        break;
                    }
                } catch (Exception e) {
                    logger.debug("Error checking path against allowed path {}: {}", allowedPath, e.getMessage());
                }
            }

            if (!pathAllowed) {
                logger.warn("Path access denied: {}", pathString);
                throw new ToolExecutionException("Path is outside allowed directories");
            }

            return path;

        } catch (Exception e) {
            if (e instanceof ToolExecutionException) {
                throw e;
            }
            logger.error("Error validating path: {}", pathString, e);
            throw new ToolExecutionException("Invalid path: " + e.getMessage());
        }
    }

    /**
     * Validate a command for execution
     */
    public void validateCommand(String command) throws ToolExecutionException {
        if (command == null || command.trim().isEmpty()) {
            throw new ToolExecutionException("Command cannot be null or empty");
        }

        // Extract base command name
        String baseCommand = extractBaseCommand(command);

        if (!allowedCommands.contains(baseCommand.toLowerCase())) {
            logger.warn("Command execution denied: {}", command);
            throw new ToolExecutionException("Command not allowed: " + baseCommand);
        }

        // Additional command validation
        if (containsSuspiciousPatterns(command)) {
            logger.warn("Suspicious command pattern detected: {}", command);
            throw new ToolExecutionException("Command contains suspicious patterns");
        }
    }

    /**
     * Validate file size
     */
    public void validateFileSize(Path file) throws ToolExecutionException {
        try {
            if (Files.exists(file) && Files.isRegularFile(file)) {
                long size = Files.size(file);
                if (size > maxFileSize) {
                    throw new ToolExecutionException(
                            String.format("File too large: %d bytes (max: %d bytes)", size, maxFileSize)
                    );
                }
            }
        } catch (IOException e) {
            throw new ToolExecutionException("Error checking file size: " + e.getMessage());
        }
    }

    /**
     * Validate regex pattern for safety
     */
    public Pattern validateRegexPattern(String pattern, boolean caseSensitive) throws ToolExecutionException {
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new ToolExecutionException("Pattern cannot be null or empty");
        }

        // Check for potentially dangerous regex patterns
        if (isDangerousRegex(pattern)) {
            throw new ToolExecutionException("Regex pattern may cause performance issues");
        }

        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            return Pattern.compile(pattern, flags);
        } catch (PatternSyntaxException e) {
            throw new ToolExecutionException("Invalid regex pattern: " + e.getMessage());
        }
    }

    /**
     * Check if file matches allowed patterns
     */
    public boolean isFileAllowed(Path file) {
        String fileName = file.getFileName().toString();

        // Check against allowed patterns
        for (Pattern pattern : allowedFilePatterns) {
            if (pattern.matcher(fileName).matches()) {
                return true;
            }
        }

        return allowedFilePatterns.isEmpty(); // If no patterns specified, allow all
    }

    private String extractBaseCommand(String command) {
        // Handle paths and extract just the command name
        Path commandPath = Paths.get(command);
        String baseCommand = commandPath.getFileName().toString();

        // Remove common extensions
        if (baseCommand.endsWith(".exe") || baseCommand.endsWith(".bat") ||
                baseCommand.endsWith(".cmd") || baseCommand.endsWith(".sh")) {
            int lastDot = baseCommand.lastIndexOf('.');
            baseCommand = baseCommand.substring(0, lastDot);
        }

        return baseCommand;
    }

    private boolean containsSuspiciousPatterns(String command) {
        // Check for command injection patterns
        String[] suspiciousPatterns = {
                ";", "&&", "||", "|", "`", "$(", "${",
                "../", "..\\", "/etc/passwd", "/etc/shadow",
                "rm -rf", "del /", "format", "fdisk"
        };

        String lowerCommand = command.toLowerCase();
        for (String pattern : suspiciousPatterns) {
            if (lowerCommand.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    private boolean isDangerousRegex(String pattern) {
        // Check for patterns that could cause ReDoS
        return pattern.contains("(.*)*") ||
                pattern.contains("(.+)+") ||
                pattern.contains("(.*)+") ||
                pattern.matches(".*\\([^)]*\\*[^)]*\\)\\*.*") ||
                pattern.matches(".*\\([^)]*\\+[^)]*\\)\\+.*");
    }

    /**
     * Builder for SecurityContext
     */
    public static class Builder {
        private final Set<Path> allowedPaths = new HashSet<>();
        private final Set<String> allowedCommands = new HashSet<>();
        private final Set<Pattern> allowedFilePatterns = new HashSet<>();
        private final Set<Pattern> blockedPatterns = new HashSet<>();
        private long maxFileSize = 10 * 1024 * 1024; // 10MB default
        private int maxPathDepth = 20; // Default max depth

        public Builder addAllowedPath(String path) {
            try {
                allowedPaths.add(Paths.get(path).toAbsolutePath().normalize());
            } catch (Exception e) {
                logger.warn("Invalid allowed path: {}", path, e);
            }
            return this;
        }

        public Builder addAllowedCommand(String command) {
            allowedCommands.add(command.toLowerCase());
            return this;
        }

        public Builder addAllowedFilePattern(String pattern) {
            try {
                allowedFilePatterns.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException e) {
                logger.warn("Invalid file pattern: {}", pattern, e);
            }
            return this;
        }

        public Builder addBlockedPattern(String pattern) {
            try {
                blockedPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                logger.warn("Invalid blocked pattern: {}", pattern, e);
            }
            return this;
        }

        public Builder maxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
            return this;
        }

        public Builder maxPathDepth(int maxPathDepth) {
            this.maxPathDepth = maxPathDepth;
            return this;
        }

        public SecurityContext build() {
            // Add default safe commands if none specified
            if (allowedCommands.isEmpty()) {
                addDefaultSafeCommands();
            }

            // Add default blocked patterns
            addDefaultBlockedPatterns();

            return new SecurityContext(this);
        }

        private void addDefaultSafeCommands() {
            String[] safeCommands = {
                    "ls", "dir", "pwd", "cd", "cat", "head", "tail", "grep", "find",
                    "git", "mvn", "gradle", "npm", "yarn", "java", "javac",
                    "clojure", "lein", "python", "python3", "node", "rustc", "cargo",
                    "echo", "date", "whoami", "uname", "which", "whereis", "wc", "sort"
            };

            for (String cmd : safeCommands) {
                allowedCommands.add(cmd);
            }
        }

        private void addDefaultBlockedPatterns() {
            String[] blockedPatterns = {
                    ".*\\.\\./.*", // Path traversal
                    ".*/etc/passwd.*", // System files
                    ".*/etc/shadow.*",
                    ".*\\.ssh.*", // SSH keys
                    ".*\\$\\{.*\\}.*", // Variable expansion
                    ".*`.*`.*", // Command substitution
                    ".*\\|\\|.*", // Command chaining
                    ".*&&.*", // Command chaining
                    ".*;.*" // Command separation
            };

            for (String pattern : blockedPatterns) {
                addBlockedPattern(pattern);
            }
        }
    }

    /**
     * Create a default secure context for development environments
     */
    public static SecurityContext createDevelopmentContext() {
        return new Builder()
                .addAllowedPath(System.getProperty("user.dir"))
                .addAllowedPath(System.getProperty("user.home"))
                .maxFileSize(50 * 1024 * 1024) // 50MB for development
                .maxPathDepth(25)
                .build();
    }

    /**
     * Create a restricted context for production environments
     */
    public static SecurityContext createProductionContext(String workingDirectory) {
        return new Builder()
                .addAllowedPath(workingDirectory)
                .maxFileSize(10 * 1024 * 1024) // 10MB for production
                .maxPathDepth(15)
                .build();
    }
}
