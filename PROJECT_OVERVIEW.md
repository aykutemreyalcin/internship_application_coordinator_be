# Internship Application Coordinator — Project Overview

> Shared document. Both the frontend and backend teams read this. The same content lives in both repos (single source of truth).

## 1. What Is This Project?

An **agentic** system that helps an internship coordinator process student internship applications received by email. The system reads applications, checks for missing fields and compliance, suggests clarification emails or company supervisor verification when needed, and produces an **approve/reject recommendation**. **The coordinator always makes the final decision.** Fully automated approval is not allowed.

This project is based on the TDD (Technical Design Document) v1.0. Our team chose the **Vertex AI** framework path; the LLM is **Google Gemini** (via Vertex AI).

## 2. Team and Responsibilities

| Name | Role | Repo |
|------|------|------|
| **Aykut** | Backend (Spring Boot + Vertex/Gemini + agents) | `internship_application_coordinator_be` |
| **Nizamettin** | Frontend — core scaffold, application list, upload flow, API layer | `internship_application_coordinator_fe` |
| **Alvin** | Frontend — application detail, validation/decision panels, email modals, audit timeline | `internship_application_coordinator_fe` |

Detailed tasks:
- Backend → `internship_application_coordinator_be/BACKEND_TASKS.md`
- Frontend → `internship_application_coordinator_fe/FRONTEND_TASKS.md`

## 3. Technology Stack

| Layer | Technology |
|--------|------------|
| Frontend | React + TypeScript + Vite, React Router, TanStack Query (data fetching), CSS Modules / a lightweight UI library |
| Backend | Java 21 + Spring Boot 3 (Web, Validation, Data JPA) |
| Database | PostgreSQL (local Docker) — H2 fallback optional during development |
| AI | Vertex AI — Gemini (multimodal: PDF/image → structured data + text drafts) |
| Files | Local filesystem (uploaded PDFs) — Cloud Storage later |
| Email | Phase 2: IMAP/Gmail API intake + SMTP send. MVP: manual PDF upload |
| Build / Run | Backend: Maven; Frontend: npm/pnpm; Postgres: docker-compose |

> Deployment is **out of scope** for now. Everything is designed to run locally. Deploy URLs will be added later.

## 4. Architecture (6 Agents)

The 6 agents from the TDD are implemented in the backend as logical steps/services:

1. **Email Intake Agent** — Receives application emails, stores attachments, creates a case. *(Replaced by manual upload in MVP.)*
2. **Document Extraction Agent** — Extracts fields from PDF (Vertex Gemini, multimodal).
3. **Completeness Validation Agent** — Are required fields missing? (Java rules)
4. **University Rules Agent** — Compliance with university rules (JSON-configurable rules engine).
5. **Supervisor Verification Agent** — Supervisor verification email when required.
6. **Decision Recommendation Agent** — Approve/reject/clarify recommendation (Gemini + rule results).

```
[Email / Manual Upload]
        │
        ▼
   Email Intake ──► Document Extraction (Gemini)
                          │
                          ▼
                 Completeness Validation
                          │
                          ▼
                  University Rules
                          │
            ┌─────────────┼───────────────┐
            ▼             ▼                ▼
     Clarification  Supervisor      Decision
     (to student)   Verification    Recommendation (Gemini)
            └─────────────┴───────────────┘
                          │
                          ▼
              [COORDINATOR — Human Approval]
              Approve / Reject / Request Clarification
```

Every step is written to the **audit log** (TDD: full traceability).

## 5. Data Model

**Case (Application Case)**

| Field | Type | Description |
|-------|------|-------------|
| `caseId` | UUID | Primary key |
| `status` | enum | `NEW`, `EXTRACTING`, `NEEDS_CLARIFICATION`, `PENDING_SUPERVISOR`, `READY_FOR_REVIEW`, `APPROVED`, `REJECTED`, `CLARIFICATION_REQUESTED` |
| `studentName` | string | |
| `studentId` | string | |
| `companyName` | string | |
| `supervisorName` | string | |
| `supervisorEmail` | string | |
| `internshipStartDate` | date | |
| `internshipEndDate` | date | |
| `fieldOfStudy` | string | |
| `recommendation` | enum | `APPROVE`, `REJECT`, `CLARIFY`, `null` |
| `recommendationReason` | text | Gemini reasoning |
| `createdAt` / `updatedAt` | timestamp | |

**ApplicationDocument** — `id`, `caseId`, `fileName`, `storagePath`, `pageCount`, `uploadedAt`  
**ValidationResult** — `id`, `caseId`, `type` (`COMPLETENESS`/`RULES`), `passed` (bool), `issues` (list: field, message, severity)  
**AuditLogEntry** — `id`, `caseId`, `actor` (`SYSTEM`/`COORDINATOR`/agent name), `action`, `detail`, `timestamp`

## 6. API Contract (FE ↔ BE)

> This contract is fixed so FE and BE can work **in parallel**. If it changes, update this document first and notify the team.

Base URL (local): `http://localhost:8080/api`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/cases` | New application (multipart: PDF file). Creates case, status `NEW`. |
| `GET` | `/cases` | Case list. Query: `?status=&search=&page=&size=` |
| `GET` | `/cases/{id}` | Case detail (fields + validation + recommendation) |
| `POST` | `/cases/{id}/extract` | Triggers extraction (Gemini). Status `EXTRACTING` → result. |
| `GET` | `/cases/{id}/validation` | Completeness + rules results |
| `POST` | `/cases/{id}/recommendation` | Generates recommendation (Gemini) |
| `POST` | `/cases/{id}/decision` | Coordinator decision. Body: `{ decision: "APPROVE"\|"REJECT"\|"CLARIFY", note }` |
| `POST` | `/cases/{id}/clarification` | Generates/sends clarification email draft to student |
| `POST` | `/cases/{id}/supervisor-verification` | Generates/sends supervisor verification email draft |
| `GET` | `/cases/{id}/audit` | Audit log (timeline) |
| `GET` | `/cases/{id}/documents/{docId}` | Download/preview PDF |

**Example `GET /cases/{id}` response:**

```json
{
  "caseId": "a1b2c3d4-...",
  "status": "READY_FOR_REVIEW",
  "studentName": "Jan Kowalski",
  "studentId": "123456",
  "companyName": "Astana Kebab Sp. z o.o.",
  "supervisorName": "Anna Nowak",
  "supervisorEmail": "supervisor@example.com",
  "fieldOfStudy": "Computer Engineering",
  "internshipStartDate": "2026-06-01",
  "internshipEndDate": "2026-11-30",
  "recommendation": "APPROVE",
  "recommendationReason": "All required fields are present; duration complies with rules.",
  "validation": {
    "completeness": { "passed": true, "issues": [] },
    "rules": { "passed": true, "issues": [] }
  },
  "documents": [
    { "id": "doc-1", "fileName": "application.pdf", "pageCount": 4 }
  ],
  "createdAt": "2026-06-04T10:00:00Z",
  "updatedAt": "2026-06-04T10:02:00Z"
}
```

**Error format (all endpoints):**

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "Description", "path": "/api/cases" }
```

> The FE team can use **mocks** (MSW or static JSON) against this contract until the backend is ready.

## 7. Local Development Together

### Ports
- Backend: `http://localhost:8080`
- Frontend (Vite dev): `http://localhost:5173`
- PostgreSQL: `localhost:5432`

### Run backend (summary)
```bash
cd internship_application_coordinator_be
docker compose up -d        # PostgreSQL
./mvnw spring-boot:run      # API → :8080
```

### Run frontend (summary)
```bash
cd internship_application_coordinator_fe
npm install
npm run dev                 # → :5173
```

### Connection
- Frontend `.env`: `VITE_API_BASE_URL=http://localhost:8080/api`
- Backend CORS: allow `http://localhost:5173`.
- Vertex/Gemini credentials in backend `.env` (service account JSON path + project id + region). **Do not commit keys** (`.gitignore`).

### Recommended GCP setup
- Project: `intern_application_coordinator`
- Enable API: **Vertex AI API**
- Region: `europe-west1` (Warsaw / GDPR proximity)
- Local auth: service account JSON (env: `GOOGLE_APPLICATION_CREDENTIALS`)

## 8. Branch / Workflow

- `main` is protected; everyone uses feature branches: `fe/<name>/<topic>` or `be/<topic>`.
- Small PRs; at least 1 review.
- If the API contract changes → update this document + notify the team.
- Commit messages: English, short and descriptive.

## 9. Evaluation Criteria (TDD)

| Criterion | Weight | Owner |
|-----------|--------|-------|
| Data extraction accuracy | 25% | Backend (Gemini extraction) |
| Rule validation accuracy | 25% | Backend (rules engine) |
| Email quality | 15% | Backend (drafts) + FE (display) |
| Escalation quality | 15% | Backend + FE (clarification/supervisor) |
| Auditability | 10% | Backend (log) + FE (timeline) |
| User experience | 10% | Frontend |

## 10. Deliverables (TDD)

- Source code (2 repos)
- Architecture diagram (this document + visual)
- Run instructions (README + this document)
- Test report (40-case test set: 10 valid, 10 incomplete, 10 rule violations, 10 ambiguous)
- Demo video
