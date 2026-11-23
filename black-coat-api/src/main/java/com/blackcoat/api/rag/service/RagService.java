package com.blackcoat.api.rag.service;

import com.blackcoat.api.common.exception.InsufficientLegalSourcesException;
import com.blackcoat.api.llm.adapter.LlmFallbackRouter;
import com.blackcoat.api.llm.model.LlmRequest;
import com.blackcoat.api.rag.model.LegalSectionDto;
import com.blackcoat.api.rag.model.VerificationResult;
import com.blackcoat.api.rag.repository.LegalRetrievalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final LanguageDetectionService languageDetectionService;
    private final LegalRetrievalRepository retrievalRepository;
    private final PromptBuilderService promptBuilderService;
    private final CitationVerificationService citationVerificationService;
    private final SafetyCheckService safetyCheckService;
    private final LlmFallbackRouter llmFallbackRouter;

    public RagService(
        LanguageDetectionService languageDetectionService,
        LegalRetrievalRepository retrievalRepository,
        PromptBuilderService promptBuilderService,
        CitationVerificationService citationVerificationService,
        SafetyCheckService safetyCheckService,
        LlmFallbackRouter llmFallbackRouter
    ) {
        this.languageDetectionService = languageDetectionService;
        this.retrievalRepository = retrievalRepository;
        this.promptBuilderService = promptBuilderService;
        this.citationVerificationService = citationVerificationService;
        this.safetyCheckService = safetyCheckService;
        this.llmFallbackRouter = llmFallbackRouter;
    }

    public record RagResponse(
        String answer,
        String language,
        String providerUsed,
        long latencyMs,
        VerificationResult verificationResult,
        SafetyCheckService.SafetyEvaluation safetyEvaluation
    ) {}

    /**
     * Executes end-to-end grounded legal generation with streaming.
     */
    public RagResponse streamLegalAnswer(
        String userQuestion,
        float[] queryEmbedding,
        Consumer<String> tokenConsumer
    ) {
        // 1. Language & Script Detection
        var langResult = languageDetectionService.detectLanguage(userQuestion);
        log.info("Detected query script: {} (lang: {})", langResult.scriptName(), langResult.langCode());

        // 2. Safety pre-check
        var safetyEval = safetyCheckService.evaluate(userQuestion);

        // 3. Hybrid Retrieval (Vector Top-20 + FTS Top-20 with RRF)
        List<LegalSectionDto> retrievedSections = retrievalRepository.searchHybrid(
            userQuestion,
            queryEmbedding
        );

        // 4. Threshold & Refusal Gate: If no sources pass threshold, abort without calling LLM
        if (retrievedSections.isEmpty()) {
            log.info("No legal sections passed similarity threshold. Triggering refusal path.");
            throw new InsufficientLegalSourcesException(
                "I could not find relevant Indian statutory provisions in the law corpus to answer your question. " +
                "To prevent hallucinations, Black-Coat only answers when grounded in verified legal statutes."
            );
        }

        // 5. Prompt Construction
        String systemPrompt = promptBuilderService.buildSystemPrompt(langResult.scriptName());
        String userPrompt = promptBuilderService.buildUserPrompt(userQuestion, retrievedSections);

        LlmRequest llmRequest = LlmRequest.of(systemPrompt, userPrompt);

        // 6. Streaming Generation via Provider Chain (Gemini -> Groq -> Ollama)
        StringBuilder rawGeneratedContent = new StringBuilder();
        var execution = llmFallbackRouter.executeStream(llmRequest, token -> {
            rawGeneratedContent.append(token);
            tokenConsumer.accept(token);
        });

        // 7. Citation Verification & Grounding Check
        VerificationResult verification = citationVerificationService.verifyCitations(
            rawGeneratedContent.toString(),
            retrievedSections
        );

        // 8. Append emergency legal aid contacts if urgent situation detected
        if (safetyEval.urgentFlag() && safetyEval.advisoryNotice() != null) {
            tokenConsumer.accept(safetyEval.advisoryNotice());
        }

        return new RagResponse(
            verification.cleanedContent(),
            langResult.langCode(),
            execution.providerUsed(),
            execution.latencyMs(),
            verification,
            safetyEval
        );
    }
}
