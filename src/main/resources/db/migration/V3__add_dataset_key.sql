ALTER TABLE application_cases ADD COLUMN dataset_key VARCHAR(64) UNIQUE;

CREATE INDEX idx_application_cases_dataset_key ON application_cases (dataset_key);
