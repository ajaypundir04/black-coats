package com.blackcoat.api.llm.adapter;

import com.blackcoat.api.common.exception.AllLlmProvidersExhaustedException;
import com.blackcoat.api.llm.client.GeminiLlmClient;
import com.blackcoat.api.llm.client.GroqLlmClient;
import com.blackcoat.api.llm.client.LlmClient;
import com.blackcoat.api.llm.client.OllamaLlmClient;
import com.blackcoat.api.llm.model.LlmRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class LlmFallbackRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmFallbackRouter.class);

    private final List<LlmClient> providerChain;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ApplicationEventPublisher eventPublisher;

    public LlmFallbackRouter(
        GeminiLlmClient geminiClient,
        GroqLlmClient groqClient,
        OllamaLlmClient ollamaClient,
        CircuitBreakerRegistry circuitBreakerRegistry,
        ApplicationEventPublisher eventPublisher
    ) {
        // Priority order: Gemini free -> Groq free -> Ollama local
        this.providerChain = List.of(geminiClient, groqClient, ollamaClient);
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.eventPublisher = eventPublisher;
    }

    public record ExecutionResult(String providerUsed, long latencyMs) {}

    /**
     * Attempts generation through the provider chain until one succeeds.
     * Streams output tokens to onToken.
     */
    public ExecutionResult executeStream(
        LlmRequest request,
        Consumer<String> onToken
    ) {
        long startTime = System.currentTimeMillis();
        Throwable lastError = null;

        for (LlmClient provider : providerChain) {
            String name = provider.getProviderName();

            if (!provider.isConfigured()) {
                log.debug("Provider {} is not configured or disabled, skipping", name);
                continue;
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name + "Llm");
            if (!cb.tryAcquirePermission()) {
                log.warn("Circuit breaker for {} is OPEN or rate-limited, skipping to next provider", name);
                continue;
            }

            log.info("Attempting LLM generation via provider: {}", name);
            long providerStart = System.currentTimeMillis();
            AtomicBoolean succeeded = new AtomicBoolean(false);
            AtomicReference<Throwable> failureRef = new AtomicReference<>();
            StringBuilder buffer = new StringBuilder();

            try {
                // Synchronously drive the provider streaming loop
                provider.streamCompletion(
                    request,
                    token -> {
                        buffer.append(token);
                        onToken.accept(token);
                    },
                    err -> {
                        failureRef.set(err);
                        cb.onError(System.currentTimeMillis() - providerStart, java.util.concurrent.TimeUnit.MILLISECONDS, err);
                    },
                    () -> {
                        succeeded.set(true);
                        cb.onSuccess(System.currentTimeMillis() - providerStart, java.util.concurrent.TimeUnit.MILLISECONDS);
                    }
                );

                if (succeeded.get()) {
                    long latency = System.currentTimeMillis() - startTime;
                    log.info("Generation succeeded using {} in {}ms", name, latency);
                    return new ExecutionResult(name, latency);
                } else if (failureRef.get() != null) {
                    lastError = failureRef.get();
                    log.warn("Provider {} failed during streaming: {}", name, lastError.getMessage());
                }

            } catch (Exception ex) {
                lastError = ex;
                cb.onError(System.currentTimeMillis() - providerStart, java.util.concurrent.TimeUnit.MILLISECONDS, ex);
                log.warn("Provider {} failed with exception: {}", name, ex.getMessage());
            }
        }

        // All providers in the fallback chain were exhausted
        log.error("All LLM providers in fallback chain failed. Last error: {}",
            lastError != null ? lastError.getMessage() : "none configured");

        throw new AllLlmProvidersExhaustedException(
            "All LLM providers are currently unavailable. Quota has been refunded.",
            lastError
        );
    }
}
