# Black-Coat: Future Modifications & Engineering Roadmap

This document outlines prioritized modifications, planned feature sets, and infrastructure enhancements for future iterations of Black-Coat.

---

## 📅 Roadmap Overview

| Phase | Milestone | Focus Areas | Timeline |
|---|---|---|---|
| **Phase 1** | Near-Term Hardening | Automated Ingestion CLI, RAGAS Evaluation Harness, PDF upload | Weeks 1–4 |
| **Phase 2** | Accessibility & Outreach | WhatsApp / Telegram bot, Bhashini Voice pipeline, Offline Tauri app | Months 2–3 |
| **Phase 3** | Legal Practice Automation | Automated Bail & FIR Draft generator, e-Courts API integration | Months 4–6 |
| **Phase 4** | Pro & Enterprise | Advocate Directory, UPI/Razorpay monetization, Law Firm Workspaces | Months 6+ |

---

## 1. Phase 1: Near-Term Engineering Modifications

### 1.1 Automated Ingestion CLI (`ingest-cli`)
- **Automated Web Scraping:** Scheduled scraper for India Code (`indiacode.nic.in`) to detect and ingest newly notified Central and State amendments.
- **Section Parsing Engine:** Multi-column PDF extraction using Apache PDFBox / Tika with regex recognition of Schedules, Tables, and Explanations.
- **Atomic Corpus Versioning:** Tooling to embed acts using `BGE-M3` and load them alongside the live database with zero-downtime corpus version switching (`v1.0` → `v1.1`).

### 1.2 Automated Evaluation Harness (`eval-cli`)
- **Golden Question Benchmark:** 100 benchmark legal questions across civil, criminal, and constitutional law in English and Hindi.
- **Metrics Tracked:**
  - *Context Precision & Recall:* Proportion of ground-truth statutory sections retrieved.
  - *Faithfulness:* Grounding score measuring absence of hallucinations.
  - *Citation Validity Rate:* Percentage of `[S#]` tags that match verified laws.
- **Regression Blocker:** CI pipeline gate blocking prompt or corpus updates that degrade retrieval hit rate below 92%.

---

## 2. Phase 2: Accessibility & Inclusive Channels

### 2.1 Bhashini Government API Voice Pipeline
- **Problem:** Many Indian citizens in rural jurisdictions communicate exclusively in regional dialects and may not be literate in formal legal writing.
- **Modification:** Integrate Government of India's **Bhashini (National Language Translation Mission)** API:
  - Speech-to-Text (ASR) across 22 scheduled Indian languages (Bhojpuri, Maithili, Odia, Marathi, Telugu, Tamil, etc.).
  - Translate to English for hybrid search.
  - Grounded answer translated back to native tongue.
  - Text-to-Speech (TTS) voice playback of verified legal rights.

### 2.2 WhatsApp & Telegram Legal Aid Bot
- **Implementation:** Connect Twilio WhatsApp Business API and Telegram Bot API to `black-coat-api`.
- **Interaction Model:**
  - Citizens can send text or voice notes to a verified WhatsApp legal aid helpline number.
  - Returns concise, grounded answers with emergency helpline numbers directly in chat.

### 2.3 Offline Desktop Application (Tauri + SQLite-vec)
- **Use Case:** Remote court complexes and taluka advocates with intermittent internet connectivity.
- **Architecture:** Lightweight desktop application built using Tauri (Rust + Angular):
  - Local SQLite with `sqlite-vec` extension holding pre-embedded BNS statutes.
  - Bundled local quantized SLM (e.g. Llama-3.2-3B via llama.cpp).
  - 100% offline operation without any cloud dependencies.

---

## 3. Phase 3: Legal Practice Automation

### 3.1 Automated Bail Application & Legal Notice Draft Generator
- **Feature:** Based on the verified statutory sections and user facts, automatically generate standard court drafts:
  - Regular Bail Application under Section 480 of BNSS (formerly Section 437 CrPC).
  - Anticipatory Bail Application under Section 482 of BNSS (formerly Section 438 CrPC).
  - Legal Demand Notice under Section 138 of Negotiable Instruments Act (Cheque Bounce).
- **Format:** Exportable in ready-to-file legal green sheet layout (`.docx` / `.pdf`).

### 3.2 e-Courts National Case Status Integration
- **Feature:** Query real-time court dates, causelists, and orders from Supreme Court, High Courts, and District Courts via national e-Courts APIs using CNR (Case Number Record).

---

## 4. Phase 4: Sustainability & Monetization

```
┌────────────────────────────────────────────────────────┐
│ Black-Coat Public Tier (100% Free Forever)             │
│ • 10 queries/day for citizens & law students           │
│ • Grounded statutory answers with helpline links       │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ Black-Coat Pro (Advocates & Law Firms)                 │
│ • Unlimited queries & full Supreme Court case search   │
│ • Automated petition drafting & client document upload │
│ • Razorpay / UPI monthly subscription (e.g. ₹499/mo)   │
└────────────────────────────────────────────────────────┘
```

- **Advocate Network Directory:** Allow licensed advocates to list their pro-bono availability and connect with citizens whose queries require court representation.
- **Payment Gateway:** Seamless micro-transactions via UPI (PhonePe, Google Pay, Paytm) and Razorpay for Pro tier features.
