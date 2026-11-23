# Black-Coat: Render Production Deployment Guide

This guide provides step-by-step instructions to deploy the entire Black-Coat stack (Database with `pgvector`, Spring Boot API, and Angular Web Application) on Render's free tier at **$0 running cost**.

---

## 🏗️ Deployment Topology

```
┌─────────────────────────────────┐
│ Client Browser                  │
└────────┬────────────────────────┘
         │
         ├───► HTTPS (Static UI) ────► Render Static Site (`black-coat-web`)
         │                             (Angular 18 standalone build)
         │
         └───► HTTPS + SSE ──────────► Render Web Service (`black-coat-api`)
                                       (Spring Boot 4, Docker JRE 21 runtime)
                                       │
                        ┌──────────────┴──────────────┐
                        ▼                             ▼
              PostgreSQL (pgvector)          Free LLM Provider Chain
              (Supabase / Neon / Render)     (Gemini Free → Groq Free)
```

---

## 📋 Prerequisites

Before starting, create free accounts on:
1. [Render.com](https://render.com)
2. [Google AI Studio](https://aistudio.google.com) (for free Gemini API Key)
3. [Groq Cloud](https://console.groq.com) (for free Groq API Key)
4. [Supabase](https://supabase.com) or [Neon](https://neon.tech) (for free PostgreSQL with `pgvector`)

---

## Step 1: Set Up Free PostgreSQL with `pgvector`

Render's free database expires after 30 days and does not include pgvector by default. We strongly recommend **Supabase** or **Neon** for permanent free PostgreSQL with `pgvector`.

### 1.1 Create Database on Supabase (Recommended)
1. Log in to [Supabase](https://supabase.com) and click **New Project**.
2. Set a project name (e.g. `black-coat-db`) and generate a strong database password.
3. Choose the region closest to your users (e.g. `Singapore` or `Frankfurt`).
4. Once provisioned, open the **SQL Editor** in the left sidebar and run:
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
   CREATE EXTENSION IF NOT EXISTS "pgcrypto";
   ```
5. Click **Run** to confirm the extensions are enabled.
6. Navigate to **Project Settings** $\rightarrow$ **Database** $\rightarrow$ **Connection string** $\rightarrow$ **URI**.
7. Your JDBC connection URL will look like:
   ```
   jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
   ```

---

## Step 2: Option A — One-Click Blueprint Deployment (Fastest)

Black-Coat includes a pre-configured `render.yaml` specification that provisions both the backend and frontend simultaneously.

1. Go to the [Render Dashboard](https://dashboard.render.com).
2. Click **New +** in the top right $\rightarrow$ **Blueprint**.
3. Connect your GitHub repository: `ajaypundir04/black-coats`.
4. Render will parse `render.yaml` and prompt you for environment variables:
   - `SPRING_DATASOURCE_URL`: Your Supabase JDBC URL.
   - `SPRING_DATASOURCE_USERNAME`: `postgres`
   - `SPRING_DATASOURCE_PASSWORD`: Your database password.
   - `GEMINI_API_KEY`: Your Google AI Studio key.
   - `GROQ_API_KEY`: Your Groq Cloud key.
5. Click **Apply**. Render will automatically build the Docker container for the API and build the Angular static site.

---

## Step 3: Option B — Manual Step-by-Step Deployment

If you prefer configuring services individually:

### 3.1 Deploy the Backend API (`black-coat-api`)
1. On the Render Dashboard, click **New +** $\rightarrow$ **Web Service**.
2. Select your repository `black-coats`.
3. Configure settings:
   - **Name:** `black-coat-api`
   - **Region:** `Oregon` (or matching your database region)
   - **Branch:** `main`
   - **Root Directory:** *(leave blank)*
   - **Runtime:** `Docker`
   - **Dockerfile Path:** `./black-coat-api/Dockerfile`
   - **Docker Build Context:** `./black-coat-api`
   - **Instance Type:** `Free`
4. Expand **Advanced** and set:
   - **Health Check Path:** `/actuator/health`
5. Under **Environment Variables**, add:

| Key | Value / Example | Notes |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db.<ref>.supabase.co:5432/postgres?sslmode=require` | Enable SSL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `<your-db-password>` | DB password |
| `BLACKCOAT_JWT_SECRET` | Generate with `openssl rand -hex 32` | 64-char hex key |
| `GEMINI_API_KEY` | `AIzaSy...` | Free Gemini API key |
| `GROQ_API_KEY` | `gsk_...` | Free Groq API key |
| `LLM_OLLAMA_ENABLED` | `false` | Ollama is local only |
| `BLACKCOAT_CORPUS_VERSION`| `v1.0` | Active corpus version |

6. Click **Create Web Service**.
7. Render will build the container, execute Flyway migrations (`V1__init_schema.sql` and `V2__seed_bns_sample.sql`), and launch Spring Boot on port 8080.
8. Once live, note your backend URL:  
   `https://black-coat-api.onrender.com`

---

### 3.2 Deploy the Angular Frontend (`black-coat-web`)
1. On the Render Dashboard, click **New +** $\rightarrow$ **Static Site**.
2. Select your repository `black-coats`.
3. Configure settings:
   - **Name:** `black-coat-web`
   - **Branch:** `main`
   - **Build Command:**
     ```bash
     cd black-coat-web && npm install && npm run build
     ```
   - **Publish Directory:**
     ```
     ./black-coat-web/dist/black-coat-web
     ```
4. Under **Redirects/Rewrites**, add a rewrite rule for Angular routing:
   - **Type:** `Rewrite`
   - **Source:** `/*`
   - **Destination:** `/index.html`
5. Click **Create Static Site**.
6. Render will compile Angular in production mode and host the static assets across its global CDN:  
   `https://black-coat-web.onrender.com`

---

## Step 4: Verification & Smoke Test

### 4.1 Verify Backend Health
Open your backend URL in your browser:
```
https://black-coat-api.onrender.com/actuator/health
```
Expected response:
```json
{"status":"UP"}
```

### 4.2 Verify Database Migrations in Render Logs
In the Render dashboard for `black-coat-api`, open **Logs** and verify:
```text
Successfully applied 2 migrations to schema 'public'
Current version of schema 'public': 2
Started BlackCoatApiApplication in 8.421 seconds
```

### 4.3 Test Streaming Chat
In a terminal, send a test query:
```bash
curl -N -X POST https://black-coat-api.onrender.com/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the penalty for theft under BNS?"}'
```
You should see incoming Server-Sent Events (`event: token`, `event: citations`, `event: done`).

---

## 🔧 Troubleshooting Common Free Tier Issues

### 1. Free Tier Cold Starts
- **Symptom:** The first request after 15 minutes of inactivity takes 40–50 seconds.
- **Cause:** Render spins down free web services when idle.
- **Mitigation:** The Black-Coat UI features an automatic health ping with an animated *"Server Waking Up..."* status pill. Once awake, virtual threads respond in under 600ms.
- **Permanent Solution:** Set up a free uptime monitor (e.g. [UptimeRobot](https://uptimerobot.com) or [Cron-job.org](https://cron-job.org)) to ping `/actuator/health` every 10 minutes.

### 2. Database Connection SSL Errors
- **Error:** `The server does not support SSL` or `FATAL: no pg_hba.conf entry for host`.
- **Fix:** Ensure your connection string includes `?sslmode=require`:
  ```
  jdbc:postgresql://<host>:5432/postgres?sslmode=require
  ```

### 3. Out of Memory (OOM) on 512MB RAM
- **Mitigation:** `black-coat-api/Dockerfile` has pre-configured JVM memory guards:
  ```
  -XX:MaxRAMPercentage=75.0 -XX:+UseStringDeduplication
  ```
  This restricts the JVM heap to 384MB, leaving plenty of memory for the OS and off-heap operations.
