package com.blackcoat.api.rag.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class LanguageDetectionService {

    public record DetectionResult(String langCode, String scriptName, boolean isEnglish) {}

    /**
     * Fast local script and language detection without LLM overhead.
     * Accurately detects primary Indian scripts by inspecting Unicode blocks.
     */
    public DetectionResult detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return new DetectionResult("en", "Latin", true);
        }

        Map<String, Integer> scriptCounts = new HashMap<>();
        int latinCount = 0;
        int totalChars = 0;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint) || Character.isDigit(codePoint)) {
                continue;
            }

            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            totalChars++;

            if (script == Character.UnicodeScript.LATIN) {
                latinCount++;
            } else if (script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED) {
                scriptCounts.merge(script.name(), 1, Integer::sum);
            }
        }

        if (totalChars == 0 || (double) latinCount / totalChars > 0.6) {
            return new DetectionResult("en", "Latin", true);
        }

        // Find dominant Indic script
        String dominantScript = scriptCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("LATIN");

        return switch (dominantScript) {
            case "DEVANAGARI" -> new DetectionResult("hi", "Devanagari", false);
            case "TAMIL" -> new DetectionResult("ta", "Tamil", false);
            case "TELUGU" -> new DetectionResult("te", "Telugu", false);
            case "BENGALI" -> new DetectionResult("bn", "Bengali", false);
            case "GUJARATI" -> new DetectionResult("gu", "Gujarati", false);
            case "KANNADA" -> new DetectionResult("kn", "Kannada", false);
            case "MALAYALAM" -> new DetectionResult("ml", "Malayalam", false);
            case "GURMUKHI" -> new DetectionResult("pa", "Gurmukhi", false);
            case "ORIYA" -> new DetectionResult("or", "Odia", false);
            default -> new DetectionResult("en", "Latin", true);
        };
    }
}
