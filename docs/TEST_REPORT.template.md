# Test Report — Internship Application Coordinator (Backend)

> **Template.** Copy this file to `docs/TEST_REPORT.md` (or `TEST_REPORT-YYYY-MM-DD.md`) and fill in the blanks before submission.

## 1. Run metadata

| Field | Value |
|-------|-------|
| **Evaluator** | |
| **Date** | |
| **Git commit / tag** | `git rev-parse --short HEAD` → |
| **Gemini model** | e.g. `gemini-2.5-flash` |
| **GCP region** | e.g. `europe-west1` |
| **Rules config** | `university-rules.json` (default) or path: |
| **Dataset seed** | `./scripts/seed-test-dataset.sh` on commit: |

### Environment used

| Setting | Value |
|---------|-------|
| `VERTEX_AI_ENABLED` | `true` |
| `TEST_DATASET_ENABLED` | `true` |
| Backend URL | `http://localhost:8080/api` |

---

## 2. Evaluation procedure

Follow these steps so results are reproducible:

1. **Start infrastructure**
   ```bash
   docker compose up -d
   cp .env.example .env.local   # if not done yet
   # Set VERTEX_AI_ENABLED=true, TEST_DATASET_ENABLED=true, GCP creds
   ./mvnw spring-boot:run
   ```

2. **Seed the 40-case dataset**
   ```bash
   ./scripts/seed-test-dataset.sh
   ```
   Confirm response: `"total": 40` with 10 cases per category.

3. **List seeded cases**
   ```bash
   curl -s "http://localhost:8080/api/cases?size=50" | python3 -m json.tool
   ```
   Note each case UUID and its `datasetKey` (e.g. `valid-01`, `incomplete-03`).

4. **Extraction pass** (requires live Gemini)  
   For **each** of the 40 cases:
   ```bash
   curl -s -X POST "http://localhost:8080/api/cases/{caseId}/extract" | python3 -m json.tool
   curl -s "http://localhost:8080/api/cases/{caseId}" | python3 -m json.tool
   ```
   Compare extracted fields to ground truth in §5 and record in §6.

5. **Validation pass** (rules engine — no Gemini needed for validation itself)  
   After extraction (or on freshly seeded cases for rules-only baseline):
   ```bash
   curl -s "http://localhost:8080/api/cases/{caseId}/validation" | python3 -m json.tool
   ```
   Record completeness and rules `passed` flags in §7.

6. **Compute scores** using formulas in §8.

---

## 3. Dataset overview

| Category | Count | Purpose |
|----------|------:|---------|
| VALID | 10 | All fields present; passes completeness + rules |
| INCOMPLETE | 10 | One or more required fields missing |
| RULE_VIOLATION | 10 | Complete but violates university rules |
| AMBIGUOUS | 10 | Complete + rules pass; needs human judgment |
| **Total** | **40** | |

Ground truth source: `src/main/java/com/internship/coordinator/testdataset/TestDatasetCatalog.java`

---

## 4. Metrics definitions (TDD evaluation)

| Metric | Weight | Definition |
|--------|--------|------------|
| **Extraction accuracy** | 25% | Share of field values correctly extracted from PDF vs. catalog ground truth |
| **Rule validation accuracy** | 25% | Share of cases where completeness/rules pass-fail matches expectation |

### 4.1 Extraction accuracy

**Fields evaluated (8):** `studentName`, `studentId`, `fieldOfStudy`, `companyName`, `supervisorName`, `supervisorEmail`, `internshipStartDate`, `internshipEndDate`

| Rule | Scoring |
|------|---------|
| Exact string match | Case-sensitive for IDs/emails; trim whitespace |
| Dates | ISO `YYYY-MM-DD`; must match exactly |
| Both null | Count as correct if ground truth is null |
| One null, one value | Incorrect |

**Field accuracy** = correct field comparisons / total field comparisons  
(Typically 40 cases × 8 fields = **320** comparisons, minus fields intentionally null in incomplete cases if PDF omits them — document any exclusions in notes.)

**Per-field accuracy** = correct for that field / 40

### 4.2 Rule validation accuracy

Two checks per case:

| Check | Expected when |
|-------|----------------|
| **Completeness** `passed=true` | Category VALID or AMBIGUOUS (all required fields present in ground truth) |
| **Completeness** `passed=false` | Category INCOMPLETE |
| **Rules** `passed=true` | Category VALID or AMBIGUOUS |
| **Rules** `passed=false` | Category RULE_VIOLATION |

For INCOMPLETE cases with partial dates, rules may pass or fail depending on which fields are missing — record **actual expected** outcome from seeded validation or re-run after extraction.

**Validation accuracy** = (correct completeness calls + correct rules calls) / (40 × 2) = / 80

---

## 5. Expected outcomes by case

Use this table as the answer key. `C` = completeness pass, `R` = rules pass.

| Case key | Category | C | R | Notes |
|----------|----------|---|---|-------|
| valid-01 … valid-10 | VALID | ✓ | ✓ | |
| incomplete-01 | INCOMPLETE | ✗ | — | missing studentName |
| incomplete-02 | INCOMPLETE | ✗ | — | missing studentId |
| incomplete-03 | INCOMPLETE | ✗ | — | missing fieldOfStudy |
| incomplete-04 | INCOMPLETE | ✗ | — | missing companyName |
| incomplete-05 | INCOMPLETE | ✗ | — | missing supervisorName |
| incomplete-06 | INCOMPLETE | ✗ | — | missing supervisorEmail |
| incomplete-07 | INCOMPLETE | ✗ | — | missing startDate |
| incomplete-08 | INCOMPLETE | ✗ | — | missing endDate |
| incomplete-09 | INCOMPLETE | ✗ | — | missing studentId, fieldOfStudy |
| incomplete-10 | INCOMPLETE | ✗ | — | missing supervisorName, supervisorEmail |
| rules-01 | RULE_VIOLATION | ✓ | ✗ | duration too short (< 84 days) |
| rules-02 | RULE_VIOLATION | ✓ | ✗ | duration too long (> 180 days) |
| rules-03 | RULE_VIOLATION | ✓ | ✗ | end before start |
| rules-04 | RULE_VIOLATION | ✓ | ✗ | start before earliest allowed |
| rules-05 | RULE_VIOLATION | ✓ | ✗ | end after latest allowed |
| rules-06 | RULE_VIOLATION | ✓ | ✗ | invalid studentId pattern |
| rules-07 | RULE_VIOLATION | ✓ | ✗ | invalid studentId pattern |
| rules-08 | RULE_VIOLATION | ✓ | ✗ | invalid supervisor email |
| rules-09 | RULE_VIOLATION | ✓ | ✗ | invalid studentId + short duration |
| rules-10 | RULE_VIOLATION | ✓ | ✗ | invalid email + end before start |
| ambiguous-01 … ambiguous-10 | AMBIGUOUS | ✓ | ✓ | human review expected |

---

## 6. Extraction results (fill in)

### 6.1 Summary

| Metric | Value |
|--------|------:|
| Total field comparisons | |
| Correct field comparisons | |
| **Extraction accuracy (%)** | |
| Cases with zero field errors | / 40 |
| Worst-performing field | |

### 6.2 Per-field accuracy

| Field | Correct | Total | Accuracy (%) |
|-------|--------:|------:|-------------:|
| studentName | | 40 | |
| studentId | | 40 | |
| fieldOfStudy | | 40 | |
| companyName | | 40 | |
| supervisorName | | 40 | |
| supervisorEmail | | 40 | |
| internshipStartDate | | 40 | |
| internshipEndDate | | 40 | |

### 6.3 Per-category extraction accuracy

| Category | Correct fields | Total fields | Accuracy (%) |
|----------|---------------:|-------------:|-------------:|
| VALID (10) | | | |
| INCOMPLETE (10) | | | |
| RULE_VIOLATION (10) | | | |
| AMBIGUOUS (10) | | | |

### 6.4 Per-case extraction worksheet

Mark each field: **✓** correct, **✗** wrong, **—** not applicable / omitted in PDF.

| Case key | studentName | studentId | fieldOfStudy | companyName | supervisorName | supervisorEmail | startDate | endDate | Errors |
|----------|:-----------:|:---------:|:------------:|:-----------:|:--------------:|:---------------:|:---------:|:-------:|-------:|
| valid-01 | | | | | | | | | |
| valid-02 | | | | | | | | | |
| valid-03 | | | | | | | | | |
| valid-04 | | | | | | | | | |
| valid-05 | | | | | | | | | |
| valid-06 | | | | | | | | | |
| valid-07 | | | | | | | | | |
| valid-08 | | | | | | | | | |
| valid-09 | | | | | | | | | |
| valid-10 | | | | | | | | | |
| incomplete-01 | | | | | | | | | |
| incomplete-02 | | | | | | | | | |
| incomplete-03 | | | | | | | | | |
| incomplete-04 | | | | | | | | | |
| incomplete-05 | | | | | | | | | |
| incomplete-06 | | | | | | | | | |
| incomplete-07 | | | | | | | | | |
| incomplete-08 | | | | | | | | | |
| incomplete-09 | | | | | | | | | |
| incomplete-10 | | | | | | | | | |
| rules-01 | | | | | | | | | |
| rules-02 | | | | | | | | | |
| rules-03 | | | | | | | | | |
| rules-04 | | | | | | | | | |
| rules-05 | | | | | | | | | |
| rules-06 | | | | | | | | | |
| rules-07 | | | | | | | | | |
| rules-08 | | | | | | | | | |
| rules-09 | | | | | | | | | |
| rules-10 | | | | | | | | | |
| ambiguous-01 | | | | | | | | | |
| ambiguous-02 | | | | | | | | | |
| ambiguous-03 | | | | | | | | | |
| ambiguous-04 | | | | | | | | | |
| ambiguous-05 | | | | | | | | | |
| ambiguous-06 | | | | | | | | | |
| ambiguous-07 | | | | | | | | | |
| ambiguous-08 | | | | | | | | | |
| ambiguous-09 | | | | | | | | | |
| ambiguous-10 | | | | | | | | | |

### 6.5 Extraction error log (optional detail)

| Case key | Field | Expected | Actual | Notes |
|----------|-------|----------|--------|-------|
| | | | | |
| | | | | |

---

## 7. Rule validation results (fill in)

### 7.1 Summary

| Metric | Value |
|--------|------:|
| Completeness checks correct | / 40 |
| Rules checks correct | / 40 |
| **Combined validation accuracy (%)** | / 80 → % |

### 7.2 Confusion matrix — Completeness

|  | Predicted PASS | Predicted FAIL |
|--|---------------:|---------------:|
| **Expected PASS** (20 cases: valid + ambiguous) | TP: | FN: |
| **Expected FAIL** (10 incomplete) | FP: | TN: |

Completeness precision/recall (optional):

- Precision = TP / (TP + FP) =  
- Recall = TP / (TP + FN) =  

### 7.3 Confusion matrix — University rules

|  | Predicted PASS | Predicted FAIL |
|--|---------------:|---------------:|
| **Expected PASS** (20 cases: valid + ambiguous) | TP: | FN: |
| **Expected FAIL** (10 rule violations) | FP: | TN: |

### 7.4 Per-case validation worksheet

| Case key | Exp. C | Act. C | C OK? | Exp. R | Act. R | R OK? | Issues found (if wrong) |
|----------|:------:|:------:|:-----:|:------:|:------:|:-----:|-------------------------|
| valid-01 | ✓ | | | ✓ | | | |
| valid-02 | ✓ | | | ✓ | | | |
| valid-03 | ✓ | | | ✓ | | | |
| valid-04 | ✓ | | | ✓ | | | |
| valid-05 | ✓ | | | ✓ | | | |
| valid-06 | ✓ | | | ✓ | | | |
| valid-07 | ✓ | | | ✓ | | | |
| valid-08 | ✓ | | | ✓ | | | |
| valid-09 | ✓ | | | ✓ | | | |
| valid-10 | ✓ | | | ✓ | | | |
| incomplete-01 | ✗ | | | — | | | |
| incomplete-02 | ✗ | | | — | | | |
| incomplete-03 | ✗ | | | — | | | |
| incomplete-04 | ✗ | | | — | | | |
| incomplete-05 | ✗ | | | — | | | |
| incomplete-06 | ✗ | | | — | | | |
| incomplete-07 | ✗ | | | — | | | |
| incomplete-08 | ✗ | | | — | | | |
| incomplete-09 | ✗ | | | — | | | |
| incomplete-10 | ✗ | | | — | | | |
| rules-01 | ✓ | | | ✗ | | | |
| rules-02 | ✓ | | | ✗ | | | |
| rules-03 | ✓ | | | ✗ | | | |
| rules-04 | ✓ | | | ✗ | | | |
| rules-05 | ✓ | | | ✗ | | | |
| rules-06 | ✓ | | | ✗ | | | |
| rules-07 | ✓ | | | ✗ | | | |
| rules-08 | ✓ | | | ✗ | | | |
| rules-09 | ✓ | | | ✗ | | | |
| rules-10 | ✓ | | | ✗ | | | |
| ambiguous-01 | ✓ | | | ✓ | | | |
| ambiguous-02 | ✓ | | | ✓ | | | |
| ambiguous-03 | ✓ | | | ✓ | | | |
| ambiguous-04 | ✓ | | | ✓ | | | |
| ambiguous-05 | ✓ | | | ✓ | | | |
| ambiguous-06 | ✓ | | | ✓ | | | |
| ambiguous-07 | ✓ | | | ✓ | | | |
| ambiguous-08 | ✓ | | | ✓ | | | |
| ambiguous-09 | ✓ | | | ✓ | | | |
| ambiguous-10 | ✓ | | | ✓ | | | |

---

## 8. Overall scores (TDD weights)

| Criterion | Weight | Score (0–100) | Weighted |
|-----------|--------|--------------:|---------:|
| Data extraction accuracy | 25% | | |
| Rule validation accuracy | 25% | | |
| Email quality | 15% | *(separate evaluation)* | |
| Escalation quality | 15% | *(separate evaluation)* | |
| Auditability | 10% | *(manual check of audit API)* | |
| User experience | 10% | *(frontend)* | |
| **Backend subtotal (extraction + rules)** | **50%** | | |

---

## 9. Findings and follow-ups

### Strengths

- 

### Issues / regressions

| ID | Severity | Case(s) | Description | Suggested fix |
|----|----------|---------|-------------|---------------|
| | | | | |

### Automated test suite (BE-19)

Record CI/local result for traceability:

```bash
./mvnw clean test
```

| Metric | Value |
|--------|------:|
| Tests run | |
| Failures | |
| Skipped (live Gemini) | 2 |
| Date | |

---

## 10. Sign-off

| Role | Name | Date |
|------|------|------|
| Evaluator | | |
| Reviewer | | |
