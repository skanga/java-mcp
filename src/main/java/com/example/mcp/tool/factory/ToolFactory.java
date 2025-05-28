
package com.example.mcp.tool.factory;

import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;
import com.example.mcp.tool.McpTool;
import com.example.mcp.tool.filesystem.*;
import com.example.mcp.tool.system.*;
import com.example.mcp.tool.development.GitOperationsTool;
import com.example.mcp.tool.development.ProjectAnalysisTool;
import com.example.mcp.tool.development.CodeCritiqueTool; // Corrected import
import com.example.mcp.tool.development.DependencyLookupTool;
import com.example.mcp.tool.development.REPLEvaluationTool;
import com.example.mcp.tool.builtin.CurrentTimeTool;
import com.example.mcp.tool.builtin.EchoTool; // Added import for EchoTool
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
                // Filesystem tools
                "read_file", "write_file_secure", "search_files", "directory_tree",
                // System tools
                "execute_command",
                // Development tools
                "git_operations", "analyze_project",
                "code_critique", "dependency_lookup", "eval_code",
                // Built-in tools
                "current_time", "echo"
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
            case "directory_tree" -> new DirectoryTreeTool(securityContext, resourceLimiter); // Changed SecureDirectoryTreeTool to DirectoryTreeTool

            // System tools
            case "execute_command" -> new ProcessExecutorTool(securityContext, resourceLimiter);

            // Development tools (these would need to be updated to extend BaseMcpTool)
            case "git_operations" -> new GitOperationsTool(securityContext, resourceLimiter);
            case "analyze_project" -> new ProjectAnalysisTool(securityContext, resourceLimiter);
            case "code_critique" -> new CodeCritiqueTool(securityContext, resourceLimiter);
            case "dependency_lookup" -> new DependencyLookupTool(securityContext, resourceLimiter);
            case "eval_code" -> new REPLEvaluationTool(securityContext, resourceLimiter);

            // Built-in tools
            case "current_time" -> new CurrentTimeTool(securityContext, resourceLimiter);
            case "echo" -> new EchoTool(securityContext, resourceLimiter);

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
