CREATE TABLE application_cases (
    case_id              UUID PRIMARY KEY,
    status               VARCHAR(50)  NOT NULL,
    student_name         VARCHAR(255),
    student_id           VARCHAR(255),
    company_name         VARCHAR(255),
    supervisor_name      VARCHAR(255),
    supervisor_email     VARCHAR(255),
    internship_start_date DATE,
    internship_end_date   DATE,
    field_of_study       VARCHAR(255),
    recommendation       VARCHAR(50),
    recommendation_reason TEXT,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE application_documents (
    id           UUID PRIMARY KEY,
    case_id      UUID         NOT NULL REFERENCES application_cases (case_id) ON DELETE CASCADE,
    file_name    VARCHAR(255) NOT NULL,
    storage_path VARCHAR(255) NOT NULL,
    page_count   INTEGER,
    uploaded_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_application_documents_case_id ON application_documents (case_id);

CREATE TABLE validation_results (
    id      UUID PRIMARY KEY,
    case_id UUID        NOT NULL REFERENCES application_cases (case_id) ON DELETE CASCADE,
    type    VARCHAR(50) NOT NULL,
    passed  BOOLEAN     NOT NULL
);

CREATE INDEX idx_validation_results_case_id ON validation_results (case_id);

CREATE TABLE validation_issues (
    validation_result_id UUID         NOT NULL REFERENCES validation_results (id) ON DELETE CASCADE,
    field                VARCHAR(255) NOT NULL,
    message              TEXT         NOT NULL,
    severity             VARCHAR(50)  NOT NULL
);

CREATE INDEX idx_validation_issues_validation_result_id ON validation_issues (validation_result_id);

CREATE TABLE audit_log_entries (
    id        UUID PRIMARY KEY,
    case_id   UUID         NOT NULL REFERENCES application_cases (case_id) ON DELETE CASCADE,
    actor     VARCHAR(255) NOT NULL,
    action    VARCHAR(255) NOT NULL,
    detail    TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_log_entries_case_id ON audit_log_entries (case_id);
