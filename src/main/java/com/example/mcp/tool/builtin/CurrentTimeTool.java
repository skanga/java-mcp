package com.example.mcp.tool.builtin;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Current Time tool - returns the current server time
 */
public class CurrentTimeTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(CurrentTimeTool.class);

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
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String format = getOptionalString(arguments, "format", "readable");
            LocalDateTime now = LocalDateTime.now();

            String timeString = switch (format.toLowerCase()) {
                case "iso" -> now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                case "timestamp" -> String.valueOf(System.currentTimeMillis());
                case "readable" -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                default -> throw new ToolExecutionException("Invalid format: " + format);
            };

            String response = String.format("Current server time (%s): %s", format, timeString);
            logger.debug("Generated time response in format '{}'", format);
            return createTextResult(response);

        } catch (Exception e) {
            logger.error("Error in CurrentTimeTool execution", e);
            throw new ToolExecutionException("Failed to get current time: " + e.getMessage(), e);
        }
    }

    private String getOptionalString(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        return value != null ? String.valueOf(value).trim() : defaultValue;
    }

    private McpModels.CallToolResponse.CallToolResult createTextResult(String text) {
        McpModels.CallToolResponse.CallToolResult result = new McpModels.CallToolResponse.CallToolResult();
        McpModels.Content content = new McpModels.Content();
        content.type = "text";
        content.text = text;
        result.content = List.of(content);
        return result;
    }
}
