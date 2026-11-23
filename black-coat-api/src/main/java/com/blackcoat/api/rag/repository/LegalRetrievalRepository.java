package com.blackcoat.api.rag.repository;

import com.blackcoat.api.rag.model.LegalSectionDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class LegalRetrievalRepository {

    private static final Logger log = LoggerFactory.getLogger(LegalRetrievalRepository.class);
    private static final int RRF_K = 60; // Standard reciprocal rank fusion smoothing constant

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
        // 1. Vector Search Top 20
        List<ScoredDoc> vectorHits = searchVector(queryEmbedding, 20);

        // 2. Full-Text Search Top 20
        List<ScoredDoc> ftsHits = searchFullText(queryText, 20);

        // 3. Reciprocal Rank Fusion (RRF)
        Map<UUID, RrfAccumulator> rrfMap = new LinkedHashMap<>();

        // Score vector hits
        for (int rank = 0; rank < vectorHits.size(); rank++) {
            ScoredDoc doc = vectorHits.get(rank);
            double rrfScore = 1.0 / (RRF_K + (rank + 1));
            rrfMap.computeIfAbsent(doc.id(), id -> new RrfAccumulator(doc))
                  .addScore(rrfScore, doc.rawScore());
        }

        // Score FTS hits
        for (int rank = 0; rank < ftsHits.size(); rank++) {
            ScoredDoc doc = ftsHits.get(rank);
            double rrfScore = 1.0 / (RRF_K + (rank + 1));
            rrfMap.computeIfAbsent(doc.id(), id -> new RrfAccumulator(doc))
                  .addScore(rrfScore, doc.rawScore());
        }

        // Sort by RRF score descending and take top K
        List<LegalSectionDto> mergedResults = new ArrayList<>();
        int rankIndex = 1;
        for (RrfAccumulator acc : rrfMap.values().stream()
                .sorted(Comparator.comparingDouble(RrfAccumulator::getTotalRrfScore).reversed())
                .limit(topK)
                .toList()) {

            // Threshold gate: check vector similarity or FTS relevance
            if (acc.doc.rawScore() >= similarityThreshold || acc.totalRrfScore > 0.02) {
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
            log.warn("Vector search failed (falling back to FTS-only): {}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ScoredDoc> searchFullText(String queryText, int limit) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }

        try {
            String sql = """
                SELECT s.id, d.short_name, s.section_no, s.heading, s.text,
                       ts_rank_cd(s.text_tsv, websearch_to_tsquery('english', :q)) AS fts_score
                FROM legal_sections s
                JOIN legal_documents d ON s.document_id = d.id
                WHERE s.corpus_version = :corpusVersion
                  AND s.text_tsv @@ websearch_to_tsquery('english', :q)
                ORDER BY fts_score DESC
                LIMIT :limit
                """;

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("q", queryText);
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
            log.warn("Full-text search query failed: {}", ex.getMessage());
            return List.of();
        }
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
        double maxRawScore = 0.0;

        RrfAccumulator(ScoredDoc doc) {
            this.doc = doc;
        }

        void addScore(double rrf, double raw) {
            this.totalRrfScore += rrf;
            if (raw > maxRawScore) {
                this.maxRawScore = raw;
            }
        }

        double getTotalRrfScore() {
            return totalRrfScore;
        }
    }
}
