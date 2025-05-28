package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;
import com.example.mcp.tool.BaseMcpTool;
// import org.slf4j.Logger; // Logger inherited from BaseMcpTool
// import org.slf4j.LoggerFactory; // LoggerFactory inherited from BaseMcpTool

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
public class ProjectAnalysisTool extends BaseMcpTool {
    // private static final Logger logger = LoggerFactory.getLogger(ProjectAnalysisTool.class); // Logger inherited

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

    public ProjectAnalysisTool(SecurityContext securityContext, ResourceLimiter resourceLimiter) {
        super(securityContext, resourceLimiter);
    }

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
    protected void validateInputs(Map<String, Object> arguments) throws ToolExecutionException {
        String pathStr = getOptionalString(arguments, "path", ".");
        Path projectPath = validatePath(pathStr); // From BaseMcpTool

        if (!Files.isDirectory(projectPath)) {
            throw new ToolExecutionException("Project path is not a directory: " + projectPath);
        }

        // Validate max_depth
        int maxDepth = getOptionalInt(arguments, "max_depth", 5);
        if (maxDepth < 1 || maxDepth > 10) { // As per original schema
            throw new ToolExecutionException("max_depth must be between 1 and 10.");
        }

        // Validate analysis_type (enum)
        String analysisType = getOptionalString(arguments, "analysis_type", "full");
        List<String> validAnalysisTypes = List.of("full", "structure", "dependencies", "metrics", "summary");
        if (!validAnalysisTypes.contains(analysisType)) {
            throw new ToolExecutionException("Invalid analysis_type: " + analysisType + ". Must be one of " + validAnalysisTypes);
        }
    }

    @Override
    protected ResourceLimiter.ResourcePermit acquireResources() throws ToolExecutionException {
        return resourceLimiter.acquireFileOperation("project_analysis");
    }

    @Override
    protected McpModels.CallToolResponse.CallToolResult executeInternal(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String pathStr = getOptionalString(arguments, "path", ".");
            Path projectPath = validatePath(pathStr); // Path already validated by validateInputs

            String analysisType = getOptionalString(arguments, "analysis_type", "full");
            int maxDepth = getOptionalInt(arguments, "max_depth", 5);
            boolean includeTestFiles = getOptionalBoolean(arguments, "include_test_files", true);
            boolean generateSummary = getOptionalBoolean(arguments, "generate_summary", false);
            List<String> excludePatterns = getOptionalStringList(arguments, "exclude_patterns",
                    List.of("node_modules", "target", "build", ".git", "*.log"));

            // Path existence and directory check done in validateInputs

            // Perform analysis
            ProjectAnalysis analysis = analyzeProject(projectPath, maxDepth, includeTestFiles, excludePatterns);

            // Format results based on analysis type
            String result = switch (analysisType) {
                case "structure" -> formatStructureAnalysis(analysis);
                case "dependencies" -> formatDependencyAnalysis(analysis);
                case "metrics" -> formatMetricsAnalysis(analysis);
                case "summary" -> formatProjectSummary(analysis, generateSummary);
                case "full" -> formatFullAnalysis(analysis, generateSummary);
                default -> throw new ToolExecutionException("Unknown analysis type: " + analysisType); // Should be caught by validateInputs
            };

            logger.debug("Project analysis completed for: {}", projectPath); // Use validated path
            return super.createTextResult(result); // Use BaseMcpTool's createTextResult

        } catch (IOException e) { // Catch specific IOException from analyzeProject
             logger.error("IO error during project analysis for path: {}", getOptionalString(arguments, "path", "."), e);
            throw new ToolExecutionException("Project analysis IO failed: " + e.getMessage(), e);
        } catch (ToolExecutionException e) { // Rethrow ToolExecutionExceptions
            throw e;
        }
        catch (Exception e) { // Catch any other unexpected exceptions
            logger.error("Unexpected error analyzing project", e);
            throw new ToolExecutionException("Unexpected project analysis error: " + e.getMessage(), e);
        }
    }

    private ProjectAnalysis analyzeProject(Path projectPath, int maxDepth, boolean includeTestFiles,
                                           List<String> excludePatterns) throws IOException, ToolExecutionException {
        // Ensure the root project path itself is allowed before walking
        if (!securityContext.isFileAllowed(projectPath)) {
            throw new ToolExecutionException("Access to project path is denied by security policy: " + projectPath);
        }

        ProjectAnalysis analysis = new ProjectAnalysis(projectPath);
        ProjectVisitor visitor = new ProjectVisitor(analysis, includeTestFiles, excludePatterns, securityContext); // Pass SecurityContext

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

    private Set<String> detectProjectTypes(Path projectPath) throws IOException, ToolExecutionException {
        Set<String> types = new HashSet<>();

        for (Map.Entry<String, List<String>> entry : PROJECT_PATTERNS.entrySet()) {
            String projectType = entry.getKey();
            List<String> patterns = entry.getValue();

            for (String pattern : patterns) {
                Path specificFilePath = projectPath.resolve(pattern);
                if (Files.exists(specificFilePath)) {
                    // Before checking existence, ensure it's allowed to be accessed (though it's under root)
                    // and validate its size if we were to read it here.
                    // For now, just checking existence as original code.
                    if (securityContext.isFileAllowed(specificFilePath)) {
                         types.add(projectType);
                         break;
                    } else {
                        logger.warn("Access to project file {} denied by security policy.", specificFilePath);
                    }
                }
            }
        }
        return types;
    }

    private void analyzeDependencies(ProjectAnalysis analysis) throws ToolExecutionException { // Added ToolExecutionException
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

    private void analyzeMavenDependencies(ProjectAnalysis analysis) throws ToolExecutionException {
        Path pomPath = analysis.rootPath.resolve("pom.xml");
        if (Files.exists(pomPath) && securityContext.isFileAllowed(pomPath)) {
            try {
                validateFileSize(pomPath); // Validate size before reading
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
                logger.warn("Could not read pom.xml: {}", e.getMessage()); // Changed to warn
            }
        } else if (Files.exists(pomPath)) {
             logger.warn("Access to pom.xml denied by security policy: {}", pomPath);
        }
    }

    private void analyzeGradleDependencies(ProjectAnalysis analysis) throws ToolExecutionException {
        // Try both build.gradle and build.gradle.kts
        for (String buildFileName : List.of("build.gradle", "build.gradle.kts")) {
            Path buildPath = analysis.rootPath.resolve(buildFileName);
            if (Files.exists(buildPath) && securityContext.isFileAllowed(buildPath)) {
                try {
                    validateFileSize(buildPath); // Validate size before reading
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
                    logger.warn("Could not read {}: {}", buildFileName, e.getMessage());
                }
            } else if (Files.exists(buildPath)) {
                logger.warn("Access to {} denied by security policy: {}", buildFileName, buildPath);
            }
        }
    }

    private void analyzeNpmDependencies(ProjectAnalysis analysis) throws ToolExecutionException {
        Path packagePath = analysis.rootPath.resolve("package.json");
        if (Files.exists(packagePath) && securityContext.isFileAllowed(packagePath)) {
            try {
                validateFileSize(packagePath); // Validate size before reading
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
                logger.warn("Could not read package.json: {}", e.getMessage());
            }
        } else if (Files.exists(packagePath)) {
            logger.warn("Access to package.json denied by security policy: {}", packagePath);
        }
    }

    private void analyzePythonDependencies(ProjectAnalysis analysis) throws ToolExecutionException {
        // Check requirements.txt
        Path reqPath = analysis.rootPath.resolve("requirements.txt");
        if (Files.exists(reqPath) && securityContext.isFileAllowed(reqPath)) {
            try {
                validateFileSize(reqPath); // Validate size before reading
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
                logger.warn("Could not read requirements.txt: {}", e.getMessage());
            }
        } else if (Files.exists(reqPath)) {
            logger.warn("Access to requirements.txt denied by security policy: {}", reqPath);
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
                if (dep.version != null && !dep.version.equals(dep.type)) { // Avoid printing type as version for gradle
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
            String[] parts = dir.split("/"); // This might need platform-specific separator
            String indent = "  ".repeat(parts.length);
            result.append(indent).append("├── ").append(parts[parts.length - 1]).append("/\n");
        }
    }

    // Helper methods (getOptionalString, etc.) removed as they are inherited from BaseMcpTool

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
        private final SecurityContext securityContext; // Added SecurityContext

        ProjectVisitor(ProjectAnalysis analysis, boolean includeTestFiles, List<String> excludePatterns, SecurityContext securityContext) {
            this.analysis = analysis;
            this.includeTestFiles = includeTestFiles;
            this.excludePatterns = excludePatterns.stream()
                    .map(pattern -> Pattern.compile(pattern.replace("*", ".*"))) // Basic glob to regex
                    .collect(Collectors.toList());
            this.securityContext = securityContext; // Store SecurityContext
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (!securityContext.isFileAllowed(dir)) { // Check if directory itself is allowed
                logger.debug("Skipping directory due to security policy: {}", dir);
                return FileVisitResult.SKIP_SUBTREE;
            }

            String dirName = dir.getFileName().toString();
            for (Pattern pattern : excludePatterns) {
                if (pattern.matcher(dirName).matches()) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
            if (!includeTestFiles && (dirName.equals("test") || dirName.equals("tests") || dirName.contains("test"))) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            analysis.directories.add(dir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!securityContext.isFileAllowed(file)) { // Check if file is allowed
                logger.debug("Skipping file due to security policy: {}", file);
                return FileVisitResult.CONTINUE;
            }

            String fileName = file.getFileName().toString();
            for (Pattern pattern : excludePatterns) {
                if (pattern.matcher(fileName).matches()) {
                    return FileVisitResult.CONTINUE;
                }
            }
            if (!includeTestFiles && (fileName.contains("test") || fileName.contains("Test") || fileName.contains("spec") || fileName.contains("Spec"))) {
                return FileVisitResult.CONTINUE;
            }

            String extension = getFileExtension(fileName);
            analysis.filesByType.computeIfAbsent(extension, k -> new ArrayList<>()).add(file);

            if (CODE_EXTENSIONS.contains(extension)) {
                try {
                    // It's good practice to check file size before reading all lines,
                    // but BaseMcpTool.validateFileSize is not directly available here.
                    // This check should ideally be done before calling Files.lines().
                    // For now, we proceed as original, but this is a point for future enhancement.
                    // securityContext.validateFileSize(file); // conceptual placement
                    long lines = Files.lines(file).count();
                    analysis.totalLines += (int) lines;
                } catch (SecurityException se) {
                    logger.warn("Security exception while counting lines for file {}: {}", file, se.getMessage());
                }
                catch (IOException | UncheckedIOException e) { // Catch UncheckedIOException from Files.lines
                    logger.warn("Could not count lines for file {}: {}", file, e.getMessage());
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