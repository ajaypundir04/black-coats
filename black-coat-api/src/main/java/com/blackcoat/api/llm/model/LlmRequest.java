package com.blackcoat.api.llm.model;

import java.util.List;

public record LlmRequest(
    String systemPrompt,
    List<ChatMessage> messages,
    double temperature,
    int maxTokens
) {
    public record ChatMessage(String role, String content) {}

    public static LlmRequest of(String systemPrompt, String userMessage) {
        return new LlmRequest(
            systemPrompt,
            List.of(new ChatMessage("user", userMessage)),
            0.1, // low temperature for legal precision
            1024
        );
    }
}
