# Internship Application Coordinator — Backend

Java 21 + Spring Boot API with Vertex AI (Gemini) agents for internship application processing.

## Documentation

| Document | Description |
|----------|-------------|
| [PROJECT_OVERVIEW.md](./PROJECT_OVERVIEW.md) | Architecture, API contract, shared FE/BE setup |
| [BACKEND_TASKS.md](./BACKEND_TASKS.md) | Backend task plan |
| [docs/TEST_REPORT.template.md](./docs/TEST_REPORT.template.md) | Fillable 40-case evaluation report (extraction & rule metrics) |

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21 | `./mvnw` uses the project wrapper; no global Maven required |
| Docker | recent | PostgreSQL via `docker compose` |
| Python 3 | optional | Pretty-prints JSON from helper scripts (`python3 -m json.tool`) |

For Gemini-powered features (PDF extraction, recommendations, email drafts):

- GCP project with **Vertex AI API** enabled
- Service account JSON with Vertex AI access
- Set `VERTEX_AI_ENABLED=true` in `.env.local` (see below)

## First-time setup

### 1. Clone and enter the repo

```bash
git clone https://github.com/aykutemreyalcin/internship_application_coordinator_be.git
cd internship_application_coordinator_be
```

### 2. Environment variables

Copy the example file and edit values for your machine:

```bash
cp .env.example .env.local
```

Spring Boot loads `.env.local` automatically (`spring.config.import` in `application.yml`).

**Minimum for local API (no Gemini):**

```properties
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=internship_coordinator
POSTGRES_USER=coordinator
POSTGRES_PASSWORD=coordinator
VERTEX_AI_ENABLED=false
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

**To enable Gemini agents**, also set:

```properties
VERTEX_AI_ENABLED=true
GOOGLE_APPLICATION_CREDENTIALS=./credentials/your-service-account.json
GCP_PROJECT_ID=your-gcp-project
GCP_REGION=europe-west1
GCP_GEMINI_MODEL=gemini-2.5-flash
```

Place the service account JSON under `credentials/` (gitignored). Never commit real keys.

**Optional features:**

| Variable | Default | Purpose |
|----------|---------|---------|
| `UPLOAD_DIR` | `uploads` | PDF storage directory |
| `MAX_UPLOAD_SIZE_BYTES` | `10485760` | Max upload size (10 MB) |
| `UNIVERSITY_RULES_CONFIG_PATH` | `classpath:university-rules.json` | Rules engine config |
| `EMAIL_INTAKE_ENABLED` | `false` | IMAP email intake (BE-16) |
| `TEST_DATASET_ENABLED` | `false` | 40-case seed endpoints (BE-18) |

See [.env.example](./.env.example) for the full list including Gmail IMAP settings.

### 3. Start PostgreSQL

```bash
docker compose up -d
docker compose ps   # wait until postgres is healthy
```

Database defaults match `.env.example`: `internship_coordinator` on port `5432`.

Flyway migrations run automatically on startup (`spring.flyway.enabled=true`).

### 4. Run the API

```bash
./mvnw spring-boot:run
```

- API base URL: **http://localhost:8080/api**
- Health check: upload a case or call an internal endpoint (see below)

### 5. Verify Gemini (optional)

With `VERTEX_AI_ENABLED=true`:

```bash
curl -s http://localhost:8080/api/internal/gemini/ping | python3 -m json.tool
```

## Running tests

Unit and integration tests use an in-memory H2 database and **mock Gemini** by default.

```bash
./mvnw test
```

Live Vertex AI integration tests are **skipped** unless explicitly enabled:

```bash
VERTEX_AI_LIVE_TEST=true ./mvnw test -Dtest=GeminiLiveIntegrationTest,DocumentExtractionLiveIntegrationTest
```

Expected: **113 tests**, 0 failures, 2 skipped (live Gemini).

## Common workflows

### Upload and process a case manually

```bash
# 1. Create case (multipart PDF upload)
curl -s -X POST http://localhost:8080/api/cases \
  -F "file=@/path/to/application.pdf" | python3 -m json.tool

# 2. Extract fields (requires VERTEX_AI_ENABLED=true)
curl -s -X POST "http://localhost:8080/api/cases/{caseId}/extract" | python3 -m json.tool

# 3. View validation results
curl -s "http://localhost:8080/api/cases/{caseId}/validation" | python3 -m json.tool

# 4. Generate recommendation (Gemini)
curl -s -X POST "http://localhost:8080/api/cases/{caseId}/recommendation" | python3 -m json.tool

# 5. Coordinator decision
curl -s -X POST "http://localhost:8080/api/cases/{caseId}/decision" \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVE","note":"Looks good"}' | python3 -m json.tool

# 6. Audit timeline
curl -s "http://localhost:8080/api/cases/{caseId}/audit" | python3 -m json.tool
```

Replace `{caseId}` with the UUID from step 1.

### Seed the 40-case test dataset (BE-18)

Enable the seed endpoints in `.env.local`:

```properties
TEST_DATASET_ENABLED=true
```

Restart the app, then:

```bash
./scripts/seed-test-dataset.sh
```

Or manually:

```bash
curl -s -X POST http://localhost:8080/api/internal/test-dataset/seed | python3 -m json.tool
curl -s http://localhost:8080/api/internal/test-dataset/summary | python3 -m json.tool
```

Categories: 10 **VALID**, 10 **INCOMPLETE**, 10 **RULE_VIOLATION**, 10 **AMBIGUOUS**. Ground truth is defined in `TestDatasetCatalog.java`; sample PDFs are generated on seed.

Use [docs/TEST_REPORT.template.md](./docs/TEST_REPORT.template.md) to record extraction and rule-validation accuracy against this dataset.

### Email intake (optional, BE-16)

```properties
EMAIL_INTAKE_ENABLED=true
GMAIL_IMAP_USERNAME=your@gmail.com
GMAIL_IMAP_PASSWORD=your-app-password   # Gmail App Password, not account password
```

Trigger a poll manually:

```bash
curl -s -X POST http://localhost:8080/api/internal/email-intake/poll | python3 -m json.tool
```

### Connect the frontend

Frontend repo: [internship_application_coordinator_fe](https://github.com/aykutemreyalcin/internship_application_coordinator_fe)

```bash
# In the FE repo
npm install
npm run dev   # http://localhost:5173
```

Set `VITE_API_BASE_URL=http://localhost:8080/api` in the frontend `.env`. Backend CORS allows `http://localhost:5173` by default.

## API overview

Base URL: `http://localhost:8080/api`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/cases` | Upload PDF, create case |
| `GET` | `/cases` | List cases (`?status=&search=&page=&size=`) |
| `GET` | `/cases/{id}` | Case detail |
| `POST` | `/cases/{id}/extract` | Extract fields from PDF (Gemini) |
| `GET` | `/cases/{id}/validation` | Completeness + rules results |
| `POST` | `/cases/{id}/recommendation` | AI recommendation |
| `POST` | `/cases/{id}/decision` | Coordinator approve/reject/clarify |
| `POST` | `/cases/{id}/clarification` | Clarification email draft |
| `POST` | `/cases/{id}/supervisor-verification` | Supervisor verification draft |
| `GET` | `/cases/{id}/audit` | Audit log |
| `GET` | `/cases/{id}/documents/{docId}` | Download PDF |

Internal (feature-flagged):

| Method | Endpoint | Flag |
|--------|----------|------|
| `GET` | `/internal/gemini/ping` | `VERTEX_AI_ENABLED=true` |
| `POST` | `/internal/test-dataset/seed` | `TEST_DATASET_ENABLED=true` |
| `GET` | `/internal/test-dataset/summary` | `TEST_DATASET_ENABLED=true` |
| `POST` | `/internal/email-intake/poll` | `EMAIL_INTAKE_ENABLED=true` |

Full request/response examples: [PROJECT_OVERVIEW.md §6](./PROJECT_OVERVIEW.md#6-api-contract-fe--be).

## Project layout

```
src/main/java/com/internship/coordinator/
  agent/          # Gemini + rules agents
  controller/     # REST endpoints
  service/        # Case workflow, storage, audit
  testdataset/    # 40-case catalog + seeder
src/main/resources/
  db/migration/   # Flyway SQL
  university-rules.json
src/test/         # Unit + MockMvc integration tests
scripts/          # Helper shell scripts
docs/             # Test report template
```

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Connection refused` on startup | Run `docker compose up -d` and wait for Postgres healthcheck |
| Flyway / schema errors | Ensure Postgres is empty or volumes are consistent; `docker compose down -v` resets DB (destroys data) |
| Gemini endpoints 404 | Set `VERTEX_AI_ENABLED=true` and restart |
| Test dataset 404 | Set `TEST_DATASET_ENABLED=true` and restart |
| `./mvnw test` duplicate class errors | Run `./mvnw clean test` |
| CORS errors from FE | Confirm `CORS_ALLOWED_ORIGINS` includes `http://localhost:5173` |

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | Stable / release-ready |
| `be/<topic>` | Backend feature branches → PR to `main` |

## Team

- **Aykut** — backend + Vertex/Gemini agents
- **Nizamettin** & **Alvin** — frontend
