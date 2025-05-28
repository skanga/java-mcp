# Enhanced MCP Server - Testing and Usage Guide

## 🚀 New Features

### Robust Error Handling
- Comprehensive exception hierarchy with specific error codes
- Centralized error handling with appropriate HTTP status codes
- Debug mode for detailed error information
- Request validation and sanitization

### Tool Registry System
- Thread-safe tool registration and management
- Concurrent tool execution with configurable timeouts
- Input validation against JSON schemas
- Tool lifecycle management

### Enhanced Tools (15 Total)

#### Original Basic Tools (5)
- **Hello Tool**: Multi-language greetings with input validation
- **Math Tool**: Basic calculator with proper error handling
- **Random Tool**: Generate random numbers, decimals, and booleans
- **Echo Tool**: Message echoing with transformations
- **Current Time Tool**: Multiple time format support

#### Filesystem Tools (4) - Ported from clojure-mcp
- **Directory Tree Tool**: Display directory structure with filtering
- **File Read Tool**: Read file contents with encoding and range support
- **File Write Tool**: Write, append, insert, and replace file content
- **File Search Tool**: Search text patterns in files with regex support

#### System & Development Tools (2)
- **Process Executor Tool**: Execute system commands safely
- **Git Operations Tool**: Common git operations with formatted output

#### AI Agent Tools (4) - Ported from clojure-mcp with System Prompts
- **Architect Tool**: AI-powered system architecture analysis and design
- **Code Critique Tool**: AI code review and analysis with best practices
- **Dispatch Agent Tool**: Intelligent task orchestration and multi-agent coordination
- **Think Tool**: AI reasoning and problem-solving for complex questions

### Production Features
- Health monitoring with uptime tracking
- Graceful shutdown handling
- Configurable thread pools and timeouts
- Comprehensive logging
- Debug endpoints for development

## 🧪 Testing the Enhanced Server

### 1. Health Check with Detailed Status
```bash
curl http://localhost:8080/health
```

### 2. List All Available Tools (Now 15!)
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "tools-list",
    "method": "tools/list"
  }'
```

### 3. AI Agent Tools Testing

**Note**: AI agent tools require API keys. Set one or more:
```bash
export ANTHROPIC_API_KEY="your-anthropic-api-key"
export OPENAI_API_KEY="your-openai-api-key"  
export GEMINI_API_KEY="your-gemini-api-key"
```

#### Architect Tool - System Architecture Analysis
```bash
# Architecture design request
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "architect-1",
    "method": "tools/call",
    "params": {
      "name": "architect",
      "arguments": {
        "task": "Design a scalable microservices architecture for an e-commerce platform handling 1M+ users",
        "context": "Need to handle high traffic, ensure data consistency, and support multiple payment methods",
        "system_type": "microservices",
        "scale": "internet_scale",
        "preferred_technologies": ["Java", "Spring Boot", "PostgreSQL", "Redis", "Kubernetes"],
        "constraints": ["PCI compliance required", "99.9% uptime SLA", "sub-200ms response times"],
        "detail_level": "comprehensive"
      }
    }
  }'

# Technology selection guidance
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "architect-2",
    "method": "tools/call",
    "params": {
      "name": "architect",
      "arguments": {
        "task": "Recommend the best database architecture for a real-time analytics platform",
        "system_type": "data_platform",
        "scale": "enterprise",
        "constraints": ["Real-time processing required", "Petabyte scale data", "Multi-region deployment"]
      }
    }
  }'
```

#### Code Critique Tool - AI Code Review
```bash
# Java code review
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "critique-1",
    "method": "tools/call",
    "params": {
      "name": "code_critique",
      "arguments": {
        "code": "public class UserService {\n    private List<User> users = new ArrayList<>();\n    \n    public User findUser(String id) {\n        for (User user : users) {\n            if (user.getId().equals(id)) {\n                return user;\n            }\n        }\n        return null;\n    }\n    \n    public void addUser(User user) {\n        users.add(user);\n    }\n}",
        "language": "java",
        "context": "Service class for managing users in a web application",
        "focus_areas": ["performance", "security", "best_practices"],
        "review_level": "thorough",
        "check_security": true
      }
    }
  }'

# Python code security review
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "critique-2",
    "method": "tools/call",
    "params": {
      "name": "code_critique",
      "arguments": {
        "code": "import os\nimport subprocess\n\ndef execute_command(user_input):\n    command = f\"ls {user_input}\"\n    result = subprocess.run(command, shell=True, capture_output=True, text=True)\n    return result.stdout",
        "language": "python",
        "focus_areas": ["security"],
        "review_level": "quick",
        "check_security": true
      }
    }
  }'
```

#### Dispatch Agent Tool - Task Orchestration
```bash
# Complex project planning
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "dispatch-1",
    "method": "tools/call",
    "params": {
      "name": "dispatch_agent",
      "arguments": {
        "request": "Migrate a legacy monolithic Java application to modern microservices architecture while maintaining zero downtime",
        "domain": "software_development",
        "priority": "high",
        "timeline": "months",
        "team_size": "large_team",
        "complexity": "enterprise",
        "resources": ["Existing Java 8 monolith", "PostgreSQL database", "Kubernetes cluster", "CI/CD pipeline"],
        "include_alternatives": true,
        "detail_level": "comprehensive"
      }
    }
  }'

# Data analysis project
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "dispatch-2",
    "method": "tools/call",
    "params": {
      "name": "dispatch_agent",
      "arguments": {
        "request": "Build a real-time customer behavior analytics dashboard with machine learning insights",
        "domain": "data_analysis",
        "priority": "medium",
        "timeline": "weeks",
        "team_size": "small_team",
        "complexity": "complex",
        "resources": ["Customer transaction data", "Web analytics", "Python/Pandas", "AWS cloud"]
      }
    }
  }'
```

#### Think Tool - Deep Reasoning and Analysis
```bash
# Strategic business analysis
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "think-1",
    "method": "tools/call",
    "params": {
      "name": "think",
      "arguments": {
        "question": "Should our company adopt a microservices architecture, and what are the long-term implications?",
        "context": "Currently using a monolithic Java application serving 500K users, team of 20 developers, growing 50% annually",
        "thinking_style": "strategic",
        "depth": "deep",
        "perspectives": ["technical", "business", "operational", "financial"],
        "time_horizon": "long_term",
        "include_alternatives": true,
        "practical_focus": true
      }
    }
  }'

# Technical problem solving
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "think-2",
    "method": "tools/call",
    "params": {
      "name": "think",
      "arguments": {
        "question": "How can we reduce database query response times from 500ms to under 100ms?",
        "context": "PostgreSQL database with 10M+ records, complex joins, current indexes may not be optimal",
        "thinking_style": "analytical",
        "depth": "deep",
        "show_reasoning": true,
        "practical_focus": true
      }
    }
  }'

# Philosophical/ethical analysis
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "think-3",
    "method": "tools/call",
    "params": {
      "name": "think",
      "arguments": {
        "question": "What are the ethical implications of using AI for automated hiring decisions?",
        "thinking_style": "philosophical",
        "depth": "deep",
        "perspectives": ["ethical", "legal", "social", "technical"],
        "include_alternatives": true,
        "time_horizon": "long_term"
      }
    }
  }'
```

### 4. Filesystem Tools Testing

#### Directory Tree Tool
```bash
# Basic directory tree
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "dir-tree-1",
    "method": "tools/call",
    "params": {
      "name": "directory_tree",
      "arguments": {
        "path": ".",
        "max_depth": 3,
        "include_files": true,
        "show_hidden": false
      }
    }
  }'
```

#### File Search Tool
```bash
# Search for TODO comments
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "search-1",
    "method": "tools/call",
    "params": {
      "name": "search_files",
      "arguments": {
        "pattern": "TODO|FIXME|XXX",
        "path": "src",
        "regex": true,
        "case_sensitive": false,
        "file_pattern": "*.java",
        "context_lines": 2
      }
    }
  }'
```

### 5. Git Operations
```bash
# Git status with formatted output
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "git-1",
    "method": "tools/call",
    "params": {
      "name": "git_operations",
      "arguments": {
        "operation": "status"
      }
    }
  }'
```

### 6. Error Handling Tests

#### AI Tool Without API Key
```bash
# This will show graceful error handling
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "ai-error-1",
    "method": "tools/call",
    "params": {
      "name": "think",
      "arguments": {
        "question": "Test question"
      }
    }
  }'
```

## 🔧 Advanced Configuration

### Environment Variables
```bash
# Server configuration
export PORT=9000
export MCP_DEBUG=true
export MCP_TOOL_TIMEOUT=120
export MCP_TOOL_THREADS=30

# AI API Keys (set at least one)
export ANTHROPIC_API_KEY="your-anthropic-key"
export OPENAI_API_KEY="your-openai-key"
export GEMINI_API_KEY="your-gemini-key"

# Security settings
export MCP_ALLOW_UNSAFE_COMMANDS=false
export MCP_MAX_FILE_SIZE=52428800  # 50MB

# AI tool settings
export MCP_AI_DEFAULT_PROVIDER="anthropic"
export MCP_AI_MAX_TOKENS=8000
```

## 🎯 **AI Agent Tools Capabilities**

### **Architect Tool**
- System architecture design and analysis
- Technology stack recommendations
- Scalability and performance planning
- Security architecture guidance
- Cloud architecture design
- Migration strategy planning

### **Code Critique Tool**
- Comprehensive code quality analysis
- Security vulnerability detection
- Performance optimization suggestions
- Best practices validation
- Architecture pattern recognition
- Test coverage recommendations

### **Dispatch Agent Tool**
- Complex task breakdown and planning
- Multi-step workflow orchestration
- Resource allocation and optimization
- Risk assessment and mitigation
- Timeline and effort estimation
- Quality checkpoint definition

### **Think Tool**
- Deep reasoning and analysis
- Strategic decision support
- Problem decomposition and solving
- Multi-perspective analysis
- Critical thinking and evaluation
- Philosophical and ethical reasoning

## 📊 **Complete Tool Summary (15 Tools)**

### **Basic Utilities (5)**
- `hello` - Multi-language greetings
- `math` - Calculator operations
- `random` - Number generation
- `echo` - Message transformation
- `current_time` - Time formatting

### **Filesystem Operations (4)**
- `directory_tree` - Directory visualization
- `read_file` - File content reading
- `write_file` - File editing and creation
- `search_files` - Pattern search

### **System & Development (2)**
- `execute_command` - Safe command execution
- `git_operations` - Git repository management

### **AI Agents (4)**
- `architect` - Architecture analysis
- `code_critique` - Code review
- `dispatch_agent` - Task orchestration
- `think` - Deep reasoning

This enhanced MCP server now provides a complete development and analysis toolkit with sophisticated AI agents that can assist with complex architectural decisions, code quality improvements, project planning, and strategic analysis - all while maintaining production-grade reliability and security., append, insert, and replace file content
- **File Search Tool**: Search text patterns in files with regex support

#### System & Development Tools (2)
- **Process Executor Tool**: Execute system commands safely
- **Git Operations Tool**: Common git operations with formatted output

### Production Features
- Health monitoring with uptime tracking
- Graceful shutdown handling
- Configurable thread pools and timeouts
- Comprehensive logging
- Debug endpoints for development

## 🧪 Testing the Enhanced Server

### 1. Health Check with Detailed Status
```bash
curl http://localhost:8080/health
```

### 2. List All Available Tools
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "write-1",
    "method": "tools/call",
    "params": {
      "name": "write_file",
      "arguments": {
        "path": "test-output.txt",
        "content": "Hello from MCP!\nThis is a test file.",
        "mode": "write",
        "create_backup": true,
        "validate_syntax": false
      }
    }
  }'

# Append to existing file
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "write-2",
    "method": "tools/call",
    "params": {
      "name": "write_file",
      "arguments": {
        "path": "test-output.txt",
        "content": "\nAppended line from MCP",
        "mode": "append"
      }
    }
  }'

# Insert at specific line
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "write-3",
    "method": "tools/call",
    "params": {
      "name": "write_file",
      "arguments": {
        "path": "test-output.txt",
        "content": "Inserted line",
        "mode": "insert",
        "line_number": 2
      }
    }
  }'
```

#### File Search Tool
```bash
# Basic text search
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "search-1",
    "method": "tools/call",
    "params": {
      "name": "search_files",
      "arguments": {
        "pattern": "public class",
        "path": "src",
        "file_pattern": "*.java",
        "context_lines": 2,
        "max_results": 20
      }
    }
  }'

# Regex search with case insensitive
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "search-2",
    "method": "tools/call",
    "params": {
      "name": "search_files",
      "arguments": {
        "pattern": "TODO|FIXME|XXX",
        "path": ".",
        "regex": true,
        "case_sensitive": false,
        "file_pattern": "*.java",
        "context_lines": 1,
        "max_depth": 5
      }
    }
  }'
```

### 4. System Tools Testing

#### Process Executor Tool
```bash
# Execute safe command
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "exec-1",
    "method": "tools/call",
    "params": {
      "name": "execute_command",
      "arguments": {
        "command": "ls",
        "args": ["-la", "."],
        "timeout_seconds": 10,
        "capture_output": true
      }
    }
  }'

# Execute git command
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "exec-2",
    "method": "tools/call",
    "params": {
      "name": "execute_command",
      "arguments": {
        "command": "git",
        "args": ["status", "--short"],
        "working_directory": ".",
        "timeout_seconds": 15
      }
    }
  }'

# Execute Maven build
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "exec-3",
    "method": "tools/call",
    "params": {
      "name": "execute_command",
      "arguments": {
        "command": "mvn",
        "args": ["compile"],
        "timeout_seconds": 120,
        "environment": {
          "MAVEN_OPTS": "-Xmx1g"
        }
      }
    }
  }'
```

#### Git Operations Tool
```bash
# Git status
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "git-1",
    "method": "tools/call",
    "params": {
      "name": "git_operations",
      "arguments": {
        "operation": "status",
        "repository_path": "."
      }
    }
  }'

# Git log
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "git-2",
    "method": "tools/call",
    "params": {
      "name": "git_operations",
      "arguments": {
        "operation": "log",
        "limit": 5
      }
    }
  }'

# Git add files
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "git-3",
    "method": "tools/call",
    "params": {
      "name": "git_operations",
      "arguments": {
        "operation": "add",
        "files": ["src/main/java/NewFile.java", "README.md"]
      }
    }
  }'

# Git commit
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "git-4",
    "method": "tools/call",
    "params": {
      "name": "git_operations",
      "arguments": {
        "operation": "commit",
        "message": "Add new MCP tools and enhancements"
      }
    }
  }'

# Git branch operations
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "git-5",
    "method": "tools/call",
    "params": {
      "name": "git_operations",
      "arguments": {
        "operation": "branch"
      }
    }
  }'
```

### 5. Error Handling Tests

#### Security Test - Unsafe Command
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "security-1",
    "method": "tools/call",
    "params": {
      "name": "execute_command",
      "arguments": {
        "command": "rm",
        "args": ["-rf", "/"],
        "allow_unsafe": false
      }
    }
  }'
```

#### File Not Found Test
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "error-1",
    "method": "tools/call",
    "params": {
      "name": "read_file",
      "arguments": {
        "path": "nonexistent-file.txt"
      }
    }
  }'
```

## 🔧 Advanced Configuration

### Environment Variables
```bash
# Enhanced configuration options
export PORT=9000
export MCP_DEBUG=true
export MCP_TOOL_TIMEOUT=60
export MCP_TOOL_THREADS=20

# Security settings
export MCP_ALLOW_UNSAFE_COMMANDS=false
export MCP_MAX_FILE_SIZE=52428800  # 50MB

# Filesystem tool settings
export MCP_MAX_SEARCH_RESULTS=500
export MCP_DEFAULT_ENCODING=UTF-8
```

### System Properties
```bash
java -jar server.jar \
  -Dserver.port=9000 \
  -Dmcp.debug=true \
  -Dmcp.tool.timeout=60 \
  -Dmcp.filesystem.max-file-size=10485760 \
  -Dmcp.security.allow-unsafe=false
```

## 🏗️ Tool Categories and Use Cases

### Filesystem Tools
- **Development**: Read source files, search for patterns, edit configuration
- **Documentation**: Generate project summaries, read documentation files
- **Debugging**: Search for error patterns, examine log files
- **Refactoring**: Find and replace patterns across multiple files

### System Tools
- **Build Automation**: Execute Maven, Gradle, npm builds
- **Testing**: Run test suites and capture results
- **Deployment**: Execute deployment scripts with monitoring
- **Environment Setup**: Install dependencies, configure systems

### Git Tools
- **Code Review**: Check status, view diffs, examine commit history
- **Branch Management**: Create, switch, and list branches
- **Collaboration**: Stage changes, commit with messages, push/pull
- **Project Tracking**: View project history and changes

## 📊 Performance and Limits

### Built-in Limits
- **File Operations**: 10MB max file size for read/write
- **Search Results**: 1000 max results, 20 max depth
- **Command Execution**: 5 minute timeout, 1MB output limit
- **Concurrent Operations**: 20 thread pool, per-tool timeouts

### Monitoring
```bash
# Check server health
curl http://localhost:8080/health

# View tool registry status  
curl http://localhost:8080/ | jq '.toolsAvailable'

# Debug tool list (debug mode only)
curl http://localhost:8080/tools
```

### Performance Tuning
```bash
# Increase thread pool for heavy workloads
export MCP_TOOL_THREADS=50

# Adjust timeouts for long-running operations
export MCP_TOOL_TIMEOUT=300

# Enable debug logging
export JAVA_OPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"
```

## 🔒 Security Features

### Command Execution Security
- Whitelist of allowed commands
- Working directory restrictions
- Timeout enforcement
- Output size limits

### File System Security
- Path traversal protection
- File size limits
- Encoding validation
- Backup creation for writes

### Input Validation
- JSON schema validation for all inputs
- Parameter type checking
- Range validation for numeric inputs
- Pattern validation for strings

This enhanced MCP server now provides a comprehensive toolkit similar to the clojure-mcp project, with robust Java implementations of filesystem operations, system command execution, and git integration - all with production-ready error handling and security controls.pc": "2.0",
"id": "tools-list",
"method": "tools/list"
}'
```

### 3. Filesystem Tools Testing

#### Directory Tree Tool
```bash
# Basic directory tree
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "dir-tree-1",
    "method": "tools/call",
    "params": {
      "name": "directory_tree",
      "arguments": {
        "path": ".",
        "max_depth": 3,
        "include_files": true,
        "show_hidden": false
      }
    }
  }'

# Directory tree with custom ignore patterns
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "dir-tree-2",
    "method": "tools/call",
    "params": {
      "name": "directory_tree",
      "arguments": {
        "path": "/path/to/project",
        "max_depth": 4,
        "ignore_patterns": ["*.log", "target/*", "node_modules"],
        "show_hidden": true
      }
    }
  }'
```

#### File Read Tool
```bash
# Read entire file
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "read-1",
    "method": "tools/call",
    "params": {
      "name": "read_file",
      "arguments": {
        "path": "README.md",
        "show_line_numbers": true,
        "max_lines": 50
      }
    }
  }'

# Read specific line range
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "read-2",
    "method": "tools/call",
    "params": {
      "name": "read_file",
      "arguments": {
        "path": "src/main/java/Example.java",
        "start_line": 10,
        "end_line": 25,
        "show_line_numbers": true,
        "encoding": "UTF-8"
      }
    }
  }'
```

#### File Write Tool
```bash
# Write new file
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonr# Enhanced MCP Server - Testing and Usage Guide

## 🚀 New Features

### Robust Error Handling
- Comprehensive exception hierarchy with specific error codes
- Centralized error handling with appropriate HTTP status codes
- Debug mode for detailed error information
- Request validation and sanitization

### Tool Registry System
- Thread-safe tool registration and management
- Concurrent tool execution with configurable timeouts
- Input validation against JSON schemas
- Tool lifecycle management

### Enhanced Tools
- **Hello Tool**: Multi-language greetings with input validation
- **Math Tool**: Basic calculator with proper error handling
- **Random Tool**: Generate random numbers, decimals, and booleans
- **Echo Tool**: Message echoing with transformations
- **Current Time Tool**: Multiple time format support

### Production Features
- Health monitoring with uptime tracking
- Graceful shutdown handling
- Configurable thread pools and timeouts
- Comprehensive logging
- Debug endpoints for development

## 🧪 Testing the Enhanced Server

### 1. Health Check with Detailed Status
```bash
curl http://localhost:8080/health
```

**Expected Response:**
```json
{
  "status": "healthy",
  "timestamp": "2025-05-27T10:30:45",
  "server": "MCP Hello World Server",
  "version": "1.0.0",
  "protocolVersion": "2024-11-05",
  "uptime": "5m 23s",
  "toolsRegistered": 5,
  "initialized": true
}
```

### 2. Enhanced Server Information
```bash
curl http://localhost:8080/
```

### 3. Debug Tools Endpoint (Development Only)
```bash
# Enable debug mode first
export MCP_DEBUG=true
curl http://localhost:8080/tools
```

### 4. Advanced Tool Testing

#### Math Tool Examples
```bash
# Addition
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "math-1",
    "method": "tools/call",
    "params": {
      "name": "math",
      "arguments": {
        "operation": "add",
        "a": 15.5,
        "b": 23.7
      }
    }
  }'

# Square root
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "math-2",
    "method": "tools/call",
    "params": {
      "name": "math",
      "arguments": {
        "operation": "sqrt",
        "a": 144
      }
    }
  }'

# Division by zero (error handling test)
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "math-3",
    "method": "tools/call",
    "params": {
      "name": "math",
      "arguments": {
        "operation": "divide",
        "a": 10,
        "b": 0
      }
    }
  }'
```

#### Random Number Generator
```bash
# Generate random integers
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "random-1",
    "method": "tools/call",
    "params": {
      "name": "random",
      "arguments": {
        "type": "integer",
        "min": 1,
        "max": 100,
        "count": 5
      }
    }
  }'

# Generate random decimals
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "random-2",
    "method": "tools/call",
    "params": {
      "name": "random",
      "arguments": {
        "type": "decimal",
        "min": 0.0,
        "max": 1.0,
        "count": 3
      }
    }
  }'
```

#### Enhanced Echo Tool
```bash
# Echo with transformations
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "echo-1",
    "method": "tools/call",
    "params": {
      "name": "echo",
      "arguments": {
        "message": "Hello MCP World",
        "transform": "uppercase",
        "repeat": 3
      }
    }
  }'

# Reverse transformation
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "echo-2",
    "method": "tools/call",
    "params": {
      "name": "echo",
      "arguments": {
        "message": "Hello World",
        "transform": "reverse"
      }
    }
  }'
```

#### Multi-language Hello Tool
```bash
# Spanish greeting
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "hello-1",
    "method": "tools/call",
    "params": {
      "name": "hello",
      "arguments": {
        "name": "María",
        "language": "es"
      }
    }
  }'

# French greeting
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "hello-2",
    "method": "tools/call",
    "params": {
      "name": "hello",
      "arguments": {
        "name": "Pierre",
        "language": "fr"
      }
    }
  }'
```

#### Time Tool with Formats
```bash
# ISO format
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "time-1",
    "method": "tools/call",
    "params": {
      "name": "current_time",
      "arguments": {
        "format": "iso"
      }
    }
  }'

# Timestamp format
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "time-2",
    "method": "tools/call",
    "params": {
      "name": "current_time",
      "arguments": {
        "format": "timestamp"
      }
    }
  }'
```

### 5. Error Handling Tests

#### Invalid Method
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "error-1",
    "method": "invalid/method"
  }'
```

#### Missing Required Parameters
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "error-2",
    "method": "tools/call",
    "params": {
      "name": "echo"
    }
  }'
```

#### Invalid JSON
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": 1, "method": "invalid'
```

## 🔧 Configuration Options

### Environment Variables
```bash
# Server configuration
export PORT=9000
export MCP_DEBUG=true

# Tool registry configuration
export MCP_TOOL_TIMEOUT=60        # Tool execution timeout in seconds
export MCP_TOOL_THREADS=20        # Thread pool size for tool execution

# Logging configuration
export JAVA_OPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"
```

### System Properties
```bash
# Run with custom configuration
java -jar target/mcp-hello-world-server-1.0.0.jar \
  -Dserver.port=9000 \
  -Dmcp.debug=true \
  -Dmcp.tool.timeout=30 \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=info
```

## 🏗️ Adding Custom Tools

### 1. Implement the McpTool Interface
```java
package com.example.mcp.tool.custom;

import com.example.mcp.tool.McpTool;
import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;

public class CustomTool implements McpTool {
    @Override
    public String getName() {
        return "custom_tool";
    }
    
    @Override
    public String getDescription() {
        return "A custom tool example";
    }
    
    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "input", Map.of(
                    "type", "string",
                    "description", "Input parameter"
                )
            ),
            "required", List.of("input")
        );
    }
    
    @Override
    public McpModels.CallToolResponse.CallToolResult execute(Map<String, Object> arguments) 
            throws ToolExecutionException {
        // Implementation here
        return createTextResult("Custom tool result");
    }
}
```

### 2. Register the Tool
```java
// In McpServer.registerBuiltinTools() or at runtime
toolRegistry.registerTool(new CustomTool());
```

## 📊 Monitoring and Debugging

### Production Monitoring
- Health endpoint provides comprehensive status
- Structured logging for observability
- Error rates and tool execution metrics
- Uptime and performance tracking

### Development Debugging
- Enable debug mode: `MCP_DEBUG=true`
- Access debug endpoints
- Detailed error stack traces
- Tool execution timing

### Performance Tuning
- Adjust thread pool size: `MCP_TOOL_THREADS`
- Configure execution timeouts: `MCP_TOOL_TIMEOUT`
- Memory settings: `-Xmx512m -Xms256m`
- JVM optimization for containers

## 🔒 Security Considerations

### Input Validation
- All tool inputs are validated against schemas
- String length limits enforced
- Numeric range validation
- Required parameter checking

### Error Information
- Detailed stack traces only in debug mode
- Sanitized error messages in production
- No sensitive information in error responses

### Resource Management
- Tool execution timeouts prevent resource exhaustion
- Thread pool limits concurrent execution
- Request size limits (10MB default)
- Graceful degradation under load

This enhanced MCP server provides a robust foundation for building production-ready Model Context Protocol implementations with comprehensive error handling, tool management, and monitoring capabilities.
