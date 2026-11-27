-- Background e-mail and scheduled jobs.

-- A teacher can turn off assignment e-mails (reminder, summary). Password reset mails ignore it.
ALTER TABLE users ADD COLUMN email_notifications BOOLEAN NOT NULL DEFAULT TRUE;

-- Set when the reminder / summary is queued, so a job that runs again never queues it twice.
ALTER TABLE assignments ADD COLUMN reminder_queued_at TIMESTAMPTZ NULL;
ALTER TABLE assignments ADD COLUMN summary_queued_at TIMESTAMPTZ NULL;

-- Assignments that ended before this migration get no late summary or reminder.
UPDATE assignments
SET summary_queued_at = now(), reminder_queued_at = now()
WHERE status = 'closed' OR (status = 'published' AND due_at <= now());

-- Outbox: a mail is a row written in the same transaction as the change that causes it, and a
-- job sends it later. A failed send is retried with backoff; after the last try it is 'FAILED'.
CREATE TABLE email_outbox (
    id               BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_email_outbox PRIMARY KEY,
    to_address       VARCHAR(254)   NOT NULL,
    subject          VARCHAR(200)   NOT NULL,
    -- Cleared once sent: a password reset mail holds a live link.
    body             VARCHAR(10000) NULL,
    status           VARCHAR(10)    NOT NULL,
    attempts         INT            NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ    NOT NULL,
    last_error       VARCHAR(500)   NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    sent_at          TIMESTAMPTZ    NULL,
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);
-- The sender only reads pending rows that are due; the index stays small.
CREATE INDEX ix_email_outbox_due ON email_outbox (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_email_outbox_created ON email_outbox (created_at);

-- ShedLock: with several backend instances a job runs on only one of them at a time.
-- Layout required by ShedLock's JdbcTemplateLockProvider.
CREATE TABLE shedlock (
    name        VARCHAR(64)   NOT NULL CONSTRAINT pk_shedlock PRIMARY KEY,
    lock_until  TIMESTAMPTZ   NOT NULL,
    locked_at   TIMESTAMPTZ   NOT NULL,
    locked_by   VARCHAR(255)  NOT NULL
);
