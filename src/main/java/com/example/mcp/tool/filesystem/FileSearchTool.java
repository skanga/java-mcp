package com.example.mcp.tool.filesystem;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * File Search Tool - Search for text patterns in files and directories
 */
public class FileSearchTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(FileSearchTool.class);
    private static final int MAX_SEARCH_DEPTH = 20;
    private static final int MAX_RESULTS = 1000;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Override
    public String getName() {
        return "search_files";
    }

    @Override
    public String getDescription() {
        return "Search for text patterns in files with regex support, file filtering, and context display";
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
                                "description", "Directory or file path to search (default: current directory)",
                                "default", "."
                        ),
                        "file_pattern", Map.of(
                                "type", "string",
                                "description", "File name pattern to include (glob-style, e.g., '*.java')",
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
                        "whole_words", Map.of(
                                "type", "boolean",
                                "description", "Match whole words only",
                                "default", false
                        ),
                        "context_lines", Map.of(
                                "type", "integer",
                                "description", "Number of context lines before/after matches",
                                "minimum", 0,
                                "maximum", 10,
                                "default", 2
                        ),
                        "max_depth", Map.of(
                                "type", "integer",
                                "description", "Maximum directory depth to search",
                                "minimum", 1,
                                "maximum", MAX_SEARCH_DEPTH,
                                "default", 10
                        ),
                        "max_results", Map.of(
                                "type", "integer",
                                "description", "Maximum number of results to return",
                                "minimum", 1,
                                "maximum", MAX_RESULTS,
                                "default", 100
                        ),
                        "include_hidden", Map.of(
                                "type", "boolean",
                                "description", "Include hidden files and directories",
                                "default", false
                        )
                ),
                "required", List.of("pattern")
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String pattern = getRequiredString(arguments, "pattern");
            String pathStr = getOptionalString(arguments, "path", ".");
            String filePattern = getOptionalString(arguments, "file_pattern", "*");
            boolean useRegex = getOptionalBoolean(arguments, "regex", false);
            boolean caseSensitive = getOptionalBoolean(arguments, "case_sensitive", true);
            boolean wholeWords = getOptionalBoolean(arguments, "whole_words", false);
            int contextLines = getOptionalInt(arguments, "context_lines", 2);
            int maxDepth = getOptionalInt(arguments, "max_depth", 10);
            int maxResults = getOptionalInt(arguments, "max_results", 100);
            boolean includeHidden = getOptionalBoolean(arguments, "include_hidden", false);

            // Validate path
            Path searchPath = Paths.get(pathStr);
            if (!Files.exists(searchPath)) {
                throw new ToolExecutionException("Path does not exist: " + pathStr);
            }

            // Compile search pattern
            Pattern searchPattern = compileSearchPattern(pattern, useRegex, caseSensitive, wholeWords);

            // Compile file pattern
            PathMatcher fileMatcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);

            // Perform search
            SearchResult searchResult = performSearch(searchPath, searchPattern, fileMatcher,
                    contextLines, maxDepth, maxResults, includeHidden);

            // Format results
            String formattedResults = formatSearchResults(searchResult, pattern, pathStr, filePattern);

            logger.debug("Search completed: {} matches in {} files",
                    searchResult.totalMatches, searchResult.matchingFiles.size());

            return createTextResult(formattedResults);

        } catch (Exception e) {
            logger.error("Error performing file search", e);
            throw new ToolExecutionException("Search failed: " + e.getMessage(), e);
        }
    }

    private Pattern compileSearchPattern(String pattern, boolean useRegex, boolean caseSensitive, boolean wholeWords)
            throws ToolExecutionException {
        try {
            String actualPattern = pattern;

            if (!useRegex) {
                // Escape regex special characters for literal search
                actualPattern = Pattern.quote(pattern);
            }

            if (wholeWords) {
                actualPattern = "\\b" + actualPattern + "\\b";
            }

            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            return Pattern.compile(actualPattern, flags);

        } catch (PatternSyntaxException e) {
            throw new ToolExecutionException("Invalid regex pattern: " + e.getMessage());
        }
    }

    private SearchResult performSearch(Path searchPath, Pattern searchPattern, PathMatcher fileMatcher,
                                       int contextLines, int maxDepth, int maxResults, boolean includeHidden)
            throws IOException {

        SearchResult result = new SearchResult();
        SearchVisitor visitor = new SearchVisitor(searchPattern, fileMatcher, contextLines,
                maxResults, includeHidden, result);

        if (Files.isRegularFile(searchPath)) {
            // Search single file
            visitor.searchFile(searchPath);
        } else {
            // Search directory tree
            Files.walkFileTree(searchPath,
                    Set.of(FileVisitOption.FOLLOW_LINKS),
                    maxDepth,
                    visitor);
        }

        return result;
    }

    private String formatSearchResults(SearchResult result, String pattern, String searchPath, String filePattern) {
        StringBuilder output = new StringBuilder();

        // Header
        output.append("Search Results\n");
        output.append("═".repeat(60)).append("\n");
        output.append("Pattern: ").append(pattern).append("\n");
        output.append("Path: ").append(searchPath).append("\n");
        output.append("File pattern: ").append(filePattern).append("\n");
        output.append("Results: ").append(result.totalMatches).append(" matches in ")
                .append(result.matchingFiles.size()).append(" files\n");

        if (result.truncated) {
            output.append("⚠️  Results truncated (showing first ").append(result.matches.size()).append(" matches)\n");
        }

        output.append("\n");

        // Group matches by file
        Map<String, List<SearchMatch>> matchesByFile = new LinkedHashMap<>();
        for (SearchMatch match : result.matches) {
            matchesByFile.computeIfAbsent(match.filePath, k -> new ArrayList<>()).add(match);
        }

        // Display results
        for (Map.Entry<String, List<SearchMatch>> entry : matchesByFile.entrySet()) {
            String filePath = entry.getKey();
            List<SearchMatch> fileMatches = entry.getValue();

            output.append("📄 ").append(filePath).append(" (").append(fileMatches.size()).append(" matches)\n");
            output.append("─".repeat(50)).append("\n");

            for (SearchMatch match : fileMatches) {
                // Line number and content
                output.append(String.format("%4d: %s\n", match.lineNumber, match.line));

                // Context lines
                for (String contextLine : match.contextBefore) {
                    output.append("    | ").append(contextLine).append("\n");
                }
                for (String contextLine : match.contextAfter) {
                    output.append("    | ").append(contextLine).append("\n");
                }

                if (!match.contextBefore.isEmpty() || !match.contextAfter.isEmpty()) {
                    output.append("\n");
                }
            }

            output.append("\n");
        }

        // Summary
        if (!result.errors.isEmpty()) {
            output.append("Errors encountered:\n");
            for (String error : result.errors) {
                output.append("❌ ").append(error).append("\n");
            }
        }

        return output.toString();
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
        final List<String> contextAfter;

        SearchMatch(String filePath, int lineNumber, String line,
                    List<String> contextBefore, List<String> contextAfter) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.line = line;
            this.contextBefore = new ArrayList<>(contextBefore);
            this.contextAfter = new ArrayList<>(contextAfter);
        }
    }

    private static class SearchVisitor extends SimpleFileVisitor<Path> {
        private final Pattern searchPattern;
        private final PathMatcher fileMatcher;
        private final int contextLines;
        private final int maxResults;
        private final boolean includeHidden;
        private final SearchResult result;

        SearchVisitor(Pattern searchPattern, PathMatcher fileMatcher, int contextLines,
                      int maxResults, boolean includeHidden, SearchResult result) {
            this.searchPattern = searchPattern;
            this.fileMatcher = fileMatcher;
            this.contextLines = contextLines;
            this.maxResults = maxResults;
            this.includeHidden = includeHidden;
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
            String dirName = dir.getFileName().toString();
            if (!includeHidden && dirName.startsWith(".")) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        void searchFile(Path file) {
            try {
                String fileName = file.getFileName().toString();

                // Skip hidden files if not included
                if (!includeHidden && fileName.startsWith(".")) {
                    return;
                }

                // Check file pattern match
                if (!fileMatcher.matches(file.getFileName())) {
                    return;
                }

                // Check file size
                if (Files.size(file) > MAX_FILE_SIZE) {
                    result.errors.add("File too large, skipped: " + file);
                    return;
                }

                // Read and search file content
                List<String> lines = Files.readAllLines(file);
                searchInLines(file.toString(), lines);

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

                    // Extract context lines
                    List<String> contextBefore = new ArrayList<>();
                    List<String> contextAfter = new ArrayList<>();

                    // Context before
                    for (int j = Math.max(0, i - contextLines); j < i; j++) {
                        contextBefore.add(lines.get(j));
                    }

                    // Context after
                    for (int j = i + 1; j < Math.min(lines.size(), i + 1 + contextLines); j++) {
                        contextAfter.add(lines.get(j));
                    }

                    SearchMatch match = new SearchMatch(filePath, i + 1, line, contextBefore, contextAfter);
                    result.matches.add(match);
                    result.totalMatches++;
                }
            }
        }
    }
}