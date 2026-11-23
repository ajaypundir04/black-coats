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
public class OllamaLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmClient.class);

    private final String model;
    private final String baseUrl;
    private final boolean enabled;
    private final int timeoutMs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaLlmClient(
        @Value("${blackcoat.llm.chain[2].model:llama3.2:3b}") String model,
        @Value("${blackcoat.llm.chain[2].base-url:http://localhost:11434}") String baseUrl,
        @Value("${blackcoat.llm.chain[2].enabled:true}") boolean enabled,
        @Value("${blackcoat.llm.chain[2].timeout-ms:12000}") int timeoutMs,
        ObjectMapper objectMapper
    ) {
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
        return "ollama";
    }

    @Override
    public boolean isConfigured() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public void streamCompletion(
        LlmRequest request,
        Consumer<String> onToken,
        Consumer<Throwable> onError,
        Runnable onComplete
    ) {
        if (!isConfigured()) {
            onError.accept(new IllegalStateException("Ollama endpoint is not configured or disabled"));
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
            payload.put("stream", true);
            payload.put("options", Map.of(
                "temperature", request.temperature(),
                "num_predict", request.maxTokens()
            ));

            String requestBody = objectMapper.writeValueAsString(payload);
            String url = baseUrl.endsWith("/api/chat") ? baseUrl : baseUrl + "/api/chat";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() >= 400) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.warn("Ollama returned HTTP {}: {}", response.statusCode(), errorBody);
                onError.accept(new RuntimeException("Ollama HTTP error " + response.statusCode() + ": " + errorBody));
                return;
            }

            // Ollama streams NDJSON lines (newline-delimited JSON)
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    try {
                        JsonNode root = objectMapper.readTree(line);
                        String content = root.path("message").path("content").asText("");
                        if (!content.isEmpty()) {
                            onToken.accept(content);
                        }
                        if (root.path("done").asBoolean(false)) {
                            break;
                        }
                    } catch (Exception parseEx) {
                        log.debug("Skipping unparseable Ollama line: {}", line);
                    }
                }
            }

            onComplete.run();

        } catch (Exception ex) {
            log.error("Ollama invocation failed: {}", ex.getMessage());
            onError.accept(ex);
        }
    }
}
