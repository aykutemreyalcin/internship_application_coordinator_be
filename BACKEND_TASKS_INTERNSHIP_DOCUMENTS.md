# Backend — Internship Documents Module (Aykut)

> **Scope:** New pages inside the existing `internship_application_coordinator` app — **not** a separate repo.
> Processes two post-internship document types submitted as **PDF or Word (.docx) only**:
> 1. **Learning Outcomes Report** — *Report on the achievement of learning outcomes intended for internship*
> 2. **Internship Journal** — *Student internship journal*
>
> Reuse the existing case pipeline (upload → extract → validate → recommend → coordinator decision → audit).
> **No email agents** for this module (no clarification / supervisor-verification endpoints).
> Same Vertex AI / Gemini credentials as the application module.
>
> Sample documents (lightly filled, for reference):
> - `/Users/aykut/Desktop/intern/Report on the achievement of learning outcomes intended for internship Computer engineering I cycle.docx`
> - `/Users/aykut/Desktop/intern/Student internship journal (1) - filled.docx`
>
> ClickUp import: `clickup_internship_documents_backend_tasks.csv`
> API contract additions: update `PROJECT_OVERVIEW.md` in **BE-ID-00** before FE starts.

## 0. Design Principles (Minimal Diff)

- **Extend, don't rewrite:** Add `caseType` to existing `ApplicationCase` instead of new tables/controllers where possible.
- **Same endpoints:** Keep `/api/cases/*`; filter list with `?caseType=`. Upload body includes `caseType`.
- **Branch in agents:** Extraction / completeness / rules agents switch on `caseType`; application flow unchanged.
- **Skip email steps:** `CaseService` must not expose clarification / supervisor-verification for document case types.
- **File formats:** Accept **only** `.pdf` and `.docx`. Reject everything else (images, `.doc`, `.odt`, etc.).
- **Branches / PRs:** `be/internship-documents/<topic>` → PR into `main` on **`internship_application_coordinator_be`**.

### Agent pipeline (this module)

```
[Manual Upload — PDF or DOCX]
        │
        ▼
   Document Extraction (Gemini) — type-specific prompt
        │
        ▼
   Completeness Validation — type-specific required fields
        │
        ▼
   University Rules — type-specific JSON rules
        │
        ▼
   Decision Recommendation (Gemini)
        │
        ▼
   [COORDINATOR — Human Approval]
   Approve / Reject / Request Clarification (note only — no email draft)
```

---

## Document Types & Extracted Fields

### `LEARNING_OUTCOMES_REPORT`

| Field | Description |
|-------|-------------|
| `studentName` | Header |
| `studentId` | Header |
| `reportDate` | Header date |
| `hostCompanyOrEmployer` | "Applies to internship completed/employment in …" |
| `internshipStartDate` / `internshipEndDate` | Period from–to |
| `learningOutcomes` | List: `{ code, description, waysOfAchieving }` — codes W1–W5, U1–U11, K01–K03, K05 |
| `studentSignaturePresent` | bool |
| `supervisorName` | Company supervisor section |
| `supervisorComments` | text (nullable) |
| `supervisorSignaturePresent` | bool |
| `supervisorConfirmationDate` | date (nullable) |
| `ectsCredits` | Dean's section — number (nullable until filled by university) |
| `recognizedInternshipMonths` | Dean's section (nullable) |
| `allOutcomesAchieved` | `YES` / `NO` / null — Dean's confirmation |
| `deanSupervisorComments` | text (nullable) |

Store type-specific payload in `extractedPayload` JSON column on `ApplicationCase` (see BE-ID-01).

### `INTERNSHIP_JOURNAL`

| Field | Description |
|-------|-------------|
| `studentName` | Header |
| `studentId` | Header |
| `faculty` | Header |
| `fieldOfStudy` | Header |
| `studyForm` | `FULL_TIME` / `PART_TIME` |
| `academicYear` | e.g. `2025/2026` |
| `companyName` | Host company |
| `companyAddress` | Full address |
| `internshipStartDate` / `internshipEndDate` | Internship period |
| `companySupervisorName` | Host supervisor |
| `weeklyEntries` | List: `{ weekStart, weekEnd, days: [{ date, hoursFrom, hoursTo, workingHours, activities }], supervisorSignaturePresent }` |

---

## Validation Rules (Draft — refine later)

Rules live in `src/main/resources/internship-document-rules.json` (separate from `university-rules.json`).

### Learning Outcomes Report — Completeness

- Required: `studentName`, `studentId`, `hostCompanyOrEmployer`, `internshipStartDate`, `internshipEndDate`
- Every expected outcome code must exist **and** `waysOfAchieving` must be non-empty (min 20 characters):
  - Knowledge: W1, W2, W3, W4, W5
  - Skills: U1–U11
  - Social: K01, K02, K03, K05 (both K05 rows in template map to one K05 entry + self-assessment text)
- `studentSignaturePresent` must be `true`

### Learning Outcomes Report — Rules

- `internshipEndDate` ≥ `internshipStartDate`
- Internship duration ≥ 3 months (configurable)
- Each `waysOfAchieving` must mention concrete activities (not placeholder dots `…` or `---`)
- Supervisor section: `supervisorName` required; `supervisorSignaturePresent` should be `true` before approval (warning if missing, error at decision if still missing)
- If Dean section is filled: `allOutcomesAchieved` must be `YES` or `NO` (not blank); if `NO`, coordinator should reject or request clarification

### Internship Journal — Completeness

- Required header: `faculty`, `fieldOfStudy`, `studentName`, `studentId`, `studyForm`, `academicYear`, `companyName`, `companyAddress`, `companySupervisorName`, internship period dates
- At least **1 weekly timesheet** block extracted
- Each working day row (Mon–Fri) in a week must have: date, `workingHours` > 0, non-empty `activities`

### Internship Journal — Rules

- `internshipEndDate` ≥ `internshipStartDate`
- Per week: sum of `workingHours` ≥ 30 (5 × 6h default — configurable)
- Per day: `workingHours` ≤ 8 (warning if > 8)
- Weekly date ranges must not overlap; must fall within overall internship period
- Total logged hours ≥ minimum for internship (default 360h ≈ 3 months × 30h/week × 4 — configurable)
- `activities` must not be generic placeholders (`…`, `---`, `TBD`)

---

## Task List

### BE-ID-00 — Update PROJECT_OVERVIEW (API contract)
- Document new `caseType` values: `APPLICATION`, `LEARNING_OUTCOMES_REPORT`, `INTERNSHIP_JOURNAL`.
- Extend `POST /cases` multipart: `file` + `caseType` (default `APPLICATION` for backward compatibility).
- Extend `GET /cases`: query param `caseType`.
- Document `extractedPayload` shape for both document types in overview.
- Note: clarification / supervisor-verification endpoints return `400` for non-application cases.
- **Done when:** FE can implement against updated contract without guessing.
- **Priority:** Urgent · **Estimate:** 0.25 day · **Depends on:** —
- **PR:** `internship_application_coordinator_be` — `be/internship-documents/overview`

### BE-ID-01 — `caseType` enum + DB migration
- Add enum `CaseType` on `ApplicationCase`; default `APPLICATION` for existing rows.
- Add nullable JSON column `extractedPayload` (JSONB in Postgres) for type-specific fields.
- Flyway `V4__case_type_and_extracted_payload.sql`.
- Update DTOs: list + detail include `caseType` and typed `extractedPayload`.
- **Done when:** Existing application cases still work; migration runs clean.
- **Priority:** Urgent · **Estimate:** 0.5 day · **Depends on:** BE-ID-00
- **PR:** `be/internship-documents/case-type`

### BE-ID-02 — File upload: PDF + DOCX validator
- Generalize `PdfFileValidator` → `DocumentFileValidator`.
- Allow MIME: `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`.
- Extension whitelist: `.pdf`, `.docx` only.
- Magic-byte / ZIP check for DOCX (`PK` + `[Content_Types].xml`).
- Reject `.doc`, images, archives, etc. with clear error message.
- Store original content type on `ApplicationDocument`; serve with correct `Content-Type` on download.
- **Done when:** Upload accepts sample PDF and both sample DOCX files; rejects `.doc` and `.jpg`.
- **Priority:** Urgent · **Estimate:** 0.5 day · **Depends on:** BE-ID-01
- **PR:** `be/internship-documents/file-validator`

### BE-ID-03 — DOCX text extraction utility
- Add Apache POI (`poi-ooxml`) for `.docx` → plain text (+ optional simple table rows for journal weeks).
- `DocxTextExtractor` util used when file is DOCX before Gemini call.
- Keep existing PDF multimodal path via `GeminiClient.generateFromPdf`.
- For DOCX: `GeminiClient.generateFromText(extractedText, prompt)` (reuse or add thin wrapper).
- **Done when:** Sample journal DOCX produces readable text including weekly activity rows.
- **Priority:** High · **Estimate:** 0.5 day · **Depends on:** BE-ID-02
- **PR:** `be/internship-documents/docx-extractor`

### BE-ID-04 — Learning Outcomes Report extraction agent
- `LearningOutcomesReportExtractionAgent` with Gemini prompt → JSON matching fields above.
- Map common fields to `ApplicationCase` (`studentName`, `studentId`, dates, `companyName` ← host company).
- Store outcomes list + signatures in `extractedPayload`.
- Wire in `CaseService.extract()`: if `caseType == LEARNING_OUTCOMES_REPORT`, use this agent.
- **Done when:** Sample report DOCX (lightly filled header + 2–3 outcomes) extracts structured JSON.
- **Priority:** Urgent · **Estimate:** 1.5 days · **Depends on:** BE-ID-03
- **PR:** `be/internship-documents/report-extraction`

### BE-ID-05 — Internship Journal extraction agent
- `InternshipJournalExtractionAgent` with Gemini prompt → JSON including `weeklyEntries`.
- Map header fields to `ApplicationCase` + `extractedPayload`.
- Handle multi-week tables; parse hours and activity lists per day.
- Wire in `CaseService.extract()` for `INTERNSHIP_JOURNAL`.
- **Done when:** Filled sample journal DOCX extracts ≥ 3 weeks with daily activities.
- **Priority:** Urgent · **Estimate:** 2 days · **Depends on:** BE-ID-03
- **PR:** `be/internship-documents/journal-extraction`

### BE-ID-06 — Report completeness validation agent
- `LearningOutcomesReportCompletenessAgent` implementing rules in "Completeness" section above.
- Issues reference outcome codes (e.g. field `W3.waysOfAchieving`).
- Integrate into validation pipeline after extraction.
- **Done when:** Empty "ways of achieving" and missing signatures produce correct issues.
- **Priority:** High · **Estimate:** 1 day · **Depends on:** BE-ID-04
- **PR:** `be/internship-documents/report-completeness`

### BE-ID-07 — Report university rules agent
- `LearningOutcomesReportRulesAgent` reads `internship-document-rules.json`.
- Duration, placeholder text, supervisor signature warnings, Dean section rules.
- **Done when:** Rule violations listed separately from completeness; config-driven thresholds.
- **Priority:** High · **Estimate:** 1 day · **Depends on:** BE-ID-06
- **PR:** `be/internship-documents/report-rules`

### BE-ID-08 — Journal completeness validation agent
- `InternshipJournalCompletenessAgent` — header fields + weekly row completeness.
- **Done when:** Journal missing activities or empty week flagged correctly.
- **Priority:** High · **Estimate:** 1 day · **Depends on:** BE-ID-05
- **PR:** `be/internship-documents/journal-completeness`

### BE-ID-09 — Journal university rules agent
- `InternshipJournalRulesAgent` — weekly hour sums, total hours, date range, overlap checks.
- **Done when:** Under-logged week and out-of-range dates produce rule issues.
- **Priority:** High · **Estimate:** 1.5 days · **Depends on:** BE-ID-08
- **PR:** `be/internship-documents/journal-rules`

### BE-ID-10 — CaseService branching + disable email for documents
- `createCaseWithDocument(file, caseType)` — validate type is one of three enums; document types require `LEARNING_OUTCOMES_REPORT` or `INTERNSHIP_JOURNAL`.
- `extract()` runs type-specific agent chain; auto-runs completeness + rules after extract (same as application).
- `clarification` / `supervisor-verification` return `400 Bad Request` when `caseType != APPLICATION`.
- `decision` with `CLARIFY` for documents: status → `CLARIFICATION_REQUESTED` + audit note only (no email draft generation).
- **Done when:** Full document flow works without touching email agents.
- **Priority:** Urgent · **Estimate:** 0.5 day · **Depends on:** BE-ID-07, BE-ID-09
- **PR:** `be/internship-documents/service-branching`

### BE-ID-11 — Decision recommendation prompts (report + journal)
- Extend `DecisionRecommendationAgent` (or type-specific thin wrappers) with prompts for:
  - Report: focus on outcome coverage, supervisor confirmation, Dean YES/NO consistency.
  - Journal: focus on hour totals, activity quality, period coverage.
- **Done when:** Recommendation + reason sensible on both sample documents.
- **Priority:** High · **Estimate:** 0.5 day · **Depends on:** BE-ID-10
- **PR:** `be/internship-documents/recommendation`

### BE-ID-12 — List API filter + specifications
- `GET /cases?caseType=LEARNING_OUTCOMES_REPORT|INTERNSHIP_JOURNAL|APPLICATION`.
- Update `CaseSpecifications` for optional `caseType` filter.
- Search still works on `studentName`, `studentId`, `companyName`.
- **Done when:** FE can show document-only lists separate from applications.
- **Priority:** High · **Estimate:** 0.25 day · **Depends on:** BE-ID-01
- **PR:** `be/internship-documents/list-filter`

### BE-ID-13 — Sample documents + seed endpoints
- Copy sample DOCX files into `src/test/resources/sample-documents/` (or `docs/samples/`).
- Extend test-dataset seed (or add `POST /api/internal/internship-documents/seed`) with:
  - 2 report cases (1 complete, 1 missing outcomes)
  - 2 journal cases (1 complete, 1 under-logged hours)
- **Done when:** Demo cases loadable without manual upload.
- **Priority:** Normal · **Estimate:** 0.5 day · **Depends on:** BE-ID-11
- **PR:** `be/internship-documents/seed-data`

### BE-ID-14 — Tests
- Unit tests: report/journal completeness + rules agents (no Gemini).
- Integration: MockMvc upload DOCX → extract (mock Gemini) → validation → decision.
- Regression: existing application case tests still green.
- **Done when:** `./mvnw test` green.
- **Priority:** High · **Estimate:** 1.5 days · **Depends on:** BE-ID-11
- **PR:** `be/internship-documents/tests`

### BE-ID-15 — README + docs update
- Document new env unchanged (same Vertex credentials).
- How to upload report vs journal; supported formats; no email for this module.
- Link sample DOCX paths.
- **Done when:** New dev can run document flow from README alone.
- **Priority:** Normal · **Estimate:** 0.25 day · **Depends on:** BE-ID-14
- **PR:** `be/internship-documents/readme`

---

## Recommended Order

1. **Contract & model:** BE-ID-00 → BE-ID-01 → BE-ID-12
2. **Files:** BE-ID-02 → BE-ID-03
3. **Report chain:** BE-ID-04 → BE-ID-06 → BE-ID-07
4. **Journal chain (parallel after BE-ID-03):** BE-ID-05 → BE-ID-08 → BE-ID-09
5. **Integration:** BE-ID-10 → BE-ID-11 → BE-ID-13 → BE-ID-14 → BE-ID-15

## Total estimated effort

~**12 working days** (Aykut). Report and journal agent chains can partially overlap after BE-ID-03.
