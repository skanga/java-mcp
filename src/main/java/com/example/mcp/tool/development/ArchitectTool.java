package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Architect Tool - Provides system architecture analysis, design patterns identification,
 * and architectural recommendations for software projects
 */
public class ArchitectTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(ArchitectTool.class);

    // Architecture patterns to detect
    private static final Map<String, ArchitecturePattern> PATTERNS = Map.of(
            "mvc", new ArchitecturePattern("Model-View-Controller",
                    List.of("controller", "model", "view"),
                    "Separates application logic into three interconnected components"),
            "microservices", new ArchitecturePattern("Microservices",
                    List.of("service", "api", "gateway", "docker", "k8s"),
                    "Architectural style for developing applications as loosely coupled services"),
            "layered", new ArchitecturePattern("Layered Architecture",
                    List.of("service", "repository", "entity", "dto"),
                    "Organizes code into horizontal layers"),
            "hexagonal", new ArchitecturePattern("Hexagonal/Ports & Adapters",
                    List.of("port", "adapter", "domain", "infrastructure"),
                    "Isolates core business logic from external concerns"),
            "cqrs", new ArchitecturePattern("CQRS",
                    List.of("command", "query", "handler", "event"),
                    "Separates read and write operations"),
            "eventdriven", new ArchitecturePattern("Event-Driven",
                    List.of("event", "listener", "publisher", "subscriber"),
                    "Uses events to trigger and communicate between decoupled services")
    );

    @Override
    public String getName() {
        return "architect";
    }

    @Override
    public String getDescription() {
        return "Analyzes system architecture, identifies design patterns, and provides architectural recommendations for software projects";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "description", "Type of architectural analysis",
                                "enum", List.of("analyze_project", "suggest_patterns", "review_architecture", "design_system"),
                                "default", "analyze_project"
                        ),
                        "project_path", Map.of(
                                "type", "string",
                                "description", "Path to project root for analysis",
                                "default", "."
                        ),
                        "requirements", Map.of(
                                "type", "string",
                                "description", "System requirements or description for design suggestions"
                        ),
                        "scale", Map.of(
                                "type", "string",
                                "description", "Expected system scale",
                                "enum", List.of("small", "medium", "large", "enterprise"),
                                "default", "medium"
                        ),
                        "domain", Map.of(
                                "type", "string",
                                "description", "Application domain",
                                "enum", List.of("web", "mobile", "desktop", "api", "microservices", "monolith", "data"),
                                "default", "web"
                        ),
                        "focus_areas", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Specific architectural concerns",
                                "default", List.of("all")
                        ),
                        "max_depth", Map.of(
                                "type", "integer",
                                "description", "Maximum directory depth to analyze",
                                "minimum", 1,
                                "maximum", 10,
                                "default", 5
                        )
                )
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String action = getOptionalString(arguments, "action", "analyze_project");
            String projectPath = getOptionalString(arguments, "project_path", ".");
            String requirements = getOptionalString(arguments, "requirements", null);
            String scale = getOptionalString(arguments, "scale", "medium");
            String domain = getOptionalString(arguments, "domain", "web");
            @SuppressWarnings("unchecked")
            List<String> focusAreas = (List<String>) arguments.getOrDefault("focus_areas", List.of("all"));
            int maxDepth = getOptionalInt(arguments, "max_depth", 5);

            String result = switch (action) {
                case "analyze_project" -> analyzeProjectArchitecture(projectPath, maxDepth, focusAreas);
                case "suggest_patterns" -> suggestArchitecturePatterns(requirements, scale, domain);
                case "review_architecture" -> reviewArchitecture(projectPath, maxDepth, scale, domain);
                case "design_system" -> designSystemArchitecture(requirements, scale, domain, focusAreas);
                default -> throw new ToolExecutionException("Unknown action: " + action);
            };

            logger.debug("Architecture analysis completed for action: {}", action);
            return createTextResult(result);

        } catch (Exception e) {
            logger.error("Error in architect tool", e);
            throw new ToolExecutionException("Architecture analysis failed: " + e.getMessage(), e);
        }
    }

    private String analyzeProjectArchitecture(String projectPath, int maxDepth, List<String> focusAreas)
            throws IOException, ToolExecutionException {

        Path path = Paths.get(projectPath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new ToolExecutionException("Project path does not exist or is not a directory: " + projectPath);
        }

        ProjectStructure structure = analyzeProjectStructure(path, maxDepth);
        ArchitecturalAnalysis analysis = performArchitecturalAnalysis(structure);

        return formatProjectAnalysis(analysis, structure, projectPath);
    }

    private String suggestArchitecturePatterns(String requirements, String scale, String domain) throws ToolExecutionException {
        if (requirements == null || requirements.trim().isEmpty()) {
            throw new ToolExecutionException("Requirements are needed for pattern suggestions");
        }

        List<PatternRecommendation> recommendations = generatePatternRecommendations(requirements, scale, domain);
        return formatPatternSuggestions(recommendations, requirements, scale, domain);
    }

    private String reviewArchitecture(String projectPath, int maxDepth, String scale, String domain)
            throws IOException, ToolExecutionException {

        Path path = Paths.get(projectPath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new ToolExecutionException("Project path does not exist: " + projectPath);
        }

        ProjectStructure structure = analyzeProjectStructure(path, maxDepth);
        ArchitecturalAnalysis analysis = performArchitecturalAnalysis(structure);

        List<ArchitecturalIssue> issues = identifyArchitecturalIssues(structure, analysis, scale, domain);
        List<ArchitecturalRecommendation> recommendations = generateRecommendations(issues, structure, scale, domain);

        return formatArchitecturalReview(analysis, issues, recommendations, projectPath);
    }

    private String designSystemArchitecture(String requirements, String scale, String domain, List<String> focusAreas) throws ToolExecutionException {
        if (requirements == null || requirements.trim().isEmpty()) {
            throw new ToolExecutionException("Requirements are needed for system design");
        }

        SystemDesign design = generateSystemDesign(requirements, scale, domain, focusAreas);
        return formatSystemDesign(design, requirements);
    }

    private ProjectStructure analyzeProjectStructure(Path projectPath, int maxDepth) throws IOException {
        ProjectStructure structure = new ProjectStructure(projectPath);

        Files.walk(projectPath, maxDepth)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    String fileName = file.getFileName().toString();
                    String relativePath = projectPath.relativize(file).toString();

                    // Categorize files
                    if (fileName.endsWith(".java") || fileName.endsWith(".kt") || fileName.endsWith(".scala")) {
                        structure.sourceFiles.add(relativePath);
                        analyzeJavaFile(file, structure);
                    } else if (fileName.endsWith(".js") || fileName.endsWith(".ts")) {
                        structure.sourceFiles.add(relativePath);
                        analyzeJavaScriptFile(file, structure);
                    } else if (fileName.endsWith(".py")) {
                        structure.sourceFiles.add(relativePath);
                        analyzePythonFile(file, structure);
                    } else if (fileName.equals("pom.xml") || fileName.equals("build.gradle") ||
                            fileName.equals("package.json") || fileName.equals("Dockerfile")) {
                        structure.configFiles.add(relativePath);
                        analyzeConfigFile(file, structure);
                    } else if (fileName.endsWith(".sql") || fileName.endsWith(".migration")) {
                        structure.dataFiles.add(relativePath);
                    } else if (fileName.endsWith(".test.") || fileName.contains("test") || fileName.contains("spec")) {
                        structure.testFiles.add(relativePath);
                    }
                });

        return structure;
    }

    private void analyzeJavaFile(Path file, ProjectStructure structure) {
        try {
            String content = Files.readString(file);

            // Look for architectural patterns
            if (content.contains("@Controller") || content.contains("@RestController")) {
                structure.detectedPatterns.add("mvc");
                structure.controllers.add(file.toString());
            }
            if (content.contains("@Service")) {
                structure.services.add(file.toString());
            }
            if (content.contains("@Repository") || content.contains("@Entity")) {
                structure.detectedPatterns.add("layered");
                structure.repositories.add(file.toString());
            }
            if (content.contains("@Component") || content.contains("@Bean")) {
                structure.components.add(file.toString());
            }
            if (content.contains("Command") || content.contains("Query")) {
                structure.detectedPatterns.add("cqrs");
            }
            if (content.contains("Event") || content.contains("Listener")) {
                structure.detectedPatterns.add("eventdriven");
            }

        } catch (IOException e) {
            // Skip file if can't read
        }
    }

    private void analyzeJavaScriptFile(Path file, ProjectStructure structure) {
        try {
            String content = Files.readString(file);

            if (content.contains("controller") || content.contains("Controller")) {
                structure.detectedPatterns.add("mvc");
                structure.controllers.add(file.toString());
            }
            if (content.contains("service") || content.contains("Service")) {
                structure.services.add(file.toString());
            }
            if (content.contains("model") || content.contains("Model")) {
                structure.models.add(file.toString());
            }
            if (content.contains("express") || content.contains("app.")) {
                structure.frameworks.add("Express.js");
            }
            if (content.contains("react") || content.contains("React")) {
                structure.frameworks.add("React");
            }

        } catch (IOException e) {
            // Skip file if can't read
        }
    }

    private void analyzePythonFile(Path file, ProjectStructure structure) {
        try {
            String content = Files.readString(file);

            if (content.contains("django") || content.contains("Django")) {
                structure.frameworks.add("Django");
                structure.detectedPatterns.add("mvc");
            }
            if (content.contains("flask") || content.contains("Flask")) {
                structure.frameworks.add("Flask");
            }
            if (content.contains("fastapi") || content.contains("FastAPI")) {
                structure.frameworks.add("FastAPI");
            }

        } catch (IOException e) {
            // Skip file if can't read
        }
    }

    private void analyzeConfigFile(Path file, ProjectStructure structure) {
        try {
            String content = Files.readString(file);
            String fileName = file.getFileName().toString();

            if (fileName.equals("Dockerfile")) {
                structure.deploymentPatterns.add("containerization");
            }
            if (content.contains("spring-boot") || content.contains("org.springframework")) {
                structure.frameworks.add("Spring Boot");
            }
            if (content.contains("kubernetes") || content.contains("k8s")) {
                structure.deploymentPatterns.add("kubernetes");
                structure.detectedPatterns.add("microservices");
            }
            if (content.contains("docker-compose")) {
                structure.deploymentPatterns.add("docker-compose");
            }

        } catch (IOException e) {
            // Skip file if can't read
        }
    }

    private ArchitecturalAnalysis performArchitecturalAnalysis(ProjectStructure structure) {
        ArchitecturalAnalysis analysis = new ArchitecturalAnalysis();

        // Detect primary patterns
        analysis.primaryPatterns = identifyPrimaryPatterns(structure);

        // Calculate architectural metrics
        analysis.complexity = calculateComplexity(structure);
        analysis.modularity = calculateModularity(structure);
        analysis.testability = calculateTestability(structure);
        analysis.maintainability = calculateMaintainability(structure);

        // Identify architectural smells
        analysis.architecturalSmells = identifyArchitecturalSmells(structure);

        return analysis;
    }

    private List<String> identifyPrimaryPatterns(ProjectStructure structure) {
        Map<String, Integer> patternScores = new HashMap<>();

        for (String pattern : structure.detectedPatterns) {
            patternScores.merge(pattern, 1, Integer::sum);
        }

        // Add scoring based on file structure
        if (!structure.controllers.isEmpty() && !structure.services.isEmpty()) {
            patternScores.merge("layered", 2, Integer::sum);
        }
        if (structure.deploymentPatterns.contains("kubernetes")) {
            patternScores.merge("microservices", 3, Integer::sum);
        }

        return patternScores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String calculateComplexity(ProjectStructure structure) {
        int totalFiles = structure.sourceFiles.size();
        int totalComponents = structure.services.size() + structure.controllers.size() + structure.repositories.size();

        if (totalFiles < 50 && totalComponents < 10) return "Low";
        if (totalFiles < 200 && totalComponents < 30) return "Medium";
        return "High";
    }

    private String calculateModularity(ProjectStructure structure) {
        // Simple heuristic based on directory structure and separation of concerns
        int layers = 0;
        if (!structure.controllers.isEmpty()) layers++;
        if (!structure.services.isEmpty()) layers++;
        if (!structure.repositories.isEmpty()) layers++;

        if (layers >= 3) return "Good";
        if (layers >= 2) return "Fair";
        return "Poor";
    }

    private String calculateTestability(ProjectStructure structure) {
        double testRatio = (double) structure.testFiles.size() / Math.max(1, structure.sourceFiles.size());

        if (testRatio >= 0.8) return "Excellent";
        if (testRatio >= 0.5) return "Good";
        if (testRatio >= 0.2) return "Fair";
        return "Poor";
    }

    private String calculateMaintainability(ProjectStructure structure) {
        // Combine complexity, modularity, and testability
        int score = 0;

        if ("Low".equals(calculateComplexity(structure))) score += 3;
        else if ("Medium".equals(calculateComplexity(structure))) score += 2;
        else score += 1;

        if ("Good".equals(calculateModularity(structure))) score += 3;
        else if ("Fair".equals(calculateModularity(structure))) score += 2;
        else score += 1;

        if ("Excellent".equals(calculateTestability(structure)) || "Good".equals(calculateTestability(structure))) score += 3;
        else if ("Fair".equals(calculateTestability(structure))) score += 2;
        else score += 1;

        if (score >= 8) return "High";
        if (score >= 6) return "Medium";
        return "Low";
    }

    private List<String> identifyArchitecturalSmells(ProjectStructure structure) {
        List<String> smells = new ArrayList<>();

        if (structure.controllers.size() > 20) {
            smells.add("Too many controllers - consider feature-based organization");
        }
        if (structure.services.isEmpty() && !structure.controllers.isEmpty()) {
            smells.add("Missing service layer - business logic may be in controllers");
        }
        if (structure.testFiles.size() < structure.sourceFiles.size() * 0.3) {
            smells.add("Insufficient test coverage");
        }
        if (structure.detectedPatterns.isEmpty()) {
            smells.add("No clear architectural patterns detected");
        }

        return smells;
    }

    private List<ArchitecturalIssue> identifyArchitecturalIssues(ProjectStructure structure, ArchitecturalAnalysis analysis, String scale, String domain) {
        List<ArchitecturalIssue> issues = new ArrayList<>();

        if ("High".equals(analysis.complexity) && "large".equals(scale)) {
            issues.add(new ArchitecturalIssue("High complexity for large-scale system", "high",
                    "Consider breaking down into smaller modules or microservices"));
        }

        if ("Poor".equals(analysis.testability)) {
            issues.add(new ArchitecturalIssue("Poor testability", "medium",
                    "Improve dependency injection and separation of concerns"));
        }

        if (structure.deploymentPatterns.isEmpty() && "large".equals(scale)) {
            issues.add(new ArchitecturalIssue("No deployment strategy detected", "medium",
                    "Consider containerization and orchestration strategies"));
        }

        return issues;
    }

    private List<ArchitecturalRecommendation> generateRecommendations(List<ArchitecturalIssue> issues, ProjectStructure structure, String scale, String domain) {
        List<ArchitecturalRecommendation> recommendations = new ArrayList<>();

        for (ArchitecturalIssue issue : issues) {
            if (issue.description.contains("complexity")) {
                recommendations.add(new ArchitecturalRecommendation("Modularization",
                        "Break down large components into smaller, focused modules", "high"));
            }
            if (issue.description.contains("testability")) {
                recommendations.add(new ArchitecturalRecommendation("Dependency Injection",
                        "Implement dependency injection to improve testability", "medium"));
            }
        }

        // General recommendations based on scale and domain
        if ("large".equals(scale) && structure.detectedPatterns.contains("microservices")) {
            recommendations.add(new ArchitecturalRecommendation("API Gateway",
                    "Implement API Gateway pattern for service coordination", "high"));
        }

        return recommendations;
    }

    private List<PatternRecommendation> generatePatternRecommendations(String requirements, String scale, String domain) {
        List<PatternRecommendation> recommendations = new ArrayList<>();
        String lowerReq = requirements.toLowerCase();

        if (lowerReq.contains("web") || lowerReq.contains("api")) {
            recommendations.add(new PatternRecommendation("mvc", 90,
                    "Excellent for web applications with clear separation of concerns"));
        }

        if (lowerReq.contains("scale") || lowerReq.contains("distributed") || "large".equals(scale)) {
            recommendations.add(new PatternRecommendation("microservices", 85,
                    "Ideal for large-scale, distributed systems"));
        }

        if (lowerReq.contains("event") || lowerReq.contains("message") || lowerReq.contains("async")) {
            recommendations.add(new PatternRecommendation("eventdriven", 80,
                    "Great for asynchronous, event-based communication"));
        }

        if (lowerReq.contains("read") && lowerReq.contains("write") || lowerReq.contains("performance")) {
            recommendations.add(new PatternRecommendation("cqrs", 75,
                    "Optimizes read/write operations separately"));
        }

        return recommendations.stream()
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .collect(Collectors.toList());
    }

    private SystemDesign generateSystemDesign(String requirements, String scale, String domain, List<String> focusAreas) {
        SystemDesign design = new SystemDesign();
        design.requirements = requirements;
        design.scale = scale;
        design.domain = domain;

        // Generate high-level architecture
        if ("microservices".equals(domain) || "large".equals(scale)) {
            design.architecture = "Microservices Architecture";
            design.components.addAll(List.of("API Gateway", "Service Discovery", "Load Balancer", "Database per Service"));
        } else if ("web".equals(domain)) {
            design.architecture = "Layered Web Architecture";
            design.components.addAll(List.of("Presentation Layer", "Business Logic Layer", "Data Access Layer", "Database"));
        } else {
            design.architecture = "Modular Monolith";
            design.components.addAll(List.of("Controller Layer", "Service Layer", "Repository Layer", "Database"));
        }

        // Add cross-cutting concerns
        design.crossCuttingConcerns.addAll(List.of("Logging", "Authentication", "Authorization", "Monitoring"));

        if ("large".equals(scale)) {
            design.crossCuttingConcerns.addAll(List.of("Caching", "Rate Limiting", "Circuit Breaker"));
        }

        return design;
    }

    private String formatProjectAnalysis(ArchitecturalAnalysis analysis, ProjectStructure structure, String projectPath) {
        StringBuilder output = new StringBuilder();

        output.append("🏗️  Project Architecture Analysis\n");
        output.append("═".repeat(60)).append("\n");
        output.append("Project: ").append(projectPath).append("\n");
        output.append("Source Files: ").append(structure.sourceFiles.size()).append("\n");
        output.append("Test Files: ").append(structure.testFiles.size()).append("\n\n");

        // Detected Patterns
        output.append("🎯 Detected Architecture Patterns\n");
        output.append("─".repeat(40)).append("\n");
        if (analysis.primaryPatterns.isEmpty()) {
            output.append("No clear patterns detected\n");
        } else {
            for (String pattern : analysis.primaryPatterns) {
                ArchitecturePattern patternInfo = PATTERNS.get(pattern);
                if (patternInfo != null) {
                    output.append("✅ ").append(patternInfo.name).append("\n");
                    output.append("   ").append(patternInfo.description).append("\n\n");
                }
            }
        }

        // Architecture Quality Metrics
        output.append("📊 Architecture Quality\n");
        output.append("─".repeat(40)).append("\n");
        output.append("Complexity: ").append(getQualityEmoji(analysis.complexity)).append(" ").append(analysis.complexity).append("\n");
        output.append("Modularity: ").append(getQualityEmoji(analysis.modularity)).append(" ").append(analysis.modularity).append("\n");
        output.append("Testability: ").append(getQualityEmoji(analysis.testability)).append(" ").append(analysis.testability).append("\n");
        output.append("Maintainability: ").append(getQualityEmoji(analysis.maintainability)).append(" ").append(analysis.maintainability).append("\n\n");

        // Component Overview
        output.append("🔧 Component Overview\n");
        output.append("─".repeat(40)).append("\n");
        output.append("Controllers: ").append(structure.controllers.size()).append("\n");
        output.append("Services: ").append(structure.services.size()).append("\n");
        output.append("Repositories: ").append(structure.repositories.size()).append("\n");
        output.append("Models: ").append(structure.models.size()).append("\n");

        if (!structure.frameworks.isEmpty()) {
            output.append("Frameworks: ").append(String.join(", ", structure.frameworks)).append("\n");
        }
        output.append("\n");

        // Architectural Smells
        if (!analysis.architecturalSmells.isEmpty()) {
            output.append("🚨 Architectural Concerns\n");
            output.append("─".repeat(40)).append("\n");
            for (String smell : analysis.architecturalSmells) {
                output.append("⚠️  ").append(smell).append("\n");
            }
        }

        return output.toString();
    }

    private String formatPatternSuggestions(List<PatternRecommendation> recommendations, String requirements, String scale, String domain) {
        StringBuilder output = new StringBuilder();

        output.append("🎯 Architecture Pattern Recommendations\n");
        output.append("═".repeat(60)).append("\n");
        output.append("Requirements: ").append(requirements).append("\n");
        output.append("Scale: ").append(scale).append("\n");
        output.append("Domain: ").append(domain).append("\n\n");

        output.append("📋 Recommended Patterns\n");
        output.append("─".repeat(40)).append("\n");

        for (int i = 0; i < recommendations.size(); i++) {
            PatternRecommendation rec = recommendations.get(i);
            ArchitecturePattern pattern = PATTERNS.get(rec.patternId);

            output.append(String.format("%d. %s (%d%% match)\n", i + 1,
                    pattern != null ? pattern.name : rec.patternId, rec.score));
            output.append("   💡 ").append(rec.reasoning).append("\n");
            if (pattern != null) {
                output.append("   📝 ").append(pattern.description).append("\n");
            }
            output.append("\n");
        }

        return output.toString();
    }

    private String formatArchitecturalReview(ArchitecturalAnalysis analysis, List<ArchitecturalIssue> issues,
                                             List<ArchitecturalRecommendation> recommendations, String projectPath) {
        StringBuilder output = new StringBuilder();

        output.append("🔍 Architectural Review\n");
        output.append("═".repeat(60)).append("\n");
        output.append("Project: ").append(projectPath).append("\n\n");

        // Issues
        if (!issues.isEmpty()) {
            output.append("🚨 Issues Identified (").append(issues.size()).append(")\n");
            output.append("─".repeat(40)).append("\n");
            for (ArchitecturalIssue issue : issues) {
                output.append(getSeverityEmoji(issue.severity)).append(" ").append(issue.description).append("\n");
                if (issue.recommendation != null) {
                    output.append("   💡 ").append(issue.recommendation).append("\n");
                }
                output.append("\n");
            }
        }

        // Recommendations
        if (!recommendations.isEmpty()) {
            output.append("💡 Recommendations (").append(recommendations.size()).append(")\n");
            output.append("─".repeat(40)).append("\n");
            for (ArchitecturalRecommendation rec : recommendations) {
                output.append("✨ ").append(rec.title).append("\n");
                output.append("   ").append(rec.description).append("\n");
                output.append("   Priority: ").append(rec.priority).append("\n\n");
            }
        }

        return output.toString();
    }

    private String formatSystemDesign(SystemDesign design, String requirements) {
        StringBuilder output = new StringBuilder();

        output.append("🏛️  System Architecture Design\n");
        output.append("═".repeat(60)).append("\n");
        output.append("Requirements: ").append(requirements).append("\n");
        output.append("Recommended Architecture: ").append(design.architecture).append("\n");
        output.append("Scale: ").append(design.scale).append("\n");
        output.append("Domain: ").append(design.domain).append("\n\n");

        // Core Components
        output.append("🔧 Core Components\n");
        output.append("─".repeat(40)).append("\n");
        for (String component : design.components) {
            output.append("• ").append(component).append("\n");
        }
        output.append("\n");

        // Cross-cutting Concerns
        output.append("⚡ Cross-cutting Concerns\n");
        output.append("─".repeat(40)).append("\n");
        for (String concern : design.crossCuttingConcerns) {
            output.append("• ").append(concern).append("\n");
        }

        return output.toString();
    }

    private String getQualityEmoji(String quality) {
        return switch (quality.toLowerCase()) {
            case "excellent", "high", "good" -> "🟢";
            case "fair", "medium" -> "🟡";
            case "poor", "low" -> "🔴";
            default -> "⚪";
        };
    }

    private String getSeverityEmoji(String severity) {
        return switch (severity.toLowerCase()) {
            case "high", "critical" -> "🔴";
            case "medium" -> "🟡";
            case "low" -> "🟢";
            default -> "ℹ️";
        };
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

    // Data classes
    private static class ProjectStructure {
        final Path rootPath;
        final List<String> sourceFiles = new ArrayList<>();
        final List<String> testFiles = new ArrayList<>();
        final List<String> configFiles = new ArrayList<>();
        final List<String> dataFiles = new ArrayList<>();
        final List<String> controllers = new ArrayList<>();
        final List<String> services = new ArrayList<>();
        final List<String> repositories = new ArrayList<>();
        final List<String> models = new ArrayList<>();
        final List<String> components = new ArrayList<>();
        final Set<String> detectedPatterns = new HashSet<>();
        final Set<String> frameworks = new HashSet<>();
        final Set<String> deploymentPatterns = new HashSet<>();

        ProjectStructure(Path rootPath) {
            this.rootPath = rootPath;
        }
    }

    private static class ArchitecturalAnalysis {
        List<String> primaryPatterns = new ArrayList<>();
        String complexity;
        String modularity;
        String testability;
        String maintainability;
        List<String> architecturalSmells = new ArrayList<>();
    }

    private static class ArchitecturePattern {
        final String name;
        final List<String> indicators;
        final String description;

        ArchitecturePattern(String name, List<String> indicators, String description) {
            this.name = name;
            this.indicators = indicators;
            this.description = description;
        }
    }

    private static class PatternRecommendation {
        final String patternId;
        final int score;
        final String reasoning;

        PatternRecommendation(String patternId, int score, String reasoning) {
            this.patternId = patternId;
            this.score = score;
            this.reasoning = reasoning;
        }
    }

    private static class ArchitecturalIssue {
        final String description;
        final String severity;
        final String recommendation;

        ArchitecturalIssue(String description, String severity, String recommendation) {
            this.description = description;
            this.severity = severity;
            this.recommendation = recommendation;
        }
    }

    private static class ArchitecturalRecommendation {
        final String title;
        final String description;
        final String priority;

        ArchitecturalRecommendation(String title, String description, String priority) {
            this.title = title;
            this.description = description;
            this.priority = priority;
        }
    }

    private static class SystemDesign {
        String requirements;
        String scale;
        String domain;
        String architecture;
        final List<String> components = new ArrayList<>();
        final List<String> crossCuttingConcerns = new ArrayList<>();
    }
}