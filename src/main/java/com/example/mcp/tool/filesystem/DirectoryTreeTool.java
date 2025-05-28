package com.example.mcp.tool.filesystem;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Directory Tree Tool - Shows the directory structure in a tree format
 */
public class DirectoryTreeTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryTreeTool.class);

    // Default ignore patterns similar to gitignore
    private static final Set<String> DEFAULT_IGNORE_PATTERNS = Set.of(
            ".git", ".svn", ".hg", ".bzr",
            "node_modules", "target", "build", "dist",
            ".idea", ".vscode", ".eclipse",
            "*.class", "*.jar", "*.war", "*.ear",
            ".DS_Store", "Thumbs.db",
            "*.log", "*.tmp", "*.temp"
    );

    @Override
    public String getName() {
        return "directory_tree";
    }

    @Override
    public String getDescription() {
        return "Display the directory tree structure with optional filtering and depth control";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "Directory path to explore (default: current directory)",
                                "default", "."
                        ),
                        "max_depth", Map.of(
                                "type", "integer",
                                "description", "Maximum depth to traverse (default: 5)",
                                "minimum", 1,
                                "maximum", 20,
                                "default", 5
                        ),
                        "show_hidden", Map.of(
                                "type", "boolean",
                                "description", "Include hidden files and directories",
                                "default", false
                        ),
                        "include_files", Map.of(
                                "type", "boolean",
                                "description", "Include files in the tree (not just directories)",
                                "default", true
                        ),
                        "ignore_patterns", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Additional patterns to ignore (glob-style)",
                                "default", List.of()
                        )
                )
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String pathStr = getOptionalString(arguments, "path", ".");
            int maxDepth = getOptionalInt(arguments, "max_depth", 5);
            boolean showHidden = getOptionalBoolean(arguments, "show_hidden", false);
            boolean includeFiles = getOptionalBoolean(arguments, "include_files", true);
            @SuppressWarnings("unchecked")
            List<String> ignorePatterns = (List<String>) arguments.getOrDefault("ignore_patterns", List.of());

            // Validate and resolve path
            Path rootPath = Paths.get(pathStr);
            if (!Files.exists(rootPath)) {
                throw new ToolExecutionException("Path does not exist: " + pathStr);
            }

            if (!Files.isDirectory(rootPath)) {
                throw new ToolExecutionException("Path is not a directory: " + pathStr);
            }

            // Create combined ignore patterns
            Set<String> allIgnorePatterns = new HashSet<>(DEFAULT_IGNORE_PATTERNS);
            allIgnorePatterns.addAll(ignorePatterns);

            StringBuilder treeBuilder = new StringBuilder();
            treeBuilder.append("Directory Tree: ").append(rootPath.toAbsolutePath()).append("\n");
            treeBuilder.append("━".repeat(60)).append("\n");

            // Build the tree
            TreeWalker walker = new TreeWalker(maxDepth, showHidden, includeFiles, allIgnorePatterns);
            Files.walkFileTree(rootPath, walker);

            treeBuilder.append(walker.getTree());

            // Add summary
            treeBuilder.append("\n").append("─".repeat(40)).append("\n");
            treeBuilder.append("Summary: ")
                    .append(walker.getDirectoryCount()).append(" directories, ")
                    .append(walker.getFileCount()).append(" files");

            if (walker.getIgnoredCount() > 0) {
                treeBuilder.append(" (").append(walker.getIgnoredCount()).append(" ignored)");
            }

            logger.debug("Generated directory tree for {} with depth {}", pathStr, maxDepth);
            return createTextResult(treeBuilder.toString());

        } catch (Exception e) {
            logger.error("Error generating directory tree", e);
            throw new ToolExecutionException("Failed to generate directory tree: " + e.getMessage(), e);
        }
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
     * File tree walker implementation
     */
    private static class TreeWalker extends SimpleFileVisitor<Path> {
        private final int maxDepth;
        private final boolean showHidden;
        private final boolean includeFiles;
        private final Set<String> ignorePatterns;
        private final StringBuilder tree = new StringBuilder();
        private Path rootPath;
        private int directoryCount = 0;
        private int fileCount = 0;
        private int ignoredCount = 0;

        public TreeWalker(int maxDepth, boolean showHidden, boolean includeFiles, Set<String> ignorePatterns) {
            this.maxDepth = maxDepth;
            this.showHidden = showHidden;
            this.includeFiles = includeFiles;
            this.ignorePatterns = ignorePatterns;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (rootPath == null) {
                rootPath = dir;
                return FileVisitResult.CONTINUE;
            }

            int depth = rootPath.relativize(dir).getNameCount();
            if (depth > maxDepth) {
                return FileVisitResult.SKIP_SUBTREE;
            }

            String dirName = dir.getFileName().toString();

            // Check if should ignore
            if (shouldIgnore(dirName) || (!showHidden && dirName.startsWith("."))) {
                ignoredCount++;
                return FileVisitResult.SKIP_SUBTREE;
            }

            // Add directory to tree
            addToTree(dir, true, depth);
            directoryCount++;

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!includeFiles) {
                return FileVisitResult.CONTINUE;
            }

            int depth = rootPath.relativize(file).getNameCount();
            String fileName = file.getFileName().toString();

            // Check if should ignore
            if (shouldIgnore(fileName) || (!showHidden && fileName.startsWith("."))) {
                ignoredCount++;
                return FileVisitResult.CONTINUE;
            }

            // Add file to tree
            addToTree(file, false, depth);
            fileCount++;

            return FileVisitResult.CONTINUE;
        }

        private void addToTree(Path path, boolean isDirectory, int depth) {
            String indent = "│   ".repeat(Math.max(0, depth - 1));
            String connector = depth > 0 ? "├── " : "";
            String name = path.getFileName().toString();
            String icon = isDirectory ? "📁 " : "📄 ";

            tree.append(indent).append(connector).append(icon).append(name);

            if (isDirectory) {
                tree.append("/");
            } else {
                // Add file size for files
                try {
                    long size = Files.size(path);
                    tree.append(" (").append(formatFileSize(size)).append(")");
                } catch (IOException e) {
                    // Ignore size errors
                }
            }

            tree.append("\n");
        }

        private boolean shouldIgnore(String name) {
            for (String pattern : ignorePatterns) {
                if (matchesPattern(name, pattern)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesPattern(String name, String pattern) {
            // Simple glob-style matching
            if (pattern.contains("*")) {
                String regex = pattern.replace(".", "\\.")
                        .replace("*", ".*");
                return name.matches(regex);
            }
            return name.equals(pattern);
        }

        private String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }

        public String getTree() {
            return tree.toString();
        }

        public int getDirectoryCount() {
            return directoryCount;
        }

        public int getFileCount() {
            return fileCount;
        }

        public int getIgnoredCount() {
            return ignoredCount;
        }
    }
}
