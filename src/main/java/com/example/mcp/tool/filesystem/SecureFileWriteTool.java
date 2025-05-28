package com.example.mcp.tool.filesystem;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.BaseMcpTool;
import com.example.mcp.security.SecurityContext;
import com.example.mcp.resource.ResourceLimiter;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Secure File Write Tool - Example of how to use the new security framework
 */
public class SecureFileWriteTool extends BaseMcpTool {

    public SecureFileWriteTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

    @Override
    public String getName() {
        return "write_file_secure";
    }

    @Override
    public String getDescription() {
        return "Securely write file contents with comprehensive validation and backup";
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
                                "description", "Content to write"
                        ),
                        "mode", Map.of(
                                "type", "string",
                                "description", "Write mode",
                                "enum", List.of("write", "append"),
                                "default", "write"
                        ),
                        "create_backup", Map.of(
                                "type", "boolean",
                                "description", "Create backup of existing file",
                                "default", true
                        )
                ),
                "required", List.of("path", "content")
        );
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        return resourceLimiter.acquireFileOperation("write");
    }

    @Override
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String pathStr = getRequiredString(arguments, "path");
        String content = getRequiredString(arguments, "content");

        // Validate path
        Path filePath = validatePath(pathStr);

        // Check if we can write to the parent directory
        Path parentDir = filePath.getParent();
        if (parentDir != null && Files.exists(parentDir) && !Files.isWritable(parentDir)) {
            throw new ToolExecutionException("Cannot write to directory: " + parentDir);
        }

        // Validate content size
        long contentSize = content.getBytes().length;
        checkMemoryUsage(contentSize * 2); // Account for processing overhead

        // If file exists, validate it
        if (Files.exists(filePath)) {
            if (!Files.isRegularFile(filePath)) {
                throw new ToolExecutionException("Path exists but is not a regular file: " + pathStr);
            }
            if (!Files.isWritable(filePath)) {
                throw new ToolExecutionException("File is not writable: " + pathStr);
            }
            validateFileSize(filePath);
        }
    }

    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments)
            throws ToolExecutionException {

        String pathStr = getRequiredString(arguments, "path");
        String content = getRequiredString(arguments, "content");
        String mode = getOptionalString(arguments, "mode", "write");
        boolean createBackup = getOptionalBoolean(arguments, "create_backup", true);

        Path filePath = validatePath(pathStr);

        try {
            // Reserve memory for the operation
            long contentSize = content.getBytes().length;
            try (ResourceLimiter.MemoryReservation memReservation = reserveMemory(contentSize * 2)) {

                String backupPath = null;

                // Create backup if requested and file exists
                if (createBackup && Files.exists(filePath) && Files.isRegularFile(filePath)) {
                    backupPath = createSecureBackup(filePath);
                }

                // Perform the write operation
                WriteResult result = performSecureWrite(filePath, content, mode);

                // Format response
                StringBuilder response = new StringBuilder();
                response.append("✍️ Secure File Write Completed\n");
                response.append("═".repeat(50)).append("\n");
                response.append("File: ").append(filePath.toAbsolutePath()).append("\n");
                response.append("Mode: ").append(mode.toUpperCase()).append("\n");
                response.append("Size: ").append(formatFileSize(result.bytesWritten)).append("\n");
                response.append("Lines: ").append(result.lineCount).append("\n");

                if (backupPath != null) {
                    response.append("Backup: ").append(backupPath).append("\n");
                }

                response.append("Timestamp: ").append(
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                logger.info("Successfully wrote file: {} ({} bytes)", pathStr, result.bytesWritten);
                return createTextResult(response.toString());
            }

        } catch (IOException e) {
            logger.error("Error writing file: {}", pathStr, e);
            throw new ToolExecutionException("Failed to write file: " + e.getMessage(), e);
        }
    }

    private String createSecureBackup(Path originalFile) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupName = originalFile.getFileName().toString() + ".backup." + timestamp;
        Path backupPath = originalFile.getParent().resolve(backupName);

        // Use atomic copy operation
        Files.copy(originalFile, backupPath, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);

        logger.debug("Created backup: {}", backupPath);
        return backupPath.toString();
    }

    private WriteResult performSecureWrite(Path filePath, String content, String mode)
            throws IOException {

        if ("append".equals(mode)) {
            Files.writeString(filePath, content, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } else {
            // Use atomic write operation for safety
            Path tempFile = Files.createTempFile(filePath.getParent(),
                    filePath.getFileName().toString(), ".tmp");
            try {
                Files.writeString(tempFile, content);
                Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                // Clean up temp file on failure
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException deleteEx) {
                    logger.warn("Could not delete temp file: {}", tempFile, deleteEx);
                }
                throw e;
            }
        }

        long fileSize = Files.size(filePath);
        int lineCount = countLines(content);

        return new WriteResult(fileSize, lineCount);
    }

    private static class WriteResult {
        final long bytesWritten;
        final int lineCount;

        WriteResult(long bytesWritten, int lineCount) {
            this.bytesWritten = bytesWritten;
            this.lineCount = lineCount;
        }
    }
}
