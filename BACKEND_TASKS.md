# Backend — Task Plan (Aykut)

> This document breaks backend work into tasks from start to finish. **Aykut** owns the backend solo.
> For architecture, stack, data model, and API contract: `PROJECT_OVERVIEW.md`.
> ClickUp import format for these tasks: `clickup_backend_tasks.csv`.

## 0. Stack and Conventions

- **Java 21 + Spring Boot 3** (Web, Validation, Data JPA, Mail)
- **PostgreSQL** (local docker-compose) — H2 fallback optional during development
- **Flyway** (DB migrations)
- **Vertex AI Java SDK** (`google-cloud-vertexai`) — Gemini
- **Maven** build
- Branches: `be/<topic>`, small PRs
- No deployment for now; everything runs locally.

### Phase plan
- **Phase 1 (MVP):** Manual PDF upload → extraction → validation → recommendation → coordinator decision → audit.
- **Phase 2:** Email intake (IMAP/Gmail), real email sending, test set + report.

---

## Task List

### BE-01 — Project scaffold (Spring Boot)
- Spring Initializr: Web, Validation, Data JPA, Mail, Lombok.
- Package layout: `controller`, `service`, `agent`, `model`, `repository`, `dto`, `config`, `util`.
- `application.yml` + profiles (`local`).
- **Done when:** `./mvnw spring-boot:run` starts empty app on `:8080`.
- **Priority:** Urgent · **Estimate:** 0.5 day · **Depends on:** —

### BE-02 — Local infrastructure: PostgreSQL + config
- `docker-compose.yml` (PostgreSQL), `.env.example`, datasource settings.
- **Done when:** After `docker compose up -d`, app connects to DB.
- **Priority:** Urgent · **Estimate:** 0.5 day · **Depends on:** BE-01

### BE-03 — Data model / entities
- `Case`, `ApplicationDocument`, `ValidationResult`, `AuditLogEntry`, enums (`CaseStatus`, `Recommendation`) per `PROJECT_OVERVIEW.md` data model.
- **Done when:** Entities compile; relationships correct.
- **Priority:** High · **Estimate:** 1 day · **Depends on:** BE-01

### BE-04 — Repositories + Flyway migration
- JPA repositories; initial schema via Flyway (`V1__init.sql`).
- **Done when:** Migration runs; tables created.
- **Priority:** High · **Estimate:** 0.5 day · **Depends on:** BE-03

### BE-05 — Case REST API (CRUD + list/filter)
- `GET /cases` (status/search/page/size), `GET /cases/{id}`, base DTOs.
- **Done when:** List and detail endpoints return contract-compliant responses.
- **Priority:** High · **Estimate:** 1 day · **Depends on:** BE-04

### BE-06 — File upload & storage
- `POST /cases` or `POST /cases/{id}` (multipart PDF) → save to local filesystem, create `ApplicationDocument`, serve via `GET /cases/{id}/documents/{docId}`.
- PDF validation (content type, size).
- **Done when:** PDF can be uploaded and downloaded.
- **Priority:** High · **Estimate:** 1 day · **Depends on:** BE-05

### BE-07 — Vertex AI / Gemini integration config
- `google-cloud-vertexai` dependency; `VertexAiConfig` (projectId, region, model name from env).
- Service account via `GOOGLE_APPLICATION_CREDENTIALS`; key in `.gitignore`.
- Simple "ping" call to verify connection.
- **Done when:** Sample prompt to Gemini returns a response.
- **Priority:** Urgent · **Estimate:** 1 day · **Depends on:** BE-01

### BE-08 — Document Extraction Agent (Gemini multimodal)
- Send PDF pages to Gemini (multimodal), request structured JSON (student, ID, field of study, company, supervisor, supervisor email, start/end dates).
- Map response to `Case` fields; status `EXTRACTING` → updated.
- Endpoint `POST /cases/{id}/extract`.
- **Done when:** Fields extracted correctly from sample application PDF.
- **Priority:** Urgent · **Estimate:** 2 days · **Depends on:** BE-06, BE-07

### BE-09 — Completeness Validation Agent
- Required-field checks (Java rules); produce `ValidationResult(type=COMPLETENESS)` for missing fields.
- **Done when:** Case with missing fields yields correct issue list.
- **Priority:** High · **Estimate:** 1 day · **Depends on:** BE-08

### BE-10 — University Rules Agent (rules engine)
- JSON/configurable rules: e.g. min/max internship duration, valid date range, field formats, supervisor email required.
- Produce `ValidationResult(type=RULES)`; endpoint `GET /cases/{id}/validation`.
- **Done when:** Rule violations listed; rules read from config.
- **Priority:** Urgent · **Estimate:** 2 days · **Depends on:** BE-09

### BE-11 — Decision Recommendation Agent
- Feed validation results + fields to Gemini for `APPROVE`/`REJECT`/`CLARIFY` + reasoning.
- Deterministic when rules are clear; `CLARIFY` when ambiguous. `POST /cases/{id}/recommendation`.
- **Done when:** Recommendation + reason stored on `Case`; status `READY_FOR_REVIEW`.
- **Priority:** Urgent · **Estimate:** 1.5 days · **Depends on:** BE-10

### BE-12 — Clarification request generation
- Generate student clarification email **draft** (Gemini) for missing/ambiguous fields. `POST /cases/{id}/clarification`.
- Phase 1: return draft; Phase 2: send via SMTP.
- **Done when:** Meaningful personalized draft returned; status `CLARIFICATION_REQUESTED`.
- **Priority:** Normal · **Estimate:** 1 day · **Depends on:** BE-11

### BE-13 — Supervisor Verification Agent
- Generate supervisor verification email draft. `POST /cases/{id}/supervisor-verification`; status `PENDING_SUPERVISOR`.
- Phase 1: draft; Phase 2: send.
- **Done when:** Draft addressed to supervisor email.
- **Priority:** Normal · **Estimate:** 1 day · **Depends on:** BE-11

### BE-14 — Coordinator decision + state machine (Human-in-the-Loop)
- `POST /cases/{id}/decision` (`APPROVE`/`REJECT`/`CLARIFY` + note). Enforce valid state transitions; no fully automated final approval.
- **Done when:** Coordinator decision changes status; invalid transitions rejected.
- **Priority:** Urgent · **Estimate:** 1 day · **Depends on:** BE-11

### BE-15 — Audit log (cross-cutting)
- `AuditLogEntry` for every status change, agent action, coordinator decision. `GET /cases/{id}/audit`.
- **Done when:** All events recorded with timestamp + actor and listed.
- **Priority:** High · **Estimate:** 1 day · **Depends on:** BE-05

### BE-16 — Email Intake Agent (Phase 2)
- IMAP/Gmail API: fetch application emails, download PDF attachments, auto-create `Case`, trigger pipeline.
- **Done when:** Emailed attachment from test mailbox becomes a case.
- **Priority:** Normal · **Estimate:** 2 days · **Depends on:** BE-08

### BE-17 — CORS, error handling, validation
- CORS for FE (`:5173`); global exception handler (contract error format); request validation.
- **Done when:** FE calls work; errors return consistent JSON.
- **Priority:** High · **Estimate:** 0.5 day · **Depends on:** BE-05

### BE-18 — Test dataset (40 cases) + seed
- Per TDD: 10 valid, 10 incomplete, 10 rule violations, 10 ambiguous. Seed script / sample PDFs.
- **Done when:** 40 cases loadable and behave per category.
- **Priority:** Normal · **Estimate:** 1.5 days · **Depends on:** BE-11

### BE-19 — Tests (unit + integration)
- Unit tests for rules engine and state machine; MockMvc integration for endpoints; Gemini calls mocked.
- **Done when:** `./mvnw test` green; critical paths covered.
- **Priority:** High · **Estimate:** 2 days · **Depends on:** BE-14

### BE-20 — README + run instructions + test report
- Setup, env, how to run; test report template (extraction & rule accuracy metrics).
- **Done when:** New developer can run locally via README; report fillable.
- **Priority:** Normal · **Estimate:** 1 day · **Depends on:** BE-18, BE-19

---

## Recommended Order

1. **Foundation:** BE-01 → BE-02 → BE-03 → BE-04 → BE-05 → BE-17
2. **Files + AI:** BE-06 → BE-07 → BE-08
3. **Agent chain:** BE-09 → BE-10 → BE-11 → BE-14 (+ BE-15 in parallel)
4. **Email / escalation:** BE-12 → BE-13
5. **Phase 2 / quality:** BE-16, BE-18, BE-19, BE-20

## Total estimated effort
~25 working days (Phase 1 heavy). Email (BE-16) and extended tests can slip to Phase 2.
