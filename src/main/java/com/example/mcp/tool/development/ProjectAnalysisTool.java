package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Project Analysis Tool - Analyze project structure, dependencies, and generate summaries
 */
public class ProjectAnalysisTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(ProjectAnalysisTool.class);

    // File patterns for different project types
    private static final Map<String, List<String>> PROJECT_PATTERNS = Map.of(
            "maven", List.of("pom.xml"),
            "gradle", List.of("build.gradle", "build.gradle.kts", "settings.gradle"),
            "npm", List.of("package.json"),
            "python", List.of("setup.py", "pyproject.toml", "requirements.txt"),
            "clojure", List.of("project.clj", "deps.edn"),
            "rust", List.of("Cargo.toml"),
            "go", List.of("go.mod")
    );

    // Code file extensions
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            ".java", ".kt", ".scala", ".clj", ".cljs", ".js", ".ts", ".py", ".rb", ".go", ".rs", ".cpp", ".c", ".h"
    );

    @Override
    public String getName() {
        return "analyze_project";
    }

    @Override
    public String getDescription() {
        return "Analyze project structure, dependencies, code metrics, and generate comprehensive project summaries";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "Project root path to analyze (default: current directory)",
                                "default", "."
                        ),
                        "analysis_type", Map.of(
                                "type", "string",
                                "description", "Type of analysis to perform",
                                "enum", List.of("full", "structure", "dependencies", "metrics", "summary"),
                                "default", "full"
                        ),
                        "max_depth", Map.of(
                                "type", "integer",
                                "description", "Maximum directory depth to analyze",
                                "minimum", 1,
                                "maximum", 10,
                                "default", 5
                        ),
                        "include_test_files", Map.of(
                                "type", "boolean",
                                "description", "Include test files in analysis",
                                "default", true
                        ),
                        "generate_summary", Map.of(
                                "type", "boolean",
                                "description", "Generate a PROJECT_SUMMARY.md style output",
                                "default", false
                        ),
                        "exclude_patterns", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Patterns to exclude from analysis",
                                "default", List.of("node_modules", "target", "build", ".git", "*.log")
                        )
                )
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String pathStr = getOptionalString(arguments, "path", ".");
            String analysisType = getOptionalString(arguments, "analysis_type", "full");
            int maxDepth = getOptionalInt(arguments, "max_depth", 5);
            boolean includeTestFiles = getOptionalBoolean(arguments, "include_test_files", true);
            boolean generateSummary = getOptionalBoolean(arguments, "generate_summary", false);
            @SuppressWarnings("unchecked")
            List<String> excludePatterns = (List<String>) arguments.getOrDefault("exclude_patterns",
                    List.of("node_modules", "target", "build", ".git", "*.log"));

            // Validate path
            Path projectPath = Paths.get(pathStr);
            if (!Files.exists(projectPath) || !Files.isDirectory(projectPath)) {
                throw new ToolExecutionException("Path does not exist or is not a directory: " + pathStr);
            }

            // Perform analysis
            ProjectAnalysis analysis = analyzeProject(projectPath, maxDepth, includeTestFiles, excludePatterns);

            // Format results based on analysis type
            String result = switch (analysisType) {
                case "structure" -> formatStructureAnalysis(analysis);
                case "dependencies" -> formatDependencyAnalysis(analysis);
                case "metrics" -> formatMetricsAnalysis(analysis);
                case "summary" -> formatProjectSummary(analysis, generateSummary);
                case "full" -> formatFullAnalysis(analysis, generateSummary);
                default -> throw new ToolExecutionException("Unknown analysis type: " + analysisType);
            };

            logger.debug("Project analysis completed for: {}", pathStr);
            return createTextResult(result);

        } catch (Exception e) {
            logger.error("Error analyzing project", e);
            throw new ToolExecutionException("Project analysis failed: " + e.getMessage(), e);
        }
    }

    private ProjectAnalysis analyzeProject(Path projectPath, int maxDepth, boolean includeTestFiles,
                                           List<String> excludePatterns) throws IOException {

        ProjectAnalysis analysis = new ProjectAnalysis(projectPath);
        ProjectVisitor visitor = new ProjectVisitor(analysis, includeTestFiles, excludePatterns);

        Files.walkFileTree(projectPath,
                Set.of(FileVisitOption.FOLLOW_LINKS),
                maxDepth,
                visitor);

        // Detect project types
        analysis.projectTypes = detectProjectTypes(projectPath);

        // Analyze dependencies
        analyzeDependencies(analysis);

        // Calculate metrics
        calculateMetrics(analysis);

        return analysis;
    }

    private Set<String> detectProjectTypes(Path projectPath) throws IOException {
        Set<String> types = new HashSet<>();

        for (Map.Entry<String, List<String>> entry : PROJECT_PATTERNS.entrySet()) {
            String projectType = entry.getKey();
            List<String> patterns = entry.getValue();

            for (String pattern : patterns) {
                if (Files.exists(projectPath.resolve(pattern))) {
                    types.add(projectType);
                    break;
                }
            }
        }

        return types;
    }

    private void analyzeDependencies(ProjectAnalysis analysis) {
        // Analyze based on detected project types
        for (String type : analysis.projectTypes) {
            switch (type) {
                case "maven" -> analyzeMavenDependencies(analysis);
                case "gradle" -> analyzeGradleDependencies(analysis);
                case "npm" -> analyzeNpmDependencies(analysis);
                case "python" -> analyzePythonDependencies(analysis);
                // Add more as needed
            }
        }
    }

    private void analyzeMavenDependencies(ProjectAnalysis analysis) {
        Path pomPath = analysis.rootPath.resolve("pom.xml");
        if (Files.exists(pomPath)) {
            try {
                String content = Files.readString(pomPath);

                // Extract dependencies using regex (simplified)
                Pattern depPattern = Pattern.compile(
                        "<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>\\s*<version>([^<]+)</version>",
                        Pattern.DOTALL
                );

                Matcher matcher = depPattern.matcher(content);
                while (matcher.find()) {
                    String groupId = matcher.group(1);
                    String artifactId = matcher.group(2);
                    String version = matcher.group(3);

                    analysis.dependencies.add(new Dependency(groupId + ":" + artifactId, version, "maven"));
                }

                // Extract project info
                extractProjectInfo(analysis, content, "maven");

            } catch (IOException e) {
                logger.debug("Could not read pom.xml: {}", e.getMessage());
            }
        }
    }

    private void analyzeGradleDependencies(ProjectAnalysis analysis) {
        // Try both build.gradle and build.gradle.kts
        for (String buildFile : List.of("build.gradle", "build.gradle.kts")) {
            Path buildPath = analysis.rootPath.resolve(buildFile);
            if (Files.exists(buildPath)) {
                try {
                    String content = Files.readString(buildPath);

                    // Extract dependencies using regex (simplified)
                    Pattern depPattern = Pattern.compile(
                            "(implementation|compile|api|testImplementation)\\s+['\"]([^'\"]+)['\"]"
                    );

                    Matcher matcher = depPattern.matcher(content);
                    while (matcher.find()) {
                        String scope = matcher.group(1);
                        String dependency = matcher.group(2);

                        analysis.dependencies.add(new Dependency(dependency, "gradle", scope));
                    }

                    break; // Found one, stop looking
                } catch (IOException e) {
                    logger.debug("Could not read {}: {}", buildFile, e.getMessage());
                }
            }
        }
    }

    private void analyzeNpmDependencies(ProjectAnalysis analysis) {
        Path packagePath = analysis.rootPath.resolve("package.json");
        if (Files.exists(packagePath)) {
            try {
                String content = Files.readString(packagePath);

                // Extract dependencies using regex (simplified JSON parsing)
                Pattern depPattern = Pattern.compile("\"([^\"]+)\":\\s*\"([^\"]+)\"");

                boolean inDependencies = false;
                for (String line : content.split("\n")) {
                    if (line.contains("\"dependencies\"") || line.contains("\"devDependencies\"")) {
                        inDependencies = true;
                        continue;
                    }
                    if (inDependencies && line.contains("}")) {
                        inDependencies = false;
                        continue;
                    }
                    if (inDependencies) {
                        Matcher matcher = depPattern.matcher(line);
                        if (matcher.find()) {
                            String name = matcher.group(1);
                            String version = matcher.group(2);
                            analysis.dependencies.add(new Dependency(name, version, "npm"));
                        }
                    }
                }

                // Extract project name and version
                extractProjectInfo(analysis, content, "npm");

            } catch (IOException e) {
                logger.debug("Could not read package.json: {}", e.getMessage());
            }
        }
    }

    private void analyzePythonDependencies(ProjectAnalysis analysis) {
        // Check requirements.txt
        Path reqPath = analysis.rootPath.resolve("requirements.txt");
        if (Files.exists(reqPath)) {
            try {
                List<String> lines = Files.readAllLines(reqPath);
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        String[] parts = line.split("[>=<]");
                        String name = parts[0].trim();
                        String version = parts.length > 1 ? parts[1].trim() : "unspecified";
                        analysis.dependencies.add(new Dependency(name, version, "python"));
                    }
                }
            } catch (IOException e) {
                logger.debug("Could not read requirements.txt: {}", e.getMessage());
            }
        }
    }

    private void extractProjectInfo(ProjectAnalysis analysis, String content, String type) {
        // Extract basic project information based on file type
        switch (type) {
            case "maven" -> {
                extractXmlValue(content, "artifactId").ifPresent(v -> analysis.projectName = v);
                extractXmlValue(content, "version").ifPresent(v -> analysis.version = v);
                extractXmlValue(content, "description").ifPresent(v -> analysis.description = v);
            }
            case "npm" -> {
                // Simplified JSON extraction
                extractJsonValue(content, "name").ifPresent(v -> analysis.projectName = v);
                extractJsonValue(content, "version").ifPresent(v -> analysis.version = v);
                extractJsonValue(content, "description").ifPresent(v -> analysis.description = v);
            }
        }
    }

    private Optional<String> extractXmlValue(String content, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">([^<]+)</" + tag + ">");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private Optional<String> extractJsonValue(String content, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\":\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private void calculateMetrics(ProjectAnalysis analysis) {
        analysis.metrics.put("totalFiles", analysis.filesByType.values().stream().mapToInt(List::size).sum());
        analysis.metrics.put("totalDirectories", analysis.directories.size());
        analysis.metrics.put("codeFiles", analysis.filesByType.entrySet().stream()
                .filter(e -> CODE_EXTENSIONS.contains(e.getKey()))
                .mapToInt(e -> e.getValue().size())
                .sum());
        analysis.metrics.put("totalLines", analysis.totalLines);
        analysis.metrics.put("dependencies", analysis.dependencies.size());
        analysis.metrics.put("projectTypes", analysis.projectTypes.size());
    }

    private String formatFullAnalysis(ProjectAnalysis analysis, boolean generateSummary) {
        StringBuilder result = new StringBuilder();

        if (generateSummary) {
            result.append(formatProjectSummary(analysis, true));
        } else {
            result.append("Project Analysis Report\n");
            result.append("═".repeat(60)).append("\n\n");

            result.append(formatStructureAnalysis(analysis)).append("\n");
            result.append(formatDependencyAnalysis(analysis)).append("\n");
            result.append(formatMetricsAnalysis(analysis));
        }

        return result.toString();
    }

    private String formatProjectSummary(ProjectAnalysis analysis, boolean markdownFormat) {
        StringBuilder result = new StringBuilder();

        if (markdownFormat) {
            result.append("# PROJECT_SUMMARY.md\n\n");
            result.append("## Project Overview\n\n");

            if (analysis.projectName != null) {
                result.append("**Name:** ").append(analysis.projectName).append("\n");
            }
            if (analysis.version != null) {
                result.append("**Version:** ").append(analysis.version).append("\n");
            }
            if (analysis.description != null) {
                result.append("**Description:** ").append(analysis.description).append("\n");
            }

            result.append("**Project Types:** ").append(String.join(", ", analysis.projectTypes)).append("\n\n");

            result.append("## Project Structure\n\n");
            result.append("```\n");
            formatDirectoryTree(result, analysis);
            result.append("```\n\n");

            result.append("## Key Metrics\n\n");
            result.append("- **Total Files:** ").append(analysis.metrics.get("totalFiles")).append("\n");
            result.append("- **Code Files:** ").append(analysis.metrics.get("codeFiles")).append("\n");
            result.append("- **Total Lines:** ").append(analysis.metrics.get("totalLines")).append("\n");
            result.append("- **Dependencies:** ").append(analysis.metrics.get("dependencies")).append("\n\n");

            if (!analysis.dependencies.isEmpty()) {
                result.append("## Dependencies\n\n");
                Map<String, List<Dependency>> depsByType = analysis.dependencies.stream()
                        .collect(Collectors.groupingBy(d -> d.type));

                for (Map.Entry<String, List<Dependency>> entry : depsByType.entrySet()) {
                    result.append("### ").append(entry.getKey().toUpperCase()).append("\n\n");
                    for (Dependency dep : entry.getValue()) {
                        result.append("- ").append(dep.name);
                        if (dep.version != null) {
                            result.append(" (").append(dep.version).append(")");
                        }
                        result.append("\n");
                    }
                    result.append("\n");
                }
            }
        } else {
            result.append("📋 Project Summary\n");
            result.append("═".repeat(50)).append("\n");
            result.append("Project: ").append(analysis.projectName != null ? analysis.projectName : "Unknown").append("\n");
            result.append("Types: ").append(String.join(", ", analysis.projectTypes)).append("\n");
            result.append("Files: ").append(analysis.metrics.get("totalFiles")).append(" total, ")
                    .append(analysis.metrics.get("codeFiles")).append(" code files\n");
            result.append("Dependencies: ").append(analysis.metrics.get("dependencies")).append("\n");
        }

        return result.toString();
    }

    private String formatStructureAnalysis(ProjectAnalysis analysis) {
        StringBuilder result = new StringBuilder();

        result.append("📁 Project Structure\n");
        result.append("─".repeat(40)).append("\n");
        result.append("Root: ").append(analysis.rootPath.toAbsolutePath()).append("\n");
        result.append("Project Types: ").append(String.join(", ", analysis.projectTypes)).append("\n\n");

        result.append("📊 File Distribution:\n");
        for (Map.Entry<String, List<Path>> entry : analysis.filesByType.entrySet()) {
            String extension = entry.getKey();
            int count = entry.getValue().size();
            result.append("  ").append(extension).append(": ").append(count).append(" files\n");
        }

        return result.toString();
    }

    private String formatDependencyAnalysis(ProjectAnalysis analysis) {
        StringBuilder result = new StringBuilder();

        result.append("📦 Dependencies\n");
        result.append("─".repeat(40)).append("\n");

        if (analysis.dependencies.isEmpty()) {
            result.append("No dependencies found.\n");
            return result.toString();
        }

        Map<String, List<Dependency>> depsByType = analysis.dependencies.stream()
                .collect(Collectors.groupingBy(d -> d.type));

        for (Map.Entry<String, List<Dependency>> entry : depsByType.entrySet()) {
            String type = entry.getKey();
            List<Dependency> deps = entry.getValue();

            result.append("\n🔹 ").append(type.toUpperCase()).append(" (").append(deps.size()).append("):\n");
            for (Dependency dep : deps) {
                result.append("  • ").append(dep.name);
                if (dep.version != null && !dep.version.equals(dep.type)) {
                    result.append(" : ").append(dep.version);
                }
                result.append("\n");
            }
        }

        return result.toString();
    }

    private String formatMetricsAnalysis(ProjectAnalysis analysis) {
        StringBuilder result = new StringBuilder();

        result.append("📊 Code Metrics\n");
        result.append("─".repeat(40)).append("\n");

        for (Map.Entry<String, Integer> entry : analysis.metrics.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();

            String displayName = switch (key) {
                case "totalFiles" -> "Total Files";
                case "totalDirectories" -> "Directories";
                case "codeFiles" -> "Code Files";
                case "totalLines" -> "Total Lines";
                case "dependencies" -> "Dependencies";
                case "projectTypes" -> "Project Types";
                default -> key;
            };

            result.append(displayName).append(": ").append(value).append("\n");
        }

        return result.toString();
    }

    private void formatDirectoryTree(StringBuilder result, ProjectAnalysis analysis) {
        // Simple directory tree representation
        result.append(analysis.rootPath.getFileName()).append("/\n");

        List<String> sortedDirs = analysis.directories.stream()
                .map(p -> analysis.rootPath.relativize(p).toString())
                .filter(s -> !s.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        for (String dir : sortedDirs) {
            String[] parts = dir.split("/");
            String indent = "  ".repeat(parts.length);
            result.append(indent).append("├── ").append(parts[parts.length - 1]).append("/\n");
        }
    }

    // Helper methods for parameter extraction
    private String getOptionalString(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
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

    // Data classes
    private static class ProjectAnalysis {
        final Path rootPath;
        final Map<String, List<Path>> filesByType = new HashMap<>();
        final List<Path> directories = new ArrayList<>();
        final List<Dependency> dependencies = new ArrayList<>();
        final Map<String, Integer> metrics = new HashMap<>();
        Set<String> projectTypes = new HashSet<>();
        String projectName;
        String version;
        String description;
        int totalLines = 0;

        ProjectAnalysis(Path rootPath) {
            this.rootPath = rootPath;
        }
    }

    private static class Dependency {
        final String name;
        final String version;
        final String type;
        final String scope;

        Dependency(String name, String version, String type) {
            this(name, version, type, null);
        }

        Dependency(String name, String version, String type, String scope) {
            this.name = name;
            this.version = version;
            this.type = type;
            this.scope = scope;
        }
    }

    private static class ProjectVisitor extends SimpleFileVisitor<Path> {
        private final ProjectAnalysis analysis;
        private final boolean includeTestFiles;
        private final List<Pattern> excludePatterns;

        ProjectVisitor(ProjectAnalysis analysis, boolean includeTestFiles, List<String> excludePatterns) {
            this.analysis = analysis;
            this.includeTestFiles = includeTestFiles;
            this.excludePatterns = excludePatterns.stream()
                    .map(pattern -> Pattern.compile(pattern.replace("*", ".*")))
                    .collect(Collectors.toList());
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            String dirName = dir.getFileName().toString();

            // Check exclude patterns
            for (Pattern pattern : excludePatterns) {
                if (pattern.matcher(dirName).matches()) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }

            // Skip test directories if not including test files
            if (!includeTestFiles && (dirName.equals("test") || dirName.equals("tests") ||
                    dirName.contains("test"))) {
                return FileVisitResult.SKIP_SUBTREE;
            }

            analysis.directories.add(dir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String fileName = file.getFileName().toString();

            // Check exclude patterns
            for (Pattern pattern : excludePatterns) {
                if (pattern.matcher(fileName).matches()) {
                    return FileVisitResult.CONTINUE;
                }
            }

            // Skip test files if not including them
            if (!includeTestFiles && (fileName.contains("test") || fileName.contains("Test") ||
                    fileName.contains("spec") || fileName.contains("Spec"))) {
                return FileVisitResult.CONTINUE;
            }

            // Get file extension
            String extension = getFileExtension(fileName);
            analysis.filesByType.computeIfAbsent(extension, k -> new ArrayList<>()).add(file);

            // Count lines for code files
            if (CODE_EXTENSIONS.contains(extension)) {
                try {
                    long lines = Files.lines(file).count();
                    analysis.totalLines += (int) lines;
                } catch (Exception e) {
                    // Ignore files that can't be read
                }
            }

            return FileVisitResult.CONTINUE;
        }

        private String getFileExtension(String fileName) {
            int lastDot = fileName.lastIndexOf('.');
            return lastDot > 0 ? fileName.substring(lastDot) : "no-extension";
        }
    }
}