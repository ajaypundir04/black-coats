package com.blackcoat.api.rag.model;

import java.util.List;

public record VerificationResult(
    String cleanedContent,
    List<VerifiedCitation> citations,
    List<String> ungroundedSections,
    boolean containsHallucinations
) {}
