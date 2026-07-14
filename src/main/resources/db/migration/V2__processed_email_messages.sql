CREATE TABLE processed_email_messages (
    id          UUID PRIMARY KEY,
    message_id  VARCHAR(512) NOT NULL UNIQUE,
    case_id     UUID REFERENCES application_cases (case_id) ON DELETE SET NULL,
    sender      VARCHAR(512),
    subject     VARCHAR(1024),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_processed_email_messages_case_id ON processed_email_messages (case_id);
