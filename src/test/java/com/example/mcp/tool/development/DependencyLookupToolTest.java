package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DependencyLookupToolTest {

    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ResourceLimiter mockResourceLimiter;
    @Mock
    private ResourceLimiter.ResourcePermit mockResourcePermit;
    @Mock
    private HttpClient mockHttpClient;
    @Mock
    private HttpResponse<String> mockHttpResponse;

    @Spy
    private DependencyLookupTool toolSpy;

    @Captor
    private ArgumentCaptor<HttpRequest> httpRequestCaptor;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String MAVEN_CENTRAL_SEARCH_URL_BASE = "https://search.maven.org/solrsearch/select";

    @BeforeEach
    void setUp() throws ToolExecutionException {
        when(mockResourceLimiter.acquireNetworkOperation("maven_central_search")).thenReturn(mockResourcePermit);
        DependencyLookupTool realTool = new DependencyLookupTool(mockSecurityContext, mockResourceLimiter);
        toolSpy = spy(realTool);
    }

    private Map<String, Object> createArgs(String operation, Map<String, Object> params) {
        Map<String, Object> args = new java.util.HashMap<>(params);
        args.put("operation", operation);
        return args;
    }
    
    private String sampleSingleDocJsonResponse(String groupId, String artifactId, String version, String description, long timestamp) {
        String descField = (description != null) ? String.format(",\"d\":\"%s\"", description) : "";
        return String.format("""
        {
          "response": {
            "docs":[
              {
                "g":"%s",
                "a":"%s",
                "latestVersion":"%s", 
                "v":"%s",
                "timestamp":%d
                %s
              }
            ]
          }
        }
        """, groupId, artifactId, version, version, timestamp, descField);
    }
    
    private String sampleMultipleVersionsJsonResponse(String groupId, String artifactId, List<Map<String, Object>> versionsData) {
        StringBuilder docsBuilder = new StringBuilder();
        for (int i = 0; i < versionsData.size(); i++) {
            Map<String, Object> data = versionsData.get(i);
            docsBuilder.append(String.format("""
            {
              "g":"%s",
              "a":"%s",
              "v":"%s",
              "timestamp":%d
            }
            """, groupId, artifactId, data.get("v"), data.get("timestamp")));
            if (i < versionsData.size() - 1) {
                docsBuilder.append(",");
            }
        }
        return String.format("""
        {
          "response": { "docs": [%s] }
        }
        """, docsBuilder.toString());
    }


    @Test
    void testToolInstantiationAndBasicInfo() {
        assertNotNull(toolSpy);
        assertEquals("dependency_lookup", toolSpy.getName());
        assertNotNull(toolSpy.getDescription());
        assertTrue(toolSpy.getInputSchema().containsKey("type"));
        verify(mockResourcePermit, never()).close(); 
    }

    // --- validateInputs Tests ---
    @Test
    void testValidateInputs_search_validQuery() {
        Map<String, Object> args = createArgs("search", Map.of("query", "spring-core"));
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
    }
    
    @Test
    void testValidateInputs_search_validGroupArtifact() {
         Map<String, Object> args = createArgs("search", Map.of("group_id", "org.springframework", "artifact_id", "spring-core"));
         assertDoesNotThrow(() -> toolSpy.validateInputs(args));
    }

    @Test
    void testValidateInputs_search_missingQueryAndGroupArtifact() {
        Map<String, Object> args = createArgs("search", Collections.emptyMap());
         ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
         assertTrue(ex.getMessage().contains("Parameter 'query' or both 'group_id' and 'artifact_id' are required for 'search' operation."));
    }

    @Test
    void testValidateInputs_latestVersion_missingGroup() {
        Map<String, Object> args = createArgs("latest_version", Map.of("artifact_id", "spring-core"));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Both 'group_id' and 'artifact_id' (or a parsable 'query') are required for 'latest_version' operation."));
    }
    
    @Test
    void testValidateInputs_latestVersion_validWithQuery() {
        Map<String, Object> args = createArgs("latest_version", Map.of("query", "org.springframework:spring-core"));
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
    }

    @Test
    void testValidateInputs_artifactInfo_missingArtifact() {
        Map<String, Object> args = createArgs("artifact_info", Map.of("group_id", "org.springframework"));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Both 'group_id' and 'artifact_id' (or a parsable 'query') are required for 'artifact_info' operation."));
    }

    @Test
    void testValidateInputs_versions_missingAllIdentifiers() {
        Map<String, Object> args = createArgs("versions", Collections.emptyMap());
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Both 'group_id' and 'artifact_id' (or a parsable 'query') are required for 'versions' operation."));
    }


    @Test
    void testValidateInputs_invalidOperation() {
        Map<String, Object> args = createArgs("delete_everything", Map.of("query", "test"));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Invalid operation: delete_everything"));
    }

    @Test
    void testAcquireResourcesAndPermitClosureOnSuccess() throws Exception {
        try (MockedStatic<HttpClient> httpMock = mockStatic(HttpClient.class)) {
            HttpClient.Builder mockBuilder = mock(HttpClient.Builder.class);
            httpMock.when(HttpClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.connectTimeout(any(Duration.class))).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockHttpClient);

            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);
            when(mockHttpResponse.statusCode()).thenReturn(200);
            when(mockHttpResponse.body()).thenReturn(sampleSingleDocJsonResponse("org.example", "lib", "1.0.0", null, System.currentTimeMillis()));
            
            Map<String, Object> args = createArgs("search", Map.of("query", "org.example:lib"));
            toolSpy.execute(args); 
            verify(mockResourceLimiter).acquireNetworkOperation("maven_central_search");
            verify(mockResourcePermit).close();
        }
    }

    // --- Core Logic & HTTP Interaction Tests ---
    private void setupHttpClientMock() {
        // This setup is needed for each test method that makes HTTP calls.
        // It's complex due to the static final httpClient in SUT.
        // This static mock block must be within the test method or a helper called by it.
        // For brevity, this comment serves as a reminder. It's applied in each test.
    }

    @Test
    void testSearchDependencies_Success() throws Exception {
         try (MockedStatic<HttpClient> httpMock = mockStatic(HttpClient.class)) {
            HttpClient.Builder mockBuilder = mock(HttpClient.Builder.class);
            httpMock.when(HttpClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.connectTimeout(any(Duration.class))).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockHttpClient);
            
            String query = "spring-boot-starter";
            String jsonResponse = """
            {
              "response": {
                "docs": [
                  { "g": "org.springframework.boot", "a": "spring-boot-starter", "latestVersion": "2.5.5", "timestamp": 1633582959000 },
                  { "g": "org.springframework.boot", "a": "spring-boot-starter-web", "latestVersion": "2.5.5", "timestamp": 1633582969000 }
                ]
              }
            }
            """;
            when(mockHttpClient.send(httpRequestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);
            when(mockHttpResponse.statusCode()).thenReturn(200);
            when(mockHttpResponse.body()).thenReturn(jsonResponse);

            Map<String, Object> args = createArgs("search", Map.of("query", query, "limit", 2));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

            assertFalse(result.isError);
            String output = result.content.get(0).text;
            assertTrue(output.contains("org.springframework.boot:spring-boot-starter"));
            assertTrue(output.contains("Latest: 2.5.5"));
            assertTrue(output.contains("org.springframework.boot:spring-boot-starter-web"));
            
            HttpRequest actualRequest = httpRequestCaptor.getValue();
            assertTrue(actualRequest.uri().toString().startsWith(MAVEN_CENTRAL_SEARCH_URL_BASE));
            assertTrue(actualRequest.uri().toString().contains("q=" + query)); // Query should be URL encoded by SUT
            assertTrue(actualRequest.uri().toString().contains("rows=2"));
        }
    }
    
    @Test
    void testSearchDependencies_HttpError() throws Exception {
        try (MockedStatic<HttpClient> httpMock = mockStatic(HttpClient.class)) {
            HttpClient.Builder mockBuilder = mock(HttpClient.Builder.class);
            httpMock.when(HttpClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.connectTimeout(any(Duration.class))).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockHttpClient);

            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);
            when(mockHttpResponse.statusCode()).thenReturn(500); 

            Map<String, Object> args = createArgs("search", Map.of("query", "test"));
            ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.execute(args));
            assertTrue(ex.getMessage().contains("Search request failed with status: 500"));
        }
    }

    @Test
    void testGetLatestVersion_Success() throws Exception {
        try (MockedStatic<HttpClient> httpMock = mockStatic(HttpClient.class)) {
            HttpClient.Builder mockBuilder = mock(HttpClient.Builder.class);
            httpMock.when(HttpClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.connectTimeout(any(Duration.class))).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockHttpClient);

            String groupId = "com.fasterxml.jackson.core";
            String artifactId = "jackson-databind";
            String version = "2.13.0";
            String jsonResponse = sampleSingleDocJsonResponse(groupId, artifactId, version, null, System.currentTimeMillis());
            
            when(mockHttpClient.send(httpRequestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);
            when(mockHttpResponse.statusCode()).thenReturn(200);
            when(mockHttpResponse.body()).thenReturn(jsonResponse);

            Map<String, Object> args = createArgs("latest_version", Map.of("group_id", groupId, "artifact_id", artifactId));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

            assertFalse(result.isError);
            String output = result.content.get(0).text;
            assertTrue(output.contains("Artifact: " + groupId + ":" + artifactId));
            assertTrue(output.contains("Latest Version: " + version));
            assertTrue(output.contains("implementation '" + groupId + ":" + artifactId + ":" + version + "'"));

            HttpRequest actualRequest = httpRequestCaptor.getValue();
            String expectedSearchQuery = "g%3A" + groupId + "+AND+a%3A" + artifactId; // URL encoded
            assertTrue(actualRequest.uri().toString().contains(expectedSearchQuery));
        }
    }

    @Test
    void testGetArtifactInfo_WithVersion_Success() throws Exception {
         try (MockedStatic<HttpClient> httpMock = mockStatic(HttpClient.class)) {
            HttpClient.Builder mockBuilder = mock(HttpClient.Builder.class);
            httpMock.when(HttpClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.connectTimeout(any(Duration.class))).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockHttpClient);

            String groupId = "junit";
            String artifactId = "junit";
            String version = "4.12";
            String description = "JUnit is a unit testing framework for Java.";
            String jsonResponse = sampleSingleDocJsonResponse(groupId, artifactId, version, description, System.currentTimeMillis());

            when(mockHttpClient.send(httpRequestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);
            when(mockHttpResponse.statusCode()).thenReturn(200);
            when(mockHttpResponse.body()).thenReturn(jsonResponse);
            
            Map<String, Object> args = createArgs("artifact_info", Map.of("group_id", groupId, "artifact_id", artifactId, "version", version));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

            assertFalse(result.isError);
            String output = result.content.get(0).text;
            assertTrue(output.contains("Group ID: " + groupId));
            assertTrue(output.contains("Artifact ID: " + artifactId));
            assertTrue(output.contains("Version: " + version));
            assertTrue(output.contains("Description: " + description));
            
            HttpRequest actualRequest = httpRequestCaptor.getValue();
            String expectedSearchQuery = "g%3A" + groupId + "+AND+a%3A" + artifactId + "+AND+v%3A" + version;
            assertTrue(actualRequest.uri().toString().contains(expectedSearchQuery));
        }
    }
    
    @Test
    void testGetArtifactInfo_NoVersion_FetchesLatest() throws Exception {
        // This test ensures that if no version is provided, getArtifactInfo internally calls getLatestVersion.
        // It requires mocking two separate HTTP calls.
        try (MockedStatic<HttpClient> httpMock = mockStatic(HttpClient.class)) {
            HttpClient.Builder mockBuilder = mock(HttpClient.Builder.class);
            httpMock.when(HttpClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.connectTimeout(any(Duration.class))).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockHttpClient);

            String groupId = "org.slf4j";
            String artifactId = "slf4j-api";
            String latestVersion = "1.7.32";
            String description = "The Simple Logging Facade for Java (SLF4J)";
            
            // Mock for the first call (getLatestVersion within getArtifactInfo)
            String latestVersionJsonResponse = sampleSingleDocJsonResponse(groupId, artifactId, latestVersion, null, System.currentTimeMillis());
            // Mock for the second call (getArtifactInfo with the resolved latestVersion)
            String artifactInfoJsonResponse = sampleSingleDocJsonResponse(groupId, artifactId, latestVersion, description, System.currentTimeMillis());

            // Setup sequential responses or more specific request matching
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest req = invocation.getArgument(0);
                    if (req.uri().toString().contains("&rows=1&wt=json") && !req.uri().toString().contains("&v=")) { // Matches getLatestVersion call
                        HttpResponse<String> resp = mock(HttpResponse.class);
                        when(resp.statusCode()).thenReturn(200);
                        when(resp.body()).thenReturn(latestVersionJsonResponse);
                        return resp;
                    } else if (req.uri().toString().contains("&v=" + latestVersion)) { // Matches getArtifactInfo with version
                         HttpResponse<String> resp = mock(HttpResponse.class);
                        when(resp.statusCode()).thenReturn(200);
                        when(resp.body()).thenReturn(artifactInfoJsonResponse);
                        return resp;
                    }
                    return mockHttpResponse; // Fallback
                });

            Map<String, Object> args = createArgs("artifact_info", Map.of("group_id", groupId, "artifact_id", artifactId));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

            assertFalse(result.isError);
            String output = result.content.get(0).text;
            assertTrue(output.contains("Version: " + latestVersion));
            assertTrue(output.contains("Description: " + description));
            
            verify(mockHttpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }
    }

    @Test
    void testGetVersions_Success() throws Exception {
         try (MockedStatic<HttpClient> httpMock = mockStatic(HttpClient.class)) {
            HttpClient.Builder mockBuilder = mock(HttpClient.Builder.class);
            httpMock.when(HttpClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.connectTimeout(any(Duration.class))).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockHttpClient);

            String groupId = "commons-io";
            String artifactId = "commons-io";
            List<Map<String, Object>> versionsData = List.of(
                Map.of("v", "2.11.0", "timestamp", System.currentTimeMillis()),
                Map.of("v", "2.10.0", "timestamp", System.currentTimeMillis() - 100000)
            );
            String jsonResponse = sampleMultipleVersionsJsonResponse(groupId, artifactId, versionsData);

            when(mockHttpClient.send(httpRequestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);
            when(mockHttpResponse.statusCode()).thenReturn(200);
            when(mockHttpResponse.body()).thenReturn(jsonResponse);

            Map<String, Object> args = createArgs("versions", Map.of("group_id", groupId, "artifact_id", artifactId, "limit", 2));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

            assertFalse(result.isError);
            String output = result.content.get(0).text;
            assertTrue(output.contains("Artifact: " + groupId + ":" + artifactId));
            assertTrue(output.contains("🌟 2.11.0 (latest)"));
            assertTrue(output.contains("🔹 2.10.0"));
            
            HttpRequest actualRequest = httpRequestCaptor.getValue();
            assertTrue(actualRequest.uri().toString().contains("core=gav")); // Specific to versions query
            assertTrue(actualRequest.uri().toString().contains("rows=2"));
        }
    }
    
    @Test
    void testParseArtifactQuery_colonSyntax() throws Exception {
        // Test this private method's logic via a public method that uses it.
        // latest_version with a query string "group:artifact" will trigger parseArtifactQuery.
         try (MockedStatic<HttpClient> httpMock = mockStatic(HttpClient.class)) {
            HttpClient.Builder mockBuilder = mock(HttpClient.Builder.class);
            httpMock.when(HttpClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.connectTimeout(any(Duration.class))).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockHttpClient);

            String query = "org.example:my-lib";
            String groupId = "org.example";
            String artifactId = "my-lib";
            String version = "1.0";
            String jsonResponse = sampleSingleDocJsonResponse(groupId, artifactId, version, null, System.currentTimeMillis());

            when(mockHttpClient.send(httpRequestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);
            when(mockHttpResponse.statusCode()).thenReturn(200);
            when(mockHttpResponse.body()).thenReturn(jsonResponse);

            Map<String, Object> args = createArgs("latest_version", Map.of("query", query));
            toolSpy.execute(args); // Expected to parse "org.example:my-lib"

            HttpRequest actualRequest = httpRequestCaptor.getValue();
            String expectedSearchQuery = "g%3A" + groupId + "+AND+a%3A" + artifactId;
            assertTrue(actualRequest.uri().toString().contains(expectedSearchQuery), "URL should contain parsed group and artifact.");
        }
    }

    @Test
    void testParseArtifactQuery_slashSyntax() throws Exception {
         try (MockedStatic<HttpClient> httpMock = mockStatic(HttpClient.class)) {
            HttpClient.Builder mockBuilder = mock(HttpClient.Builder.class);
            httpMock.when(HttpClient::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.connectTimeout(any(Duration.class))).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockHttpClient);

            String query = "org.example/my-lib"; // Slash syntax
            String groupId = "org.example";
            String artifactId = "my-lib";
            String version = "1.0";
            String jsonResponse = sampleSingleDocJsonResponse(groupId, artifactId, version, null, System.currentTimeMillis());

            when(mockHttpClient.send(httpRequestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);
            when(mockHttpResponse.statusCode()).thenReturn(200);
            when(mockHttpResponse.body()).thenReturn(jsonResponse);

            Map<String, Object> args = createArgs("latest_version", Map.of("query", query));
            toolSpy.execute(args);

            HttpRequest actualRequest = httpRequestCaptor.getValue();
            String expectedSearchQuery = "g%3A" + groupId + "+AND+a%3A" + artifactId;
            assertTrue(actualRequest.uri().toString().contains(expectedSearchQuery), "URL should contain parsed group and artifact from slash syntax.");
        }
    }
}
