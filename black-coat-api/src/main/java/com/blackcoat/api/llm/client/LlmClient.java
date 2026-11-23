package com.blackcoat.api.llm.client;

import com.blackcoat.api.llm.model.LlmRequest;

import java.util.function.Consumer;

/**
 * Common abstraction for LLM providers in the fallback chain.
 * Implementations stream tokens via reactive consumers.
 */
public interface LlmClient {

    /**
     * Unique identifier for the provider (e.g. "gemini", "groq", "ollama").
     */
    String getProviderName();

    /**
     * Whether the provider has required credentials and is enabled in config.
     */
    boolean isConfigured();

    /**
     * Stream response tokens asynchronously.
     *
     * @param request    The prompt, sources and parameters
     * @param onToken    Consumer invoked for each parsed text token
     * @param onError    Consumer invoked if this provider errors or times out
     * @param onComplete Callback invoked when streaming completes successfully
     */
    void streamCompletion(
        LlmRequest request,
        Consumer<String> onToken,
        Consumer<Throwable> onError,
        Runnable onComplete
    );
}
