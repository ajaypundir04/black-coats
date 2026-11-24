package com.blackcoat.api.chat.controller;

import com.blackcoat.api.chat.model.ChatStreamRequest;
import com.blackcoat.api.chat.service.QuotaService;
import com.blackcoat.api.common.exception.AllLlmProvidersExhaustedException;
import com.blackcoat.api.common.exception.InsufficientLegalSourcesException;
import com.blackcoat.api.common.exception.QuotaExceededException;
import com.blackcoat.api.rag.service.RagService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatStreamController {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamController.class);
    private final RagService ragService;
    private final QuotaService quotaService;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatStreamController(RagService ragService, QuotaService quotaService) {
        this.ragService = ragService;
        this.quotaService = quotaService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter streamLegalQuestion(
        @Valid @RequestBody ChatStreamRequest request,
        Authentication authentication
    ) {
        // Extract authenticated user ID from JWT, or default to demo UUID
        final UUID userId;
        if (authentication != null && authentication.getPrincipal() instanceof UUID authUuid) {
            userId = authUuid;
        } else {
            userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        }

        // 30 second timeout for SSE emitter
        SseEmitter emitter = new SseEmitter(45_000L);

        virtualThreadExecutor.submit(() -> {
            boolean quotaReserved = false;
            try {
                // 1. Quota reservation (Atomic check & reserve)
                quotaService.reserveQuota(userId);
                quotaReserved = true;

                // 2. Stream answer tokens through RAG service
                RagService.RagResponse response = ragService.streamLegalAnswer(
                    request.question().trim(),
                    null, // Multilingual embedding or FTS
                    token -> sendSseEvent(emitter, "token", Map.of("text", token))
                );

                // 3. Send verified citations event
                sendSseEvent(emitter, "citations", Map.of(
                    "citations", response.verificationResult().citations(),
                    "ungrounded", response.verificationResult().ungroundedSections()
                ));

                // 4. Send completion event
                sendSseEvent(emitter, "done", Map.of(
                    "provider", response.providerUsed(),
                    "latencyMs", response.latencyMs(),
                    "language", response.language()
                ));

                emitter.complete();

            } catch (InsufficientLegalSourcesException ex) {
                log.info("Refusal path: {}", ex.getMessage());
                if (quotaReserved) {
                    quotaService.refundQuota(userId);
                }
                sendSseEvent(emitter, "refusal", Map.of("message", ex.getMessage()));
                emitter.complete();

            } catch (QuotaExceededException ex) {
                log.warn("Quota exceeded: {}", ex.getMessage());
                sendSseEvent(emitter, "error", Map.of("code", "QUOTA_EXCEEDED", "message", ex.getMessage()));
                emitter.complete();

            } catch (AllLlmProvidersExhaustedException ex) {
                log.warn("All LLM providers exhausted: {}", ex.getMessage());
                if (quotaReserved) {
                    quotaService.refundQuota(userId);
                }
                sendSseEvent(emitter, "error", Map.of(
                    "code", "SERVICE_BUSY",
                    "message", "Our legal AI assistant is currently experiencing high demand. Please try asking your question again in a moment."
                ));
                emitter.complete();

            } catch (Exception ex) {
                log.error("Pipeline failure in chat stream: {}", ex.getMessage(), ex);
                if (quotaReserved) {
                    quotaService.refundQuota(userId);
                }
                sendSseEvent(emitter, "error", Map.of(
                    "code", "TEMPORARY_ERROR",
                    "message", "A temporary issue occurred while processing your legal query. Please try again shortly."
                ));
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    private void sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                .name(eventName)
                .data(data));
        } catch (IOException e) {
            log.debug("Client disconnected during SSE stream: {}", e.getMessage());
        }
    }
}
