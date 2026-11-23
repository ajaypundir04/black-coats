package com.blackcoat.api.rag.model;

import java.util.UUID;

public record LegalSectionDto(
    UUID id,
    String shortName,
    String sectionNo,
    String heading,
    String text,
    double similarityScore,
    int rank
) {
    public String getFormattedTag(int index) {
        return String.format("[S%d] %s s.%s — %s: %s",
            index,
            shortName,
            sectionNo,
            heading != null ? heading : "Section " + sectionNo,
            text
        );
    }
}
