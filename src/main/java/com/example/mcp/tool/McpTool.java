package com.example.mcp.tool;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;

import java.util.Map;

/**
 * Interface for MCP tools
 */
public interface McpTool {
    /**
     * Get the tool name
     */
    String getName();

    /**
     * Get the tool description
     */
    String getDescription();

    /**
     * Get the input schema for this tool
     */
    Map<String, Object> getInputSchema();

    /**
     * Execute the tool with the given arguments
     */
    McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException;
}

