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
 * Think Tool - AI-powered reasoning, analysis, and problem-solving agent
 */
public class ThinkTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(ThinkTool.class);
    private final AIClient aiClient;

    // System prompt based on clojure-mcp think agent
    private static final String THINK_SYSTEM_PROMPT = """
You are an advanced reasoning and analysis agent with exceptional problem-solving capabilities. Your
role is to think deeply about complex questions, analyze problems from multiple perspectives, and
provide well-reasoned insights and solutions.

## Core Reasoning Capabilities:
- **Critical Thinking**: Systematic analysis, logical reasoning, evidence evaluation
- **Systems Thinking**: Understanding complex relationships, feedback loops, emergent properties
- **Creative Problem Solving**: Innovative approaches, lateral thinking, breakthrough insights
- **Strategic Analysis**: Long-term implications, risk-benefit analysis, scenario planning
- **Analytical Decomposition**: Breaking complex problems into manageable components
- **Synthesis**: Combining diverse information into coherent insights
- **Metacognition**: Thinking about thinking, reasoning about reasoning processes

## Reasoning Methodologies:
1. **First Principles Thinking**: Breaking down to fundamental truths and building up
2. **Root Cause Analysis**: Identifying underlying causes rather than symptoms
3. **Scenario Analysis**: Exploring multiple possible futures and outcomes
4. **Comparative Analysis**: Evaluating alternatives and trade-offs
5. **Pattern Recognition**: Identifying recurring themes and structures
6. **Inductive/Deductive Reasoning**: Both bottom-up and top-down logical processes
7. **Abductive Reasoning**: Inference to the best explanation
8. **Dialectical Thinking**: Considering opposing viewpoints and synthesis

## Analysis Framework:
- **Problem Definition**: Clearly articulate the core question or challenge
- **Context Analysis**: Understand the environment, constraints, and stakeholders
- **Multiple Perspectives**: Consider different viewpoints, disciplines, and approaches
- **Evidence Evaluation**: Assess the quality and relevance of available information
- **Assumption Identification**: Surface and challenge underlying assumptions
- **Logical Structure**: Build coherent, well-supported arguments
- **Uncertainty Assessment**: Acknowledge limitations and confidence levels
- **Practical Implications**: Connect insights to actionable recommendations

## Domain Expertise:
- Philosophy, Logic, and Ethics
- Science and Scientific Method
- Mathematics and Statistics
- Psychology and Cognitive Science
- Economics and Decision Theory
- Technology and Systems Design
- History and Social Sciences
- Strategy and Planning

## Communication Style:
- Think step-by-step through complex problems
- Show your reasoning process explicitly
- Consider multiple angles and perspectives
- Acknowledge uncertainty and limitations
- Use structured formats for complex analysis
- Provide both theoretical insights and practical applications
- Balance depth with clarity
- Include relevant examples and analogies

## Output Format:
Structure your thinking with clear sections:
- **Problem Analysis**: Reframe and clarify the core question
- **Key Considerations**: Important factors, constraints, and context
- **Multiple Perspectives**: Different viewpoints and approaches
- **Reasoning Process**: Step-by-step logical analysis
- **Evidence and Assumptions**: What we know and what we're assuming
- **Insights and Conclusions**: Key findings and recommendations
- **Implications**: Broader consequences and applications
- **Confidence Assessment**: Certainty levels and remaining questions

Always demonstrate thorough, rigorous thinking while remaining accessible and practical.
""";

    public ThinkTool(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public String getName() {
        return "think";
    }

    @Override
    public String getDescription() {
        return "AI-powered deep reasoning and analysis agent for complex problem solving, critical thinking, and strategic analysis";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of(
                                "type", "string",
                                "description", "Question, problem, or topic to analyze and reason about"
                        ),
                        "context", Map.of(
                                "type", "string",
                                "description", "Additional context, background information, or constraints"
                        ),
                        "thinking_style", Map.of(
                                "type", "string",
                                "description", "Approach to reasoning and analysis",
                                "enum", List.of("analytical", "creative", "strategic", "systematic", "philosophical",
                                        "scientific", "practical", "comprehensive"),
                                "default", "comprehensive"
                        ),
                        "perspectives", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Specific perspectives or disciplines to consider"
                        ),
                        "depth", Map.of(
                                "type", "string",
                                "description", "Depth of analysis to perform",
                                "enum", List.of("surface", "moderate", "deep", "exhaustive"),
                                "default", "deep"
                        ),
                        "include_alternatives", Map.of(
                                "type", "boolean",
                                "description", "Include alternative viewpoints and counterarguments",
                                "default", true
                        ),
                        "show_reasoning", Map.of(
                                "type", "boolean",
                                "description", "Show step-by-step reasoning process",
                                "default", true
                        ),
                        "practical_focus", Map.of(
                                "type", "boolean",
                                "description", "Focus on practical applications and actionable insights",
                                "default", false
                        ),
                        "time_horizon", Map.of(
                                "type", "string",
                                "description", "Time horizon for analysis and implications",
                                "enum", List.of("immediate", "short_term", "medium_term", "long_term", "timeless"),
                                "default", "medium_term"
                        ),
                        "ai_provider", Map.of(
                                "type", "string",
                                "description", "Preferred AI provider (anthropic, openai, gemini)",
                                "enum", List.of("anthropic", "openai", "gemini", "auto"),
                                "default", "auto"
                        )
                ),
                "required", List.of("question")
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String question = getRequiredString(arguments, "question");
            String context = getOptionalString(arguments, "context", "");
            String thinkingStyle = getOptionalString(arguments, "thinking_style", "comprehensive");
            @SuppressWarnings("unchecked")
            List<String> perspectives = (List<String>) arguments.getOrDefault("perspectives", List.of());
            String depth = getOptionalString(arguments, "depth", "deep");
            boolean includeAlternatives = getOptionalBoolean(arguments, "include_alternatives", true);
            boolean showReasoning = getOptionalBoolean(arguments, "show_reasoning", true);
            boolean practicalFocus = getOptionalBoolean(arguments, "practical_focus", false);
            String timeHorizon = getOptionalString(arguments, "time_horizon", "medium_term");
            String aiProvider = getOptionalString(arguments, "ai_provider", "auto");

            // Build comprehensive thinking prompt
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("## Deep Thinking Request\n\n");
            promptBuilder.append("**Question**: ").append(question).append("\n\n");

            if (!context.isEmpty()) {
                promptBuilder.append("**Context**: ").append(context).append("\n\n");
            }

            promptBuilder.append("**Thinking Style**: ").append(thinkingStyle).append("\n");
            promptBuilder.append("**Analysis Depth**: ").append(depth).append("\n");
            promptBuilder.append("**Time Horizon**: ").append(timeHorizon.replace("_", " ")).append("\n");
            promptBuilder.append("**Show Reasoning**: ").append(showReasoning).append("\n");
            promptBuilder.append("**Include Alternatives**: ").append(includeAlternatives).append("\n");
            promptBuilder.append("**Practical Focus**: ").append(practicalFocus).append("\n\n");

            if (!perspectives.isEmpty()) {
                promptBuilder.append("**Consider These Perspectives**: ");
                promptBuilder.append(String.join(", ", perspectives)).append("\n\n");
            }

            // Add thinking style specific instructions
            switch (thinkingStyle) {
                case "analytical" -> {
                    promptBuilder.append("Apply rigorous analytical thinking with logical decomposition, ");
                    promptBuilder.append("evidence-based reasoning, and systematic evaluation.\n\n");
                }
                case "creative" -> {
                    promptBuilder.append("Use creative and innovative thinking approaches including lateral thinking, ");
                    promptBuilder.append("analogical reasoning, and breakthrough insights.\n\n");
                }
                case "strategic" -> {
                    promptBuilder.append("Focus on strategic analysis including long-term implications, ");
                    promptBuilder.append("competitive dynamics, and scenario planning.\n\n");
                }
                case "systematic" -> {
                    promptBuilder.append("Apply systematic thinking with attention to interconnections, ");
                    promptBuilder.append("feedback loops, and emergent properties.\n\n");
                }
                case "philosophical" -> {
                    promptBuilder.append("Use philosophical reasoning including ethical considerations, ");
                    promptBuilder.append("fundamental principles, and conceptual analysis.\n\n");
                }
                case "scientific" -> {
                    promptBuilder.append("Apply scientific thinking including hypothesis formation, ");
                    promptBuilder.append("evidence evaluation, and empirical reasoning.\n\n");
                }
                case "practical" -> {
                    promptBuilder.append("Focus on practical thinking with emphasis on implementation, ");
                    promptBuilder.append("feasibility, and real-world constraints.\n\n");
                }
                default -> {
                    promptBuilder.append("Use comprehensive thinking that combines multiple reasoning approaches ");
                    promptBuilder.append("and considers various perspectives and implications.\n\n");
                }
            }

            // Add depth-specific instructions
            switch (depth) {
                case "surface" -> {
                    promptBuilder.append("Provide a focused analysis covering the key points and main insights.\n\n");
                }
                case "moderate" -> {
                    promptBuilder.append("Provide a balanced analysis with good coverage of important aspects ");
                    promptBuilder.append("and reasonable depth on key points.\n\n");
                }
                case "exhaustive" -> {
                    promptBuilder.append("Provide an exhaustive analysis exploring every angle, considering edge cases, ");
                    promptBuilder.append("and diving deeply into complex nuances and implications.\n\n");
                }
                default -> {
                    promptBuilder.append("Provide a deep analysis with thorough exploration of the question, ");
                    promptBuilder.append("multiple perspectives, and well-reasoned conclusions.\n\n");
                }
            }

            // Add special focus instructions
            if (practicalFocus) {
                promptBuilder.append("Emphasize practical applications, actionable insights, and real-world implementation.\n\n");
            }

            if (includeAlternatives) {
                promptBuilder.append("Include alternative viewpoints, counterarguments, and different interpretations.\n\n");
            }

            if (showReasoning) {
                promptBuilder.append("Show your reasoning process step-by-step, including how you arrived at insights.\n\n");
            }

            // Add time horizon considerations
            promptBuilder.append("Consider implications across the ").append(timeHorizon.replace("_", " "));
            promptBuilder.append(" time horizon.\n\n");

            promptBuilder.append("Think deeply about this question and provide a well-reasoned, insightful analysis.");

            // Create AI request with higher token limit for thinking
            AIClient.AIRequest aiRequest = new AIClient.AIRequest(promptBuilder.toString(), THINK_SYSTEM_PROMPT);
            aiRequest.setPreferredProvider(aiProvider);
            aiRequest.setMaxTokens(10000); // Higher limit for deep thinking

            // Get AI response
            AIClient.AIResponse aiResponse = aiClient.sendMessage(aiRequest);

            // Format final response
            StringBuilder responseBuilder = new StringBuilder();
            responseBuilder.append("# 🧠 Deep Thinking Analysis\n\n");
            responseBuilder.append("**Question**: ").append(question).append("\n");
            responseBuilder.append("**Thinking Style**: ").append(thinkingStyle).append("\n");
            responseBuilder.append("**Analysis Depth**: ").append(depth).append("\n");
            responseBuilder.append("**Time Horizon**: ").append(timeHorizon.replace("_", " ")).append("\n");
            responseBuilder.append("**AI Provider**: ").append(aiResponse.getProvider()).append("\n");

            if (!perspectives.isEmpty()) {
                responseBuilder.append("**Perspectives Considered**: ").append(String.join(", ", perspectives)).append("\n");
            }

            responseBuilder.append("\n---\n\n");
            responseBuilder.append(aiResponse.getContent());

            // Add usage info if available
            if (aiResponse.getUsage() != null) {
                responseBuilder.append("\n\n---\n");
                responseBuilder.append("**Token Usage**: ").append(aiResponse.getUsage().toString());
            }

            logger.info("Deep thinking analysis completed using {} style and {} depth with {} provider",
                    thinkingStyle, depth, aiResponse.getProvider());
            return createTextResult(responseBuilder.toString());

        } catch (AIClient.AIException e) {
            logger.error("AI request failed for think tool", e);
            throw new ToolExecutionException("Deep thinking analysis failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error in think tool execution", e);
            throw new ToolExecutionException("Think tool failed: " + e.getMessage(), e);
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
