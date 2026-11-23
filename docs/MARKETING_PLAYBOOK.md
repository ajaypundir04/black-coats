# Black-Coat: Developer Marketing & Growth Playbook

This playbook provides actionable distribution strategies, social copy, and community outreach templates to grow **Black-Coat** into the leading open-source Indian Legal AI project.

---

## 1. Core Narrative & Positioning

| Dimension | Description |
|---|---|
| **One-Liner** | The zero-cost, hallucination-resistant Indian legal assistant grounded in verified statutes. |
| **Primary Problem** | General-purpose LLMs hallucinate non-existent sections, cite repealed colonial laws (e.g. IPC instead of BNS 2023), and cost hundreds of dollars in cloud infrastructure. |
| **Our Solution** | A modular monolith (Spring Boot 4, Java 25, pgvector) with hybrid retrieval, citation verification, automatic provider failover (Gemini Free → Groq → Ollama), and a strict refusal path. |
| **Target Audience** | 1. Developers & open-source AI builders in India.<br>2. Law students & young advocates (NLUs, state law colleges).<br>3. Pro-bono legal clinics & citizens needing statutory guidance. |

---

## 2. Reddit Distribution Kit

### Post A: r/developersIndia (Technical Focus)
**Title:**
> *I built a zero-cost, hallucination-resistant Indian Legal AI assistant using Spring Boot 4, Java 25, and pgvector (Showcase / Open Source)*

**Body:**
```markdown
Namaste r/developersIndia!

Like many of you, I've tested ChatGPT on Indian law questions and watched it confidently invent Section numbers or cite repealed IPC provisions that don't exist in the new Bharatiya Nyaya Sanhita (BNS 2023). In law, an invented section is worse than "I don't know."

To solve this without spending a single rupee on cloud bills, I built **Black-Coat**: an open-source, modular monolith engineered for statutory correctness.

### Key Architecture Decisions:
1. **Zero Running Cost:** Designed entirely around free tiers.
   - Primary: Gemini Free API
   - Secondary: Groq / OpenRouter Free API
   - Offline / Local: Self-hosted Ollama on any cheap VM or laptop.
2. **Hybrid Retrieval (pgvector + FTS):** Combines dense vector similarity (BGE-M3 embeddings on HNSW index) with PostgreSQL full-text search (GIN on tsvector) using Reciprocal Rank Fusion (RRF).
3. **Hard Refusal Gate:** If no statutory sections pass our 0.65 similarity threshold, the system immediately returns an "insufficient law found" reply—zero LLM calls made, zero quota spent.
4. **Citation Verification Engine:** Every generated citation tag `[S#]` is strictly checked against retrieved statutes. Hallucinated tags are stripped, and ungrounded sections are flagged.
5. **Safety Net:** Detects emergency legal situations (arrest, custody, domestic violence) and appends official Indian helplines (NALSA 15100, Emergency 112).

Tech stack: Java 25 (GraalVM), Spring Boot 4, PostgreSQL 16 + pgvector, Angular standalone signals.

GitHub Repo: https://github.com/ajaypundir04/black-coats
Would love your feedback on the architecture and pull requests!
```

---

### Post B: r/LegalAdviceIndia (Practical / Accuracy Focus)
**Title:**
> *An open-source legal assistant that actually verifies Indian section citations before answering (BNS 2023 / BNSS / BSA)*

**Body:**
```markdown
Hello everyone,

One of the biggest risks of using commercial AI for Indian legal queries is hallucination—models inventing fake sections or mixing up the new 2023 criminal codes with older colonial statutes.

We open-sourced **Black-Coat** to address this. Every answer is grounded directly in India Code statutes, and every citation tag is verified against the statutory text before it reaches your screen.

If the system cannot locate verified law matching your question, it honestly tells you it doesn't have sufficient sources instead of making something up. Furthermore, any query involving police custody or domestic distress automatically links to NALSA's 24x7 Free Legal Aid helpline (15100) and 112.

Check out the project: https://github.com/ajaypundir04/black-coats
(Note: Black-Coat is an informational educational tool and not a replacement for formal legal counsel from an enrolled advocate).
```

---

## 3. Twitter / X Launch Thread

```text
1/10 ⚖️ Most AI legal assistants have a fatal flaw: they hallucinate section numbers.

In Indian law, citing a fake section in a bail petition or notice is catastrophic.

Today, we're open-sourcing Black-Coat: A zero-cost, hallucination-resistant Indian Legal AI assistant. 🧵👇

2/10 🎯 The Core Problem:
Commercial LLMs routinely blend repealed IPC sections with the new Bharatiya Nyaya Sanhita (BNS 2023) or hallucinate non-existent penalties.

Black-Coat enforces a strict rule: Grounding in verified statutes > guessing.

3/10 🏗️ The Architecture:
Built as a Spring Boot 4 + Java 25 modular monolith running on a single free JVM instance:
• Web Layer: SSE streaming + JWT auth
• DB: PostgreSQL 16 + pgvector (HNSW) + Full-Text Search (GIN)
• Resilience: Resilience4j circuit breakers

4/10 🔍 Hybrid Retrieval (Dense + Sparse):
We don't rely solely on vector embeddings. Black-Coat executes:
1. Dense Vector Top-20 (HNSW cosine distance)
2. Sparse FTS Top-20 (tsvector @@ websearch)
Merged using Reciprocal Rank Fusion (RRF, k=60).

5/10 🛡️ The Hard Refusal Gate:
If no statutory section passes our similarity threshold (0.65), Black-Coat terminates the request immediately.
No LLM call. Zero token waste. 100% quota refund.
No hallucinations allowed.

6/10 ✅ Citation Verification Engine:
The model is constrained to cite only `[S1]`, `[S2]`.
A post-processing verification engine maps each tag against the database, strips hallucinations, and flags any ungrounded sections in free text.

7/10 🔄 Triple-Redundant Free LLM Chain:
Zero cloud bills:
1. Gemini 1.5/2.0 Flash (Free Tier)
   ↓ (on 429/error)
2. Groq Llama 3.3 70B (Free Tier)
   ↓ (on error)
3. Local Self-Hosted Ollama

8/10 🚨 Safety & Legal Aid:
When questions involve urgent distress (arrest, custody, domestic violence), Black-Coat automatically connects users with @NALSA_IN Free Legal Aid Helpline (15100) and Emergency (112).

9/10 🇮🇳 Multilingual & DPDP Act 2023 Compliant:
Supports Indian language script detection (Hindi, Tamil, Bengali, Telugu, etc.), data export, and complete account deletion endpoints.

10/10 ⭐ The entire project is open-source under MIT / Apache 2.0.

Star the repo and try it locally with docker-compose:
👉 https://github.com/ajaypundir04/black-coats

Retweet the first tweet if you believe legal information should be accessible and verified! 🔁
```

---

## 4. Hacker News: Show HN Template

**Title:**
> `Show HN: Black-Coat – Zero-cost, hallucination-resistant Indian legal AI`

**Submission Text:**
```text
Hi HN,

I built Black-Coat, an open-source Indian legal AI assistant designed to run on free tiers with zero infrastructure cost while solving the legal hallucination problem.

Key technical choices:
- Backend: Modular monolith in Java 25 (GraalVM) and Spring Boot 4.
- Storage: Single PostgreSQL instance using pgvector with HNSW index for dense search and GIN tsvector for BM25-like full-text search, fused via Reciprocal Rank Fusion (RRF).
- Grounding: Prompt treats user text and legal statutes strictly as data. The model must cite as [S#]. A parser verifies all tags against database IDs and strips any hallucinated tags.
- Free Tier Failover: Circuit breaker router cascading Gemini Free -> Groq Free -> Ollama local.
- Concurrency & Quotas: Safe atomic upsert with monthly cap check, plus automatic refund if downstream retrieval refuses.

Repo: https://github.com/ajaypundir04/black-coats
Architecture doc: https://github.com/ajaypundir04/black-coats/blob/main/docs/SYSTEM_DESIGN.md

Feedback and code critique welcome!
```

---

## 5. Law School & Pro-Bono Outreach Template

**Subject:** Open-source legal research tool for Indian Law Students & Legal Aid Clinics

**Body:**
```text
Dear Team / Legal Aid Society,

We are developing Black-Coat, an open-source, non-profit Indian legal research assistant engineered to provide verified statutory provisions from the Bharatiya Nyaya Sanhita (BNS 2023), BNSS, BSA, and Indian Penal Code.

Unlike commercial AI models that often invent section numbers, Black-Coat verifies every citation against India Code statutes and includes direct links to NALSA's 24x7 Free Legal Aid helpline (15100).

The software is completely open-source and free to deploy for student research and pro-bono clinics:
https://github.com/ajaypundir04/black-coats

We would welcome the opportunity to collaborate with your student legal aid cell to expand statute coverage.
```
