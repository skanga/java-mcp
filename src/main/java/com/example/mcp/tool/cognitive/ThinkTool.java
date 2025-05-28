package com.example.mcp.tool.cognitive;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Think Tool - Provides structured reasoning space for complex problem-solving
 * Similar to Anthropic's "think" tool concept
 */
public class ThinkTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(ThinkTool.class);

    // Static storage for thoughts across sessions (ideally, this should be persistent)
    private static final Map<String, List<ThoughtEntry>> sessionThoughts = new ConcurrentHashMap<>();
    private static final AtomicLong thoughtCounter = new AtomicLong(0);
    private static final int MAX_THOUGHTS_PER_SESSION = 1000;
    private static final int MAX_THOUGHT_LENGTH = 5000;

    @Override
    public String getName() {
        return "think";
    }

    @Override
    public String getDescription() {
        return "Provides structured reasoning space for complex problem-solving, maintaining thought history and enabling reflection";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "description", "Action to perform",
                                "enum", List.of("record", "reflect", "summarize", "clear", "history", "search"),
                                "default", "record"
                        ),
                        "thought", Map.of(
                                "type", "string",
                                "description", "The thought or reasoning to record"
                        ),
                        "session_id", Map.of(
                                "type", "string",
                                "description", "Session identifier for grouping thoughts",
                                "default", "default"
                        ),
                        "category", Map.of(
                                "type", "string",
                                "description", "Category of thought",
                                "enum", List.of("analysis", "plan", "problem", "solution", "question", "insight", "decision"),
                                "default", "analysis"
                        ),
                        "query", Map.of(
                                "type", "string",
                                "description", "Search query for finding specific thoughts"
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "Maximum number of results to return",
                                "minimum", 1,
                                "maximum", 100,
                                "default", 20
                        )
                )
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String action = getOptionalString(arguments, "action", "record");
            String sessionId = getOptionalString(arguments, "session_id", "default");
            String thought = getOptionalString(arguments, "thought", null);
            String category = getOptionalString(arguments, "category", "analysis");
            String query = getOptionalString(arguments, "query", null);
            int limit = getOptionalInt(arguments, "limit", 20);

            // Ensure session exists
            sessionThoughts.computeIfAbsent(sessionId, k -> new ArrayList<>());

            String result = switch (action) {
                case "record" -> recordThought(sessionId, thought, category);
                case "reflect" -> reflectOnThoughts(sessionId, limit);
                case "summarize" -> summarizeThoughts(sessionId);
                case "clear" -> clearThoughts(sessionId);
                case "history" -> getThoughtHistory(sessionId, limit);
                case "search" -> searchThoughts(sessionId, query, limit);
                default -> throw new ToolExecutionException("Unknown action: " + action);
            };

            logger.debug("Think tool action '{}' completed for session '{}'", action, sessionId);
            return createTextResult(result);

        } catch (Exception e) {
            logger.error("Error in think tool", e);
            throw new ToolExecutionException("Think tool failed: " + e.getMessage(), e);
        }
    }

    private String recordThought(String sessionId, String thought, String category) throws ToolExecutionException {
        if (thought == null || thought.trim().isEmpty()) {
            throw new ToolExecutionException("Thought content is required for recording");
        }

        if (thought.length() > MAX_THOUGHT_LENGTH) {
            throw new ToolExecutionException("Thought too long (max " + MAX_THOUGHT_LENGTH + " characters)");
        }

        List<ThoughtEntry> thoughts = sessionThoughts.get(sessionId);

        // Check limits
        if (thoughts.size() >= MAX_THOUGHTS_PER_SESSION) {
            // Remove oldest thoughts to make room
            thoughts.removeIf(t -> thoughts.indexOf(t) < thoughts.size() - MAX_THOUGHTS_PER_SESSION + 100);
        }

        ThoughtEntry entry = new ThoughtEntry(
                thoughtCounter.incrementAndGet(),
                thought.trim(),
                category,
                LocalDateTime.now(),
                sessionId
        );

        thoughts.add(entry);

        return formatThoughtRecorded(entry, thoughts.size());
    }

    private String reflectOnThoughts(String sessionId, int limit) {
        List<ThoughtEntry> thoughts = sessionThoughts.get(sessionId);
        if (thoughts.isEmpty()) {
            return "🤔 Reflection\n" +
                    "═".repeat(40) + "\n" +
                    "No thoughts recorded yet for this session.\n" +
                    "Start by recording your initial analysis or problem statement.";
        }

        // Get recent thoughts for reflection
        List<ThoughtEntry> recentThoughts = thoughts.stream()
                .skip(Math.max(0, thoughts.size() - limit))
                .toList();

        return formatReflection(recentThoughts, sessionId);
    }

    private String summarizeThoughts(String sessionId) {
        List<ThoughtEntry> thoughts = sessionThoughts.get(sessionId);
        if (thoughts.isEmpty()) {
            return "📋 Session Summary\n" +
                    "═".repeat(40) + "\n" +
                    "No thoughts to summarize for session: " + sessionId;
        }

        return formatSummary(thoughts, sessionId);
    }

    private String clearThoughts(String sessionId) {
        List<ThoughtEntry> thoughts = sessionThoughts.get(sessionId);
        int count = thoughts.size();
        thoughts.clear();

        return "🧹 Thoughts Cleared\n" +
                "═".repeat(40) + "\n" +
                "Cleared " + count + " thoughts from session: " + sessionId + "\n" +
                "Ready for fresh thinking!";
    }

    private String getThoughtHistory(String sessionId, int limit) {
        List<ThoughtEntry> thoughts = sessionThoughts.get(sessionId);
        if (thoughts.isEmpty()) {
            return "📜 Thought History\n" +
                    "═".repeat(40) + "\n" +
                    "No thoughts recorded for session: " + sessionId;
        }

        List<ThoughtEntry> recentThoughts = thoughts.stream()
                .skip(Math.max(0, thoughts.size() - limit))
                .toList();

        return formatHistory(recentThoughts, sessionId);
    }

    private String searchThoughts(String sessionId, String query, int limit) throws ToolExecutionException {
        if (query == null || query.trim().isEmpty()) {
            throw new ToolExecutionException("Search query is required");
        }

        List<ThoughtEntry> thoughts = sessionThoughts.get(sessionId);
        if (thoughts.isEmpty()) {
            return "🔍 Search Results\n" +
                    "═".repeat(40) + "\n" +
                    "No thoughts to search in session: " + sessionId;
        }

        String lowercaseQuery = query.toLowerCase();
        List<ThoughtEntry> matches = thoughts.stream()
                .filter(t -> t.content.toLowerCase().contains(lowercaseQuery) ||
                        t.category.toLowerCase().contains(lowercaseQuery))
                .limit(limit)
                .toList();

        return formatSearchResults(matches, query, sessionId);
    }

    private String formatThoughtRecorded(ThoughtEntry entry, int totalThoughts) {
        return "💭 Thought Recorded\n" +
                "═".repeat(40) + "\n" +
                "ID: #" + entry.id + "\n" +
                "Category: " + getCategoryEmoji(entry.category) + " " + entry.category + "\n" +
                "Time: " + entry.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n" +
                "Session: " + entry.sessionId + " (" + totalThoughts + " total thoughts)\n\n" +
                "💬 Content:\n" +
                "─".repeat(30) + "\n" +
                entry.content + "\n\n" +
                "✨ Thought successfully captured for future reflection.";
    }

    private String formatReflection(List<ThoughtEntry> thoughts, String sessionId) {
        StringBuilder reflection = new StringBuilder();

        reflection.append("🤔 Reflection on Recent Thoughts\n");
        reflection.append("═".repeat(50)).append("\n");
        reflection.append("Session: ").append(sessionId).append("\n");
        reflection.append("Reflecting on ").append(thoughts.size()).append(" recent thoughts\n\n");

        // Categorize thoughts
        Map<String, List<ThoughtEntry>> byCategory = new HashMap<>();
        for (ThoughtEntry thought : thoughts) {
            byCategory.computeIfAbsent(thought.category, k -> new ArrayList<>()).add(thought);
        }

        reflection.append("📊 Thought Patterns:\n");
        reflection.append("─".repeat(30)).append("\n");
        for (Map.Entry<String, List<ThoughtEntry>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            int count = entry.getValue().size();
            reflection.append(getCategoryEmoji(category)).append(" ").append(category)
                    .append(": ").append(count).append(" thoughts\n");
        }

        reflection.append("\n🔄 Thinking Progress:\n");
        reflection.append("─".repeat(30)).append("\n");

        // Show progression of thoughts
        for (int i = 0; i < Math.min(thoughts.size(), 5); i++) {
            ThoughtEntry thought = thoughts.get(thoughts.size() - 5 + i);
            if (thought != null) {
                reflection.append(String.format("%d. [%s] %s: %s\n",
                        i + 1,
                        thought.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                        thought.category,
                        truncateText(thought.content, 80)
                ));
            }
        }

        reflection.append("\n💡 Reflection Notes:\n");
        reflection.append("─".repeat(30)).append("\n");
        reflection.append("• You have been actively thinking through this problem\n");
        reflection.append("• Consider if your recent thoughts suggest a clear direction\n");
        reflection.append("• Are there any patterns or insights emerging?\n");
        reflection.append("• What questions or next steps does this thinking reveal?\n");

        return reflection.toString();
    }

    private String formatSummary(List<ThoughtEntry> thoughts, String sessionId) {
        StringBuilder summary = new StringBuilder();

        summary.append("📋 Thinking Session Summary\n");
        summary.append("═".repeat(50)).append("\n");
        summary.append("Session: ").append(sessionId).append("\n");
        summary.append("Total Thoughts: ").append(thoughts.size()).append("\n");

        if (!thoughts.isEmpty()) {
            summary.append("Duration: ").append(formatDuration(thoughts.get(0).timestamp,
                    thoughts.get(thoughts.size() - 1).timestamp)).append("\n");
        }

        summary.append("\n");

        // Category breakdown
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (ThoughtEntry thought : thoughts) {
            categoryCounts.merge(thought.category, 1, Integer::sum);
        }

        summary.append("📊 Thought Categories:\n");
        summary.append("─".repeat(30)).append("\n");
        categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    String category = entry.getKey();
                    int count = entry.getValue();
                    double percentage = (count * 100.0) / thoughts.size();
                    summary.append(String.format("%s %s: %d (%.1f%%)\n",
                            getCategoryEmoji(category), category, count, percentage));
                });

        // Key insights (first and last few thoughts)
        if (thoughts.size() > 0) {
            summary.append("\n🎯 Key Thinking Moments:\n");
            summary.append("─".repeat(30)).append("\n");

            summary.append("🟢 Initial Thought:\n");
            ThoughtEntry first = thoughts.get(0);
            summary.append("   ").append(truncateText(first.content, 100)).append("\n\n");

            if (thoughts.size() > 1) {
                summary.append("🏁 Recent Thought:\n");
                ThoughtEntry last = thoughts.get(thoughts.size() - 1);
                summary.append("   ").append(truncateText(last.content, 100)).append("\n");
            }
        }

        return summary.toString();
    }

    private String formatHistory(List<ThoughtEntry> thoughts, String sessionId) {
        StringBuilder history = new StringBuilder();

        history.append("📜 Thought History\n");
        history.append("═".repeat(40)).append("\n");
        history.append("Session: ").append(sessionId).append("\n");
        history.append("Showing ").append(thoughts.size()).append(" recent thoughts\n\n");

        for (ThoughtEntry thought : thoughts) {
            history.append(formatThoughtEntry(thought)).append("\n");
        }

        return history.toString();
    }

    private String formatSearchResults(List<ThoughtEntry> matches, String query, String sessionId) {
        StringBuilder results = new StringBuilder();

        results.append("🔍 Search Results\n");
        results.append("═".repeat(40)).append("\n");
        results.append("Query: \"").append(query).append("\"\n");
        results.append("Session: ").append(sessionId).append("\n");
        results.append("Found: ").append(matches.size()).append(" matching thoughts\n\n");

        if (matches.isEmpty()) {
            results.append("No thoughts found matching your query.\n");
            results.append("Try different keywords or check your session ID.\n");
        } else {
            for (ThoughtEntry match : matches) {
                results.append(formatThoughtEntry(match)).append("\n");
            }
        }

        return results.toString();
    }

    private String formatThoughtEntry(ThoughtEntry thought) {
        return String.format("💭 #%d [%s] %s %s\n    📅 %s\n    💬 %s",
                thought.id,
                thought.timestamp.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                getCategoryEmoji(thought.category),
                thought.category,
                thought.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                thought.content
        );
    }

    private String getCategoryEmoji(String category) {
        return switch (category.toLowerCase()) {
            case "analysis" -> "🔍";
            case "plan" -> "📋";
            case "problem" -> "❓";
            case "solution" -> "💡";
            case "question" -> "🤔";
            case "insight" -> "✨";
            case "decision" -> "⚖️";
            default -> "💭";
        };
    }

    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private String formatDuration(LocalDateTime start, LocalDateTime end) {
        long minutes = java.time.Duration.between(start, end).toMinutes();
        if (minutes < 60) {
            return minutes + " minutes";
        } else {
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            return hours + "h " + remainingMinutes + "m";
        }
    }

    // Helper methods
    private String getOptionalString(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        return value != null ? String.valueOf(value).trim() : defaultValue;
    }

    private int getOptionalInt(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private McpModels.CallToolResponse.CallToolResult createTextResult(String text) {
        McpModels.CallToolResponse.CallToolResult result = new McpModels.CallToolResponse.CallToolResult();
        McpModels.Content content = new McpModels.Content();
        content.type = "text";
        content.text = text;
        result.content = List.of(content);
        return result;
    }

    /**
     * Represents a single thought entry
     */
    private static class ThoughtEntry {
        final long id;
        final String content;
        final String category;
        final LocalDateTime timestamp;
        final String sessionId;

        ThoughtEntry(long id, String content, String category, LocalDateTime timestamp, String sessionId) {
            this.id = id;
            this.content = content;
            this.category = category;
            this.timestamp = timestamp;
            this.sessionId = sessionId;
        }
    }
}
