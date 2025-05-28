package com.example.mcp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;

/**
 * AI Client for integrating with various AI model providers
 * Supports Anthropic Claude, OpenAI, and other compatible APIs
 */
public class AIClient {
    private static final Logger logger = LoggerFactory.getLogger(AIClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Function<String, String> environmentProvider;
    private final String anthropicApiKey;
    private final String openaiApiKey;
    private final String geminiApiKey;
    private final String cohereApiKey;
    private final String mistralApiKey;
    private final String perplexityApiKey;
    private final String groqApiKey;
    private final String ollamaUrl;

    // Default constructor for production use
    public AIClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                new ObjectMapper(),
                System::getenv);
    }

    // Constructor for testing with dependency injection
    public AIClient(HttpClient httpClient, ObjectMapper objectMapper, Function<String, String> environmentProvider) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.environmentProvider = environmentProvider;

        this.anthropicApiKey = environmentProvider.apply("ANTHROPIC_API_KEY");
        this.openaiApiKey = environmentProvider.apply("OPENAI_API_KEY");
        this.geminiApiKey = environmentProvider.apply("GEMINI_API_KEY");
        this.cohereApiKey = environmentProvider.apply("COHERE_API_KEY");
        this.mistralApiKey = environmentProvider.apply("MISTRAL_API_KEY");
        this.perplexityApiKey = environmentProvider.apply("PERPLEXITY_API_KEY");
        this.groqApiKey = environmentProvider.apply("GROQ_API_KEY");
        this.ollamaUrl = environmentProvider.apply("OLLAMA_URL") != null ?
                environmentProvider.apply("OLLAMA_URL") : "http://localhost:11434";

        logger.info("AI Client initialized - Available providers: {}", getAvailableProviders());
    }

    public String getAvailableProviders() {
        StringBuilder providers = new StringBuilder();
        if (anthropicApiKey != null) providers.append("Anthropic ");
        if (openaiApiKey != null) providers.append("OpenAI ");
        if (geminiApiKey != null) providers.append("Gemini ");
        if (cohereApiKey != null) providers.append("Cohere ");
        if (mistralApiKey != null) providers.append("Mistral ");
        if (perplexityApiKey != null) providers.append("Perplexity ");
        if (groqApiKey != null) providers.append("Groq ");
        // Ollama is always available if URL is accessible
        providers.append("Ollama ");
        return providers.toString().trim();
    }

    /**
     * Send a message to an AI model and get the response
     */
    public AIResponse sendMessage(AIRequest request) throws AIException {
        String provider = selectProvider(request.getPreferredProvider());
        switch (provider.toLowerCase()) {
            case "anthropic" -> {
                return callAnthropicAPI(request);
            }
            case "openai" -> {
                return callOpenAIAPI(request);
            }
            case "gemini" -> {
                return callGeminiAPI(request);
            }
            case "cohere" -> {
                return callCohereAPI(request);
            }
            case "mistral" -> {
                return callMistralAPI(request);
            }
            case "perplexity" -> {
                return callPerplexityAPI(request);
            }
            case "groq" -> {
                return callGroqAPI(request);
            }
            case "ollama" -> {
                return callOllamaAPI(request);
            }
            default -> throw new AIException("No available AI providers configured");
        }
    }

    private String selectProvider(String preferred) {
        if (preferred != null && !preferred.isEmpty()) {
            String normalizedPreferred = preferred.toLowerCase();
            switch (normalizedPreferred) {
                case "anthropic", "claude" -> {
                    if (anthropicApiKey != null) return "anthropic";
                }
                case "openai", "gpt" -> {
                    if (openaiApiKey != null) return "openai";
                }
                case "gemini", "google" -> {
                    if (geminiApiKey != null) return "gemini";
                }
                case "cohere" -> {
                    if (cohereApiKey != null) return "cohere";
                }
                case "mistral" -> {
                    if (mistralApiKey != null) return "mistral";
                }
                case "perplexity" -> {
                    if (perplexityApiKey != null) return "perplexity";
                }
                case "groq" -> {
                    if (groqApiKey != null) return "groq";
                }
                case "ollama" -> {
                    return "ollama"; // Always return ollama if requested
                }
            }
        }

        // Fallback to any available provider
        if (anthropicApiKey != null) return "anthropic";
        if (openaiApiKey != null) return "openai";
        if (geminiApiKey != null) return "gemini";
        if (cohereApiKey != null) return "cohere";
        if (mistralApiKey != null) return "mistral";
        if (perplexityApiKey != null) return "perplexity";
        if (groqApiKey != null) return "groq";
        return "ollama"; // Fallback to Ollama if no API keys are configured
    }

    private AIResponse callAnthropicAPI(AIRequest request) throws AIException {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", request.getModel() != null ? request.getModel() : "claude-3-sonnet-20240229");
            requestBody.put("max_tokens", request.getMaxTokens());

            ArrayNode messages = objectMapper.createArrayNode();

            // Add system message if present
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                requestBody.put("system", request.getSystemPrompt());
            }

            // Add user message
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", request.getMessage());
            messages.add(userMessage);

            requestBody.set("messages", messages);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("x-api-key", anthropicApiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AIException("Anthropic API error: " + response.statusCode() + " - " + response.body());
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());
            JsonNode content = jsonResponse.get("content");
            if (content != null && content.isArray() && content.size() > 0) {
                String responseText = content.get(0).get("text").asText();
                return new AIResponse(responseText, "anthropic", jsonResponse.get("usage"));
            } else {
                throw new AIException("Unexpected response format from Anthropic API");
            }
        } catch (IOException | InterruptedException e) {
            throw new AIException("Failed to call Anthropic API: " + e.getMessage(), e);
        }
    }

    private AIResponse callOpenAIAPI(AIRequest request) throws AIException {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", request.getModel() != null ? request.getModel() : "gpt-4");
            requestBody.put("max_tokens", request.getMaxTokens());

            ArrayNode messages = objectMapper.createArrayNode();

            // Add system message if present
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                ObjectNode systemMessage = objectMapper.createObjectNode();
                systemMessage.put("role", "system");
                systemMessage.put("content", request.getSystemPrompt());
                messages.add(systemMessage);
            }

            // Add user message
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", request.getMessage());
            messages.add(userMessage);

            requestBody.set("messages", messages);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AIException("OpenAI API error: " + response.statusCode() + " - " + response.body());
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                String responseText = choices.get(0).get("message").get("content").asText();
                return new AIResponse(responseText, "openai", jsonResponse.get("usage"));
            } else {
                throw new AIException("Unexpected response format from OpenAI API");
            }
        } catch (IOException | InterruptedException e) {
            throw new AIException("Failed to call OpenAI API: " + e.getMessage(), e);
        }
    }

    private AIResponse callGeminiAPI(AIRequest request) throws AIException {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode content = objectMapper.createObjectNode();
            ArrayNode parts = objectMapper.createArrayNode();

            // Combine system prompt and user message for Gemini
            String fullMessage = request.getMessage();
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                fullMessage = request.getSystemPrompt() + "\n\n" + fullMessage;
            }

            ObjectNode part = objectMapper.createObjectNode();
            part.put("text", fullMessage);
            parts.add(part);
            content.set("parts", parts);
            contents.add(content);
            requestBody.set("contents", contents);

            // Add generation config
            ObjectNode generationConfig = objectMapper.createObjectNode();
            generationConfig.put("maxOutputTokens", request.getMaxTokens());
            requestBody.set("generationConfig", generationConfig);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AIException("Gemini API error: " + response.statusCode() + " - " + response.body());
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());
            JsonNode candidates = jsonResponse.get("candidates");
            if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                JsonNode candidate = candidates.get(0);
                JsonNode contentNode = candidate.get("content");
                if (contentNode != null && contentNode.get("parts") != null) {
                    String responseText = contentNode.get("parts").get(0).get("text").asText();
                    return new AIResponse(responseText, "gemini", jsonResponse.get("usageMetadata"));
                }
            }
            throw new AIException("Unexpected response format from Gemini API");
        } catch (IOException | InterruptedException e) {
            throw new AIException("Failed to call Gemini API: " + e.getMessage(), e);
        }
    }

    private AIResponse callCohereAPI(AIRequest request) throws AIException {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", request.getModel() != null ? request.getModel() : "command-r-plus");
            requestBody.put("max_tokens", request.getMaxTokens());

            // Cohere uses "message" field for the user input
            requestBody.put("message", request.getMessage());

            // Add system prompt if present (called "preamble" in Cohere)
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                requestBody.put("preamble", request.getSystemPrompt());
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cohere.ai/v1/chat"))
                    .header("Authorization", "Bearer " + cohereApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AIException("Cohere API error: " + response.statusCode() + " - " + response.body());
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());
            String responseText = jsonResponse.get("text").asText();
            return new AIResponse(responseText, "cohere", jsonResponse.get("meta"));
        } catch (IOException | InterruptedException e) {
            throw new AIException("Failed to call Cohere API: " + e.getMessage(), e);
        }
    }

    private AIResponse callMistralAPI(AIRequest request) throws AIException {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", request.getModel() != null ? request.getModel() : "mistral-large-latest");
            requestBody.put("max_tokens", request.getMaxTokens());

            ArrayNode messages = objectMapper.createArrayNode();

            // Add system message if present
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                ObjectNode systemMessage = objectMapper.createObjectNode();
                systemMessage.put("role", "system");
                systemMessage.put("content", request.getSystemPrompt());
                messages.add(systemMessage);
            }

            // Add user message
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", request.getMessage());
            messages.add(userMessage);

            requestBody.set("messages", messages);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mistral.ai/v1/chat/completions"))
                    .header("Authorization", "Bearer " + mistralApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AIException("Mistral API error: " + response.statusCode() + " - " + response.body());
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                String responseText = choices.get(0).get("message").get("content").asText();
                return new AIResponse(responseText, "mistral", jsonResponse.get("usage"));
            } else {
                throw new AIException("Unexpected response format from Mistral API");
            }
        } catch (IOException | InterruptedException e) {
            throw new AIException("Failed to call Mistral API: " + e.getMessage(), e);
        }
    }

    private AIResponse callPerplexityAPI(AIRequest request) throws AIException {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", request.getModel() != null ? request.getModel() : "llama-3.1-sonar-large-128k-online");
            requestBody.put("max_tokens", request.getMaxTokens());

            ArrayNode messages = objectMapper.createArrayNode();

            // Add system message if present
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                ObjectNode systemMessage = objectMapper.createObjectNode();
                systemMessage.put("role", "system");
                systemMessage.put("content", request.getSystemPrompt());
                messages.add(systemMessage);
            }

            // Add user message
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", request.getMessage());
            messages.add(userMessage);

            requestBody.set("messages", messages);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.perplexity.ai/chat/completions"))
                    .header("Authorization", "Bearer " + perplexityApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AIException("Perplexity API error: " + response.statusCode() + " - " + response.body());
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                String responseText = choices.get(0).get("message").get("content").asText();
                return new AIResponse(responseText, "perplexity", jsonResponse.get("usage"));
            } else {
                throw new AIException("Unexpected response format from Perplexity API");
            }
        } catch (IOException | InterruptedException e) {
            throw new AIException("Failed to call Perplexity API: " + e.getMessage(), e);
        }
    }

    private AIResponse callGroqAPI(AIRequest request) throws AIException {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", request.getModel() != null ? request.getModel() : "llama-3.1-70b-versatile");
            requestBody.put("max_tokens", request.getMaxTokens());

            ArrayNode messages = objectMapper.createArrayNode();

            // Add system message if present
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                ObjectNode systemMessage = objectMapper.createObjectNode();
                systemMessage.put("role", "system");
                systemMessage.put("content", request.getSystemPrompt());
                messages.add(systemMessage);
            }

            // Add user message
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", request.getMessage());
            messages.add(userMessage);

            requestBody.set("messages", messages);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AIException("Groq API error: " + response.statusCode() + " - " + response.body());
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                String responseText = choices.get(0).get("message").get("content").asText();
                return new AIResponse(responseText, "groq", jsonResponse.get("usage"));
            } else {
                throw new AIException("Unexpected response format from Groq API");
            }
        } catch (IOException | InterruptedException e) {
            throw new AIException("Failed to call Groq API: " + e.getMessage(), e);
        }
    }

    private AIResponse callOllamaAPI(AIRequest request) throws AIException {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", request.getModel() != null ? request.getModel() : "llama3.1");

            // Combine system prompt and user message for Ollama
            String fullMessage = request.getMessage();
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                fullMessage = request.getSystemPrompt() + "\n\nUser: " + fullMessage;
            }

            requestBody.put("prompt", fullMessage);
            requestBody.put("stream", false);

            // Ollama uses options object for parameters
            ObjectNode options = objectMapper.createObjectNode();
            options.put("num_predict", request.getMaxTokens());
            requestBody.set("options", options);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(120)) // Ollama might be slower
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AIException("Ollama API error: " + response.statusCode() + " - " + response.body());
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());
            String responseText = jsonResponse.get("response").asText();

            // Create a simple usage object for Ollama
            ObjectNode usage = objectMapper.createObjectNode();
            if (jsonResponse.has("total_duration")) {
                usage.put("total_duration", jsonResponse.get("total_duration"));
            }
            if (jsonResponse.has("eval_count")) {
                usage.put("eval_count", jsonResponse.get("eval_count"));
            }

            return new AIResponse(responseText, "ollama", usage);
        } catch (IOException | InterruptedException e) {
            throw new AIException("Failed to call Ollama API: " + e.getMessage(), e);
        }
    }

    /**
     * AI Request wrapper class
     */
    public static class AIRequest {
        private String message;
        private String systemPrompt;
        private String preferredProvider;
        private String model;
        private int maxTokens = 4000;
        private Function<String, String> environmentProvider;

        public AIRequest(String message) {
            this.message = message;
            this.environmentProvider = System::getenv; // Default to System.getenv
        }

        public AIRequest(String message, String systemPrompt) {
            this.message = message;
            this.systemPrompt = systemPrompt;
            this.environmentProvider = System::getenv;
        }

        public AIRequest(String message, String model, int maxTokens, String systemPrompt, String preferredProvider) {
            this.message = message;
            this.model = model;
            this.maxTokens = maxTokens;
            this.systemPrompt = systemPrompt;
            this.preferredProvider = preferredProvider;
            this.environmentProvider = System::getenv;
        }

        // Constructor for testing
        public AIRequest(String message, Function<String, String> environmentProvider) {
            this.message = message;
            this.environmentProvider = environmentProvider;
        }

        // Model convenience methods for popular models
        public AIRequest withLlama3_8B() {
            detectAndSetLlamaModel("llama3-8b");
            return this;
        }

        public AIRequest withLlama3_70B() {
            detectAndSetLlamaModel("llama3-70b");
            return this;
        }

        public AIRequest withLlama3_1_8B() {
            detectAndSetLlamaModel("llama3.1-8b");
            return this;
        }

        public AIRequest withLlama3_1_70B() {
            detectAndSetLlamaModel("llama3.1-70b");
            return this;
        }

        public AIRequest withLlama3_1_405B() {
            detectAndSetLlamaModel("llama3.1-405b");
            return this;
        }

        public AIRequest withLlama3_2_1B() {
            detectAndSetLlamaModel("llama3.2-1b");
            return this;
        }

        public AIRequest withLlama3_2_3B() {
            detectAndSetLlamaModel("llama3.2-3b");
            return this;
        }

        public AIRequest withLlama3_2_11B() {
            detectAndSetLlamaModel("llama3.2-11b");
            return this;
        }

        public AIRequest withLlama3_2_90B() {
            detectAndSetLlamaModel("llama3.2-90b");
            return this;
        }

        public AIRequest withClaude3_5_Sonnet() {
            this.model = "claude-3-5-sonnet-20241022";
            this.preferredProvider = "anthropic";
            return this;
        }

        public AIRequest withClaude3_5_Haiku() {
            this.model = "claude-3-5-haiku-20241022";
            this.preferredProvider = "anthropic";
            return this;
        }

        public AIRequest withGPT4o() {
            this.model = "gpt-4o";
            this.preferredProvider = "openai";
            return this;
        }

        public AIRequest withGPT4oMini() {
            this.model = "gpt-4o-mini";
            this.preferredProvider = "openai";
            return this;
        }

        public AIRequest withMistralLarge() {
            this.model = "mistral-large-latest";
            this.preferredProvider = "mistral";
            return this;
        }

        public AIRequest withCommandRPlus() {
            this.model = "command-r-plus";
            this.preferredProvider = "cohere";
            return this;
        }

        private void detectAndSetLlamaModel(String requestedModel) {
            // Use injected environment provider
            String groqKey = environmentProvider.apply("GROQ_API_KEY");
            String perplexityKey = environmentProvider.apply("PERPLEXITY_API_KEY");
            String openaiKey = environmentProvider.apply("OPENAI_API_KEY");

            // Auto-detect best provider for Llama models based on availability
            String normalizedModel = requestedModel.toLowerCase().replace(".", "");

            // Map to provider-specific model names
            switch (normalizedModel) {
                case "llama3-8b", "llama3-8b-instruct" -> {
                    if (groqKey != null) {
                        this.preferredProvider = "groq";
                        this.model = "llama3-8b-8192";
                    } else if (openaiKey != null) {
                        this.preferredProvider = "openai";
                        this.model = "meta-llama/Llama-3-8b-chat-hf";
                    } else {
                        this.preferredProvider = "ollama";
                        this.model = "llama3:8b";
                    }
                }
                case "llama3-70b", "llama3-70b-instruct" -> {
                    if (groqKey != null) {
                        this.preferredProvider = "groq";
                        this.model = "llama3-70b-8192";
                    } else if (openaiKey != null) {
                        this.preferredProvider = "openai";
                        this.model = "meta-llama/Llama-3-70b-chat-hf";
                    } else {
                        this.preferredProvider = "ollama";
                        this.model = "llama3:70b";
                    }
                }
                case "llama31-8b", "llama3.1-8b" -> {
                    if (groqKey != null) {
                        this.preferredProvider = "groq";
                        this.model = "llama-3.1-8b-instant";
                    } else if (perplexityKey != null) {
                        this.preferredProvider = "perplexity";
                        this.model = "llama-3.1-8b-instruct";
                    } else {
                        this.preferredProvider = "ollama";
                        this.model = "llama3.1:8b";
                    }
                }
                case "llama31-70b", "llama3.1-70b" -> {
                    if (groqKey != null) {
                        this.preferredProvider = "groq";
                        this.model = "meta-llama/llama-4-scout-17b-16e-instruct";
                    } else if (perplexityKey != null) {
                        this.preferredProvider = "perplexity";
                        this.model = "llama-3.1-70b-instruct";
                    } else {
                        this.preferredProvider = "ollama";
                        this.model = "llama3.1:70b";
                    }
                }
                case "llama31-405b", "llama3.1-405b" -> {
                    if (perplexityKey != null) {
                        this.preferredProvider = "perplexity";
                        this.model = "llama-3.1-405b-reasoning";
                    } else {
                        this.preferredProvider = "ollama";
                        this.model = "llama3.1:405b";
                    }
                }
                case "llama32-1b", "llama3.2-1b" -> {
                    if (groqKey != null) {
                        this.preferredProvider = "groq";
                        this.model = "llama-3.2-1b-preview";
                    } else {
                        this.preferredProvider = "ollama";
                        this.model = "llama3.2:1b";
                    }
                }
                case "llama32-3b", "llama3.2-3b" -> {
                    if (groqKey != null) {
                        this.preferredProvider = "groq";
                        this.model = "llama-3.2-3b-preview";
                    } else {
                        this.preferredProvider = "ollama";
                        this.model = "llama3.2:3b";
                    }
                }
                case "llama32-11b", "llama3.2-11b" -> {
                    if (groqKey != null) {
                        this.preferredProvider = "groq";
                        this.model = "llama-3.2-11b-text-preview";
                    } else {
                        this.preferredProvider = "ollama";
                        this.model = "llama3.2:11b";
                    }
                }
                case "llama32-90b", "llama3.2-90b" -> {
                    if (groqKey != null) {
                        this.preferredProvider = "groq";
                        this.model = "llama-3.2-90b-text-preview";
                    } else {
                        this.preferredProvider = "ollama";
                        this.model = "llama3.2:90b";
                    }
                }
                default -> this.model = requestedModel;
            }
        }

        // Getters and setters
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
        public String getPreferredProvider() { return preferredProvider; }
        public void setPreferredProvider(String preferredProvider) { this.preferredProvider = preferredProvider; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public Function<String, String> getEnvironmentProvider() { return environmentProvider; }
        public void setEnvironmentProvider(Function<String, String> environmentProvider) { this.environmentProvider = environmentProvider; }

        public static void main(String[] args) throws AIException {
            AIClient client = new AIClient();
            // Method 1: Convenience methods (recommended)
            AIRequest request = new AIRequest("Explain quantum computing")
                    .withLlama3_1_70B();  // Auto-selects best provider
            AIResponse response = client.sendMessage(request);
            System.out.println(response.getContent());
            // Method 2: Manual model selection
            AIRequest manualRequest = new AIRequest("Write a poem");
            manualRequest.setModel("llama-3.1-70b-versatile");
            manualRequest.setPreferredProvider("groq");
            // Method 3: Other popular models
            AIRequest claudeRequest = new AIRequest("Analyze this data")
                    .withClaude3_5_Sonnet();
            AIRequest gptRequest = new AIRequest("Generate code")
                    .withGPT4o();
        }
     }

    /**
     * AI Response wrapper class
     */
    public static class AIResponse {
        private final String content;
        private final String provider;
        private final JsonNode usage;

        public AIResponse(String content, String provider, JsonNode usage) {
            this.content = content;
            this.provider = provider;
            this.usage = usage;
        }

        public String getContent() { return content; }
        public String getProvider() { return provider; }
        public JsonNode getUsage() { return usage; }
    }

    /**
     * AI Exception class
     */
    public static class AIException extends Exception {
        public AIException(String message) {
            super(message);
        }

        public AIException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}