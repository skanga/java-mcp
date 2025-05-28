package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.tool.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Dependency Lookup Tool - Search and retrieve information about Maven/Gradle dependencies
 */
public class DependencyLookupTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(DependencyLookupTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Repository APIs
    private static final String MAVEN_CENTRAL_SEARCH = "https://search.maven.org/solrsearch/select";

    @Override
    public String getName() {
        return "dependency_lookup";
    }

    @Override
    public String getDescription() {
        return "Search and retrieve information about Maven/Gradle dependencies from Maven Central";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "operation", Map.of(
                                "type", "string",
                                "description", "Operation to perform",
                                "enum", List.of("search", "latest_version", "artifact_info", "versions"),
                                "default", "search"
                        ),
                        "query", Map.of(
                                "type", "string",
                                "description", "Search query or artifact identifier (group:artifact or group/artifact)"
                        ),
                        "group_id", Map.of(
                                "type", "string",
                                "description", "Maven group ID (e.g., 'org.springframework')"
                        ),
                        "artifact_id", Map.of(
                                "type", "string",
                                "description", "Maven artifact ID (e.g., 'spring-core')"
                        ),
                        "version", Map.of(
                                "type", "string",
                                "description", "Specific version to lookup"
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "Maximum number of results",
                                "minimum", 1,
                                "maximum", 50,
                                "default", 10
                        ),
                        "include_snapshots", Map.of(
                                "type", "boolean",
                                "description", "Include snapshot versions",
                                "default", false
                        )
                ),
                "required", List.of("operation", "query")
        );
    }

    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            String operation = getRequiredString(arguments, "operation");
            String query = getRequiredString(arguments, "query");
            String groupId = getOptionalString(arguments, "group_id", null);
            String artifactId = getOptionalString(arguments, "artifact_id", null);
            String version = getOptionalString(arguments, "version", null);
            int limit = getOptionalInt(arguments, "limit", 10);
            boolean includeSnapshots = getOptionalBoolean(arguments, "include_snapshots", false);

            // Parse query if it contains group:artifact format
            if (groupId == null && artifactId == null) {
                String[] parts = parseArtifactQuery(query);
                if (parts.length >= 2) {
                    groupId = parts[0];
                    artifactId = parts[1];
                }
            }

            // Execute operation
            DependencyResult result = switch (operation) {
                case "search" -> searchDependencies(query, limit, includeSnapshots);
                case "latest_version" -> getLatestVersion(groupId, artifactId, includeSnapshots);
                case "artifact_info" -> getArtifactInfo(groupId, artifactId, version);
                case "versions" -> getVersions(groupId, artifactId, limit, includeSnapshots);
                default -> throw new ToolExecutionException("Unknown operation: " + operation);
            };

            // Format response
            String response = formatDependencyResult(operation, result, query);

            logger.debug("Dependency lookup completed for operation: {} query: {}", operation, query);
            return createTextResult(response);

        } catch (Exception e) {
            logger.error("Error performing dependency lookup", e);
            throw new ToolExecutionException("Dependency lookup failed: " + e.getMessage(), e);
        }
    }

    private String[] parseArtifactQuery(String query) {
        if (query.contains(":")) {
            return query.split(":", 2);
        } else if (query.contains("/")) {
            return query.split("/", 2);
        }
        return new String[]{query};
    }

    private DependencyResult searchDependencies(String query, int limit, boolean includeSnapshots)
            throws IOException, InterruptedException {

        String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
        String url = MAVEN_CENTRAL_SEARCH + "?q=" + encodedQuery + "&rows=" + limit + "&wt=json";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Search request failed with status: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode docs = root.path("response").path("docs");

        List<DependencyInfo> dependencies = new ArrayList<>();
        for (JsonNode doc : docs) {
            String groupIdField = doc.path("g").asText();
            String artifactIdField = doc.path("a").asText();
            String latestVersion = doc.path("latestVersion").asText();
            long timestamp = doc.path("timestamp").asLong();

            if (!includeSnapshots && latestVersion.contains("SNAPSHOT")) {
                continue;
            }

            DependencyInfo info = new DependencyInfo(
                    groupIdField, artifactIdField, latestVersion, null,
                    new Date(timestamp), generateMavenCoordinates(groupIdField, artifactIdField, latestVersion)
            );
            dependencies.add(info);
        }

        return new DependencyResult(dependencies, query, true, null);
    }

    private DependencyResult getLatestVersion(String groupId, String artifactId, boolean includeSnapshots)
            throws IOException, InterruptedException, ToolExecutionException {

        if (groupId == null || artifactId == null) {
            throw new ToolExecutionException("Both group_id and artifact_id are required for latest_version operation");
        }

        String searchQuery = "g:" + groupId + " AND a:" + artifactId;
        String encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8");
        String url = MAVEN_CENTRAL_SEARCH + "?q=" + encodedQuery + "&rows=1&wt=json";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Version lookup request failed with status: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode docs = root.path("response").path("docs");

        if (docs.size() == 0) {
            return new DependencyResult(List.of(), groupId + ":" + artifactId, false, "Artifact not found");
        }

        JsonNode doc = docs.get(0);
        String latestVersion = doc.path("latestVersion").asText();
        long timestamp = doc.path("timestamp").asLong();

        DependencyInfo info = new DependencyInfo(
                groupId, artifactId, latestVersion, null,
                new Date(timestamp), generateMavenCoordinates(groupId, artifactId, latestVersion)
        );

        return new DependencyResult(List.of(info), groupId + ":" + artifactId, true, null);
    }

    private DependencyResult getArtifactInfo(String groupId, String artifactId, String version)
            throws IOException, InterruptedException, ToolExecutionException {

        if (groupId == null || artifactId == null) {
            throw new ToolExecutionException("Both group_id and artifact_id are required for artifact_info operation");
        }

        if (version == null) {
            DependencyResult latestResult = getLatestVersion(groupId, artifactId, false);
            if (!latestResult.success || latestResult.dependencies.isEmpty()) {
                return latestResult;
            }
            version = latestResult.dependencies.get(0).version;
        }

        String searchQuery = "g:" + groupId + " AND a:" + artifactId + " AND v:" + version;
        String encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8");
        String url = MAVEN_CENTRAL_SEARCH + "?q=" + encodedQuery + "&rows=1&wt=json";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Artifact info request failed with status: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode docs = root.path("response").path("docs");

        if (docs.size() == 0) {
            return new DependencyResult(List.of(), groupId + ":" + artifactId + ":" + version, false, "Artifact version not found");
        }

        JsonNode doc = docs.get(0);
        long timestamp = doc.path("timestamp").asLong();
        String description = doc.has("d") ? doc.path("d").asText() : null;

        DependencyInfo info = new DependencyInfo(
                groupId, artifactId, version, description,
                new Date(timestamp), generateMavenCoordinates(groupId, artifactId, version)
        );

        return new DependencyResult(List.of(info), groupId + ":" + artifactId + ":" + version, true, null);
    }

    private DependencyResult getVersions(String groupId, String artifactId, int limit, boolean includeSnapshots)
            throws IOException, InterruptedException, ToolExecutionException {

        if (groupId == null || artifactId == null) {
            throw new ToolExecutionException("Both group_id and artifact_id are required for versions operation");
        }

        String searchQuery = "g:" + groupId + " AND a:" + artifactId;
        String encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8");
        String url = MAVEN_CENTRAL_SEARCH + "?q=" + encodedQuery + "&rows=" + limit + "&wt=json&core=gav";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Versions request failed with status: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode docs = root.path("response").path("docs");

        List<DependencyInfo> versions = new ArrayList<>();
        for (JsonNode doc : docs) {
            String versionStr = doc.path("v").asText();
            long timestamp = doc.path("timestamp").asLong();

            if (!includeSnapshots && versionStr.contains("SNAPSHOT")) {
                continue;
            }

            DependencyInfo info = new DependencyInfo(
                    groupId, artifactId, versionStr, null,
                    new Date(timestamp), generateMavenCoordinates(groupId, artifactId, versionStr)
            );
            versions.add(info);
        }

        // Sort by timestamp (newest first)
        versions.sort((a, b) -> b.publishDate.compareTo(a.publishDate));

        return new DependencyResult(versions, groupId + ":" + artifactId, true, null);
    }

    private String generateMavenCoordinates(String groupId, String artifactId, String version) {
        return String.format("""
            Maven:
            <dependency>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>%s</version>
            </dependency>
            
            Gradle:
            implementation '%s:%s:%s'
            """, groupId, artifactId, version, groupId, artifactId, version);
    }

    private String formatDependencyResult(String operation, DependencyResult result, String query) {
        StringBuilder response = new StringBuilder();

        response.append("Dependency Lookup: ").append(operation.toUpperCase()).append("\n");
        response.append("═".repeat(60)).append("\n");
        response.append("Query: ").append(query).append("\n");
        response.append("Status: ");

        if (result.success) {
            response.append("✅ SUCCESS");
        } else {
            response.append("❌ FAILED");
            if (result.error != null) {
                response.append(" - ").append(result.error);
            }
        }
        response.append("\n");
        response.append("Results: ").append(result.dependencies.size()).append("\n\n");

        if (result.dependencies.isEmpty()) {
            response.append("No dependencies found.\n");
            return response.toString();
        }

        switch (operation) {
            case "search" -> formatSearchResults(response, result.dependencies);
            case "latest_version" -> formatLatestVersionResult(response, result.dependencies.get(0));
            case "artifact_info" -> formatArtifactInfoResult(response, result.dependencies.get(0));
            case "versions" -> formatVersionsResult(response, result.dependencies);
        }

        return response.toString();
    }

    private void formatSearchResults(StringBuilder response, List<DependencyInfo> dependencies) {
        response.append("📦 Search Results:\n");
        response.append("─".repeat(40)).append("\n");

        for (DependencyInfo dep : dependencies) {
            response.append("🔹 ").append(dep.groupId).append(":").append(dep.artifactId).append("\n");
            response.append("   Latest: ").append(dep.version).append("\n");
            response.append("   Published: ").append(formatDate(dep.publishDate)).append("\n");
            if (dep.description != null && !dep.description.trim().isEmpty()) {
                response.append("   Description: ").append(dep.description).append("\n");
            }
            response.append("\n");
        }
    }

    private void formatLatestVersionResult(StringBuilder response, DependencyInfo dep) {
        response.append("📦 Latest Version:\n");
        response.append("─".repeat(40)).append("\n");
        response.append("Artifact: ").append(dep.groupId).append(":").append(dep.artifactId).append("\n");
        response.append("Latest Version: ").append(dep.version).append("\n");
        response.append("Published: ").append(formatDate(dep.publishDate)).append("\n\n");

        if (dep.coordinates != null) {
            response.append("📋 Usage:\n");
            response.append("─".repeat(30)).append("\n");
            response.append(dep.coordinates).append("\n");
        }
    }

    private void formatArtifactInfoResult(StringBuilder response, DependencyInfo dep) {
        response.append("📦 Artifact Information:\n");
        response.append("─".repeat(40)).append("\n");
        response.append("Group ID: ").append(dep.groupId).append("\n");
        response.append("Artifact ID: ").append(dep.artifactId).append("\n");
        response.append("Version: ").append(dep.version).append("\n");
        response.append("Published: ").append(formatDate(dep.publishDate)).append("\n");

        if (dep.description != null && !dep.description.trim().isEmpty()) {
            response.append("Description: ").append(dep.description).append("\n");
        }

        response.append("\n");

        if (dep.coordinates != null) {
            response.append("📋 Usage:\n");
            response.append("─".repeat(30)).append("\n");
            response.append(dep.coordinates).append("\n");
        }
    }

    private void formatVersionsResult(StringBuilder response, List<DependencyInfo> dependencies) {
        response.append("📦 Available Versions:\n");
        response.append("─".repeat(40)).append("\n");

        if (!dependencies.isEmpty()) {
            DependencyInfo first = dependencies.get(0);
            response.append("Artifact: ").append(first.groupId).append(":").append(first.artifactId).append("\n\n");
        }

        for (int i = 0; i < dependencies.size(); i++) {
            DependencyInfo dep = dependencies.get(i);
            String marker = i == 0 ? "🌟" : "🔹"; // Star for latest
            response.append(marker).append(" ").append(dep.version);

            if (i == 0) {
                response.append(" (latest)");
            }

            response.append(" - ").append(formatDate(dep.publishDate)).append("\n");
        }
    }

    private String formatDate(Date date) {
        if (date == null) return "Unknown";
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd");
        return formatter.format(date);
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
    private static class DependencyInfo {
        final String groupId;
        final String artifactId;
        final String version;
        final String description;
        final Date publishDate;
        final String coordinates;

        DependencyInfo(String groupId, String artifactId, String version, String description,
                       Date publishDate, String coordinates) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.description = description;
            this.publishDate = publishDate;
            this.coordinates = coordinates;
        }
    }

    private static class DependencyResult {
        final List<DependencyInfo> dependencies;
        final String query;
        final boolean success;
        final String error;

        DependencyResult(List<DependencyInfo> dependencies, String query, boolean success, String error) {
            this.dependencies = dependencies;
            this.query = query;
            this.success = success;
            this.error = error;
        }
    }
}