# Coolify deployment

## 1. Add PostgreSQL

In the same Coolify project, add a **PostgreSQL** database resource.

Note the **internal hostname** (often the database UUID or service name on the `coolify` network).

## 2. Backend — Network

| Setting | Value |
|---------|--------|
| **Ports Exposes** | `8080` |
| **Port Mappings** | leave empty or `8080:8080` |

Do **not** use `3000` unless you also set `SERVER_PORT=3000`.

Remove SPA-style Caddy `try_files` if present — this is an API, not a frontend.

## 3. Backend — Environment variables

| Variable | Value |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `docker` |
| `POSTGRES_HOST` | PostgreSQL internal hostname from step 1 |
| `POSTGRES_PORT` | `5432` |
| `POSTGRES_DB` | `internship_coordinator` |
| `POSTGRES_USER` | your DB user |
| `POSTGRES_PASSWORD` | your DB password |
| `VERTEX_AI_ENABLED` | `true` |
| `GCP_PROJECT_ID` | `intern-application-coordinator` |
| `GCP_REGION` | `europe-west1` |
| `GCP_GEMINI_MODEL` | `gemini-2.5-flash` |
| `GCP_SERVICE_ACCOUNT_JSON` | full service account JSON (secret) |
| `CORS_ALLOWED_ORIGINS` | frontend URL when ready, e.g. `https://your-fe.sslip.io` |
| `EMAIL_INTAKE_ENABLED` | `true` or `false` |
| `GMAIL_IMAP_USERNAME` | Gmail address (secret) |
| `GMAIL_IMAP_PASSWORD` | Gmail app password (secret) |
| `UPLOAD_DIR` | `/app/uploads` |
| `TEST_DATASET_ENABLED` | `false` |

Mark secrets as **encrypted** in Coolify.

## 4. Deploy branch

Deploy from `main`. If the Docker build fails during Maven dependency download, ensure the server has at least **2 GB RAM** free for the build container, then redeploy (the Dockerfile uses `dependency:resolve` instead of `dependency:go-offline` to reduce memory use).

## 5. Verify

```bash
curl -s "https://apple-team-1-be.65.21.127.107.sslip.io/api/cases?page=0&size=1"
curl -s "https://apple-team-1-be.65.21.127.107.sslip.io/api/internal/gemini/ping"
```
