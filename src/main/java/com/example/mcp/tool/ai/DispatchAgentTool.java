package com.example.mcp.tool.ai;

import com.example.mcp.ai.AIClient;
import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Dispatch Agent Tool - Intelligent task orchestration and multi-agent coordination
 */
public class DispatchAgentTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(DispatchAgentTool.class);
    private final AIClient aiClient;

    // System prompt based on clojure-mcp dispatch agent
    private static final String DISPATCH_AGENT_SYSTEM_PROMPT = """
You are an intelligent task dispatch agent responsible for analyzing complex requests and determining the best approach to solve them. You excel at breaking down complex problems into manageable subtasks and recommending the most appropriate tools, specialists, or methodologies.

## Core Responsibilities:
- **Task Analysis**: Understand the nature, scope, and requirements of incoming requests
- **Solution Architecture**: Design multi-step approaches for complex problems
- **Resource Allocation**: Determine optimal tools, technologies, and expertise needed
- **Process Orchestration**: Define workflows and dependencies between subtasks
- **Quality Assurance**: Ensure comprehensive coverage and quality control
- **Risk Management**: Identify potential issues and mitigation strategies

## Analysis Framework:
1. **Request Classification**: Categorize the type of work (development, analysis, design, etc.)
2. **Complexity Assessment**: Evaluate scope, dependencies, and required expertise
3. **Resource Mapping**: Identify tools, specialists, and technologies needed
4. **Workflow Design**: Create step-by-step execution plans
5. **Quality Gates**: Define checkpoints and validation criteria
6. **Timeline Estimation**: Provide realistic effort and duration estimates

## Domain Expertise:
- **Software Development**: Architecture, coding, testing, deployment
- **Data Analysis**: ETL, analytics, machine learning, visualization
- **System Operations**: Infrastructure, monitoring, security, performance
- **Project Management**: Planning, coordination, risk management
- **Technical Writing**: Documentation, specifications, user guides
- **Quality Assurance**: Testing strategies, code review, validation

## Dispatch Strategies:
- **Sequential**: Tasks that must be completed in order
- **Parallel**: Independent tasks that can run concurrently
- **Conditional**: Tasks dependent on decision points or outcomes
- **Iterative**: Tasks requiring multiple rounds of refinement
- **Escalation**: When specialized expertise or human intervention is needed

## Output Format:
Provide structured dispatch plans with:
- **Executive Summary**: High-level overview of the approach
- **Task Breakdown**: Detailed subtasks with descriptions
- **Resource Requirements**: Tools, skills, and technologies needed
- **Execution Workflow**: Step-by-step process with dependencies
- **Quality Checkpoints**: Validation and review stages
- **Risk Assessment**: Potential issues and mitigation strategies
- **Timeline & Effort**: Estimated duration and complexity
- **Success Criteria**: Clear definition of completion

## Communication Style:
- Provide clear, actionable guidance
- Use structured formats for complex plans
- Include specific tool and methodology recommendations
- Explain reasoning behind architectural decisions
- Consider both immediate execution and long-term maintenance
- Balance thoroughness with practical implementation

Always think systematically about the best way to accomplish the requested work.
""";

    public DispatchAgentTool(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public String getName() {
        return "dispatch_agent";
    }

    @Override
    public String getDescription() {
        return "Intelligent task orchestration agent that analyzes complex requests and creates structured execution plans with tool recommendations";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "request", Map.of(
                                "type", "string",
                                "description", "Complex task or project request to analyze and plan"
                        ),
                        "domain", Map.of(
                                "type", "string",
                                "description", "Primary domain or area of work",
                                "enum", List.of("software_development", "data_analysis", "system_operations",
                                        "project_management", "technical_writing", "quality_assurance",
                                        "architecture_design", "research", "automation", "other")
                        ),
                        "priority", Map.of(
                                "type", "string",
                                "description", "Task priority level",
                                "enum", List.of("low", "medium", "high", "critical"),
                                "default", "medium"
                        ),
                        "timeline", Map.of(
                                "type", "string",
                                "description", "Expected timeline or deadline",
                                "enum", List.of("immediate", "hours", "days", "weeks", "months", "flexible")
                        ),
                        "resources", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Available resources, tools, or constraints"
                        ),
                        "team_size", Map.of(
                                "type", "string",
                                "description", "Team size or working mode",
                                "enum", List.of("individual", "small_team", "large_team", "distributed"),
                                "default", "individual"
                        ),
                        "complexity", Map.of(
                                "type", "string",
                                "description", "Expected complexity level",
                                "enum", List.of("simple", "moderate", "complex", "enterprise"),
                                "default", "moderate"
                        ),
                        "include_alternatives", Map.of(
                                "type", "boolean",
                                "description", "Include alternative approaches and trade-offs",
                                "default", true
                        ),
                        "detail_level", Map.of(
                                "type", "string",
                                "description", "Level of detail in the dispatch plan",
                                "enum", List.of("overview", "detailed", "comprehensive"),
                                "default", "detailed"
                        ),
                        "ai_provider", Map.of(
                                "type", "string",
                                "description", "Preferred AI provider (anthropic, openai, gemini)",
                                "enum", List.of("anthropic", "openai", "gemini", "auto"),
                                "default", "auto"
                        )
                ),
                "required", List.of("request")
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String request = getRequiredString(arguments, "request");
            String domain = getOptionalString(arguments, "domain", "other");
            String priority = getOptionalString(arguments, "priority", "medium");
            String timeline = getOptionalString(arguments, "timeline", "flexible");
            @SuppressWarnings("unchecked")
            List<String> resources = (List<String>) arguments.getOrDefault("resources", List.of());
            String teamSize = getOptionalString(arguments, "team_size", "individual");
            String complexity = getOptionalString(arguments, "complexity", "moderate");
            boolean includeAlternatives = getOptionalBoolean(arguments, "include_alternatives", true);
            String detailLevel = getOptionalString(arguments, "detail_level", "detailed");
            String aiProvider = getOptionalString(arguments, "ai_provider", "auto");

            // Build comprehensive dispatch prompt
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("## Task Dispatch Analysis Request\n\n");
            promptBuilder.append("**Request**: ").append(request).append("\n\n");
            promptBuilder.append("**Domain**: ").append(domain.replace("_", " ")).append("\n");
            promptBuilder.append("**Priority**: ").append(priority.toUpperCase()).append("\n");
            promptBuilder.append("**Timeline**: ").append(timeline).append("\n");
            promptBuilder.append("**Team Size**: ").append(teamSize.replace("_", " ")).append("\n");
            promptBuilder.append("**Complexity**: ").append(complexity).append("\n");
            promptBuilder.append("**Detail Level**: ").append(detailLevel).append("\n");
            promptBuilder.append("**Include Alternatives**: ").append(includeAlternatives).append("\n\n");

            if (!resources.isEmpty()) {
                promptBuilder.append("**Available Resources**:\n");
                resources.forEach(resource -> promptBuilder.append("- ").append(resource).append("\n"));
                promptBuilder.append("\n");
            }

            // Add detail level specific instructions
            switch (detailLevel) {
                case "overview" -> {
                    promptBuilder.append("Please provide a high-level dispatch plan with key phases and major deliverables.\n\n");
                }
                case "comprehensive" -> {
                    promptBuilder.append("Please provide a comprehensive dispatch plan with detailed task breakdowns, ");
                    promptBuilder.append("specific tool recommendations, risk analysis, and multiple execution scenarios.\n\n");
                }
                default -> {
                    promptBuilder.append("Please provide a detailed dispatch plan with clear task breakdown, ");
                    promptBuilder.append("tool recommendations, and execution workflow.\n\n");
                }
            }

            // Add domain-specific context
            promptBuilder.append("**Domain-Specific Considerations**:\n");
            switch (domain) {
                case "software_development" -> {
                    promptBuilder.append("Consider development lifecycle, testing strategies, deployment, and maintenance.\n");
                }
                case "data_analysis" -> {
                    promptBuilder.append("Consider data sources, processing pipelines, analysis methods, and visualization.\n");
                }
                case "system_operations" -> {
                    promptBuilder.append("Consider infrastructure, monitoring, security, and operational procedures.\n");
                }
                case "project_management" -> {
                    promptBuilder.append("Consider stakeholder management, resource allocation, and delivery milestones.\n");
                }
                default -> {
                    promptBuilder.append("Consider best practices and standard workflows for this domain.\n");
                }
            }
            promptBuilder.append("\n");

            if (includeAlternatives) {
                promptBuilder.append("Include alternative approaches with trade-off analysis.\n\n");
            }

            promptBuilder.append("Analyze this request and provide a structured dispatch plan that optimally accomplishes the work.");

            // Create AI request
            AIClient.AIRequest aiRequest = new AIClient.AIRequest(promptBuilder.toString(), DISPATCH_AGENT_SYSTEM_PROMPT);
            aiRequest.setPreferredProvider(aiProvider);
            aiRequest.setMaxTokens(8000);

            // Get AI response
            AIClient.AIResponse aiResponse = aiClient.sendMessage(aiRequest);

            // Format final response
            StringBuilder responseBuilder = new StringBuilder();
            responseBuilder.append("# 🎯 Task Dispatch Plan\n\n");
            responseBuilder.append("**Domain**: ").append(domain.replace("_", " ")).append("\n");
            responseBuilder.append("**Priority**: ").append(priority.toUpperCase()).append("\n");
            responseBuilder.append("**Timeline**: ").append(timeline).append("\n");
            responseBuilder.append("**Complexity**: ").append(complexity).append("\n");
            responseBuilder.append("**Team Size**: ").append(teamSize.replace("_", " ")).append("\n");
            responseBuilder.append("**AI Provider**: ").append(aiResponse.getProvider()).append("\n\n");

            if (!resources.isEmpty()) {
                responseBuilder.append("**Available Resources**: ");
                responseBuilder.append(String.join(", ", resources)).append("\n");
            }

            responseBuilder.append("---\n\n");
            responseBuilder.append(aiResponse.getContent());

            // Add usage info if available
            if (aiResponse.getUsage() != null) {
                responseBuilder.append("\n\n---\n");
                responseBuilder.append("**Token Usage**: ").append(aiResponse.getUsage().toString());
            }

            logger.info("Dispatch plan created for {} domain with {} complexity using {} provider",
                    domain, complexity, aiResponse.getProvider());
            return createTextResult(responseBuilder.toString());

        } catch (AIClient.AIException e) {
            logger.error("AI request failed for dispatch agent tool", e);
            throw new ToolExecutionException("Task dispatch failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error in dispatch agent tool execution", e);
            throw new ToolExecutionException("Dispatch agent tool failed: " + e.getMessage(), e);
        }
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
}
