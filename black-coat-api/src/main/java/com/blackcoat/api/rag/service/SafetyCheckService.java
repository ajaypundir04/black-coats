package com.blackcoat.api.rag.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class SafetyCheckService {

    public record SafetyEvaluation(
        boolean urgentFlag,
        List<String> categories,
        String advisoryNotice
    ) {}

    private static final Pattern DOMESTIC_VIOLENCE = Pattern.compile(
        "(?i)\\b(domestic violence|dowry|marital rape|husband beating|in-laws harassment|दहेज|घरेलू हिंसा)\\b"
    );

    private static final Pattern ARREST_CUSTODY = Pattern.compile(
        "(?i)\\b(police arrest|custody|detained|police brutality|remand|lockup|illegal arrest|गिरफ्तारी|हिरासत)\\b"
    );

    private static final Pattern SEXUAL_OFFENSE = Pattern.compile(
        "(?i)\\b(rape|molestation|sexual assault|pocso|eve teasing|यौन शोषण|बलात्कार)\\b"
    );

    private static final Pattern SELF_HARM = Pattern.compile(
        "(?i)\\b(suicide|kill myself|end my life|hanging|आत्महत्या)\\b"
    );

    public SafetyEvaluation evaluate(String text) {
        if (text == null || text.isBlank()) {
            return new SafetyEvaluation(false, List.of(), null);
        }

        List<String> categories = new ArrayList<>();

        if (DOMESTIC_VIOLENCE.matcher(text).find()) {
            categories.add("DOMESTIC_VIOLENCE");
        }
        if (ARREST_CUSTODY.matcher(text).find()) {
            categories.add("ARREST_AND_DETENTION");
        }
        if (SEXUAL_OFFENSE.matcher(text).find()) {
            categories.add("SEXUAL_OFFENSE");
        }
        if (SELF_HARM.matcher(text).find()) {
            categories.add("SELF_HARM");
        }

        if (categories.isEmpty()) {
            return new SafetyEvaluation(false, List.of(), null);
        }

        String advisory = buildEmergencyNotice(categories);
        return new SafetyEvaluation(true, categories, advisory);
    }

    private String buildEmergencyNotice(List<String> categories) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n---\n");
        sb.append("⚠️ **EMERGENCY LEGAL & SAFETY ASSISTANCE (INDIA)**\n");
        sb.append("If you or someone you know is in immediate danger or facing unlawful detention:\n");
        sb.append("• **All-India Emergency Helpline:** Dial **112** (Police, Medical, Fire)\n");
        sb.append("• **NALSA Free Legal Aid (National Legal Services Authority):** Dial **15100** (Toll-Free, 24x7)\n");

        if (categories.contains("DOMESTIC_VIOLENCE") || categories.contains("SEXUAL_OFFENSE")) {
            sb.append("• **Women Helpline (NCW):** Dial **1091** or WhatsApp **+91-7827170170**\n");
        }
        if (categories.contains("SELF_HARM")) {
            sb.append("• **Tele-MANAS Mental Health Helpline:** Dial **14416** (24x7 Free Support)\n");
        }
        sb.append("Under the Legal Services Authorities Act, 1987, free legal aid is a statutory right for women, children, and persons in custody.\n");

        return sb.toString();
    }
}
