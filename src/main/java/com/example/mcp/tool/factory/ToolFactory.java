
package com.example.mcp.tool.factory;

import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;
import com.example.mcp.tool.McpTool;
import com.example.mcp.tool.filesystem.*;
import com.example.mcp.tool.system.*;
import com.example.mcp.config.ToolConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Factory for creating properly configured and secured MCP tools
 */
public class ToolFactory {
    private final SecurityContext securityContext;
    private final ResourceLimiter resourceLimiter;
    private final Map<String, McpTool> toolCache = new HashMap<>();

    public ToolFactory(String environment) {
        this.securityContext = ToolConfiguration.createSecurityContext(environment);
        this.resourceLimiter = ToolConfiguration.createResourceLimiter(environment);
    }

    public ToolFactory(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        this.securityContext = securityContext;
        this.resourceLimiter = resourceLimiter;
    }

    /**
     * Get or create a tool by name
     */
    public McpTool getTool(String toolName) {
        return toolCache.computeIfAbsent(toolName, this::createTool);
    }

    /**
     * Get all available tool names
     */
    public Set<String> getAvailableTools() {
        return Set.of(
                "read_file", "write_file_secure", "search_files", "directory_tree",
                "execute_command", "git_operations", "analyze_project",
                "code_critique", "dependency_lookup", "eval_code"
        );
    }

    /**
     * Create all tools and return as a map
     */
    public Map<String, McpTool> createAllTools() {
        Map<String, McpTool> tools = new HashMap<>();
        for (String toolName : getAvailableTools()) {
            tools.put(toolName, getTool(toolName));
        }
        return tools;
    }

    private McpTool createTool(String toolName) {
        return switch (toolName) {
            // Filesystem tools
            case "read_file" -> new FileReadTool(securityContext, resourceLimiter);
            case "write_file_secure" -> new SecureFileWriteTool(securityContext, resourceLimiter);
            case "search_files" -> new SecureFileSearchTool(securityContext, resourceLimiter);
            case "directory_tree" -> new SecureDirectoryTreeTool(securityContext, resourceLimiter);

            // System tools
            case "execute_command" -> new ProcessExecutorTool(securityContext, resourceLimiter);

            // Development tools (these would need to be updated to extend BaseMcpTool)
            case "git_operations" -> new SecureGitOperationsTool(securityContext, resourceLimiter);
            case "analyze_project" -> new SecureProjectAnalysisTool(securityContext, resourceLimiter);
            case "code_critique" -> new SecureCodeCritiqueTool(securityContext, resourceLimiter);
            case "dependency_lookup" -> new SecureDependencyLookupTool(securityContext, resourceLimiter);
            case "eval_code" -> new SecureREPLEvaluationTool(securityContext, resourceLimiter);

            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }

    /**
     * Get resource usage statistics
     */
    public ResourceLimiter.ResourceStats getResourceStats() {
        return resourceLimiter.getStats();
    }
}
