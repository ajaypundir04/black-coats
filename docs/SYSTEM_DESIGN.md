# Black-Coat: End-to-End System Design

## 1. Requirements and Sizing

**Functional**
- Users register and log in, then ask legal questions in English or Indian languages, by text or voice.
- Answers are grounded in Indian statutes, with verified section citations.
- Chat history is kept per user, with quotas of 10 queries a day and 100 a month.
- Users can give feedback on answers, export their data, and delete their account.

**Non-functional**
- Zero running cost, using free tiers and open-source software only.
- Load: 5–10 users a day, 2–10 requests each, so at most about 100 requests a day. Peak concurrency is 1–2, and each request is roughly 3–4K prompt tokens.
- Correctness matters most. A wrong or invented section is worse than "I don't know."
- Tolerate cold starts and provider outages gracefully.

## 2. High-Level Architecture

```
┌──────────────┐   HTTPS + SSE    ┌────────────────────────────────────┐
│ black-coat-  │ ───────────────► │ black-coat-api (Spring Boot 4,     │
│ web (Angular │ ◄─────────────── │ Java 25, modular monolith)         │
│ static site) │                  │                                    │
└──────────────┘                  │  Web layer: security filter chain, │
                                  │  rate limiter, controllers, SSE    │
                                  │  ┌──────┐ ┌──────┐ ┌───────────┐   │
                                  │  │ auth │ │ chat │ │    rag    │   │
                                  │  └──────┘ └──────┘ └─────┬─────┘   │
                                  │              LLM adapter chain     │
                                  └───────┬───────────────────┬────────┘
                                          │                   │
                             ┌────────────▼───────┐   ┌───────▼─────────────────┐
                             │ Free Postgres      │   │ Primary free LLM API    │
                             │ pgvector + FTS     │   │ → secondary free API    │
                             │ users, chats,      │   │ → self-hosted Ollama VM │
                             │ usage, law corpus  │   │ + embedding endpoint    │
                             └────────────────────┘   └─────────────────────────┘

Offline (laptop only):
  ingest-cli: fetch → parse → section-split → embed → load into Postgres
  eval-cli:   run golden question set → retrieval and citation metrics
```

**Why a modular monolith:** a free instance can hold one JVM, and the volume doesn't justify network hops. ArchUnit rules keep module boundaries clean (`chat` may call `rag`, but `rag` never calls `chat`), so you can split later.

## 3. Components

### Frontend (Angular, static)
- Standalone components with Signals, and an API client generated from the OpenAPI spec.
- Streaming chat over `fetch` with a stream reader (not `EventSource`, because you need the Authorization header).
- Language switcher, Web Speech API mic button, citation chips that expand to the source text, a disclaimer banner, and a thumbs up/down control.
- A wake-up call to `/health` on page load, with a "server is waking up" state.

### Backend Modules
| Module | Responsibility |
|---|---|
| `auth` | Registration, login, refresh tokens, argon2 hashing, JWT issuing and validation, account deletion |
| `chat` | Conversations, messages, quota enforcement, SSE streaming, feedback |
| `rag` | Language handling, retrieval, prompt building, generation, citation verification |
| `llm` | Provider abstraction, fallback chain, timeouts, circuit breakers (Spring AI plus Resilience4j) |
| `common` | Error model, DTOs, security utilities, config |

### Data and AI
- **Postgres** holds everything: transactional data, the law corpus, vectors (pgvector, HNSW index), and full-text search (GIN on `tsvector`).
- **Embeddings:** one multilingual model (for example BGE-M3) used for both ingestion and queries. Store the model name and version with the corpus, since a mismatch silently ruins retrieval.
- **LLM:** a provider chain, described in section 5.

## 4. Data Model

```sql
users(id uuid pk, email unique, password_hash, display_name, lang_pref,
      created_at, deleted_at)
refresh_tokens(id, user_id fk, token_hash, expires_at, revoked_at)
conversations(id, user_id fk, title, created_at)
messages(id, conversation_id fk, role, content, lang,
         citations jsonb, model, latency_ms, created_at)
usage_daily(user_id fk, day date, count int, primary key(user_id, day))
feedback(message_id fk, rating smallint, comment, created_at)

legal_documents(id, kind, title, short_name, year, source_url,
                license_note, corpus_version)
legal_sections(id, document_id fk, section_no, heading, text,
               text_tsv tsvector, embedding vector(1024), corpus_version)
-- indexes: HNSW on embedding, GIN on text_tsv, unique(document_id, section_no, corpus_version)
```

Flyway manages migrations. `corpus_version` lets you load a new corpus alongside the old one and switch over atomically.

## 5. Core Flow: Asking a Question

1. **Request:** `POST /api/v1/chat/stream` with a JWT, `conversationId` (optional), and the question.
2. **Gate:** the filter chain validates the JWT and applies an in-memory per-IP rate limit.
3. **Quota reserve:** one atomic upsert on `usage_daily` that succeeds only while `count < 10`, plus a monthly sum check. If the request later fails, the reservation is refunded. This is safe under concurrent requests.
4. **Context:** load the last few messages, capped by token budget.
5. **Language:** detect the script locally (no LLM call). If the question isn't English, produce an English retrieval query. Multilingual embeddings handle the vector search directly, but full-text search needs English terms.
6. **Retrieve:** run vector top-20 and full-text top-20, merge with reciprocal rank fusion, and keep the top 6–8 above a similarity threshold. **If nothing passes the threshold, return a fixed "I couldn't find relevant law" response without calling the LLM, and refund the quota.**
7. **Prompt:** a fixed system prompt plus numbered sources:
   ```
   [S1] BNS s.303 — <text>
   [S2] ...
   ```
   The model must cite only as `[S1]`, `[S2]`, and so on, answer in the user's language, and say when the sources are insufficient. User text and legal text are both treated as data, never as instructions.
8. **Generate:** stream tokens over SSE through the provider chain.
9. **Verify:** the backend maps `[S#]` tags to real `legal_sections` rows. Unknown tags are stripped. Section numbers written in free text that don't match any retrieved source are flagged. A final SSE event carries the verified citations.
10. **Persist and respond:** save the message with its citations, model, and latency, then send the `done` event.

**Safety layer:** a lightweight keyword/classifier check flags urgent or criminal situations (arrest, violence, domestic abuse) and appends emergency and legal-aid contact information (e.g. 112 and NALSA 15100 legal aid helpline).
