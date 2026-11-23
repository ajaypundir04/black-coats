# ⚖️ Black-Coat: Grounded Indian Legal AI Assistant

<p align="center">
  <img src="https://raw.githubusercontent.com/ajaypundir04/black-coats/main/docs/assets/banner.png" alt="Black-Coat Banner" width="100%" onerror="this.style.display='none'"/>
</p>

<p align="center">
  <a href="https://github.com/ajaypundir04/black-coats/actions"><img src="https://img.shields.io/badge/build-passing-brightgreen?style=flat-square" alt="Build Status"></a>
  <a href="https://www.oracle.com/java/"><img src="https://img.shields.io/badge/Java-25%20GraalVM-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 25"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.3%20%2F%204.0-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://github.com/pgvector/pgvector"><img src="https://img.shields.io/badge/PostgreSQL-pgvector%20%2B%20FTS-336791?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL pgvector"></a>
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square" alt="License: MIT"></a>
  <a href="https://github.com/ajaypundir04/black-coats/stargazers"><img src="https://img.shields.io/github/stars/ajaypundir04/black-coats?style=flat-square&color=yellow" alt="GitHub Stars"></a>
</p>

---

**Black-Coat** is an open-source, zero-operational-cost Indian Legal AI assistant engineered with strict statutory grounding. Unlike general-purpose LLMs that invent legal sections or cite repealed colonial provisions, Black-Coat guarantees:

> 🛡️ **Zero Hallucination Guarantee:** If verified statutory provisions (BNS 2023, BNSS, BSA, IPC) cannot be retrieved above our similarity threshold, the system **refuses to guess**, terminates the generation early, and refunds the user's query quota.

---

## 🏛️ System Architecture

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
```

Detailed design document: [docs/SYSTEM_DESIGN.md](file:///home/shankar/project/black-coats/docs/SYSTEM_DESIGN.md).

---

## ✨ Key Features

- **Hybrid Retrieval (Dense + Sparse):** pgvector HNSW cosine index top-20 combined with PostgreSQL Full-Text Search (GIN `tsvector`) using **Reciprocal Rank Fusion (RRF)**.
- **Citation Verification Engine:** Parses and validates every statutory reference tag `[S1]`, `[S2]`. Hallucinated tags are stripped, and ungrounded section numbers in free text are automatically flagged.
- **Triple-Redundant Free LLM Provider Chain:**
  $$\text{Gemini 1.5/2.0 Flash (Free)} \xrightarrow{\text{on 429/error}} \text{Groq Llama 3.3 70B (Free)} \xrightarrow{\text{on error}} \text{Local Ollama}$$
  Guarded by Resilience4j circuit breakers, timeouts, and quota refund hooks.
- **Zero-Cost Operation:** Runs on free tiers (Render free web service, Supabase/Neon free PostgreSQL, free LLM tiers).
- **Emergency Safety Layer:** Automatically identifies urgent distress situations (police custody, arrest, domestic violence) and appends official Indian legal aid helplines:
  - 🚨 **112** (All-India Emergency Helpline)
  - ⚖️ **15100** (NALSA 24x7 Free Legal Aid Helpline)
  - 👩 **1091** (National Commission for Women Helpline)
- **DPDP Act 2023 Compliant:** Full data export (`GET /api/v1/me/export`) and complete account deletion (`DELETE /api/v1/me`).
- **Marketing Automation Built-in:** Automated GitHub Actions workflows for multi-channel release announcements, weekly legal tech digests, and community welcome bots.

---

## 📂 Project Structure

```
black-coats/
├── .github/
│   └── workflows/
│       ├── marketing-release-announcer.yml   # Social launch copy & webhooks
│       ├── marketing-weekly-digest.yml       # Automated weekly community digest
│       ├── marketing-community-welcome.yml   # Contributor onboarding & viral share
│       └── ci.yml                            # Build & ArchUnit architecture gate
├── docs/
│   ├── SYSTEM_DESIGN.md                     # Complete architectural specification
│   ├── MARKETING_PLAYBOOK.md                 # Distribution strategy (Reddit, X, HN)
│   └── API_SPEC.md                           # REST & SSE endpoint documentation
├── black-coat-api/                           # Modular Monolith Backend
│   ├── pom.xml                               # Java 25 & Spring Boot configuration
│   └── src/main/
│       ├── java/com/blackcoat/api/
│       │   ├── auth/                         # Registration, login, argon2, JWT
│       │   ├── chat/                         # Conversations, atomic quota, SSE
│       │   ├── rag/                          # Language detect, hybrid search, citations
│       │   ├── llm/                          # Fallback router, Gemini, Groq, Ollama
│       │   └── common/                       # Config, Security, Exception handlers
│       └── resources/
│           ├── application.yml
│           └── db/migration/
│               ├── V1__init_schema.sql       # PostgreSQL + pgvector + atomic quota
│               └── V2__seed_bns_sample.sql   # BNS 2023 baseline sample statutes
└── docker-compose.yml                        # Local Postgres (pgvector) + Ollama
```

---

## 🚀 Quickstart Guide

### One-Command Local Launch (`./local.sh`)
Start PostgreSQL with pgvector, the Spring Boot 4 backend, and the Angular frontend in one command:
```bash
./local.sh
```
This script checks prerequisites, launches containers, waits for health checks, boots the backend on `http://localhost:8080`, and serves the Angular frontend on `http://localhost:4200` with unified log tailing. Press `Ctrl+C` to cleanly stop all services.

### Manual Step-by-Step Launch
#### 1. Start Local Infrastructure
Start PostgreSQL with `pgvector` and Ollama:
```bash
docker compose up -d
```

#### 2. Configure Environment Variables
Copy and set your free API keys:
```bash
export GEMINI_API_KEY="your-free-gemini-key"
export GROQ_API_KEY="your-free-groq-key"
export BLACKCOAT_JWT_SECRET="your-256-bit-hex-secret"
```

#### 3. Run the Backend API
```bash
cd black-coat-api
mvn spring-boot:run
```

#### 4. Run the Angular Frontend
```bash
cd black-coat-web
npm install
npm start
```
Navigate to `http://localhost:4200` to interact with the streaming chat, login/registration, expandable citation drawer, and voice dictation.

Output streams Server-Sent Events (`token`, `citations`, `done`):
```text
event: token
data: {"text":"Under"}

event: token
data: {"text":" Section 303 of the Bharatiya Nyaya Sanhita [S1]..."}

event: citations
data: {"citations":[{"tag":"[S1]","actShortName":"BNS","sectionNo":"303","heading":"Theft","verified":true}]}

event: done
data: {"provider":"gemini","latencyMs":620,"language":"en"}
```

---

## 📢 Growth & Marketing Automation

Black-Coat includes automated GitHub Actions designed to maximize open-source visibility:
- **Release Announcer (`marketing-release-announcer.yml`):** Generates pre-formatted social copy for Twitter/X threads, LinkedIn posts, and Reddit submissions on every release.
- **Weekly Digest (`marketing-weekly-digest.yml`):** Runs every Monday at 09:00 UTC, summarizing recent commits and new statutes ingested for newsletter cross-posting.
- **Community Welcome (`marketing-community-welcome.yml`):** Greets first-time contributors with links to docs and one-click Twitter/LinkedIn share intents.

Read the complete distribution strategy in [docs/MARKETING_PLAYBOOK.md](file:///home/shankar/project/black-coats/docs/MARKETING_PLAYBOOK.md).

---

## 📜 Legal Disclaimer

*Black-Coat is an open-source educational and informational legal technology tool. It does not provide legal representation or formal advocate-client advice. For individual legal disputes, consult a licensed advocate or contact the National Legal Services Authority (NALSA) at 15100.*

---

## 📄 License

Licensed under the [MIT License](LICENSE). Contributions, PRs, and issues are warmly welcomed!
