# Enhanced MCP Hello World Server - Java Implementation

A production-ready Model Context Protocol (MCP) server implementation with robust error handling, tool registry system, and comprehensive monitoring.

## Project Structure

```
mcp-hello-world-server/
├── pom.xml
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── example/
│                   └── mcp/
│                       ├── McpServer.java
│                       ├── exception/
│                       │   ├── McpException.java
│                       │   ├── ToolNotFoundException.java
│                       │   ├── ToolExecutionException.java
│                       │   ├── InvalidParametersException.java
│                       │   └── ProtocolException.java
│                       ├── handler/
│                       │   └── ErrorHandler.java
│                       ├── model/
│                       │   └── McpModels.java
│                       ├── registry/
│                       │   └── ToolRegistry.java
│                       └── tool/
│                           ├── McpTool.java
│                           └── builtin/
│                               ├── HelloTool.java
│                               ├── CurrentTimeTool.java
│                               ├── EchoTool.java
│                               ├── MathTool.java
│                               └── RandomTool.java
└── README.md
```

## Prerequisites

- Java Development Kit (JDK) 17 or higher
- Apache Maven 3.6 or higher

## Quick Start

1. **Create and build the project:**
   ```bash
   mkdir mcp-hello-world-server
   cd mcp-hello-world-server
   # Copy all files to their respective locations
   mvn clean compile
   ```

2. **Run the server:**
   ```bash
   mvn exec:java
   # Or with custom port
   mvn exec:java -Dexec.args="9000"
   ```

3. **Test the server:**
   ```bash
   curl http://localhost:8080/health
   ```

## 🚀 Enhanced Features

### Robust Architecture
- **Tool Registry System**: Thread-safe tool management with concurrent execution
- **Exception Hierarchy**: Comprehensive error handling with proper MCP error codes
- **Request Validation**: JSON-RPC protocol compliance and input sanitization
- **Centralized Error Handling**: Consistent error responses across all endpoints

### Built-in Tools (5 Tools)
1. **hello** - Multi-language greetings (English, Spanish, French, German)
2. **current_time** - Server time in multiple formats (ISO, readable, timestamp)
3. **echo** - Message echoing with transformations (uppercase, lowercase, reverse)
4. **math** - Calculator (add, subtract, multiply, divide, power, sqrt, abs)
5. **random** - Random number generator (integers, decimals, booleans)

### Production Features
- **Health Monitoring**: Detailed status endpoint with uptime tracking
- **Debug Mode**: Enhanced error information for development
- **Graceful Shutdown**: Proper resource cleanup and connection handling
- **Configurable Timeouts**: Tool execution limits and thread pool management
- **Comprehensive Logging**: Structured logging with appropriate levels

## 📊 Monitoring Endpoints

### Health Check
```bash
curl http://localhost:8080/health
```
Returns server status, uptime, tool count, and initialization state.

### Server Information
```bash
curl http://localhost:8080/
```
Comprehensive server details including features and available tools.

### Debug Tools (Development)
```bash
export MCP_DEBUG=true
curl http://localhost:8080/tools
```
Lists all registered tools with their schemas (debug mode only).

## 🔧 Configuration

### Environment Variables
```bash
export PORT=8080                    # Server port
export MCP_DEBUG=true              # Enable debug mode
export MCP_TOOL_TIMEOUT=30         # Tool execution timeout (seconds)
export MCP_TOOL_THREADS=10         # Tool execution thread pool size
```

### System Properties
```bash
java -jar server.jar \
  -Dserver.port=9000 \
  -Dmcp.debug=true \
  -Dmcp.tool.timeout=60
```

## 🧪 Advanced Testing Examples

### Error Handling Tests
```bash
# Test invalid method
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"invalid"}'

# Test missing parameters
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo"}}'
```

### Math Tool Examples
```bash
# Complex calculation
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "math-power",
    "method": "tools/call",
    "params": {
      "name": "math",
      "arguments": {"operation": "power", "a": 2, "b": 10}
    }
  }'
```

### Random Generation
```bash
# Generate multiple random numbers
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "random-multi",
    "method": "tools/call",
    "params": {
      "name": "random",
      "arguments": {"type": "integer", "min": 1, "max": 100, "count": 5}
    }
  }'
```

## 🛠️ Extending the Server

### Adding Custom Tools

1. **Implement McpTool interface:**
```java
public class CustomTool implements McpTool {
    @Override
    public String getName() { return "custom"; }
    
    @Override
    public String getDescription() { return "Custom tool"; }
    
    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }
    
    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> args) {
        return createTextResult("Custom result");
    }
}
```

2. **Register the tool:**
```java
// In McpServer.registerBuiltinTools()
toolRegistry.registerTool(new CustomTool());
```

### Tool Registry Features
- **Thread Safety**: Concurrent tool registration and execution
- **Validation**: Input schema validation and parameter checking
- **Timeout Management**: Configurable execution timeouts
- **Error Isolation**: Tool failures don't affect other tools
- **Lifecycle Management**: Proper resource cleanup

## 🔒 Security & Production

### Input Validation
- JSON Schema validation for all tool inputs
- String length limits and numeric range checking
- Required parameter validation
- Request size limits (10MB default)

### Error Handling
- Sanitized error messages in production
- Detailed stack traces only in debug mode
- Proper HTTP status codes
- JSON-RPC compliant error responses

### Resource Management
- Bounded thread pools for tool execution
- Configurable timeouts prevent resource exhaustion
- Graceful shutdown with connection draining
- Memory-efficient request processing

## 📈 Performance

### Optimization Features
- Concurrent tool execution with thread pooling
- Non-blocking request processing
- Efficient JSON serialization
- Connection pooling and reuse

### Monitoring
- Built-in health checks with detailed metrics
- Execution timing and error rate tracking
- Resource usage monitoring
- Uptime and availability metrics

This enhanced MCP server provides a solid foundation for building production-ready Model Context Protocol implementations with enterprise-grade reliability, monitoring, and extensibility.
