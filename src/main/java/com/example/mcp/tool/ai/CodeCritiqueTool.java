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
 * Code Critique Tool - AI-powered code review and analysis agent
 */
public class CodeCritiqueTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(CodeCritiqueTool.class);
    private final AIClient aiClient;

    // System prompt based on clojure-mcp code critique agent
    private static final String CODE_CRITIQUE_SYSTEM_PROMPT = """
You are an expert code reviewer with deep knowledge of software engineering best practices, design
patterns, and code quality principles. Your role is to provide thorough, constructive code reviews
that help developers improve their code quality, maintainability, and performance.

## Core Review Areas:
- **Code Quality**: Readability, clarity, consistency, and maintainability
- **Performance**: Algorithmic efficiency, memory usage, and optimization opportunities
- **Security**: Vulnerability assessment, secure coding practices, input validation
- **Architecture**: Design patterns, SOLID principles, separation of concerns
- **Testing**: Test coverage, test quality, testability of code
- **Documentation**: Code comments, API documentation, inline explanations
- **Language Specifics**: Idiomatic usage, language best practices, modern features
- **Error Handling**: Exception management, graceful degradation, logging

## Review Methodology:
1. **Overall Assessment**: High-level code structure and organization
2. **Line-by-Line Analysis**: Detailed examination of implementation
3. **Pattern Recognition**: Identify design patterns and anti-patterns
4. **Improvement Opportunities**: Specific, actionable suggestions
5. **Risk Assessment**: Potential bugs, security issues, performance bottlenecks
6. **Best Practice Alignment**: Industry standards and conventions

## Communication Style:
- Provide constructive, specific feedback with examples
- Explain the reasoning behind each recommendation
- Suggest concrete improvements with code examples when helpful
- Balance criticism with recognition of good practices
- Prioritize issues by severity (Critical, Major, Minor, Suggestion)
- Consider both immediate fixes and long-term architectural improvements

## Code Languages Expertise:
- Java, Kotlin, Scala (JVM ecosystem)
- JavaScript, TypeScript, Node.js
- Python, Go, Rust, C++, C#
- Clojure, Haskell, F# (functional languages)
- SQL, database query optimization
- Shell scripting, configuration files

## Review Output Format:
Structure your reviews with clear sections:
- **Summary**: Overall assessment and key findings
- **Critical Issues**: Security vulnerabilities, bugs, breaking changes
- **Major Issues**: Significant design or performance problems
- **Minor Issues**: Code style, minor optimizations
- **Suggestions**: Enhancements and best practice recommendations
- **Positive Aspects**: Highlight well-written code and good practices
- **Refactoring Opportunities**: Structural improvements
- **Testing Recommendations**: Test coverage and quality improvements

Always provide specific examples and actionable recommendations for improvement.
""";

    public CodeCritiqueTool(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public String getName() {
        return "code_critique";
    }

    @Override
    public String getDescription() {
        return "AI-powered comprehensive code review and analysis agent for code quality, security, performance, and best practices";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of(
                                "type", "string",
                                "description", "Source code to review and analyze"
                        ),
                        "language", Map.of(
                                "type", "string",
                                "description", "Programming language of the code",
                                "enum", List.of("java", "javascript", "typescript", "python", "go", "rust", "cpp", "csharp",
                                        "kotlin", "scala", "clojure", "sql", "shell", "other")
                        ),
                        "context", Map.of(
                                "type", "string",
                                "description", "Additional context about the code's purpose, requirements, or constraints"
                        ),
                        "focus_areas", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Specific areas to focus on in the review",
                                "enum", List.of("security", "performance", "maintainability", "testing", "documentation",
                                        "architecture", "best_practices", "bug_detection", "style")
                        ),
                        "review_level", Map.of(
                                "type", "string",
                                "description", "Depth of review to perform",
                                "enum", List.of("quick", "standard", "thorough", "architectural"),
                                "default", "standard"
                        ),
                        "include_suggestions", Map.of(
                                "type", "boolean",
                                "description", "Include specific code improvement suggestions",
                                "default", true
                        ),
                        "check_security", Map.of(
                                "type", "boolean",
                                "description", "Perform security vulnerability analysis",
                                "default", true
                        ),
                        "ai_provider", Map.of(
                                "type", "string",
                                "description", "Preferred AI provider (anthropic, openai, gemini)",
                                "enum", List.of("anthropic", "openai", "gemini", "auto"),
                                "default", "auto"
                        )
                ),
                "required", List.of("code")
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String code = getRequiredString(arguments, "code");
            String language = getOptionalString(arguments, "language", "other");
            String context = getOptionalString(arguments, "context", "");
            @SuppressWarnings("unchecked")
            List<String> focusAreas = (List<String>) arguments.getOrDefault("focus_areas", List.of());
            String reviewLevel = getOptionalString(arguments, "review_level", "standard");
            boolean includeSuggestions = getOptionalBoolean(arguments, "include_suggestions", true);
            boolean checkSecurity = getOptionalBoolean(arguments, "check_security", true);
            String aiProvider = getOptionalString(arguments, "ai_provider", "auto");

            // Validate code length
            if (code.length() > 50000) {
                throw new ToolExecutionException("Code too large for review (max 50,000 characters)");
            }

            // Build comprehensive prompt
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("## Code Review Request\n\n");
            promptBuilder.append("**Language**: ").append(language).append("\n");
            promptBuilder.append("**Review Level**: ").append(reviewLevel).append("\n");

            if (!context.isEmpty()) {
                promptBuilder.append("**Context**: ").append(context).append("\n");
            }

            if (!focusAreas.isEmpty()) {
                promptBuilder.append("**Focus Areas**: ").append(String.join(", ", focusAreas)).append("\n");
            }

            promptBuilder.append("**Include Suggestions**: ").append(includeSuggestions).append("\n");
            promptBuilder.append("**Security Analysis**: ").append(checkSecurity).append("\n\n");

            // Add review level specific instructions
            switch (reviewLevel) {
                case "quick" -> promptBuilder.append("Please provide a quick overview focusing on the most critical issues and obvious improvements.\n\n");
                case "thorough" -> promptBuilder.append("Please provide a comprehensive, detailed review covering all aspects of code quality, with specific examples and recommendations.\n\n");
                case "architectural" -> promptBuilder.append("Please focus on architectural patterns, design decisions, and structural improvements rather than line-by-line details.\n\n");
                default -> promptBuilder.append("Please provide a balanced review covering major issues, improvements, and best practices.\n\n");
            }

            // Add focus area specific instructions
            if (!focusAreas.isEmpty()) {
                promptBuilder.append("Pay special attention to: ").append(String.join(", ", focusAreas)).append("\n\n");
            }

            promptBuilder.append("## Code to Review:\n\n");
            promptBuilder.append("```").append(language).append("\n");
            promptBuilder.append(code);
            promptBuilder.append("\n```\n\n");

            promptBuilder.append("Please provide a structured code review following your analysis methodology.");

            // Create AI request
            AIClient.AIRequest aiRequest = new AIClient.AIRequest(promptBuilder.toString(), CODE_CRITIQUE_SYSTEM_PROMPT);
            aiRequest.setPreferredProvider(aiProvider);
            aiRequest.setMaxTokens(8000);

            // Get AI response
            AIClient.AIResponse aiResponse = aiClient.sendMessage(aiRequest);

            // Format final response
            StringBuilder responseBuilder = new StringBuilder();
            responseBuilder.append("# 🔍 Code Review Analysis\n\n");
            responseBuilder.append("**Language**: ").append(language.toUpperCase()).append("\n");
            responseBuilder.append("**Review Level**: ").append(reviewLevel).append("\n");
            responseBuilder.append("**Code Length**: ").append(code.split("\n").length).append(" lines\n");
            responseBuilder.append("**AI Provider**: ").append(aiResponse.getProvider()).append("\n");

            if (!focusAreas.isEmpty()) {
                responseBuilder.append("**Focus Areas**: ").append(String.join(", ", focusAreas)).append("\n");
            }

            responseBuilder.append("\n---\n\n");
            responseBuilder.append(aiResponse.getContent());

            // Add usage info if available
            if (aiResponse.getUsage() != null) {
                responseBuilder.append("\n\n---\n");
                responseBuilder.append("**Token Usage**: ").append(aiResponse.getUsage().toString());
            }

            logger.info("Code critique completed for {} code ({} lines) using {} provider",
                    language, code.split("\n").length, aiResponse.getProvider());
            return createTextResult(responseBuilder.toString());

        } catch (AIClient.AIException e) {
            logger.error("AI request failed for code critique tool", e);
            throw new ToolExecutionException("Code review failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error in code critique tool execution", e);
            throw new ToolExecutionException("Code critique tool failed: " + e.getMessage(), e);
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
