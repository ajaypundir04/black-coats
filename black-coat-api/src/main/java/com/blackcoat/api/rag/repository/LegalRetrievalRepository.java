package com.blackcoat.api.rag.repository;

import com.blackcoat.api.rag.model.LegalSectionDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
public class LegalRetrievalRepository {

    private static final Logger log = LoggerFactory.getLogger(LegalRetrievalRepository.class);
    private static final int RRF_K = 60; // Reciprocal Rank Fusion constant

    private static final Pattern SECTION_PATTERN =
        Pattern.compile("(?i)(?:section|sec|s\\.?)\\s*(\\d+[A-Za-z]?)");

    private static final Pattern ACT_AND_SECTION_PATTERN =
        Pattern.compile("(?i)\\b(?:bns|bnss|bsa|hma|sma|ipc|crpc)\\s*(\\d+[A-Za-z]?)\\b");

    private static final Pattern STANDALONE_NUM_PATTERN =
        Pattern.compile("\\b(\\d{1,4}[A-Za-z]?)\\b");

    private static final Pattern DOC_CODE_PATTERN =
        Pattern.compile("(?i)\\b(BNS|BNSS|BSA|IPC|CrPC|HMA|SMA|CPC)\\b");

    private static final Set<String> COMMON_YEARS = Set.of(
        "1860", "1872", "1950", "1954", "1955", "1956", "1973", "2023", "2024"
    );

    private static final Set<String> NOISE_WORDS = Set.of(
        "what", "is", "the", "for", "under", "in", "to", "of", "and", "a", "an",
        "tell", "me", "about", "how", "can", "does", "explain", "rules", "law",
        "act", "section", "sec", "give", "details", "defined", "definition", "state", "indian", "provisions"
    );

    private final EntityManager entityManager;
    private final String corpusVersion;
    private final double similarityThreshold;
    private final int topK;

    public LegalRetrievalRepository(
        EntityManager entityManager,
        @Value("${blackcoat.corpus.active-version:v1.0}") String corpusVersion,
        @Value("${blackcoat.corpus.similarity-threshold:0.65}") double similarityThreshold,
        @Value("${blackcoat.corpus.top-k-results:6}") int topK
    ) {
        this.entityManager = entityManager;
        this.corpusVersion = corpusVersion;
        this.similarityThreshold = similarityThreshold;
        this.topK = topK;
    }

    public List<LegalSectionDto> searchHybrid(String queryText, float[] queryEmbedding) {
        // 1. Vector Search Top 20 (if embedding available)
        List<ScoredDoc> vectorHits = searchVector(queryEmbedding, 20);

        // 2. Full-Text Search Top 20 (smart keywords + section matching)
        List<ScoredDoc> ftsHits = searchFullText(queryText, 20);

        // 3. Reciprocal Rank Fusion (RRF)
        Map<UUID, RrfAccumulator> rrfMap = new LinkedHashMap<>();

        // Score vector hits
        for (int rank = 0; rank < vectorHits.size(); rank++) {
            ScoredDoc doc = vectorHits.get(rank);
            double rrfScore = 1.0 / (RRF_K + (rank + 1));
            rrfMap.computeIfAbsent(doc.id(), id -> new RrfAccumulator(doc))
                  .addVectorScore(rrfScore, doc.rawScore());
        }

        // Score FTS hits
        for (int rank = 0; rank < ftsHits.size(); rank++) {
            ScoredDoc doc = ftsHits.get(rank);
            double rrfScore = 1.0 / (RRF_K + (rank + 1));
            rrfMap.computeIfAbsent(doc.id(), id -> new RrfAccumulator(doc))
                  .addFtsScore(rrfScore, doc.rawScore());
        }

        // Sort by RRF score descending and apply threshold gate
        List<LegalSectionDto> mergedResults = new ArrayList<>();
        int rankIndex = 1;

        for (RrfAccumulator acc : rrfMap.values().stream()
                .sorted(Comparator.comparingDouble(RrfAccumulator::getTotalRrfScore).reversed())
                .limit(topK)
                .toList()) {

            // Threshold gate:
            // - Vector match passes if cosine similarity >= similarityThreshold (0.65)
            // - FTS match passes if ftsScore > 0.0
            if (acc.maxVectorScore >= similarityThreshold || acc.maxFtsScore > 0.0) {
                mergedResults.add(new LegalSectionDto(
                    acc.doc.id(),
                    acc.doc.shortName(),
                    acc.doc.sectionNo(),
                    acc.doc.heading(),
                    acc.doc.text(),
                    acc.totalRrfScore,
                    rankIndex++
                ));
            }
        }

        log.info("Hybrid search for query '{}' yielded {} relevant legal sections (vector: {}, fts: {})",
            queryText, mergedResults.size(), vectorHits.size(), ftsHits.size());

        return mergedResults;
    }

    @SuppressWarnings("unchecked")
    private List<ScoredDoc> searchVector(float[] queryEmbedding, int limit) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return List.of();
        }

        try {
            String embeddingString = Arrays.toString(queryEmbedding);

            String sql = """
                SELECT s.id, d.short_name, s.section_no, s.heading, s.text,
                       1 - (s.embedding <=> cast(:embedding as vector)) AS sim_score
                FROM legal_sections s
                JOIN legal_documents d ON s.document_id = d.id
                WHERE s.corpus_version = :corpusVersion
                  AND s.embedding IS NOT NULL
                ORDER BY s.embedding <=> cast(:embedding as vector) ASC
                LIMIT :limit
                """;

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("embedding", embeddingString);
            query.setParameter("corpusVersion", corpusVersion);
            query.setParameter("limit", limit);

            List<Object[]> rows = query.getResultList();
            List<ScoredDoc> docs = new ArrayList<>(rows.size());
            for (Object[] r : rows) {
                docs.add(new ScoredDoc(
                    (UUID) r[0],
                    (String) r[1],
                    (String) r[2],
                    (String) r[3],
                    (String) r[4],
                    ((Number) r[5]).doubleValue()
                ));
            }
            return docs;
        } catch (Exception ex) {
            log.warn("Vector search failed (falling back to FTS): {}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ScoredDoc> searchFullText(String queryText, int limit) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }

        try {
            String exactSection = extractSectionNumber(queryText);
            String docCode = extractDocCode(queryText);
            String websearchOr = buildWebsearchOrQuery(queryText);
            String headingPattern = extractHeadingPattern(queryText, exactSection);

            log.debug("FTS analysis - exactSection: {}, docCode: {}, websearchOr: {}, headingPattern: {}",
                exactSection, docCode, websearchOr, headingPattern);

            String sql = """
                SELECT s.id, d.short_name, s.section_no, s.heading, s.text,
                       (
                         CASE WHEN s.section_no = :exactSection THEN 3.0 ELSE 0.0 END
                         + CASE WHEN lower(d.short_name) = lower(:docCode) THEN 1.0 ELSE 0.0 END
                         + COALESCE(ts_rank_cd(s.text_tsv, plainto_tsquery('english', :rawQuery)), 0.0) * 2.0
                         + COALESCE(ts_rank_cd(s.text_tsv, websearch_to_tsquery('english', :websearchOr)), 0.0) * 1.5
                       ) AS fts_score
                FROM legal_sections s
                JOIN legal_documents d ON s.document_id = d.id
                WHERE s.corpus_version = :corpusVersion
                  AND (
                    s.section_no = :exactSection
                    OR s.text_tsv @@ plainto_tsquery('english', :rawQuery)
                    OR s.text_tsv @@ websearch_to_tsquery('english', :websearchOr)
                    OR lower(s.heading) LIKE lower(:headingPattern)
                  )
                ORDER BY fts_score DESC
                LIMIT :limit
                """;

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("corpusVersion", corpusVersion);
            query.setParameter("rawQuery", queryText.trim());
            query.setParameter("exactSection", exactSection != null ? exactSection : "__NONE__");
            query.setParameter("docCode", docCode != null ? docCode : "__NONE__");
            query.setParameter("websearchOr", websearchOr);
            query.setParameter("headingPattern", headingPattern);
            query.setParameter("limit", limit);

            List<Object[]> rows = query.getResultList();
            List<ScoredDoc> docs = new ArrayList<>(rows.size());
            for (Object[] r : rows) {
                docs.add(new ScoredDoc(
                    (UUID) r[0],
                    (String) r[1],
                    (String) r[2],
                    (String) r[3],
                    (String) r[4],
                    ((Number) r[5]).doubleValue()
                ));
            }
            return docs;
        } catch (Exception ex) {
            log.warn("Full-text search query failed, falling back to simple ILIKE: {}", ex.getMessage());
            return searchFallbackSimple(queryText, limit);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ScoredDoc> searchFallbackSimple(String queryText, int limit) {
        try {
            String exactSec = extractSectionNumber(queryText);
            String headingPattern = extractHeadingPattern(queryText, exactSec);
            String sql = """
                SELECT s.id, d.short_name, s.section_no, s.heading, s.text, 0.5 AS fts_score
                FROM legal_sections s
                JOIN legal_documents d ON s.document_id = d.id
                WHERE s.corpus_version = :corpusVersion
                  AND (
                    s.section_no = :exactSec
                    OR lower(s.heading) LIKE lower(:pattern)
                    OR lower(s.text) LIKE lower(:pattern)
                  )
                LIMIT :limit
                """;
            Query q = entityManager.createNativeQuery(sql);
            q.setParameter("corpusVersion", corpusVersion);
            q.setParameter("exactSec", exactSec != null ? exactSec : "__NONE__");
            q.setParameter("pattern", headingPattern);
            q.setParameter("limit", limit);

            List<Object[]> rows = q.getResultList();
            List<ScoredDoc> docs = new ArrayList<>(rows.size());
            for (Object[] r : rows) {
                docs.add(new ScoredDoc(
                    (UUID) r[0],
                    (String) r[1],
                    (String) r[2],
                    (String) r[3],
                    (String) r[4],
                    0.5
                ));
            }
            return docs;
        } catch (Exception e) {
            log.error("Fallback search failed completely: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractSectionNumber(String query) {
        Matcher m1 = SECTION_PATTERN.matcher(query);
        if (m1.find()) {
            return m1.group(1);
        }
        Matcher m2 = ACT_AND_SECTION_PATTERN.matcher(query);
        if (m2.find()) {
            return m2.group(1);
        }
        Matcher m3 = STANDALONE_NUM_PATTERN.matcher(query);
        while (m3.find()) {
            String candidate = m3.group(1);
            if (!COMMON_YEARS.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String extractDocCode(String query) {
        Matcher m = DOC_CODE_PATTERN.matcher(query);
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    private String extractHeadingPattern(String query, String exactSection) {
        if (exactSection != null) {
            return "%" + exactSection + "%";
        }
        String[] words = query.replaceAll("[^a-zA-Z0-9\\s]", " ").split("\\s+");
        for (String w : words) {
            String clean = w.trim().toLowerCase();
            if (clean.length() >= 4 && !NOISE_WORDS.contains(clean)) {
                return "%" + clean + "%";
            }
        }
        return "%___none___%";
    }

    private String buildWebsearchOrQuery(String query) {
        String[] words = query.replaceAll("[^a-zA-Z0-9\\s]", " ").split("\\s+");
        String joined = Arrays.stream(words)
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(w -> w.length() >= 3 && !NOISE_WORDS.contains(w) && !COMMON_YEARS.contains(w))
            .distinct()
            .collect(Collectors.joining(" or "));
        return joined.isBlank() ? "law" : joined;
    }

    private record ScoredDoc(
        UUID id,
        String shortName,
        String sectionNo,
        String heading,
        String text,
        double rawScore
    ) {}

    private static class RrfAccumulator {
        final ScoredDoc doc;
        double totalRrfScore = 0.0;
        double maxVectorScore = 0.0;
        double maxFtsScore = 0.0;

        RrfAccumulator(ScoredDoc doc) {
            this.doc = doc;
        }

        void addVectorScore(double rrf, double sim) {
            this.totalRrfScore += rrf;
            if (sim > maxVectorScore) {
                this.maxVectorScore = sim;
            }
        }

        void addFtsScore(double rrf, double ftsScore) {
            this.totalRrfScore += rrf;
            if (ftsScore > maxFtsScore) {
                this.maxFtsScore = ftsScore;
            }
        }

        double getTotalRrfScore() {
            return totalRrfScore;
        }
    }
}
