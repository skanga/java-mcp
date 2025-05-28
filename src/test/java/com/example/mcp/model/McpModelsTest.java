package com.example.mcp.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpModelsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Base Request Tests")
    class BaseRequestTests {

        @Test
        @DisplayName("Should serialize BaseRequest with default jsonrpc version")
        void shouldSerializeBaseRequestWithDefaultJsonrpc() throws Exception {
            // Given
            var request = new TestBaseRequest();
            request.id = "test-id";
            request.method = "test-method";

            // When
            String json = objectMapper.writeValueAsString(request);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("2.0", node.get("jsonrpc").asText());
            assertEquals("test-id", node.get("id").asText());
            assertEquals("test-method", node.get("method").asText());
        }

        @Test
        @DisplayName("Should handle different id types")
        void shouldHandleDifferentIdTypes() throws Exception {
            // Test with String id
            var stringRequest = new TestBaseRequest();
            stringRequest.id = "string-id";
            String stringJson = objectMapper.writeValueAsString(stringRequest);
            assertTrue(stringJson.contains("\"id\":\"string-id\""));

            // Test with Integer id
            var intRequest = new TestBaseRequest();
            intRequest.id = 123;
            String intJson = objectMapper.writeValueAsString(intRequest);
            assertTrue(intJson.contains("\"id\":123"));

            // Test with null id
            var nullRequest = new TestBaseRequest();
            nullRequest.id = null;
            String nullJson = objectMapper.writeValueAsString(nullRequest);
            assertTrue(nullJson.contains("\"id\":null"));
        }

        private static class TestBaseRequest extends McpModels.BaseRequest {
            // Concrete implementation for testing
        }
    }

    @Nested
    @DisplayName("Base Response Tests")
    class BaseResponseTests {

        @Test
        @DisplayName("Should serialize BaseResponse with default jsonrpc version")
        void shouldSerializeBaseResponseWithDefaultJsonrpc() throws Exception {
            // Given
            var response = new TestBaseResponse();
            response.id = "response-id";

            // When
            String json = objectMapper.writeValueAsString(response);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("2.0", node.get("jsonrpc").asText());
            assertEquals("response-id", node.get("id").asText());
        }

        private static class TestBaseResponse extends McpModels.BaseResponse {
            // Concrete implementation for testing
        }
    }

    @Nested
    @DisplayName("Error Response Tests")
    class ErrorResponseTests {

        @Test
        @DisplayName("Should serialize complete ErrorResponse")
        void shouldSerializeCompleteErrorResponse() throws Exception {
            // Given
            var errorResponse = new McpModels.ErrorResponse();
            errorResponse.id = "error-id";
            errorResponse.error = new McpModels.ErrorResponse.Error();
            errorResponse.error.code = 500;
            errorResponse.error.message = "Internal Server Error";
            errorResponse.error.data = Map.of("detail", "Something went wrong");

            // When
            String json = objectMapper.writeValueAsString(errorResponse);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("2.0", node.get("jsonrpc").asText());
            assertEquals("error-id", node.get("id").asText());
            assertEquals(500, node.get("error").get("code").asInt());
            assertEquals("Internal Server Error", node.get("error").get("message").asText());
            assertEquals("Something went wrong", node.get("error").get("data").get("detail").asText());
        }

        @Test
        @DisplayName("Should serialize ErrorResponse without data field")
        void shouldSerializeErrorResponseWithoutData() throws Exception {
            // Given
            var errorResponse = new McpModels.ErrorResponse();
            errorResponse.id = "error-id";
            errorResponse.error = new McpModels.ErrorResponse.Error();
            errorResponse.error.code = 404;
            errorResponse.error.message = "Not Found";
            // data is null

            // When
            String json = objectMapper.writeValueAsString(errorResponse);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals(404, node.get("error").get("code").asInt());
            assertEquals("Not Found", node.get("error").get("message").asText());
            assertNull(node.get("error").get("data"));
        }

        @Test
        @DisplayName("Should deserialize ErrorResponse from JSON")
        void shouldDeserializeErrorResponseFromJson() throws Exception {
            // Given
            String json = """
                {
                    "jsonrpc": "2.0",
                    "id": "test-id",
                    "error": {
                        "code": 400,
                        "message": "Bad Request",
                        "data": {"field": "value"}
                    }
                }
                """;

            // When
            McpModels.ErrorResponse response = objectMapper.readValue(json, McpModels.ErrorResponse.class);

            // Then
            assertEquals("2.0", response.jsonrpc);
            assertEquals("test-id", response.id);
            assertEquals(400, response.error.code);
            assertEquals("Bad Request", response.error.message);
            assertNotNull(response.error.data);
        }
    }

    @Nested
    @DisplayName("Initialize Request Tests")
    class InitializeRequestTests {

        @Test
        @DisplayName("Should serialize complete InitializeRequest")
        void shouldSerializeCompleteInitializeRequest() throws Exception {
            // Given
            var request = createCompleteInitializeRequest();

            // When
            String json = objectMapper.writeValueAsString(request);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("2.0", node.get("jsonrpc").asText());
            assertEquals("initialize", node.get("method").asText());
            assertEquals("1.0", node.get("params").get("protocolVersion").asText());
            assertEquals("TestClient", node.get("params").get("clientInfo").get("name").asText());
            assertEquals("1.0.0", node.get("params").get("clientInfo").get("version").asText());
            assertTrue(node.get("params").get("capabilities").get("roots").get("listChanged").asBoolean());
        }

        @Test
        @DisplayName("Should deserialize InitializeRequest from JSON")
        void shouldDeserializeInitializeRequestFromJson() throws Exception {
            // Given
            String json = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "initialize",
                    "params": {
                        "protocolVersion": "1.0",
                        "capabilities": {
                            "roots": {
                                "listChanged": true
                            }
                        },
                        "clientInfo": {
                            "name": "TestClient",
                            "version": "1.0.0"
                        }
                    }
                }
                """;

            // When
            McpModels.InitializeRequest request = objectMapper.readValue(json, McpModels.InitializeRequest.class);

            // Then
            assertEquals("2.0", request.jsonrpc);
            assertEquals(1, request.id);
            assertEquals("initialize", request.method);
            assertEquals("1.0", request.params.protocolVersion);
            assertEquals("TestClient", request.params.clientInfo.name);
            assertEquals("1.0.0", request.params.clientInfo.version);
            assertTrue(request.params.capabilities.roots.listChanged);
        }

        @Test
        @DisplayName("Should handle optional fields in InitializeRequest")
        void shouldHandleOptionalFieldsInInitializeRequest() throws Exception {
            // Given
            var request = new McpModels.InitializeRequest();
            request.method = "initialize";
            request.params = new McpModels.InitializeRequest.InitializeParams();
            request.params.protocolVersion = "1.0";
            request.params.clientInfo = new McpModels.InitializeRequest.ClientInfo();
            request.params.clientInfo.name = "MinimalClient";
            request.params.clientInfo.version = "1.0";
            request.params.capabilities = new McpModels.InitializeRequest.ClientCapabilities();
            // roots and sampling are null

            // When
            String json = objectMapper.writeValueAsString(request);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("MinimalClient", node.get("params").get("clientInfo").get("name").asText());
            assertNull(node.get("params").get("capabilities").get("roots"));
            assertNull(node.get("params").get("capabilities").get("sampling"));
        }

        private McpModels.InitializeRequest createCompleteInitializeRequest() {
            var request = new McpModels.InitializeRequest();
            request.id = 1;
            request.method = "initialize";

            request.params = new McpModels.InitializeRequest.InitializeParams();
            request.params.protocolVersion = "1.0";

            request.params.capabilities = new McpModels.InitializeRequest.ClientCapabilities();
            request.params.capabilities.roots = new McpModels.InitializeRequest.RootsCapability();
            request.params.capabilities.roots.listChanged = true;

            request.params.clientInfo = new McpModels.InitializeRequest.ClientInfo();
            request.params.clientInfo.name = "TestClient";
            request.params.clientInfo.version = "1.0.0";

            return request;
        }
    }

    @Nested
    @DisplayName("Initialize Response Tests")
    class InitializeResponseTests {

        @Test
        @DisplayName("Should serialize complete InitializeResponse")
        void shouldSerializeCompleteInitializeResponse() throws Exception {
            // Given
            var response = createCompleteInitializeResponse();

            // When
            String json = objectMapper.writeValueAsString(response);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("2.0", node.get("jsonrpc").asText());
            assertEquals("1.0", node.get("result").get("protocolVersion").asText());
            assertEquals("TestServer", node.get("result").get("serverInfo").get("name").asText());
            assertEquals("1.0.0", node.get("result").get("serverInfo").get("version").asText());
            assertTrue(node.get("result").get("capabilities").get("tools").get("listChanged").asBoolean());
            assertTrue(node.get("result").get("capabilities").get("resources").get("subscribe").asBoolean());
            assertTrue(node.get("result").get("capabilities").get("prompts").get("listChanged").asBoolean());
        }

        @Test
        @DisplayName("Should deserialize InitializeResponse from JSON")
        void shouldDeserializeInitializeResponseFromJson() throws Exception {
            // Given
            String json = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "result": {
                        "protocolVersion": "1.0",
                        "capabilities": {
                            "tools": {
                                "listChanged": true
                            },
                            "resources": {
                                "subscribe": true,
                                "listChanged": false
                            }
                        },
                        "serverInfo": {
                            "name": "TestServer",
                            "version": "2.0.0"
                        }
                    }
                }
                """;

            // When
            McpModels.InitializeResponse response = objectMapper.readValue(json, McpModels.InitializeResponse.class);

            // Then
            assertEquals("2.0", response.jsonrpc);
            assertEquals(1, response.id);
            assertEquals("1.0", response.result.protocolVersion);
            assertEquals("TestServer", response.result.serverInfo.name);
            assertEquals("2.0.0", response.result.serverInfo.version);
            assertTrue(response.result.capabilities.tools.listChanged);
            assertTrue(response.result.capabilities.resources.subscribe);
            assertFalse(response.result.capabilities.resources.listChanged);
        }

        private McpModels.InitializeResponse createCompleteInitializeResponse() {
            var response = new McpModels.InitializeResponse();
            response.id = 1;

            response.result = new McpModels.InitializeResponse.InitializeResult();
            response.result.protocolVersion = "1.0";

            response.result.capabilities = new McpModels.InitializeResponse.ServerCapabilities();
            response.result.capabilities.tools = new McpModels.InitializeResponse.ToolsCapability();
            response.result.capabilities.tools.listChanged = true;

            response.result.capabilities.resources = new McpModels.InitializeResponse.ResourcesCapability();
            response.result.capabilities.resources.subscribe = true;
            response.result.capabilities.resources.listChanged = false;

            response.result.capabilities.prompts = new McpModels.InitializeResponse.PromptsCapability();
            response.result.capabilities.prompts.listChanged = true;

            response.result.serverInfo = new McpModels.InitializeResponse.ServerInfo();
            response.result.serverInfo.name = "TestServer";
            response.result.serverInfo.version = "1.0.0";

            return response;
        }
    }

    @Nested
    @DisplayName("List Tools Tests")
    class ListToolsTests {

        @Test
        @DisplayName("Should serialize ListToolsRequest with cursor")
        void shouldSerializeListToolsRequestWithCursor() throws Exception {
            // Given
            var request = new McpModels.ListToolsRequest();
            request.id = "list-tools-1";
            request.method = "tools/list";
            request.params = new McpModels.ListToolsRequest.ListToolsParams();
            request.params.cursor = "next-page-token";

            // When
            String json = objectMapper.writeValueAsString(request);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("2.0", node.get("jsonrpc").asText());
            assertEquals("list-tools-1", node.get("id").asText());
            assertEquals("tools/list", node.get("method").asText());
            assertEquals("next-page-token", node.get("params").get("cursor").asText());
        }

        @Test
        @DisplayName("Should serialize ListToolsRequest without params")
        void shouldSerializeListToolsRequestWithoutParams() throws Exception {
            // Given
            var request = new McpModels.ListToolsRequest();
            request.id = "list-tools-2";
            request.method = "tools/list";
            // params is null

            // When
            String json = objectMapper.writeValueAsString(request);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("list-tools-2", node.get("id").asText());
            assertNull(node.get("params"));
        }

        @Test
        @DisplayName("Should serialize ListToolsResponse with tools")
        void shouldSerializeListToolsResponseWithTools() throws Exception {
            // Given
            var response = createListToolsResponse();

            // When
            String json = objectMapper.writeValueAsString(response);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("2.0", node.get("jsonrpc").asText());
            assertEquals(2, node.get("result").get("tools").size());
            assertEquals("calculator", node.get("result").get("tools").get(0).get("name").asText());
            assertEquals("Performs calculations", node.get("result").get("tools").get(0).get("description").asText());
            assertEquals("file-reader", node.get("result").get("tools").get(1).get("name").asText());
            assertEquals("next-cursor-123", node.get("result").get("nextCursor").asText());
        }

        @Test
        @DisplayName("Should deserialize ListToolsResponse from JSON")
        void shouldDeserializeListToolsResponseFromJson() throws Exception {
            // Given
            String json = """
                {
                    "jsonrpc": "2.0",
                    "id": "tools-list",
                    "result": {
                        "tools": [
                            {
                                "name": "search",
                                "description": "Search the web",
                                "inputSchema": {
                                    "type": "object",
                                    "properties": {
                                        "query": {"type": "string"}
                                    }
                                }
                            }
                        ]
                    }
                }
                """;

            // When
            McpModels.ListToolsResponse response = objectMapper.readValue(json, McpModels.ListToolsResponse.class);

            // Then
            assertEquals("2.0", response.jsonrpc);
            assertEquals("tools-list", response.id);
            assertEquals(1, response.result.tools.size());
            assertEquals("search", response.result.tools.get(0).name);
            assertEquals("Search the web", response.result.tools.get(0).description);
            assertNotNull(response.result.tools.get(0).inputSchema);
        }

        private McpModels.ListToolsResponse createListToolsResponse() {
            var response = new McpModels.ListToolsResponse();
            response.id = "tools-response";

            response.result = new McpModels.ListToolsResponse.ListToolsResult();
            response.result.nextCursor = "next-cursor-123";

            var tool1 = new McpModels.Tool();
            tool1.name = "calculator";
            tool1.description = "Performs calculations";
            tool1.inputSchema = Map.of("type", "object", "properties", Map.of("expression", Map.of("type", "string")));

            var tool2 = new McpModels.Tool();
            tool2.name = "file-reader";
            tool2.inputSchema = Map.of("type", "object");

            response.result.tools = Arrays.asList(tool1, tool2);

            return response;
        }
    }

    @Nested
    @DisplayName("Call Tool Tests")
    class CallToolTests {

        @Test
        @DisplayName("Should serialize CallToolRequest with arguments")
        void shouldSerializeCallToolRequestWithArguments() throws Exception {
            // Given
            var request = new McpModels.CallToolRequest();
            request.id = "call-tool-1";
            request.method = "tools/call";
            request.params = new McpModels.CallToolRequest.CallToolParams();
            request.params.name = "calculator";
            request.params.arguments = Map.of(
                    "expression", "2 + 2",
                    "format", "decimal"
            );

            // When
            String json = objectMapper.writeValueAsString(request);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("2.0", node.get("jsonrpc").asText());
            assertEquals("call-tool-1", node.get("id").asText());
            assertEquals("tools/call", node.get("method").asText());
            assertEquals("calculator", node.get("params").get("name").asText());
            assertEquals("2 + 2", node.get("params").get("arguments").get("expression").asText());
            assertEquals("decimal", node.get("params").get("arguments").get("format").asText());
        }

        @Test
        @DisplayName("Should serialize CallToolRequest without arguments")
        void shouldSerializeCallToolRequestWithoutArguments() throws Exception {
            // Given
            var request = new McpModels.CallToolRequest();
            request.id = "call-tool-2";
            request.method = "tools/call";
            request.params = new McpModels.CallToolRequest.CallToolParams();
            request.params.name = "get-time";
            // arguments is null

            // When
            String json = objectMapper.writeValueAsString(request);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("get-time", node.get("params").get("name").asText());
            assertNull(node.get("params").get("arguments"));
        }

        @Test
        @DisplayName("Should serialize CallToolResponse with content")
        void shouldSerializeCallToolResponseWithContent() throws Exception {
            // Given
            var response = createCallToolResponse();

            // When
            String json = objectMapper.writeValueAsString(response);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("2.0", node.get("jsonrpc").asText());
            assertEquals("call-response", node.get("id").asText());
            assertEquals(2, node.get("result").get("content").size());
            assertEquals("text", node.get("result").get("content").get(0).get("type").asText());
            assertEquals("Result: 4", node.get("result").get("content").get(0).get("text").asText());
            assertEquals("text", node.get("result").get("content").get(1).get("type").asText());
            assertEquals("Additional info", node.get("result").get("content").get(1).get("text").asText());
            assertFalse(node.get("result").get("isError").asBoolean());
        }

        @Test
        @DisplayName("Should serialize CallToolResponse with error")
        void shouldSerializeCallToolResponseWithError() throws Exception {
            // Given
            var response = new McpModels.CallToolResponse();
            response.id = "error-response";
            response.result = new McpModels.CallToolResponse.CallToolResult();
            response.result.isError = true;

            var content = new McpModels.Content();
            content.type = "text";
            content.text = "Error: Invalid expression";
            response.result.content = List.of(content);

            // When
            String json = objectMapper.writeValueAsString(response);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertTrue(node.get("result").get("isError").asBoolean());
            assertEquals("Error: Invalid expression", node.get("result").get("content").get(0).get("text").asText());
        }

        @Test
        @DisplayName("Should deserialize CallToolResponse from JSON")
        void shouldDeserializeCallToolResponseFromJson() throws Exception {
            // Given
            String json = """
                {
                    "jsonrpc": "2.0",
                    "id": "tool-call-123",
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": "Hello, World!"
                            }
                        ],
                        "isError": false
                    }
                }
                """;

            // When
            McpModels.CallToolResponse response = objectMapper.readValue(json, McpModels.CallToolResponse.class);

            // Then
            assertEquals("2.0", response.jsonrpc);
            assertEquals("tool-call-123", response.id);
            assertEquals(1, response.result.content.size());
            assertEquals("text", response.result.content.get(0).type);
            assertEquals("Hello, World!", response.result.content.get(0).text);
            assertFalse(response.result.isError);
        }

        private McpModels.CallToolResponse createCallToolResponse() {
            var response = new McpModels.CallToolResponse();
            response.id = "call-response";

            response.result = new McpModels.CallToolResponse.CallToolResult();
            response.result.isError = false;

            var content1 = new McpModels.Content();
            content1.type = "text";
            content1.text = "Result: 4";

            var content2 = new McpModels.Content();
            content2.type = "text";
            content2.text = "Additional info";

            response.result.content = Arrays.asList(content1, content2);

            return response;
        }
    }

    @Nested
    @DisplayName("Tool Tests")
    class ToolTests {

        @Test
        @DisplayName("Should serialize Tool with all fields")
        void shouldSerializeToolWithAllFields() throws Exception {
            // Given
            var tool = new McpModels.Tool();
            tool.name = "weather-tool";
            tool.description = "Gets weather information";
            tool.inputSchema = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "location", Map.of("type", "string"),
                            "units", Map.of("type", "string", "enum", Arrays.asList("celsius", "fahrenheit"))
                    ),
                    "required", Arrays.asList("location")
            );

            // When
            String json = objectMapper.writeValueAsString(tool);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("weather-tool", node.get("name").asText());
            assertEquals("Gets weather information", node.get("description").asText());
            assertEquals("object", node.get("inputSchema").get("type").asText());
            assertTrue(node.get("inputSchema").get("properties").has("location"));
            assertTrue(node.get("inputSchema").get("properties").has("units"));
        }

        @Test
        @DisplayName("Should serialize Tool without description")
        void shouldSerializeToolWithoutDescription() throws Exception {
            // Given
            var tool = new McpModels.Tool();
            tool.name = "simple-tool";
            tool.inputSchema = Map.of("type", "object");
            // description is null

            // When
            String json = objectMapper.writeValueAsString(tool);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("simple-tool", node.get("name").asText());
            assertNull(node.get("description"));
            assertNotNull(node.get("inputSchema"));
        }
    }

    @Nested
    @DisplayName("Content Tests")
    class ContentTests {

        @Test
        @DisplayName("Should serialize Content with text")
        void shouldSerializeContentWithText() throws Exception {
            // Given
            var content = new McpModels.Content();
            content.type = "text";
            content.text = "Sample content text";

            // When
            String json = objectMapper.writeValueAsString(content);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("text", node.get("type").asText());
            assertEquals("Sample content text", node.get("text").asText());
        }

        @Test
        @DisplayName("Should serialize Content without text")
        void shouldSerializeContentWithoutText() throws Exception {
            // Given
            var content = new McpModels.Content();
            content.type = "image";
            // text is null

            // When
            String json = objectMapper.writeValueAsString(content);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("image", node.get("type").asText());
            assertNull(node.get("text"));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validation Tests")
    class EdgeCasesTests {

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "null"})
        @DisplayName("Should handle empty and whitespace strings")
        void shouldHandleEmptyStrings(String value) throws Exception {
            // Given
            var content = new McpModels.Content();
            content.type = "text";
            content.text = value.equals("null") ? null : value;

            // When
            String json = objectMapper.writeValueAsString(content);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("text", node.get("type").asText());
            if (value.equals("null")) {
                assertNull(node.get("text"));
            } else {
                assertEquals(value, node.get("text").asText());
            }
        }

        @Test
        @DisplayName("Should handle complex nested input schema")
        void shouldHandleComplexNestedInputSchema() throws Exception {
            // Given
            Map<String, Object> complexSchema = new HashMap<>();
            complexSchema.put("type", "object");
            complexSchema.put("properties", Map.of(
                    "user", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "name", Map.of("type", "string"),
                                    "age", Map.of("type", "integer", "minimum", 0)
                            )
                    ),
                    "preferences", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string")
                    )
            ));

            var tool = new McpModels.Tool();
            tool.name = "complex-tool";
            tool.inputSchema = complexSchema;

            // When
            String json = objectMapper.writeValueAsString(tool);
            JsonNode node = objectMapper.readTree(json);

            // Then
            assertEquals("complex-tool", node.get("name").asText());
            assertEquals("object", node.get("inputSchema").get("type").asText());
            assertTrue(node.get("inputSchema").get("properties").has("user"));
            assertTrue(node.get("inputSchema").get("properties").has("preferences"));
            assertEquals("array", node.get("inputSchema").get("properties").get("preferences").get("type").asText());
        }

        @Test
        @DisplayName("Should round-trip serialize and deserialize")
        void shouldRoundTripSerializeAndDeserialize() throws Exception {
            // Given
            var originalRequest = new McpModels.CallToolRequest();
            originalRequest.id = "round-trip-test";
            originalRequest.method = "tools/call";
            originalRequest.params = new McpModels.CallToolRequest.CallToolParams();
            originalRequest.params.name = "test-tool";
            originalRequest.params.arguments = Map.of("key", "value", "number", 42);

            // When - serialize then deserialize
            String json = objectMapper.writeValueAsString(originalRequest);
            McpModels.CallToolRequest deserializedRequest = objectMapper.readValue(json, McpModels.CallToolRequest.class);

            // Then
            assertEquals(originalRequest.id, deserializedRequest.id);
            assertEquals(originalRequest.method, deserializedRequest.method);
            assertEquals(originalRequest.params.name, deserializedRequest.params.name);
            assertEquals(originalRequest.params.arguments.get("key"), deserializedRequest.params.arguments.get("key"));
            assertEquals(originalRequest.params.arguments.get("number"), deserializedRequest.params.arguments.get("number"));
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() {
            // Given
            String malformedJson = """
                {
                    "jsonrpc": "2.0",
                    "id": "test",
                    "method": "initialize"
                    // missing comma and params
                }
                """;

            // When & Then
            assertThrows(Exception.class, () -> {
                objectMapper.readValue(malformedJson, McpModels.InitializeRequest.class);
            });
        }

        @Test
        @DisplayName("Should handle null values in required fields")
        void shouldHandleNullValuesInRequiredFields() throws Exception {
            // Given
            String jsonWithNulls = """
                {
                    "jsonrpc": "2.0",
                    "id": null,
                    "method": null
                }
                """;

            // When
            McpModels.InitializeRequest request = objectMapper.readValue(jsonWithNulls, McpModels.InitializeRequest.class);

            // Then
            assertEquals("2.0", request.jsonrpc);
            assertNull(request.id);
            assertNull(request.method);
        }

        @Test
        @DisplayName("Should handle large input schemas")
        void shouldHandleLargeInputSchemas() throws Exception {
            // Given
            Map<String, Object> largeSchema = new HashMap<>();
            largeSchema.put("type", "object");

            Map<String, Object> properties = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                properties.put("field" + i, Map.of("type", "string", "description", "Field " + i));
            }
            largeSchema.put("properties", properties);

            var tool = new McpModels.Tool();
            tool.name = "large-schema-tool";
            tool.inputSchema = largeSchema;

            // When
            String json = objectMapper.writeValueAsString(tool);
            McpModels.Tool deserializedTool = objectMapper.readValue(json, McpModels.Tool.class);

            // Then
            assertEquals("large-schema-tool", deserializedTool.name);
            assertEquals(100, ((Map<?, ?>) deserializedTool.inputSchema.get("properties")).size());
        }

        @Test
        @DisplayName("Should validate JSON property annotations")
        void shouldValidateJsonPropertyAnnotations() throws Exception {
            // Given
            var response = new McpModels.InitializeResponse();
            response.id = "annotation-test";
            response.result = new McpModels.InitializeResponse.InitializeResult();
            response.result.protocolVersion = "1.0";
            response.result.serverInfo = new McpModels.InitializeResponse.ServerInfo();
            response.result.serverInfo.name = "TestServer";
            response.result.serverInfo.version = "1.0.0";
            response.result.capabilities = new McpModels.InitializeResponse.ServerCapabilities();

            // When
            String json = objectMapper.writeValueAsString(response);
            JsonNode node = objectMapper.readTree(json);

            // Then - verify all JSON property names are correctly mapped
            assertTrue(node.has("jsonrpc"));
            assertTrue(node.has("id"));
            assertTrue(node.has("result"));
            assertTrue(node.get("result").has("protocolVersion"));
            assertTrue(node.get("result").has("serverInfo"));
            assertTrue(node.get("result").has("capabilities"));
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should serialize large responses efficiently")
        void shouldSerializeLargeResponsesEfficiently() throws Exception {
            // Given
            var response = new McpModels.ListToolsResponse();
            response.id = "performance-test";
            response.result = new McpModels.ListToolsResponse.ListToolsResult();
            response.result.tools = createLargeToolList(1000);

            // When
            long startTime = System.currentTimeMillis();
            String json = objectMapper.writeValueAsString(response);
            long serializationTime = System.currentTimeMillis() - startTime;

            startTime = System.currentTimeMillis();
            McpModels.ListToolsResponse deserializedResponse = objectMapper.readValue(json, McpModels.ListToolsResponse.class);
            long deserializationTime = System.currentTimeMillis() - startTime;

            // Then
            assertNotNull(json);
            assertEquals(1000, deserializedResponse.result.tools.size());

            // Performance assertions (these are rough guidelines)
            assertTrue(serializationTime < 1000, "Serialization should complete within 1 second");
            assertTrue(deserializationTime < 1000, "Deserialization should complete within 1 second");
        }

        private List<McpModels.Tool> createLargeToolList(int count) {
            return java.util.stream.IntStream.range(0, count)
                    .mapToObj(i -> {
                        var tool = new McpModels.Tool();
                        tool.name = "tool-" + i;
                        tool.description = "Description for tool " + i;
                        tool.inputSchema = Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "param1", Map.of("type", "string"),
                                        "param2", Map.of("type", "integer")
                                )
                        );
                        return tool;
                    })
                    .toList();
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle complete MCP conversation flow")
        void shouldHandleCompleteMcpConversationFlow() throws Exception {
            // Given - Initialize Request
            var initRequest = new McpModels.InitializeRequest();
            initRequest.id = 1;
            initRequest.method = "initialize";
            initRequest.params = new McpModels.InitializeRequest.InitializeParams();
            initRequest.params.protocolVersion = "1.0";
            initRequest.params.clientInfo = new McpModels.InitializeRequest.ClientInfo();
            initRequest.params.clientInfo.name = "TestClient";
            initRequest.params.clientInfo.version = "1.0.0";

            // When - Serialize init request
            String initRequestJson = objectMapper.writeValueAsString(initRequest);

            // Then - Deserialize and create response
            McpModels.InitializeRequest deserializedInitRequest = objectMapper.readValue(initRequestJson, McpModels.InitializeRequest.class);
            assertEquals("TestClient", deserializedInitRequest.params.clientInfo.name);

            // Given - Initialize Response
            var initResponse = new McpModels.InitializeResponse();
            initResponse.id = deserializedInitRequest.id;
            initResponse.result = new McpModels.InitializeResponse.InitializeResult();
            initResponse.result.protocolVersion = "1.0";
            initResponse.result.serverInfo = new McpModels.InitializeResponse.ServerInfo();
            initResponse.result.serverInfo.name = "TestServer";
            initResponse.result.serverInfo.version = "1.0.0";

            // When - Serialize init response
            String initResponseJson = objectMapper.writeValueAsString(initResponse);

            // Then - Verify response
            McpModels.InitializeResponse deserializedInitResponse = objectMapper.readValue(initResponseJson, McpModels.InitializeResponse.class);
            assertEquals("TestServer", deserializedInitResponse.result.serverInfo.name);

            // Given - List Tools Request
            var listToolsRequest = new McpModels.ListToolsRequest();
            listToolsRequest.id = 2;
            listToolsRequest.method = "tools/list";

            // When - Serialize tools request
            String toolsRequestJson = objectMapper.writeValueAsString(listToolsRequest);

            // Given - List Tools Response
            var listToolsResponse = new McpModels.ListToolsResponse();
            listToolsResponse.id = 2;
            listToolsResponse.result = new McpModels.ListToolsResponse.ListToolsResult();

            var tool = new McpModels.Tool();
            tool.name = "calculator";
            tool.description = "Performs mathematical calculations";
            tool.inputSchema = Map.of("type", "object", "properties", Map.of("expression", Map.of("type", "string")));
            listToolsResponse.result.tools = List.of(tool);

            // When - Serialize tools response
            String toolsResponseJson = objectMapper.writeValueAsString(listToolsResponse);

            // Then - Verify tools response
            McpModels.ListToolsResponse deserializedToolsResponse = objectMapper.readValue(toolsResponseJson, McpModels.ListToolsResponse.class);
            assertEquals(1, deserializedToolsResponse.result.tools.size());
            assertEquals("calculator", deserializedToolsResponse.result.tools.get(0).name);
        }

        @Test
        @DisplayName("Should handle error scenarios in conversation flow")
        void shouldHandleErrorScenariosInConversationFlow() throws Exception {
            // Given - Call Tool Request with invalid tool
            var callToolRequest = new McpModels.CallToolRequest();
            callToolRequest.id = "invalid-tool-call";
            callToolRequest.method = "tools/call";
            callToolRequest.params = new McpModels.CallToolRequest.CallToolParams();
            callToolRequest.params.name = "non-existent-tool";

            // When - Serialize request
            String requestJson = objectMapper.writeValueAsString(callToolRequest);

            // Given - Error Response
            var errorResponse = new McpModels.ErrorResponse();
            errorResponse.id = "invalid-tool-call";
            errorResponse.error = new McpModels.ErrorResponse.Error();
            errorResponse.error.code = -32601;
            errorResponse.error.message = "Method not found";
            errorResponse.error.data = Map.of("tool", "non-existent-tool");

            // When - Serialize error response
            String errorJson = objectMapper.writeValueAsString(errorResponse);

            // Then - Verify error response
            McpModels.ErrorResponse deserializedError = objectMapper.readValue(errorJson, McpModels.ErrorResponse.class);
            assertEquals(-32601, deserializedError.error.code);
            assertEquals("Method not found", deserializedError.error.message);
            assertNotNull(deserializedError.error.data);
        }
    }

    @Nested
    @DisplayName("Boundary Value Tests")
    class BoundaryValueTests {

        @Test
        @DisplayName("Should handle very long strings")
        void shouldHandleVeryLongStrings() throws Exception {
            // Given
            String veryLongString = "x".repeat(10000);
            var content = new McpModels.Content();
            content.type = "text";
            content.text = veryLongString;

            // When
            String json = objectMapper.writeValueAsString(content);
            McpModels.Content deserializedContent = objectMapper.readValue(json, McpModels.Content.class);

            // Then
            assertEquals(veryLongString, deserializedContent.text);
            assertEquals(10000, deserializedContent.text.length());
        }

        @Test
        @DisplayName("Should handle special characters and Unicode")
        void shouldHandleSpecialCharactersAndUnicode() throws Exception {
            // Given
            String specialString = "Hello 世界! 🌍 Special chars: @#$%^&*(){}[]|\\:;\"'<>,.?/~`";
            var tool = new McpModels.Tool();
            tool.name = "unicode-tool";
            tool.description = specialString;
            tool.inputSchema = Map.of("type", "object");

            // When
            String json = objectMapper.writeValueAsString(tool);
            McpModels.Tool deserializedTool = objectMapper.readValue(json, McpModels.Tool.class);

            // Then
            assertEquals(specialString, deserializedTool.description);
        }

        @Test
        @DisplayName("Should handle deeply nested input schemas")
        void shouldHandleDeeplyNestedInputSchemas() throws Exception {
            // Given - Create a deeply nested schema (5 levels deep)
            Map<String, Object> level5 = Map.of("type", "string");
            Map<String, Object> level4 = Map.of("type", "object", "properties", Map.of("level5", level5));
            Map<String, Object> level3 = Map.of("type", "object", "properties", Map.of("level4", level4));
            Map<String, Object> level2 = Map.of("type", "object", "properties", Map.of("level3", level3));
            Map<String, Object> level1 = Map.of("type", "object", "properties", Map.of("level2", level2));

            var tool = new McpModels.Tool();
            tool.name = "deeply-nested-tool";
            tool.inputSchema = level1;

            // When
            String json = objectMapper.writeValueAsString(tool);
            McpModels.Tool deserializedTool = objectMapper.readValue(json, McpModels.Tool.class);

            // Then
            assertNotNull(deserializedTool.inputSchema);
            assertEquals("object", deserializedTool.inputSchema.get("type"));

            // Navigate down the nested structure
            Map<?, ?> properties = (Map<?, ?>) deserializedTool.inputSchema.get("properties");
            Map<?, ?> level2Map = (Map<?, ?>) properties.get("level2");
            assertEquals("object", level2Map.get("type"));
        }

        @Test
        @DisplayName("Should handle maximum integer values")
        void shouldHandleMaximumIntegerValues() throws Exception {
            // Given
            var error = new McpModels.ErrorResponse.Error();
            error.code = Integer.MAX_VALUE;
            error.message = "Maximum integer error code";

            var errorResponse = new McpModels.ErrorResponse();
            errorResponse.id = "max-int-test";
            errorResponse.error = error;

            // When
            String json = objectMapper.writeValueAsString(errorResponse);
            McpModels.ErrorResponse deserializedResponse = objectMapper.readValue(json, McpModels.ErrorResponse.class);

            // Then
            assertEquals(Integer.MAX_VALUE, deserializedResponse.error.code);
        }
    }
}
