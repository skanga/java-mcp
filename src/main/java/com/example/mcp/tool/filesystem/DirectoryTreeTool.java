package com.example.mcp.tool.filesystem;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;
import com.example.mcp.tool.BaseMcpTool;
// Removed unused Logger and LoggerFactory, BaseMcpTool has its own logger
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

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
public class DirectoryTreeTool extends BaseMcpTool {
    // private static final Logger logger = LoggerFactory.getLogger(DirectoryTreeTool.class); // Logger inherited from BaseMcpTool

    // Default ignore patterns similar to gitignore
    private static final Set<String> DEFAULT_IGNORE_PATTERNS = Set.of(
            ".git", ".svn", ".hg", ".bzr",
            "node_modules", "target", "build", "dist",
            ".idea", ".vscode", ".eclipse",
            "*.class", "*.jar", "*.war", "*.ear",
            ".DS_Store", "Thumbs.db",
            "*.log", "*.tmp", "*.temp"
    );

    public DirectoryTreeTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

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
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String pathStr = getOptionalString(arguments, "path", ".");
        // It's important to validate the path using the security context
        // The original code did Paths.get(pathStr) then checked existence.
        // We should use validatePath from BaseMcpTool for security.
        Path resolvedPath = validatePath(pathStr); // This is inherited from BaseMcpTool
        if (!Files.isDirectory(resolvedPath)) {
            throw new ToolExecutionException("Path is not a directory: " + resolvedPath);
        }
        // Add any other specific input validations if necessary e.g. for max_depth
        int maxDepth = getOptionalInt(arguments, "max_depth", 5);
        if (maxDepth < 1 || maxDepth > 20) { // As per original schema
            throw new ToolExecutionException("max_depth must be between 1 and 20.");
        }
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        // Example: Use a specific resource type if defined, or a general one
        return resourceLimiter.acquireFileOperation("directory_listing");
    }

    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String pathStr = getOptionalString(arguments, "path", ".");
            Path rootPath = validatePath(pathStr); // Path already validated by validateInputs

            int maxDepth = getOptionalInt(arguments, "max_depth", 5);
            boolean showHidden = getOptionalBoolean(arguments, "show_hidden", false);
            boolean includeFiles = getOptionalBoolean(arguments, "include_files", true);
            List<String> ignorePatterns = getOptionalStringList(arguments, "ignore_patterns", List.of());


            // Create combined ignore patterns
            Set<String> allIgnorePatterns = new HashSet<>(DEFAULT_IGNORE_PATTERNS);
            allIgnorePatterns.addAll(ignorePatterns);

            StringBuilder treeBuilder = new StringBuilder();
            treeBuilder.append("Directory Tree: ").append(rootPath.toAbsolutePath()).append("\n");
            treeBuilder.append("━".repeat(60)).append("\n");

            // Build the tree
            TreeWalker walker = new TreeWalker(maxDepth, showHidden, includeFiles, allIgnorePatterns, rootPath);
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

            // Use logger from BaseMcpTool
            logger.debug("Generated directory tree for {} with depth {}", pathStr, maxDepth);
            // Use createTextResult from BaseMcpTool
            return super.createTextResult(treeBuilder.toString());

        } catch (IOException e) { // Catch specific IOException from Files.walkFileTree
            logger.error("Error walking directory tree for path: {}", getOptionalString(arguments, "path", "."), e);
            throw new ToolExecutionException("Failed to walk directory tree: " + e.getMessage(), e);
        } catch (ToolExecutionException e) { // Rethrow ToolExecutionExceptions
            throw e;
        }
        catch (Exception e) { // Catch any other unexpected exceptions
            logger.error("Unexpected error generating directory tree", e);
            throw new ToolExecutionException("Unexpected error generating directory tree: " + e.getMessage(), e);
        }
    }

    // getOptionalString, getOptionalInt, getOptionalBoolean, createTextResult are inherited from BaseMcpTool


    /**
     * File tree walker implementation
     */
    private class TreeWalker extends SimpleFileVisitor<Path> { // Made non-static
        private final int maxDepth;
        private final boolean showHidden;
        private final boolean includeFiles;
        private final Set<String> ignorePatterns;
        private final StringBuilder tree = new StringBuilder();
        private final Path initialRootPath; // Store the initial root path passed to the walker
        private int directoryCount = 0;
        private int fileCount = 0;
        private int ignoredCount = 0;

        public TreeWalker(int maxDepth, boolean showHidden, boolean includeFiles, Set<String> ignorePatterns, Path initialRootPath) {
            this.maxDepth = maxDepth;
            this.showHidden = showHidden;
            this.includeFiles = includeFiles;
            this.ignorePatterns = ignorePatterns;
            this.initialRootPath = initialRootPath;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            // The first visited dir is the rootPath itself.
            // For the root directory, depth is 0. For its direct children, depth is 1, and so on.
            // The relativize logic needs to be against the initialRootPath of the walk.
            int depth = initialRootPath.relativize(dir).getNameCount();

            if (dir.equals(initialRootPath)) { // Special handling for the root dir itself
                 // If we are at the root, we always process it, depth check is for children.
            } else if (depth > maxDepth) { // For children, check depth
                return FileVisitResult.SKIP_SUBTREE;
            }


            String dirName = dir.getFileName().toString();

            // Check if should ignore (don't ignore the initial root even if it matches pattern)
            if (!dir.equals(initialRootPath) && (shouldIgnore(dirName) || (!showHidden && dirName.startsWith(".")))) {
                ignoredCount++;
                return FileVisitResult.SKIP_SUBTREE;
            }

            // Add directory to tree
            // For the root directory, we want to display its name without the tree structure prefix.
            // The depth passed to addToTree should reflect its level in the displayed tree.
            // If dir is initialRootPath, its display depth is 0.
            // Otherwise, depth is as calculated.
            addToTree(dir, true, dir.equals(initialRootPath) ? 0 : depth);
            directoryCount++;

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!includeFiles) {
                return FileVisitResult.CONTINUE;
            }

            // Depth relative to the initial root path of the walk
            int depth = initialRootPath.relativize(file).getNameCount();
            // If a file is directly under initialRootPath, its name count will be 1.
            // This depth is consistent with how addToTree expects it.
            if (depth > maxDepth +1) { // Files are children of directories, so maxDepth+1 for files inside maxDepth dirs
                 return FileVisitResult.CONTINUE;
            }


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
            String indent = depth > 0 ? "│   ".repeat(Math.max(0, depth - 1)) : "";
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
                    // Call formatFileSize from the outer class instance
                    tree.append(" (").append(DirectoryTreeTool.this.formatFileSize(size)).append(")");
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
            // Simple glob-style matching (remains the same for now)
            if (pattern.contains("*")) {
                String regex = pattern.replace(".", "\\.")
                        .replace("*", ".*");
                return name.matches(regex);
            }
            return name.equals(pattern);
        }

        // formatFileSize is now called from DirectoryTreeTool.this.formatFileSize

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
