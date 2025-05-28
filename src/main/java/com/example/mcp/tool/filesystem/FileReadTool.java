package com.example.mcp.tool.filesystem;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.BaseMcpTool;
import com.example.mcp.security.SecurityContext;
import com.example.mcp.resource.ResourceLimiter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Secure File Read Tool - Read file contents with comprehensive security validation
 */
public class FileReadTool extends BaseMcpTool {

    public FileReadTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "Securely read and display file contents with validation and access controls";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "File path to read"
                        ),
                        "start_line", Map.of(
                                "type", "integer",
                                "description", "Starting line number (1-based, optional)",
                                "minimum", 1
                        ),
                        "end_line", Map.of(
                                "type", "integer",
                                "description", "Ending line number (1-based, optional)",
                                "minimum", 1
                        ),
                        "max_lines", Map.of(
                                "type", "integer",
                                "description", "Maximum number of lines to read",
                                "minimum", 1,
                                "maximum", 10000,
                                "default", 1000
                        ),
                        "encoding", Map.of(
                                "type", "string",
                                "description", "Character encoding",
                                "enum", List.of("UTF-8", "ASCII", "ISO-8859-1", "UTF-16"),
                                "default", "UTF-8"
                        ),
                        "show_line_numbers", Map.of(
                                "type", "boolean",
                                "description", "Include line numbers in output",
                                "default", false
                        )
                ),
                "required", List.of("path")
        );
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        return resourceLimiter.acquireFileOperation("read");
    }

    @Override
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String pathStr = getRequiredString(arguments, "path");
        Path filePath = validatePath(pathStr);

        // Additional file validation
        if (!Files.exists(filePath)) {
            throw new ToolExecutionException("File does not exist: " + pathStr);
        }

        if (!Files.isRegularFile(filePath)) {
            throw new ToolExecutionException("Path is not a regular file: " + pathStr);
        }

        if (!Files.isReadable(filePath)) {
            throw new ToolExecutionException("File is not readable: " + pathStr);
        }

        if (!isFileAllowed(filePath)) {
            throw new ToolExecutionException("File type not allowed: " + pathStr);
        }

        // Validate file size
        validateFileSize(filePath);

        // Validate line range
        Integer startLine = getOptionalInt(arguments, "start_line", null);
        Integer endLine = getOptionalInt(arguments, "end_line", null);

        if (startLine != null && endLine != null && startLine > endLine) {
            throw new ToolExecutionException("start_line cannot be greater than end_line");
        }
    }

    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments)
            throws ToolExecutionException {

        String pathStr = getRequiredString(arguments, "path");
        Path filePath = validatePath(pathStr);

        Integer startLine = getOptionalInt(arguments, "start_line", null);
        Integer endLine = getOptionalInt(arguments, "end_line", null);
        int maxLines = getOptionalInt(arguments, "max_lines", 1000);
        String encoding = getOptionalString(arguments, "encoding", "UTF-8");
        boolean showLineNumbers = getOptionalBoolean(arguments, "show_line_numbers", false);

        try {
            // Get file size for memory reservation
            long fileSize = Files.size(filePath);

            // Reserve memory for file reading
            try (ResourceLimiter.MemoryReservation memReservation = reserveMemory(fileSize)) {

                String content = readFileSecurely(filePath, encoding, startLine, endLine,
                        maxLines, showLineNumbers);

                // Build response
                StringBuilder response = new StringBuilder();
                response.append("📄 Secure File Read\n");
                response.append("═".repeat(60)).append("\n");
                response.append("File: ").append(filePath.toAbsolutePath()).append("\n");
                response.append("Size: ").append(formatFileSize(fileSize)).append("\n");
                response.append("Encoding: ").append(encoding).append("\n");

                if (startLine != null || endLine != null) {
                    response.append("Lines: ");
                    if (startLine != null) response.append(startLine);
                    response.append("-");
                    if (endLine != null) response.append(endLine);
                    response.append("\n");
                }

                response.append("═".repeat(60)).append("\n");
                response.append(content);

                logger.debug("Successfully read file {} ({} bytes)", pathStr, fileSize);
                return createTextResult(response.toString());
            }

        } catch (IOException e) {
            logger.error("Error reading file: {}", pathStr, e);
            throw new ToolExecutionException("Failed to read file: " + e.getMessage(), e);
        }
    }

    private String readFileSecurely(Path filePath, String encoding, Integer startLine, Integer endLine,
                                   int maxLines, boolean showLineNumbers)
            throws IOException, ToolExecutionException {

        // Validate charset
        java.nio.charset.Charset charset;
        try {
            charset = java.nio.charset.Charset.forName(encoding);
        } catch (Exception e) {
            throw new ToolExecutionException("Unsupported encoding: " + encoding);
        }

        List<String> lines = Files.readAllLines(filePath, charset);
        int totalLines = lines.size();

        // Calculate actual range
        int start = startLine != null ? Math.max(1, startLine) : 1;
        int end = endLine != null ? Math.min(totalLines, endLine) : totalLines;

        // Validate line numbers
        if (start > totalLines) {
            throw new ToolExecutionException("start_line (" + start + ") exceeds file length (" + totalLines + ")");
        }

        // Adjust for zero-based indexing
        start = start - 1;
        end = Math.min(end, totalLines);

        // Apply max lines limit
        if (end - start > maxLines) {
            end = start + maxLines;
        }

        // Extract and format lines
        StringBuilder content = new StringBuilder();
        for (int i = start; i < end; i++) {
            String line = lines.get(i);

            if (showLineNumbers) {
                content.append(String.format("%4d: %s", i + 1, line));
            } else {
                content.append(line);
            }

            content.append("\n");
        }

        // Add truncation notice if needed
        if (end < totalLines) {
            content.append("\n... (file continues for ").append(totalLines - end).append(" more lines)");
        }

        return content.toString();
    }

    public String getRequiredString(Map<String, Object> arguments, String key) throws ToolExecutionException {
        Object value = arguments.get(key);
        if (value == null) {
            throw new ToolExecutionException("Missing required parameter: " + key);
        }
        return String.valueOf(value).trim();
    }

    public String getOptionalString(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        return value != null ? String.valueOf(value).trim() : defaultValue;
    }

    private Integer getOptionalInt(Map<String, Object> arguments, String key, Integer defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getOptionalBoolean(Map<String, Object> arguments, String key, boolean defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " bytes";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public McpModels.CallToolResponse.CallToolResult createTextResult(String text) {
        McpModels.CallToolResponse.CallToolResult result = new McpModels.CallToolResponse.CallToolResult();
        McpModels.Content content = new McpModels.Content();
        content.type = "text";
        content.text = text;
        result.content = List.of(content);
        return result;
    }
}