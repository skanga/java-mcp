package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter; // Added
import com.example.mcp.security.SecurityContext; // Added
import com.example.mcp.tool.BaseMcpTool; // Added
// import org.slf4j.Logger; // To be removed
// import org.slf4j.LoggerFactory; // To be removed

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
// import java.nio.file.Paths; // Will use validatePath or Paths.get as needed
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Code Critique Tool - Provides comprehensive code quality analysis and improvement suggestions
 */
public class CodeCritiqueTool extends BaseMcpTool { // Changed to extend BaseMcpTool
    // private static final Logger logger = LoggerFactory.getLogger(CodeCritiqueTool.class); // Logger inherited

    // Language-specific patterns and rules
    private static final Map<String, CodeAnalyzer> ANALYZERS = Map.of(
            ".java", new JavaAnalyzer(),
            ".js", new JavaScriptAnalyzer(),
            ".ts", new TypeScriptAnalyzer(),
            ".py", new PythonAnalyzer(),
            ".clj", new ClojureAnalyzer(),
            ".go", new GoAnalyzer()
    );

    private static final int MAX_FILE_SIZE = 1024 * 1024; // 1MB

    // Constructor added
    public CodeCritiqueTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

    @Override
    public String getName() {
        return "code_critique";
    }

    @Override
    public String getDescription() {
        return "Provides comprehensive code quality analysis, best practices review, and improvement suggestions for multiple programming languages";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "description", "Type of analysis to perform",
                                "enum", List.of("analyze_file", "analyze_code", "compare_files", "suggest_improvements"),
                                "default", "analyze_file"
                        ),
                        "file_path", Map.of(
                                "type", "string",
                                "description", "Path to file for analysis"
                        ),
                        "code", Map.of(
                                "type", "string",
                                "description", "Code content to analyze directly"
                        ),
                        "language", Map.of(
                                "type", "string",
                                "description", "Programming language (auto-detected if not specified)",
                                "enum", List.of("java", "javascript", "typescript", "python", "clojure", "go", "auto")
                        ),
                        "analysis_level", Map.of(
                                "type", "string",
                                "description", "Depth of analysis",
                                "enum", List.of("basic", "standard", "comprehensive"),
                                "default", "standard"
                        ),
                        "focus_areas", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Specific areas to focus on",
                                "default", List.of("all")
                        ),
                        "file_path_2", Map.of(
                                "type", "string",
                                "description", "Second file path for comparison analysis"
                        )
                )
        );
    }

    @Override
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String action = getOptionalString(arguments, "action", "analyze_file");
        List<String> validActions = List.of("analyze_file", "analyze_code", "compare_files", "suggest_improvements");
        if (!validActions.contains(action)) {
            throw new ToolExecutionException("Invalid action: " + action + ". Must be one of " + validActions);
        }

        String filePathStr = getOptionalString(arguments, "file_path", null);
        String code = getOptionalString(arguments, "code", null);
        String filePath2Str = getOptionalString(arguments, "file_path_2", null);

        switch (action) {
            case "analyze_file":
                if (filePathStr == null || filePathStr.trim().isEmpty()) {
                    throw new ToolExecutionException("File path is required for 'analyze_file' action.");
                }
                Path path = validatePath(filePathStr); // Validates existence and security
                 if (!Files.isRegularFile(path)) { // Check after validatePath
                    throw new ToolExecutionException("Path is not a regular file: " + path);
                }
                // File size check will be done before reading in analyzeFile method
                break;
            case "analyze_code":
                if (code == null || code.trim().isEmpty()) {
                    throw new ToolExecutionException("Code content is required for 'analyze_code' action.");
                }
                if (code.length() > MAX_FILE_SIZE) { // MAX_FILE_SIZE is a constant in the class
                    throw new ToolExecutionException("Code too large for analysis (max 1MB).");
                }
                break;
            case "compare_files":
                if (filePathStr == null || filePathStr.trim().isEmpty() || filePath2Str == null || filePath2Str.trim().isEmpty()) {
                    throw new ToolExecutionException("Both file_path and file_path_2 are required for 'compare_files' action.");
                }
                Path path1 = validatePath(filePathStr);
                Path path2 = validatePath(filePath2Str);
                 if (!Files.isRegularFile(path1)) {
                    throw new ToolExecutionException("file_path is not a regular file: " + path1);
                }
                if (!Files.isRegularFile(path2)) {
                    throw new ToolExecutionException("file_path_2 is not a regular file: " + path2);
                }
                // File size checks will be done before reading
                break;
            case "suggest_improvements":
                if ((filePathStr == null || filePathStr.trim().isEmpty()) && (code == null || code.trim().isEmpty())) {
                    throw new ToolExecutionException("Either file_path or code must be provided for 'suggest_improvements' action.");
                }
                if (filePathStr != null && !filePathStr.trim().isEmpty()) {
                    Path suggestionPath = validatePath(filePathStr);
                     if (!Files.isRegularFile(suggestionPath)) {
                        throw new ToolExecutionException("Path is not a regular file: " + suggestionPath);
                    }
                }
                if (code != null && !code.trim().isEmpty() && code.length() > MAX_FILE_SIZE) {
                    throw new ToolExecutionException("Code too large for analysis (max 1MB).");
                }
                break;
        }

        String language = getOptionalString(arguments, "language", "auto");
        List<String> validLanguages = List.of("java", "javascript", "typescript", "python", "clojure", "go", "auto");
        if (!validLanguages.contains(language)) {
            throw new ToolExecutionException("Invalid language: " + language + ". Must be one of " + validLanguages);
        }
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        // This tool primarily reads files and performs CPU-bound analysis.
        return resourceLimiter.acquireFileOperation("code_analysis");
    }

    // Renamed execute to executeInternal
    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String action = getOptionalString(arguments, "action", "analyze_file"); // Use inherited getOptionalString
            String filePathStr = getOptionalString(arguments, "file_path", null);
            String code = getOptionalString(arguments, "code", null);
            String language = getOptionalString(arguments, "language", "auto");
            String analysisLevel = getOptionalString(arguments, "analysis_level", "standard");
            List<String> focusAreas = getOptionalStringList(arguments, "focus_areas", List.of("all")); // Use getOptionalStringList
            String filePath2Str = getOptionalString(arguments, "file_path_2", null);

            String result = switch (action) {
                case "analyze_file" -> analyzeFile(filePathStr, language, analysisLevel, focusAreas);
                case "analyze_code" -> analyzeCode(code, language, analysisLevel, focusAreas);
                case "compare_files" -> compareFiles(filePathStr, filePath2Str, language, focusAreas);
                case "suggest_improvements" -> suggestImprovements(filePathStr, code, language, analysisLevel);
                default -> throw new ToolExecutionException("Unknown action: " + action); // Should be caught by validateInputs
            };

            logger.debug("Code critique completed for action: {}", action); // Use inherited logger
            return super.createTextResult(result); // Use inherited createTextResult

        } catch (IOException e) { // Catch specific IOExceptions from file operations
             logger.error("IO error during code critique action {}: {}", getOptionalString(arguments, "action", "unknown"), e.getMessage(), e);
            throw new ToolExecutionException("Code critique IO failed: " + e.getMessage(), e);
        }
        catch (Exception e) {
            logger.error("Error in code critique", e);
            throw new ToolExecutionException("Code critique failed: " + e.getMessage(), e);
        }
    }

    private String analyzeFile(String filePathStr, String language, String analysisLevel, List<String> focusAreas)
            throws ToolExecutionException, IOException {
        // filePathStr null/empty check, existence, security, and isRegularFile check done in validateInputs
        Path path = validatePath(filePathStr); // Re-validate to get Path object

        // File size validation before reading
        // Using BaseMcpTool.validateFileSize for consistency with security policies
        validateFileSize(path); 
        // The local MAX_FILE_SIZE can still be used for an additional, potentially stricter, check if needed.
        // For this refactor, we'll rely on validateFileSize from BaseMcpTool primarily.
        // If a stricter local check is needed: if (Files.size(path) > MAX_FILE_SIZE) { throw ... }

        String code = Files.readString(path);
        String detectedLanguage = detectLanguage(filePathStr, language);

        return performAnalysis(code, detectedLanguage, analysisLevel, focusAreas, filePathStr);
    }

    private String analyzeCode(String codeContent, String language, String analysisLevel, List<String> focusAreas)
            throws ToolExecutionException {
        // Code null/empty and size check done in validateInputs
        String detectedLanguage = detectLanguage("", language); // No path to infer from
        return performAnalysis(codeContent, detectedLanguage, analysisLevel, focusAreas, "<direct_input>");
    }

    private String compareFiles(String filePath1Str, String filePath2Str, String language, List<String> focusAreas)
            throws ToolExecutionException, IOException {
        // File path null/empty checks, existence, security, and isRegularFile checks done in validateInputs
        Path path1 = validatePath(filePath1Str);
        Path path2 = validatePath(filePath2Str);

        validateFileSize(path1);
        String code1 = Files.readString(path1);

        validateFileSize(path2);
        String code2 = Files.readString(path2);
        
        AnalysisResult result1 = analyzeCodeInternal(code1,
                detectLanguage(filePath1Str, language), "standard"); // Using "standard" as per original
        AnalysisResult result2 = analyzeCodeInternal(code2,
                detectLanguage(filePath2Str, language), "standard");

        return formatComparison(result1, result2, filePath1Str, filePath2Str);
    }

    private String suggestImprovements(String filePathStr, String codeContent, String language, String analysisLevel)
            throws ToolExecutionException, IOException {
        // Validation of either filePath or code presence is done in validateInputs

        String actualCode;
        String sourceName;

        if (filePathStr != null && !filePathStr.trim().isEmpty()) {
            Path path = validatePath(filePathStr);
            // isRegularFile check done in validateInputs
            
            validateFileSize(path);
            actualCode = Files.readString(path);
            sourceName = filePathStr;
        } else { // codeContent must be non-null and non-empty here due to validateInputs
            // Code size check already done in validateInputs
            actualCode = codeContent;
            sourceName = "<direct_input>";
        }

        String detectedLanguage = detectLanguage(filePathStr != null ? filePathStr : "", language);
        AnalysisResult result = analyzeCodeInternal(actualCode, detectedLanguage, analysisLevel);

        return formatImprovementSuggestions(result, sourceName, detectedLanguage);
    }

    private String performAnalysis(String code, String language, String analysisLevel,
                                   List<String> focusAreas, String sourceName) {

        AnalysisResult result = analyzeCodeInternal(code, language, analysisLevel);
        return formatAnalysisResult(result, sourceName, language, analysisLevel, focusAreas);
    }

    private AnalysisResult analyzeCodeInternal(String code, String language, String analysisLevel) {
        CodeAnalyzer analyzer = getAnalyzer(language);

        AnalysisResult result = new AnalysisResult();
        result.language = language;
        result.lineCount = code.split("\n").length;
        result.characterCount = code.length();

        // Basic metrics
        result.metrics = analyzer.calculateMetrics(code);

        // Code quality issues
        result.issues = analyzer.findIssues(code);

        // Best practices
        result.bestPractices = analyzer.checkBestPractices(code);

        // Security concerns
        result.securityIssues = analyzer.findSecurityIssues(code);

        // Performance suggestions
        result.performanceSuggestions = analyzer.findPerformanceIssues(code);

        // Calculate overall score
        result.qualityScore = calculateQualityScore(result);

        return result;
    }

    private String detectLanguage(String filePath, String language) {
        if (!"auto".equals(language) && language != null && !language.trim().isEmpty()) {
            return language;
        }

        if (filePath == null || filePath.trim().isEmpty()) {
            return "java"; // default if no path and language is auto/null
        }

        String extension = getFileExtension(filePath);
        return switch (extension) {
            case ".java" -> "java";
            case ".js" -> "javascript";
            case ".ts" -> "typescript";
            case ".py" -> "python";
            case ".clj", ".cljs" -> "clojure";
            case ".go" -> "go";
            default -> "java"; // Default if extension not recognized
        };
    }

    private String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        return lastDot > 0 ? filePath.substring(lastDot) : "";
    }

    private CodeAnalyzer getAnalyzer(String language) {
        String extension = switch (language) {
            case "java" -> ".java";
            case "javascript" -> ".js";
            case "typescript" -> ".ts";
            case "python" -> ".py";
            case "clojure" -> ".clj";
            case "go" -> ".go";
            default -> ".java"; // Default to JavaAnalyzer if language not in map key set
        };
        return ANALYZERS.getOrDefault(extension, new JavaAnalyzer()); // Ensure a default analyzer
    }

    private int calculateQualityScore(AnalysisResult result) {
        int score = 100;

        // Deduct points for issues
        score -= result.issues.size() * 5;
        score -= result.securityIssues.size() * 10;
        score -= result.performanceSuggestions.size() * 3;

        // Bonus for good practices
        score += result.bestPractices.size() * 2;

        return Math.max(0, Math.min(100, score));
    }

    private String formatAnalysisResult(AnalysisResult result, String sourceName, String language,
                                        String analysisLevel, List<String> focusAreas) {
        StringBuilder output = new StringBuilder();

        output.append("🔍 Code Quality Analysis\n");
        output.append("═".repeat(60)).append("\n");
        output.append("Source: ").append(sourceName).append("\n");
        output.append("Language: ").append(language.toUpperCase()).append("\n");
        output.append("Analysis Level: ").append(analysisLevel).append("\n");
        output.append("Quality Score: ").append(getScoreEmoji(result.qualityScore))
                .append(" ").append(result.qualityScore).append("/100\n\n");

        // Metrics
        output.append("📊 Code Metrics\n");
        output.append("─".repeat(30)).append("\n");
        output.append("Lines of Code: ").append(result.lineCount).append("\n");
        output.append("Characters: ").append(result.characterCount).append("\n");
        for (Map.Entry<String, Object> metric : result.metrics.entrySet()) {
            output.append(metric.getKey()).append(": ").append(metric.getValue()).append("\n");
        }
        output.append("\n");

        // Issues
        if (!result.issues.isEmpty()) {
            output.append("⚠️  Code Issues (").append(result.issues.size()).append(")\n");
            output.append("─".repeat(30)).append("\n");
            for (CodeIssue issue : result.issues) {
                output.append(formatIssue(issue)).append("\n");
            }
            output.append("\n");
        }

        // Security Issues
        if (!result.securityIssues.isEmpty()) {
            output.append("🔒 Security Concerns (").append(result.securityIssues.size()).append(")\n");
            output.append("─".repeat(30)).append("\n");
            for (CodeIssue issue : result.securityIssues) {
                output.append(formatIssue(issue)).append("\n");
            }
            output.append("\n");
        }

        // Performance
        if (!result.performanceSuggestions.isEmpty()) {
            output.append("⚡ Performance Suggestions (").append(result.performanceSuggestions.size()).append(")\n");
            output.append("─".repeat(30)).append("\n");
            for (CodeIssue issue : result.performanceSuggestions) {
                output.append(formatIssue(issue)).append("\n");
            }
            output.append("\n");
        }

        // Best Practices
        if (!result.bestPractices.isEmpty()) {
            output.append("✅ Good Practices Found (").append(result.bestPractices.size()).append(")\n");
            output.append("─".repeat(30)).append("\n");
            for (String practice : result.bestPractices) {
                output.append("• ").append(practice).append("\n");
            }
        }

        return output.toString();
    }

    private String formatImprovementSuggestions(AnalysisResult result, String sourceName, String language) {
        StringBuilder output = new StringBuilder();

        output.append("💡 Code Improvement Suggestions\n");
        output.append("═".repeat(60)).append("\n");
        output.append("Source: ").append(sourceName).append("\n");
        output.append("Language: ").append(language.toUpperCase()).append("\n");
        output.append("Current Score: ").append(getScoreEmoji(result.qualityScore))
                .append(" ").append(result.qualityScore).append("/100\n\n");

        // Priority improvements
        List<CodeIssue> allIssues = new ArrayList<>();
        allIssues.addAll(result.securityIssues);
        allIssues.addAll(result.issues);
        allIssues.addAll(result.performanceSuggestions);

        // Sort by severity
        allIssues.sort((a, b) -> getSeverityLevel(b.severity) - getSeverityLevel(a.severity));

        output.append("🎯 Priority Improvements\n");
        output.append("─".repeat(30)).append("\n");

        for (int i = 0; i < Math.min(5, allIssues.size()); i++) {
            CodeIssue issue = allIssues.get(i);
            output.append(String.format("%d. %s %s\n", i + 1, getSeverityEmoji(issue.severity), issue.description));
            if (issue.suggestion != null) {
                output.append("   💡 ").append(issue.suggestion).append("\n");
            }
            output.append("\n");
        }

        // Quick wins
        output.append("🚀 Quick Wins\n");
        output.append("─".repeat(30)).append("\n");
        List<CodeIssue> quickWins = allIssues.stream()
                .filter(issue -> "low".equals(issue.severity) || "medium".equals(issue.severity))
                .limit(3)
                .collect(Collectors.toList());

        for (CodeIssue issue : quickWins) {
            output.append("• ").append(issue.description).append("\n");
        }

        return output.toString();
    }

    private String formatComparison(AnalysisResult result1, AnalysisResult result2, String file1, String file2) {
        StringBuilder output = new StringBuilder();

        output.append("🔍 Code Comparison Analysis\n");
        output.append("═".repeat(60)).append("\n");
        output.append("File 1: ").append(file1).append("\n");
        output.append("File 2: ").append(file2).append("\n\n");

        output.append("📊 Quality Comparison\n");
        output.append("─".repeat(30)).append("\n");
        output.append(String.format("%-20s %s %-10s %s %s\n", "Metric", "|", "File 1", "|", "File 2"));
        output.append("─".repeat(50)).append("\n");
        output.append(String.format("%-20s %s %-10d %s %d\n", "Quality Score", "|",
                result1.qualityScore, "|", result2.qualityScore));
        output.append(String.format("%-20s %s %-10d %s %d\n", "Lines of Code", "|",
                result1.lineCount, "|", result2.lineCount));
        output.append(String.format("%-20s %s %-10d %s %d\n", "Issues Found", "|",
                result1.issues.size(), "|", result2.issues.size()));
        output.append(String.format("%-20s %s %-10d %s %d\n", "Security Issues", "|",
                result1.securityIssues.size(), "|", result2.securityIssues.size()));

        // Recommendation
        output.append("\n🎯 Recommendation\n");
        output.append("─".repeat(30)).append("\n");
        if (result1.qualityScore > result2.qualityScore) {
            output.append("📈 File 1 has better overall code quality\n");
        } else if (result2.qualityScore > result1.qualityScore) {
            output.append("📈 File 2 has better overall code quality\n");
        } else {
            output.append("⚖️  Both files have similar code quality\n");
        }

        return output.toString();
    }

    private String formatIssue(CodeIssue issue) {
        return String.format("%s %s", getSeverityEmoji(issue.severity), issue.description);
    }

    private String getScoreEmoji(int score) {
        if (score >= 90) return "🟢";
        if (score >= 70) return "🟡";
        if (score >= 50) return "🟠";
        return "🔴";
    }

    private String getSeverityEmoji(String severity) {
        return switch (severity.toLowerCase()) {
            case "high", "critical" -> "🔴";
            case "medium" -> "🟡";
            case "low" -> "🟢";
            default -> "ℹ️";
        };
    }

    private int getSeverityLevel(String severity) {
        return switch (severity.toLowerCase()) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    // Helper methods getOptionalString and createTextResult are inherited from BaseMcpTool.

    // Data classes
    private static class AnalysisResult {
        String language;
        int lineCount;
        int characterCount;
        int qualityScore;
        Map<String, Object> metrics = new HashMap<>();
        List<CodeIssue> issues = new ArrayList<>();
        List<CodeIssue> securityIssues = new ArrayList<>();
        List<CodeIssue> performanceSuggestions = new ArrayList<>();
        List<String> bestPractices = new ArrayList<>();
    }

    private static class CodeIssue {
        String description;
        String severity;
        int lineNumber;
        String suggestion;

        CodeIssue(String description, String severity, int lineNumber, String suggestion) {
            this.description = description;
            this.severity = severity;
            this.lineNumber = lineNumber;
            this.suggestion = suggestion;
        }

        CodeIssue(String description, String severity) {
            this(description, severity, 0, null);
        }
    }

    // Abstract analyzer interface
    private static abstract class CodeAnalyzer {
        abstract Map<String, Object> calculateMetrics(String code);
        abstract List<CodeIssue> findIssues(String code);
        abstract List<CodeIssue> findSecurityIssues(String code);
        abstract List<CodeIssue> findPerformanceIssues(String code);
        abstract List<String> checkBestPractices(String code);
    }

    // Java-specific analyzer
    private static class JavaAnalyzer extends CodeAnalyzer {
        @Override
        Map<String, Object> calculateMetrics(String code) {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("Classes", countPattern(code, "\\bclass\\s+\\w+"));
            metrics.put("Methods", countPattern(code, "\\b(public|private|protected)\\s+.*\\s+\\w+\\s*\\("));
            metrics.put("Interfaces", countPattern(code, "\\binterface\\s+\\w+"));
            metrics.put("Imports", countPattern(code, "^import\\s+"));
            return metrics;
        }

        @Override
        List<CodeIssue> findIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("System.out.println")) {
                issues.add(new CodeIssue("Use proper logging instead of System.out.println", "medium"));
            }
            if (code.contains("catch (Exception e)") && !code.contains("logger")) {
                issues.add(new CodeIssue("Generic exception catching without logging", "medium"));
            }
            if (countPattern(code, "\\btodo\\b|\\bfixme\\b") > 0) {
                issues.add(new CodeIssue("TODO/FIXME comments found", "low"));
            }

            return issues;
        }

        @Override
        List<CodeIssue> findSecurityIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("MessageDigest.getInstance(\"MD5\")")) {
                issues.add(new CodeIssue("MD5 is cryptographically broken", "high", 0,
                        "Use SHA-256 or stronger algorithms"));
            }
            if (code.contains("Random()") && !code.contains("SecureRandom")) {
                issues.add(new CodeIssue("Use SecureRandom for security-sensitive operations", "medium"));
            }

            return issues;
        }

        @Override
        List<CodeIssue> findPerformanceIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("String") && code.contains("+") && code.contains("for")) {
                issues.add(new CodeIssue("Consider StringBuilder for string concatenation in loops", "medium"));
            }
            if (code.contains("ArrayList") && code.contains("contains(")) {
                issues.add(new CodeIssue("Consider HashSet for frequent contains() operations", "low"));
            }

            return issues;
        }

        @Override
        List<String> checkBestPractices(String code) {
            List<String> practices = new ArrayList<>();

            if (code.contains("private final")) {
                practices.add("Good use of immutable fields");
            }
            if (code.contains("@Override")) {
                practices.add("Proper use of @Override annotation");
            }
            if (code.contains("try-with-resources") || code.contains("try (")) {
                practices.add("Good resource management with try-with-resources");
            }

            return practices;
        }
    }

    // JavaScript analyzer
    private static class JavaScriptAnalyzer extends CodeAnalyzer {
        @Override
        Map<String, Object> calculateMetrics(String code) {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("Functions", countPattern(code, "function\\s+\\w+|const\\s+\\w+\\s*=\\s*\\("));
            metrics.put("Classes", countPattern(code, "class\\s+\\w+"));
            metrics.put("Async Functions", countPattern(code, "async\\s+function|async\\s+\\("));
            return metrics;
        }

        @Override
        List<CodeIssue> findIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("var ")) {
                issues.add(new CodeIssue("Use 'let' or 'const' instead of 'var'", "medium"));
            }
            if (code.contains("==") && !code.contains("===")) {
                issues.add(new CodeIssue("Use strict equality (===) instead of loose equality (==)", "medium"));
            }

            return issues;
        }

        @Override
        List<CodeIssue> findSecurityIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("eval(")) {
                issues.add(new CodeIssue("Avoid eval() - code injection risk", "high"));
            }
            if (code.contains("innerHTML") && !code.contains("sanitize")) {
                issues.add(new CodeIssue("innerHTML usage may lead to XSS", "medium"));
            }

            return issues;
        }

        @Override
        List<CodeIssue> findPerformanceIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("for") && code.contains("document.getElementById")) {
                issues.add(new CodeIssue("Cache DOM queries outside loops", "medium"));
            }

            return issues;
        }

        @Override
        List<String> checkBestPractices(String code) {
            List<String> practices = new ArrayList<>();

            if (code.contains("const ")) {
                practices.add("Good use of const for immutable bindings");
            }
            if (code.contains("async/await")) {
                practices.add("Modern async/await pattern usage");
            }

            return practices;
        }
    }

    // Add other language analyzers (TypeScript, Python, Clojure, Go)
    private static class TypeScriptAnalyzer extends JavaScriptAnalyzer {
        @Override
        List<String> checkBestPractices(String code) {
            List<String> practices = super.checkBestPractices(code);

            if (code.contains(": ") && (code.contains("string") || code.contains("number"))) {
                practices.add("Good use of TypeScript type annotations");
            }
            if (code.contains("interface ")) {
                practices.add("Well-defined interfaces for type safety");
            }

            return practices;
        }
    }

    private static class PythonAnalyzer extends CodeAnalyzer {
        @Override
        Map<String, Object> calculateMetrics(String code) {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("Functions", countPattern(code, "def\\s+\\w+"));
            metrics.put("Classes", countPattern(code, "class\\s+\\w+"));
            metrics.put("Imports", countPattern(code, "^(import|from)\\s+"));
            return metrics;
        }

        @Override
        List<CodeIssue> findIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("except:")) {
                issues.add(new CodeIssue("Avoid bare except clauses", "medium"));
            }

            return issues;
        }

        @Override
        List<CodeIssue> findSecurityIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("eval(")) {
                issues.add(new CodeIssue("Avoid eval() - code injection risk", "high"));
            }

            return issues;
        }

        @Override
        List<CodeIssue> findPerformanceIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("list(") && code.contains("range(")) {
                issues.add(new CodeIssue("Consider using range() directly instead of list(range())", "low"));
            }

            return issues;
        }

        @Override
        List<String> checkBestPractices(String code) {
            List<String> practices = new ArrayList<>();

            if (code.contains("if __name__ == '__main__':")) {
                practices.add("Good use of main guard pattern");
            }

            return practices;
        }
    }

    private static class ClojureAnalyzer extends CodeAnalyzer {
        @Override
        Map<String, Object> calculateMetrics(String code) {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("Functions", countPattern(code, "\\(defn\\s+"));
            metrics.put("Namespaces", countPattern(code, "\\(ns\\s+"));
            metrics.put("Macros", countPattern(code, "\\(defmacro\\s+"));
            return metrics;
        }

        @Override
        List<CodeIssue> findIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            // Check for unbalanced parentheses (simplified)
            long openParens = code.chars().filter(ch -> ch == '(').count();
            long closeParens = code.chars().filter(ch -> ch == ')').count();
            if (openParens != closeParens) {
                issues.add(new CodeIssue("Unbalanced parentheses detected", "high"));
            }

            return issues;
        }

        @Override
        List<CodeIssue> findSecurityIssues(String code) {
            return new ArrayList<>(); // Clojure has fewer common security anti-patterns
        }

        @Override
        List<CodeIssue> findPerformanceIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("(concat") && code.contains("for")) {
                issues.add(new CodeIssue("Consider using into or reduce instead of concat in loops", "medium"));
            }

            return issues;
        }

        @Override
        List<String> checkBestPractices(String code) {
            List<String> practices = new ArrayList<>();

            if (code.contains("let [")) {
                practices.add("Good use of destructuring");
            }
            if (code.contains("->") || code.contains("->>")) {
                practices.add("Good use of threading macros");
            }

            return practices;
        }
    }

    private static class GoAnalyzer extends CodeAnalyzer {
        @Override
        Map<String, Object> calculateMetrics(String code) {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("Functions", countPattern(code, "func\\s+\\w+"));
            metrics.put("Structs", countPattern(code, "type\\s+\\w+\\s+struct"));
            metrics.put("Interfaces", countPattern(code, "type\\s+\\w+\\s+interface"));
            return metrics;
        }

        @Override
        List<CodeIssue> findIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("_ =") && code.contains("err")) {
                issues.add(new CodeIssue("Error ignored - handle errors properly", "high"));
            }

            return issues;
        }

        @Override
        List<CodeIssue> findSecurityIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("md5.")) {
                issues.add(new CodeIssue("MD5 is cryptographically broken", "high"));
            }

            return issues;
        }

        @Override
        List<CodeIssue> findPerformanceIssues(String code) {
            List<CodeIssue> issues = new ArrayList<>();

            if (code.contains("range") && code.contains("append")) {
                issues.add(new CodeIssue("Consider pre-allocating slices when size is known", "medium"));
            }

            return issues;
        }

        @Override
        List<String> checkBestPractices(String code) {
            List<String> practices = new ArrayList<>();

            if (code.contains("defer ")) {
                practices.add("Good use of defer for cleanup");
            }
            if (code.contains("context.")) {
                practices.add("Proper context usage for cancellation");
            }

            return practices;
        }
    }

    // Utility method for pattern counting
    private static int countPattern(String code, String pattern) {
        Pattern p = Pattern.compile(pattern, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(code);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }
}