package com.blackcoat.api.rag.service;

import com.blackcoat.api.rag.model.LegalSectionDto;
import com.blackcoat.api.rag.model.VerificationResult;
import com.blackcoat.api.rag.model.VerifiedCitation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CitationVerificationService {

    private static final Logger log = LoggerFactory.getLogger(CitationVerificationService.class);

    // Pattern matching citation tags like [S1], [S2]
    private static final Pattern TAG_PATTERN = Pattern.compile("\\[S(\\d+)\\]");

    // Pattern matching free-text section citations like "Section 303", "s.420", "Sec 103"
    private static final Pattern FREE_SECTION_PATTERN = Pattern.compile("(?i)(?:section|sec\\.|s\\.)\\s*(\\d+[A-Z]*)");

    public VerificationResult verifyCitations(String generatedContent, List<LegalSectionDto> retrievedSources) {
        if (generatedContent == null || generatedContent.isBlank()) {
            return new VerificationResult("", List.of(), List.of(), false);
        }

        // Map tag numbers 1-based index to retrieved sources
        Map<Integer, LegalSectionDto> sourceMap = new HashMap<>();
        Set<String> validSectionNumbers = new HashSet<>();
        for (int i = 0; i < retrievedSources.size(); i++) {
            LegalSectionDto doc = retrievedSources.get(i);
            sourceMap.put(i + 1, doc);
            validSectionNumbers.add(doc.sectionNo().trim().toUpperCase());
        }

        // 1. Verify and strip [S#] tags
        Matcher tagMatcher = TAG_PATTERN.matcher(generatedContent);
        StringBuilder cleanedBuffer = new StringBuilder();
        Map<String, VerifiedCitation> verifiedCitationMap = new LinkedHashMap<>();
        boolean hasHallucinatedTags = false;

        while (tagMatcher.find()) {
            int tagNumber = Integer.parseInt(tagMatcher.group(1));
            if (sourceMap.containsKey(tagNumber)) {
                LegalSectionDto source = sourceMap.get(tagNumber);
                String tag = "[S" + tagNumber + "]";
                verifiedCitationMap.putIfAbsent(tag, new VerifiedCitation(
                    source.id(),
                    tag,
                    source.shortName(),
                    source.sectionNo(),
                    source.heading(),
                    source.text().length() > 200 ? source.text().substring(0, 200) + "..." : source.text(),
                    true
                ));
                tagMatcher.appendReplacement(cleanedBuffer, Matcher.quoteReplacement(tag));
            } else {
                // Stripping unknown / hallucinated tag
                hasHallucinatedTags = true;
                log.warn("Stripping hallucinated citation tag [S{}] from response", tagNumber);
                tagMatcher.appendReplacement(cleanedBuffer, "");
            }
        }
        tagMatcher.appendTail(cleanedBuffer);
        String cleanedContent = cleanedBuffer.toString();

        // 2. Scan for free-text section mentions and flag ungrounded sections
        Matcher freeMatcher = FREE_SECTION_PATTERN.matcher(cleanedContent);
        List<String> ungroundedSections = new ArrayList<>();

        while (freeMatcher.find()) {
            String sectionMentioned = freeMatcher.group(1).trim().toUpperCase();
            if (!validSectionNumbers.contains(sectionMentioned)) {
                log.warn("Flagged ungrounded section mentioned in free text: Section {}", sectionMentioned);
                ungroundedSections.add(sectionMentioned);
            }
        }

        boolean containsHallucinations = hasHallucinatedTags || !ungroundedSections.isEmpty();

        return new VerificationResult(
            cleanedContent,
            new ArrayList<>(verifiedCitationMap.values()),
            ungroundedSections,
            containsHallucinations
        );
    }
}
