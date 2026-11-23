package com.blackcoat.api.llm.client;

import com.blackcoat.api.llm.model.LlmRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class GroqLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmClient.class);

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final boolean enabled;
    private final int timeoutMs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GroqLlmClient(
        @Value("${blackcoat.llm.chain[1].api-key:}") String apiKey,
        @Value("${blackcoat.llm.chain[1].model:llama-3.3-70b-versatile}") String model,
        @Value("${blackcoat.llm.chain[1].base-url:https://api.groq.com/openai/v1}") String baseUrl,
        @Value("${blackcoat.llm.chain[1].enabled:true}") boolean enabled,
        @Value("${blackcoat.llm.chain[1].timeout-ms:6000}") int timeoutMs,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
    }

    @Override
    public String getProviderName() {
        return "groq";
    }

    @Override
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public void streamCompletion(
        LlmRequest request,
        Consumer<String> onToken,
        Consumer<Throwable> onError,
        Runnable onComplete
    ) {
        if (!isConfigured()) {
            onError.accept(new IllegalStateException("Groq API key is not configured"));
            return;
        }

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                messages.add(Map.of("role", "system", "content", request.systemPrompt()));
            }
            for (LlmRequest.ChatMessage msg : request.messages()) {
                messages.add(Map.of("role", msg.role(), "content", msg.content()));
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("messages", messages);
            payload.put("temperature", request.temperature());
            payload.put("max_tokens", request.maxTokens());
            payload.put("stream", true);

            String requestBody = objectMapper.writeValueAsString(payload);
            String url = baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() >= 400) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.warn("Groq returned HTTP {}: {}", response.statusCode(), errorBody);
                onError.accept(new RuntimeException("Groq HTTP error " + response.statusCode() + ": " + errorBody));
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String json = line.substring(6).trim();
                        if (json.isEmpty() || "[DONE]".equalsIgnoreCase(json)) {
                            continue;
                        }
                        try {
                            JsonNode root = objectMapper.readTree(json);
                            JsonNode choices = root.path("choices");
                            if (choices.isArray() && !choices.isEmpty()) {
                                JsonNode delta = choices.get(0).path("delta");
                                String content = delta.path("content").asText("");
                                if (!content.isEmpty()) {
                                    onToken.accept(content);
                                }
                            }
                        } catch (Exception parseEx) {
                            log.debug("Skipping unparseable Groq SSE line: {}", line);
                        }
                    }
                }
            }

            onComplete.run();

        } catch (Exception ex) {
            log.error("Groq invocation failed: {}", ex.getMessage());
            onError.accept(ex);
        }
    }
}
