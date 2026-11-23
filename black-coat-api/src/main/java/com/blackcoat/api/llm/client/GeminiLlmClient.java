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
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final boolean enabled;
    private final int timeoutMs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiLlmClient(
        @Value("${blackcoat.llm.chain[0].api-key:}") String apiKey,
        @Value("${blackcoat.llm.chain[0].model:gemini-1.5-flash}") String model,
        @Value("${blackcoat.llm.chain[0].base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
        @Value("${blackcoat.llm.chain[0].enabled:true}") boolean enabled,
        @Value("${blackcoat.llm.chain[0].timeout-ms:8000}") int timeoutMs,
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
        return "gemini";
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
            onError.accept(new IllegalStateException("Gemini API key is not configured"));
            return;
        }

        try {
            // Build Gemini REST payload with system instruction
            Map<String, Object> payload = new HashMap<>();
            
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                payload.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", request.systemPrompt()))
                ));
            }

            List<Map<String, Object>> contents = new ArrayList<>();
            for (LlmRequest.ChatMessage msg : request.messages()) {
                String role = "user".equalsIgnoreCase(msg.role()) ? "user" : "model";
                contents.add(Map.of(
                    "role", role,
                    "parts", List.of(Map.of("text", msg.content()))
                ));
            }
            payload.put("contents", contents);
            payload.put("generationConfig", Map.of(
                "temperature", request.temperature(),
                "maxOutputTokens", request.maxTokens()
            ));

            String requestBody = objectMapper.writeValueAsString(payload);
            String url = String.format("%s/models/%s:streamGenerateContent?alt=sse&key=%s",
                baseUrl, model, apiKey);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

            // Stream response
            HttpResponse<java.io.InputStream> response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() >= 400) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.warn("Gemini returned HTTP {}: {}", response.statusCode(), errorBody);
                onError.accept(new RuntimeException("Gemini HTTP error " + response.statusCode() + ": " + errorBody));
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
                            JsonNode candidates = root.path("candidates");
                            if (candidates.isArray() && !candidates.isEmpty()) {
                                JsonNode parts = candidates.get(0).path("content").path("parts");
                                if (parts.isArray()) {
                                    for (JsonNode part : parts) {
                                        String text = part.path("text").asText("");
                                        if (!text.isEmpty()) {
                                            onToken.accept(text);
                                        }
                                    }
                                }
                            }
                        } catch (Exception parseEx) {
                            log.debug("Skipping unparseable SSE line: {}", line);
                        }
                    }
                }
            }

            onComplete.run();

        } catch (Exception ex) {
            log.error("Gemini invocation failed: {}", ex.getMessage());
            onError.accept(ex);
        }
    }
}
