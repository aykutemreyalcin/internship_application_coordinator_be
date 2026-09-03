ALTER TABLE application_cases
    ADD COLUMN case_type VARCHAR(50) NOT NULL DEFAULT 'APPLICATION';

ALTER TABLE application_cases
    ADD COLUMN extracted_payload TEXT;

ALTER TABLE application_documents
    ADD COLUMN content_type VARCHAR(255);

UPDATE application_documents
SET content_type = 'application/pdf'
WHERE content_type IS NULL;

CREATE INDEX idx_application_cases_case_type ON application_cases (case_type);
