ALTER TABLE job_events ADD COLUMN source_email_id VARCHAR(150);

CREATE INDEX job_events_source_email_idx
    ON job_events (user_id, source_email_id)
    WHERE source_email_id IS NOT NULL;
