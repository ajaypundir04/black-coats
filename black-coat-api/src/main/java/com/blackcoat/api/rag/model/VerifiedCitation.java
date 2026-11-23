package com.blackcoat.api.rag.model;

import java.util.List;
import java.util.UUID;

public record VerifiedCitation(
    UUID sectionId,
    String tag,            // e.g. "[S1]"
    String actShortName,   // e.g. "BNS"
    String sectionNo,      // e.g. "303"
    String heading,        // e.g. "Theft"
    String textSnippet,
    boolean verified
) {}
