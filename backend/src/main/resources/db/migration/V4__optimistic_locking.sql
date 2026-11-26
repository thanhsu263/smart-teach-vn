-- Optimistic locking (JPA @Version): every save bumps the number; a save based on an older
-- number is refused with 409, so two tabs cannot silently overwrite each other.
ALTER TABLE quizzes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE assignments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
