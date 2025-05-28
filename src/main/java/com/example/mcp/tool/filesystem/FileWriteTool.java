package com.example.mcp.tool.filesystem;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * File Write Tool - Write, append, or edit file contents
 */
public class FileWriteTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(FileWriteTool.class);
    private static final int MAX_CONTENT_SIZE = 5 * 1024 * 1024; // 5MB limit

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "Write, append, or edit file contents with backup and validation options";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "File path to write to"
                        ),
                        "content", Map.of(
                                "type", "string",
                                "description", "Content to write to the file"
                        ),
                        "mode", Map.of(
                                "type", "string",
                                "description", "Write mode",
                                "enum", List.of("write", "append", "insert", "replace_line"),
                                "default", "write"
                        ),
                        "line_number", Map.of(
                                "type", "integer",
                                "description", "Line number for insert/replace operations (1-based)",
                                "minimum", 1
                        ),
                        "encoding", Map.of(
                                "type", "string",
                                "description", "Character encoding (default: UTF-8)",
                                "enum", List.of("UTF-8", "ASCII", "ISO-8859-1", "UTF-16"),
                                "default", "UTF-8"
                        ),
                        "create_backup", Map.of(
                                "type", "boolean",
                                "description", "Create backup of existing file",
                                "default", true
                        ),
                        "create_directories", Map.of(
                                "type", "boolean",
                                "description", "Create parent directories if they don't exist",
                                "default", false
                        ),
                        "validate_syntax", Map.of(
                                "type", "boolean",
                                "description", "Validate syntax for known file types",
                                "default", false
                        )
                ),
                "required", List.of("path", "content")
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String pathStr = getRequiredString(arguments, "path");
            String content = getRequiredString(arguments, "content");
            String mode = getOptionalString(arguments, "mode", "write");
            Integer lineNumber = getOptionalInt(arguments, "line_number", null);
            String encoding = getOptionalString(arguments, "encoding", "UTF-8");
            boolean createBackup = getOptionalBoolean(arguments, "create_backup", true);
            boolean createDirectories = getOptionalBoolean(arguments, "create_directories", false);
            boolean validateSyntax = getOptionalBoolean(arguments, "validate_syntax", false);

            // Validate content size
            if (content.length() > MAX_CONTENT_SIZE) {
                throw new ToolExecutionException(
                        String.format("Content too large: %d characters (max: %d)", content.length(), MAX_CONTENT_SIZE)
                );
            }

            // Validate and prepare path
            Path filePath = Paths.get(pathStr);

            if (createDirectories && filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }

            // Validate mode-specific requirements
            if (("insert".equals(mode) || "replace_line".equals(mode)) && lineNumber == null) {
                throw new ToolExecutionException("line_number is required for " + mode + " mode");
            }

            // Determine charset
            java.nio.charset.Charset charset;
            try {
                charset = java.nio.charset.Charset.forName(encoding);
            } catch (Exception e) {
                throw new ToolExecutionException("Unsupported encoding: " + encoding);
            }

            // Perform syntax validation if requested
            if (validateSyntax) {
                validateFileSyntax(filePath, content);
            }

            // Create backup if file exists and backup is requested
            String backupPath = null;
            if (createBackup && Files.exists(filePath) && Files.isRegularFile(filePath)) {
                backupPath = createBackupFile(filePath);
            }

            // Perform the write operation
            WriteResult result = performWriteOperation(filePath, content, mode, lineNumber, charset);

            // Build response
            StringBuilder response = new StringBuilder();
            response.append("File operation completed successfully\n");
            response.append("═".repeat(50)).append("\n");
            response.append("File: ").append(filePath.toAbsolutePath()).append("\n");
            response.append("Mode: ").append(mode).append("\n");
            response.append("Encoding: ").append(encoding).append("\n");
            response.append("Size: ").append(formatFileSize(result.bytesWritten)).append("\n");
            response.append("Lines: ").append(result.lineCount).append("\n");

            if (backupPath != null) {
                response.append("Backup: ").append(backupPath).append("\n");
            }

            if (lineNumber != null) {
                response.append("Target line: ").append(lineNumber).append("\n");
            }

            response.append("Timestamp: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            logger.info("File operation completed: {} ({} bytes, {} lines)", pathStr, result.bytesWritten, result.lineCount);
            return createTextResult(response.toString());

        } catch (Exception e) {
            logger.error("Error writing file", e);
            throw new ToolExecutionException("Failed to write file: " + e.getMessage(), e);
        }
    }

    private WriteResult performWriteOperation(Path filePath, String content, String mode,
                                              Integer lineNumber, java.nio.charset.Charset charset)
            throws IOException, ToolExecutionException {

        switch (mode) {
            case "write" -> {
                Files.writeString(filePath, content, charset, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return new WriteResult(content.getBytes(charset).length, countLines(content));
            }
            case "append" -> {
                Files.writeString(filePath, content, charset, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return new WriteResult(content.getBytes(charset).length, countLines(content));
            }
            case "insert" -> {
                return insertAtLine(filePath, content, lineNumber, charset);
            }
            case "replace_line" -> {
                return replaceLineAt(filePath, content, lineNumber, charset);
            }
            default -> throw new ToolExecutionException("Unknown write mode: " + mode);
        }
    }

    private WriteResult insertAtLine(Path filePath, String content, int lineNumber,
                                     java.nio.charset.Charset charset) throws IOException, ToolExecutionException {
        List<String> lines;

        if (Files.exists(filePath)) {
            lines = Files.readAllLines(filePath, charset);
        } else {
            lines = new java.util.ArrayList<>();
        }

        // Validate line number
        if (lineNumber < 1 || lineNumber > lines.size() + 1) {
            throw new ToolExecutionException(
                    String.format("Invalid line number %d for file with %d lines", lineNumber, lines.size())
            );
        }

        // Insert content (convert to 0-based index)
        String[] contentLines = content.split("\n");
        for (int i = contentLines.length - 1; i >= 0; i--) {
            lines.add(lineNumber - 1, contentLines[i]);
        }

        // Write back to file
        Files.write(filePath, lines, charset);
        long bytesWritten = Files.size(filePath);

        return new WriteResult(bytesWritten, lines.size());
    }

    private WriteResult replaceLineAt(Path filePath, String content, int lineNumber,
                                      java.nio.charset.Charset charset) throws IOException, ToolExecutionException {
        List<String> lines;

        if (Files.exists(filePath)) {
            lines = Files.readAllLines(filePath, charset);
        } else {
            throw new ToolExecutionException("Cannot replace line in non-existent file");
        }

        // Validate line number
        if (lineNumber < 1 || lineNumber > lines.size()) {
            throw new ToolExecutionException(
                    String.format("Invalid line number %d for file with %d lines", lineNumber, lines.size())
            );
        }

        // Replace line (convert to 0-based index)
        String[] contentLines = content.split("\n");

        // Remove the original line
        lines.remove(lineNumber - 1);

        // Insert new content lines
        for (int i = 0; i < contentLines.length; i++) {
            lines.add(lineNumber - 1 + i, contentLines[i]);
        }

        // Write back to file
        Files.write(filePath, lines, charset);
        long bytesWritten = Files.size(filePath);

        return new WriteResult(bytesWritten, lines.size());
    }

    private String createBackupFile(Path originalFile) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupName = originalFile.getFileName().toString() + ".backup." + timestamp;
        Path backupPath = originalFile.getParent().resolve(backupName);

        Files.copy(originalFile, backupPath, StandardCopyOption.REPLACE_EXISTING);
        return backupPath.toString();
    }

    private void validateFileSyntax(Path filePath, String content) throws ToolExecutionException {
        String fileName = filePath.getFileName().toString().toLowerCase();

        // Basic syntax validation for common file types
        if (fileName.endsWith(".json")) {
            validateJsonSyntax(content);
        } else if (fileName.endsWith(".xml")) {
            validateXmlSyntax(content);
        } else if (fileName.endsWith(".properties")) {
            validatePropertiesSyntax(content);
        }
        // Add more validators as needed
    }

    private void validateJsonSyntax(String content) throws ToolExecutionException {
        try {
            // Use a simple JSON parser to validate
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(content);
        } catch (Exception e) {
            throw new ToolExecutionException("Invalid JSON syntax: " + e.getMessage());
        }
    }

    private void validateXmlSyntax(String content) throws ToolExecutionException {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new java.io.ByteArrayInputStream(content.getBytes()));
        } catch (Exception e) {
            throw new ToolExecutionException("Invalid XML syntax: " + e.getMessage());
        }
    }

    private void validatePropertiesSyntax(String content) throws ToolExecutionException {
        try {
            java.util.Properties props = new java.util.Properties();
            props.load(new java.io.StringReader(content));
        } catch (Exception e) {
            throw new ToolExecutionException("Invalid properties syntax: " + e.getMessage());
        }
    }

    private int countLines(String content) {
        if (content.isEmpty()) return 0;
        return (int) content.chars().filter(ch -> ch == '\n').count() + 1;
    }

    private String getRequiredString(Map<String, Object> arguments, String key) throws ToolExecutionException {
        Object value = arguments.get(key);
        if (value == null) {
            throw new ToolExecutionException("Missing required parameter: " + key);
        }
        return String.valueOf(value);
    }

    private String getOptionalString(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
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

    private boolean getOptionalBoolean(Map<String, Object> arguments, String key, boolean defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " bytes";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
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
     * Result of a write operation
     */
    private static class WriteResult {
        final long bytesWritten;
        final int lineCount;

        WriteResult(long bytesWritten, int lineCount) {
            this.bytesWritten = bytesWritten;
            this.lineCount = lineCount;
        }
    }
}
