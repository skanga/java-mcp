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
 * Architect Tool - AI-powered system architecture analysis and design agent
 */
public class ArchitectTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(ArchitectTool.class);
    private final AIClient aiClient;

    // System prompt based on clojure-mcp architect agent
    private static final String ARCHITECT_SYSTEM_PROMPT = """
You are an expert software architect with deep knowledge of system design, software engineering
principles, and modern technology stacks. Your role is to analyze, design, and provide architectural
guidance for software systems.

## Core Expertise Areas:
- **System Architecture**: Microservices, monoliths, serverless, event-driven architectures
- **Scalability & Performance**: Load balancing, caching strategies, database optimization
- **Security**: Authentication, authorization, data protection, secure communication
- **Technology Stack Selection**: Languages, frameworks, databases, cloud services
- **Design Patterns**: GoF patterns, architectural patterns, anti-patterns
- **Cloud Architecture**: AWS, Azure, GCP, container orchestration, containerization, virtualization
- **Data Architecture**: SQL/NoSQL databases, data lakes, streaming, ETL/ELT
- **DevOps & Infrastructure**: Infrastructure as Code, monitoring, observability, CI/CD

## Analysis Approach:
1. **Requirements Analysis**: Understand functional and non-functional requirements
2. **Constraint Identification**: Technical, business, regulatory, and resource constraints
3. **Trade-off Analysis**: Performance vs complexity, cost vs scalability, etc.
4. **Risk Assessment**: Technical risks, architectural debt, future challenges
5. **Recommendation Synthesis**: Provide clear, actionable architectural guidance

## Communication Style:
- Provide structured, detailed architectural analysis
- Use diagrams and visual representations when helpful (ASCII art, mermaid syntax)
- Explain trade-offs and reasoning behind recommendations
- Include implementation considerations and migration strategies
- Suggest metrics and monitoring approaches
- Consider both current needs and future evolution

## Output Format:
Structure your responses with clear sections:
- Executive Summary
- Requirements Analysis
- Architectural Recommendations
- Technology Stack Suggestions
- Implementation Roadmap
- Risk Considerations
- Monitoring & Observability

Always consider maintainability, testability, and operational excellence in your recommendations.
""";

    public ArchitectTool(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public String getName() {
        return "architect";
    }

    @Override
    public String getDescription() {
        return "AI-powered software architecture analysis and design agent for system architecture, technology selection, and design guidance";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "task", Map.of(
                                "type", "string",
                                "description", "Architecture task or question to analyze"
                        ),
                        "context", Map.of(
                                "type", "string",
                                "description", "Additional context about the system, requirements, or constraints"
                        ),
                        "system_type", Map.of(
                                "type", "string",
                                "description", "Type of system (web app, mobile app, microservices, data platform, etc.)",
                                "enum", List.of("web_application", "mobile_application", "microservices", "data_platform",
                                        "api_service", "enterprise_system", "real_time_system", "batch_processing", "other")
                        ),
                        "scale", Map.of(
                                "type", "string",
                                "description", "Expected scale and load requirements",
                                "enum", List.of("startup", "small_business", "enterprise", "internet_scale", "unknown")
                        ),
                        "preferred_technologies", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Preferred or existing technologies to consider"
                        ),
                        "constraints", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Technical, business, or regulatory constraints"
                        ),
                        "ai_provider", Map.of(
                                "type", "string",
                                "description", "Preferred AI provider (anthropic, openai, gemini)",
                                "enum", List.of("anthropic", "openai", "gemini", "auto"),
                                "default", "auto"
                        ),
                        "detail_level", Map.of(
                                "type", "string",
                                "description", "Level of detail in the analysis",
                                "enum", List.of("overview", "detailed", "comprehensive"),
                                "default", "detailed"
                        )
                ),
                "required", List.of("task")
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String task = getRequiredString(arguments, "task");
            String context = getOptionalString(arguments, "context", "");
            String systemType = getOptionalString(arguments, "system_type", "other");
            String scale = getOptionalString(arguments, "scale", "unknown");
            @SuppressWarnings("unchecked")
            List<String> preferredTechnologies = (List<String>) arguments.getOrDefault("preferred_technologies", List.of());
            @SuppressWarnings("unchecked")
            List<String> constraints = (List<String>) arguments.getOrDefault("constraints", List.of());
            String aiProvider = getOptionalString(arguments, "ai_provider", "auto");
            String detailLevel = getOptionalString(arguments, "detail_level", "detailed");

            // Build comprehensive prompt
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("## Architecture Analysis Request\n\n");
            promptBuilder.append("**Task**: ").append(task).append("\n\n");

            if (!context.isEmpty()) {
                promptBuilder.append("**Context**: ").append(context).append("\n\n");
            }

            promptBuilder.append("**System Type**: ").append(systemType.replace("_", " ")).append("\n");
            promptBuilder.append("**Expected Scale**: ").append(scale.replace("_", " ")).append("\n");
            promptBuilder.append("**Detail Level Required**: ").append(detailLevel).append("\n\n");

            if (!preferredTechnologies.isEmpty()) {
                promptBuilder.append("**Preferred/Existing Technologies**: ");
                promptBuilder.append(String.join(", ", preferredTechnologies)).append("\n\n");
            }

            if (!constraints.isEmpty()) {
                promptBuilder.append("**Constraints**:\n");
                constraints.forEach(constraint -> promptBuilder.append("- ").append(constraint).append("\n"));
                promptBuilder.append("\n");
            }

            // Add detail level specific instructions
            switch (detailLevel) {
                case "overview" -> promptBuilder.append("Please provide a high-level architectural overview with key recommendations.\n\n");
                case "comprehensive" -> promptBuilder.append("Please provide a comprehensive architectural analysis with detailed implementation guidance, code examples, and deployment strategies.\n\n");
                default -> promptBuilder.append("Please provide a detailed architectural analysis with clear recommendations and reasoning.\n\n");
            }

            promptBuilder.append("Analyze this architectural challenge and provide structured guidance.");

            // Create AI request
            AIClient.AIRequest aiRequest = new AIClient.AIRequest(promptBuilder.toString(), ARCHITECT_SYSTEM_PROMPT);
            aiRequest.setPreferredProvider(aiProvider);
            aiRequest.setMaxTokens(8000);

            // Get AI response
            AIClient.AIResponse aiResponse = aiClient.sendMessage(aiRequest);

            // Format final response
            StringBuilder responseBuilder = new StringBuilder();
            responseBuilder.append("# 🏗️ Architecture Analysis\n\n");
            responseBuilder.append("**System Type**: ").append(systemType.replace("_", " ")).append("\n");
            responseBuilder.append("**Scale**: ").append(scale.replace("_", " ")).append("\n");
            responseBuilder.append("**AI Provider**: ").append(aiResponse.getProvider()).append("\n");
            responseBuilder.append("**Analysis Level**: ").append(detailLevel).append("\n\n");
            responseBuilder.append("---\n\n");
            responseBuilder.append(aiResponse.getContent());

            // Add usage info if available
            if (aiResponse.getUsage() != null) {
                responseBuilder.append("\n\n---\n");
                responseBuilder.append("**Token Usage**: ").append(aiResponse.getUsage().toString());
            }

            logger.info("Architect analysis completed using {} provider", aiResponse.getProvider());
            return createTextResult(responseBuilder.toString());

        } catch (AIClient.AIException e) {
            logger.error("AI request failed for architect tool", e);
            throw new ToolExecutionException("Architecture analysis failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error in architect tool execution", e);
            throw new ToolExecutionException("Architect tool failed: " + e.getMessage(), e);
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

    private McpModels.CallToolResponse.CallToolResult createTextResult(String text) {
        McpModels.CallToolResponse.CallToolResult result = new McpModels.CallToolResponse.CallToolResult();
        McpModels.Content content = new McpModels.Content();
        content.type = "text";
        content.text = text;
        result.content = List.of(content);
        return result;
    }
}