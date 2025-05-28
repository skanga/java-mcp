package com.example.mcp.tool.filesystem;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.tool.BaseMcpTool;
import com.example.mcp.security.SecurityContext;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Secure File Search Tool - Search for text patterns with comprehensive security controls
 */
public class SecureFileSearchTool extends BaseMcpTool {
    private static final int MAX_RESULTS = 500; // Reduced from 1000 for security

    public SecureFileSearchTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

    @Override
    public String getName() {
        return "search_files";
    }

    @Override
    public String getDescription() {
        return "Securely search for text patterns in files with comprehensive validation";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "pattern", Map.of(
                                "type", "string",
                                "description", "Search pattern (text or regex)"
                        ),
                        "path", Map.of(
                                "type", "string",
                                "description", "Directory or file path to search",
                                "default", "."
                        ),
                        "file_pattern", Map.of(
                                "type", "string",
                                "description", "File name pattern to include",
                                "default", "*"
                        ),
                        "regex", Map.of(
                                "type", "boolean",
                                "description", "Treat pattern as regular expression",
                                "default", false
                        ),
                        "case_sensitive", Map.of(
                                "type", "boolean",
                                "description", "Case sensitive search",
                                "default", true
                        ),
                        "context_lines", Map.of(
                                "type", "integer",
                                "description", "Number of context lines",
                                "minimum", 0,
                                "maximum", 5, // Reduced for security
                                "default", 2
                        ),
                        "max_depth", Map.of(
                                "type", "integer",
                                "description", "Maximum directory depth",
                                "minimum", 1,
                                "maximum", 10, // Reduced for security
                                "default", 5
                        ),
                        "max_results", Map.of(
                                "type", "integer",
                                "description", "Maximum number of results",
                                "minimum", 1,
                                "maximum", MAX_RESULTS,
                                "default", 50
                        )
                ),
                "required", List.of("pattern")
        );
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        return resourceLimiter.acquireSearchOperation();
    }

    @Override
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String pattern = getRequiredString(arguments, "pattern");
        String pathStr = getOptionalString(arguments, "path", ".");
        boolean useRegex = getOptionalBoolean(arguments, "regex", false);
        boolean caseSensitive = getOptionalBoolean(arguments, "case_sensitive", true);

        // Validate search path
        Path searchPath = validatePath(pathStr);
        if (!Files.exists(searchPath)) {
            throw new ToolExecutionException("Search path does not exist: " + pathStr);
        }

        // Validate and compile search pattern securely
        createSafePattern(pattern, caseSensitive);

        // Additional validation for regex patterns
        if (useRegex && pattern.length() > 200) {
            throw new ToolExecutionException("Regex pattern too long (max 200 characters)");
        }
    }

    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments)
            throws ToolExecutionException {

        String pattern = getRequiredString(arguments, "pattern");
        String pathStr = getOptionalString(arguments, "path", ".");
        String filePattern = getOptionalString(arguments, "file_pattern", "*");
        boolean useRegex = getOptionalBoolean(arguments, "regex", false);
        boolean caseSensitive = getOptionalBoolean(arguments, "case_sensitive", true);
        int contextLines = getOptionalInt(arguments, "context_lines", 2);
        int maxDepth = getOptionalInt(arguments, "max_depth", 5);
        int maxResults = getOptionalInt(arguments, "max_results", 50);

        try {
            Path searchPath = validatePath(pathStr);

            // Create secure search pattern
            Pattern searchPattern = useRegex ?
                    createSafePattern(pattern, caseSensitive) :
                    createSafePattern(Pattern.quote(pattern), caseSensitive);

            // Create file matcher
            PathMatcher fileMatcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);

            // Perform secure search
            SearchResult result = performSecureSearch(searchPath, searchPattern, fileMatcher,
                    contextLines, maxDepth, maxResults);

            // Format results
            String formattedResults = formatSecureSearchResults(result, pattern, pathStr);

            logger.debug("Secure search completed: {} matches in {} files",
                    result.totalMatches, result.matchingFiles.size());

            return createTextResult(formattedResults);

        } catch (IOException e) {
            logger.error("Error performing secure search", e);
            throw new ToolExecutionException("Search failed: " + e.getMessage(), e);
        }
    }

    private SearchResult performSecureSearch(Path searchPath, Pattern searchPattern,
                                             PathMatcher fileMatcher, int contextLines,
                                             int maxDepth, int maxResults) throws IOException {

        SearchResult result = new SearchResult();
        SecureSearchVisitor visitor = new SecureSearchVisitor(searchPattern, fileMatcher,
                contextLines, maxResults, result);

        if (Files.isRegularFile(searchPath)) {
            // Search single file
            if (isFileAllowed(searchPath)) {
                visitor.searchFile(searchPath);
            }
        } else {
            // Search directory tree with security controls
            Files.walkFileTree(searchPath,
                    Set.of(FileVisitOption.FOLLOW_LINKS),
                    maxDepth,
                    visitor);
        }

        return result;
    }

    private String formatSecureSearchResults(SearchResult result, String pattern, String searchPath) {
        StringBuilder output = new StringBuilder();

        output.append("🔍 Secure Search Results\n");
        output.append("═".repeat(60)).append("\n");
        output.append("Pattern: ").append(truncateString(pattern, 50)).append("\n");
        output.append("Path: ").append(searchPath).append("\n");
        output.append("Results: ").append(result.totalMatches).append(" matches in ")
                .append(result.matchingFiles.size()).append(" files\n");

        if (result.truncated) {
            output.append("⚠️ Results truncated for security\n");
        }

        if (!result.errors.isEmpty()) {
            output.append("⚠️ ").append(result.errors.size()).append(" files skipped due to security restrictions\n");
        }

        output.append("\n");

        // Display results grouped by file
        Map<String, List<SearchMatch>> matchesByFile = new LinkedHashMap<>();
        for (SearchMatch match : result.matches) {
            matchesByFile.computeIfAbsent(match.filePath, k -> new ArrayList<>()).add(match);
        }

        for (Map.Entry<String, List<SearchMatch>> entry : matchesByFile.entrySet()) {
            String filePath = entry.getKey();
            List<SearchMatch> fileMatches = entry.getValue();

            output.append("📄 ").append(truncateString(filePath, 80))
                    .append(" (").append(fileMatches.size()).append(" matches)\n");
            output.append("─".repeat(50)).append("\n");

            for (SearchMatch match : fileMatches) {
                output.append(String.format("%4d: %s\n", match.lineNumber,
                        truncateString(match.line, 100)));

                // Show limited context for security
                for (String contextLine : match.contextBefore) {
                    output.append("    | ").append(truncateString(contextLine, 100)).append("\n");
                }
            }
            output.append("\n");
        }

        return output.toString();
    }

    // Search data structures
    private static class SearchResult {
        final List<SearchMatch> matches = new ArrayList<>();
        final Set<String> matchingFiles = new HashSet<>();
        final List<String> errors = new ArrayList<>();
        int totalMatches = 0;
        boolean truncated = false;
    }

    private static class SearchMatch {
        final String filePath;
        final int lineNumber;
        final String line;
        final List<String> contextBefore;

        SearchMatch(String filePath, int lineNumber, String line, List<String> contextBefore) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.line = line;
            this.contextBefore = new ArrayList<>(contextBefore);
        }
    }

    private class SecureSearchVisitor extends SimpleFileVisitor<Path> {
        private final Pattern searchPattern;
        private final PathMatcher fileMatcher;
        private final int contextLines;
        private final int maxResults;
        private final SearchResult result;

        SecureSearchVisitor(Pattern searchPattern, PathMatcher fileMatcher, int contextLines,
                            int maxResults, SearchResult result) {
            this.searchPattern = searchPattern;
            this.fileMatcher = fileMatcher;
            this.contextLines = contextLines;
            this.maxResults = maxResults;
            this.result = result;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (result.totalMatches >= maxResults) {
                result.truncated = true;
                return FileVisitResult.TERMINATE;
            }

            searchFile(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            // Additional security check for directory access
            try {
                validatePath(dir.toString());
                return FileVisitResult.CONTINUE;
            } catch (ToolExecutionException e) {
                result.errors.add("Directory access denied: " + dir);
                return FileVisitResult.SKIP_SUBTREE;
            }
        }

        void searchFile(Path file) {
            try {
                // Security validation
                if (!isFileAllowed(file)) {
                    result.errors.add("File type not allowed: " + file);
                    return;
                }

                if (!fileMatcher.matches(file.getFileName())) {
                    return;
                }

                validateFileSize(file);

                // Reserve memory for file reading
                long fileSize = Files.size(file);
                try (ResourceLimiter.MemoryReservation memReservation = reserveMemory(fileSize)) {

                    List<String> lines = Files.readAllLines(file);
                    searchInLines(file.toString(), lines);
                }

            } catch (Exception e) {
                result.errors.add("Error reading " + file + ": " + e.getMessage());
            }
        }

        private void searchInLines(String filePath, List<String> lines) {
            boolean fileHasMatches = false;

            for (int i = 0; i < lines.size() && result.totalMatches < maxResults; i++) {
                String line = lines.get(i);

                if (searchPattern.matcher(line).find()) {
                    if (!fileHasMatches) {
                        result.matchingFiles.add(filePath);
                        fileHasMatches = true;
                    }

                    // Extract limited context for security
                    List<String> contextBefore = new ArrayList<>();
                    for (int j = Math.max(0, i - contextLines); j < i; j++) {
                        contextBefore.add(lines.get(j));
                    }

                    SearchMatch match = new SearchMatch(filePath, i + 1, line, contextBefore);
                    result.matches.add(match);
                    result.totalMatches++;
                }
            }
        }
    }
}
