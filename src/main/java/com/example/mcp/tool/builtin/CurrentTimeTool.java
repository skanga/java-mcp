package com.example.mcp.tool.builtin;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter; // Added
import com.example.mcp.security.SecurityContext; // Added
import com.example.mcp.tool.BaseMcpTool; // Added
// import org.slf4j.Logger; // To be removed
// import org.slf4j.LoggerFactory; // To be removed

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Current Time tool - returns the current server time
 */
public class CurrentTimeTool extends BaseMcpTool { // Changed to extend BaseMcpTool
    // private static final Logger logger = LoggerFactory.getLogger(CurrentTimeTool.class); // Logger inherited

    // Constructor added
    public CurrentTimeTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

    @Override
    public String getName() {
        return "current_time";
    }

    @Override
    public String getDescription() {
        return "Returns the current server time in various formats";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "format", Map.of(
                                "type", "string",
                                "description", "Time format (iso, readable, timestamp)",
                                "enum", List.of("iso", "readable", "timestamp"),
                                "default", "readable"
                        ),
                        "timezone", Map.of(
                                "type", "string",
                                "description", "Timezone (currently only supports server timezone)",
                                "default", "server"
                        )
                )
        );
    }

    @Override
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String format = getOptionalString(arguments, "format", "readable");
        List<String> validFormats = List.of("iso", "readable", "timestamp");
        if (!validFormats.contains(format.toLowerCase())) {
            throw new ToolExecutionException("Invalid format: " + format + ". Must be one of " + validFormats);
        }
        // Timezone validation if it becomes more complex than just "server"
        String timezone = getOptionalString(arguments, "timezone", "server");
        if (!"server".equalsIgnoreCase(timezone)) {
            // For now, only "server" is implicitly supported. If other timezones were added,
            // validation against a list of supported timezones would go here.
            // This example assumes a future enhancement might require this.
            // logger.warn("Timezone parameter currently only supports 'server'. Ignoring specified timezone: {}", timezone);
            // Or throw new ToolExecutionException("Unsupported timezone: " + timezone); if strictly enforcing.
        }
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        // This tool performs no significant I/O or external calls.
        // It might not strictly need a permit, or a very lightweight one.
        // Using a general permit or a specific "no_op_permit" if available.
        return resourceLimiter.acquireFileOperation("metadata_access"); // Or a more fitting light-weight permit
    }

    // Renamed execute to executeInternal
    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String format = getOptionalString(arguments, "format", "readable"); // Use inherited
            LocalDateTime now = LocalDateTime.now();

            String timeString = switch (format.toLowerCase()) {
                case "iso" -> now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                case "timestamp" -> String.valueOf(System.currentTimeMillis());
                case "readable" -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                default -> throw new ToolExecutionException("Invalid format: " + format); // Should be caught by validateInputs
            };

            String response = String.format("Current server time (%s): %s", format, timeString);
            logger.debug("Generated time response in format '{}'", format); // Use inherited logger
            return super.createTextResult(response); // Use inherited createTextResult

        } catch (Exception e) { // Catch any other unexpected exceptions
            logger.error("Error in CurrentTimeTool execution", e);
            if (e instanceof ToolExecutionException) throw (ToolExecutionException) e; // Avoid re-wrapping
            throw new ToolExecutionException("Failed to get current time: " + e.getMessage(), e);
        }
    }

    // Helper methods getOptionalString and createTextResult are removed as they are inherited from BaseMcpTool
}
