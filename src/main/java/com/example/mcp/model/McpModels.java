package com.example.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * MCP Protocol Models
 * Based on the Model Context Protocol specification
 */
public class McpModels {

    // Base classes
    public static abstract class BaseRequest {
        @JsonProperty("jsonrpc")
        public String jsonrpc = "2.0";

        @JsonProperty("id")
        public Object id;

        @JsonProperty("method")
        public String method;
    }

    public static abstract class BaseResponse {
        @JsonProperty("jsonrpc")
        public String jsonrpc = "2.0";

        @JsonProperty("id")
        public Object id;
    }

    public static class ErrorResponse extends BaseResponse {
        @JsonProperty("error")
        public Error error;

        public static class Error {
            @JsonProperty("code")
            public int code;

            @JsonProperty("message")
            public String message;

            @JsonProperty("data")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public Object data;
        }
    }

    // Initialize Request/Response
    public static class InitializeRequest extends BaseRequest {
        @JsonProperty("params")
        public InitializeParams params;

        public static class InitializeParams {
            @JsonProperty("protocolVersion")
            public String protocolVersion;

            @JsonProperty("capabilities")
            public ClientCapabilities capabilities;

            @JsonProperty("clientInfo")
            public ClientInfo clientInfo;
        }

        public static class ClientCapabilities {
            @JsonProperty("roots")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public RootsCapability roots;

            @JsonProperty("sampling")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public Object sampling;
        }

        public static class RootsCapability {
            @JsonProperty("listChanged")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public Boolean listChanged;
        }

        public static class ClientInfo {
            @JsonProperty("name")
            public String name;

            @JsonProperty("version")
            public String version;
        }
    }

    public static class InitializeResponse extends BaseResponse {
        @JsonProperty("result")
        public InitializeResult result;

        public static class InitializeResult {
            @JsonProperty("protocolVersion")
            public String protocolVersion;

            @JsonProperty("capabilities")
            public ServerCapabilities capabilities;

            @JsonProperty("serverInfo")
            public ServerInfo serverInfo;
        }

        public static class ServerCapabilities {
            @JsonProperty("tools")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public ToolsCapability tools;

            @JsonProperty("resources")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public ResourcesCapability resources;

            @JsonProperty("prompts")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public PromptsCapability prompts;
        }

        public static class ToolsCapability {
            @JsonProperty("listChanged")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public Boolean listChanged;
        }

        public static class ResourcesCapability {
            @JsonProperty("subscribe")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public Boolean subscribe;

            @JsonProperty("listChanged")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public Boolean listChanged;
        }

        public static class PromptsCapability {
            @JsonProperty("listChanged")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public Boolean listChanged;
        }

        public static class ServerInfo {
            @JsonProperty("name")
            public String name;

            @JsonProperty("version")
            public String version;
        }
    }

    // Tools List Request/Response
    public static class ListToolsRequest extends BaseRequest {
        @JsonProperty("params")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public ListToolsParams params;

        public static class ListToolsParams {
            @JsonProperty("cursor")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public String cursor;
        }
    }

    public static class ListToolsResponse extends BaseResponse {
        @JsonProperty("result")
        public ListToolsResult result;

        public static class ListToolsResult {
            @JsonProperty("tools")
            public List<Tool> tools;

            @JsonProperty("nextCursor")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public String nextCursor;
        }
    }

    public static class Tool {
        @JsonProperty("name")
        public String name;

        @JsonProperty("description")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String description;

        @JsonProperty("inputSchema")
        public Map<String, Object> inputSchema;
    }

    // Tool Call Request/Response
    public static class CallToolRequest extends BaseRequest {
        @JsonProperty("params")
        public CallToolParams params;

        public static class CallToolParams {
            @JsonProperty("name")
            public String name;

            @JsonProperty("arguments")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public Map<String, Object> arguments;
        }
    }

    public static class CallToolResponse extends BaseResponse {
        @JsonProperty("result")
        public CallToolResult result;

        public static class CallToolResult {
            @JsonProperty("content")
            public List<Content> content;

            @JsonProperty("isError")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public Boolean isError;
        }
    }

    public static class Content {
        @JsonProperty("type")
        public String type;

        @JsonProperty("text")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String text;
    }
}