-- SmartTeach v1 schema (PostgreSQL 18).
-- Times are UTC (TIMESTAMPTZ). Text is VARCHAR (UTF-8, so Vietnamese needs nothing special).
-- PostgreSQL does not create indexes for foreign keys: the FKs used for lookups or cascades
-- get one below, unless a unique constraint or another index already starts with that column.

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_users PRIMARY KEY,
    email         VARCHAR(254)  NOT NULL,
    password_hash VARCHAR(100)  NOT NULL,
    full_name     VARCHAR(100)  NOT NULL,
    role          VARCHAR(20)   NOT NULL CONSTRAINT df_users_role DEFAULT 'TEACHER',
    created_at    TIMESTAMPTZ   NOT NULL CONSTRAINT df_users_created_at DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('TEACHER', 'ADMIN')),
    CONSTRAINT ck_users_full_name CHECK (char_length(trim(full_name)) BETWEEN 1 AND 100)
);
-- The service lowercases emails; PostgreSQL compares text case-sensitively, so this is the
-- second line of defence against "A@x.vn" and "a@x.vn" becoming two accounts.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_refresh_tokens PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    token_hash  VARCHAR(64)   NOT NULL,
    -- Rotation chain: every token issued from one login shares a family id,
    -- so reuse of a revoked token can revoke the whole chain.
    family_id   UUID          NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL CONSTRAINT df_refresh_tokens_created_at DEFAULT now(),
    expires_at  TIMESTAMPTZ   NOT NULL,
    revoked_at  TIMESTAMPTZ   NULL,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE classes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_classes PRIMARY KEY,
    owner_id    BIGINT         NOT NULL,
    name        VARCHAR(100)   NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL CONSTRAINT df_classes_created_at DEFAULT now(),
    CONSTRAINT fk_classes_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_classes_name CHECK (char_length(trim(name)) BETWEEN 1 AND 100)
);
CREATE INDEX ix_classes_owner ON classes (owner_id);

CREATE TABLE students (
    id               BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_students PRIMARY KEY,
    class_id         BIGINT         NOT NULL,
    name             VARCHAR(100)   NOT NULL,
    position         INT            NOT NULL CONSTRAINT df_students_position DEFAULT 0,
    access_code_hash VARCHAR(100)   NOT NULL,
    code_hint        VARCHAR(4)     NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL CONSTRAINT df_students_created_at DEFAULT now(),
    CONSTRAINT fk_students_class FOREIGN KEY (class_id) REFERENCES classes (id) ON DELETE CASCADE,
    CONSTRAINT ck_students_name CHECK (char_length(trim(name)) BETWEEN 1 AND 100)
);
CREATE INDEX ix_students_class ON students (class_id, position);

CREATE TABLE quizzes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_quizzes PRIMARY KEY,
    owner_id    BIGINT         NOT NULL,
    title       VARCHAR(100)   NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL CONSTRAINT df_quizzes_created_at DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL CONSTRAINT df_quizzes_updated_at DEFAULT now(),
    CONSTRAINT fk_quizzes_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_quizzes_title CHECK (char_length(trim(title)) BETWEEN 1 AND 100)
);
CREATE INDEX ix_quizzes_owner ON quizzes (owner_id, updated_at DESC);

CREATE TABLE questions (
    id                BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_questions PRIMARY KEY,
    quiz_id           BIGINT         NOT NULL,
    position          INT            NOT NULL,
    kind              VARCHAR(20)    NOT NULL,
    prompt            VARCHAR(500)   NOT NULL,
    time_limit_s      INT            NOT NULL CONSTRAINT df_questions_time_limit DEFAULT 20,
    ignore_diacritics BOOLEAN        NOT NULL CONSTRAINT df_questions_ignore_diacritics DEFAULT false,
    CONSTRAINT fk_questions_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id) ON DELETE CASCADE,
    CONSTRAINT uq_questions_quiz_position UNIQUE (quiz_id, position),
    CONSTRAINT ck_questions_kind CHECK (kind IN ('single', 'true_false', 'multi', 'short_text')),
    CONSTRAINT ck_questions_prompt CHECK (char_length(trim(prompt)) BETWEEN 1 AND 500),
    CONSTRAINT ck_questions_time_limit CHECK (time_limit_s IN (10, 20, 30, 60, 90))
);

-- Only used by short_text questions (enforced in the service layer).
CREATE TABLE question_accepted_answers (
    id          BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_question_accepted_answers PRIMARY KEY,
    question_id BIGINT         NOT NULL,
    value       VARCHAR(100)   NOT NULL,
    CONSTRAINT fk_accepted_answers_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
    CONSTRAINT ck_accepted_answers_value CHECK (char_length(trim(value)) BETWEEN 1 AND 100)
);
CREATE INDEX ix_accepted_answers_question ON question_accepted_answers (question_id);

-- "Exactly one correct" (single/true_false) and ">= 1 correct" (multi) are checked in the service layer.
CREATE TABLE options (
    id          BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_options PRIMARY KEY,
    question_id BIGINT         NOT NULL,
    position    INT            NOT NULL,
    label       VARCHAR(150)   NOT NULL,
    is_correct  BOOLEAN        NOT NULL CONSTRAINT df_options_is_correct DEFAULT false,
    CONSTRAINT fk_options_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
    CONSTRAINT uq_options_question_position UNIQUE (question_id, position),
    CONSTRAINT ck_options_label CHECK (char_length(trim(label)) BETWEEN 1 AND 150)
);

CREATE TABLE assignments (
    id             BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_assignments PRIMARY KEY,
    owner_id       BIGINT          NOT NULL,
    class_id       BIGINT          NOT NULL,
    -- SET NULL: deleting a quiz keeps its assignments, which grade from their own snapshot.
    quiz_id        BIGINT          NULL,
    title          VARCHAR(100)    NOT NULL,
    description    VARCHAR(1000)   NOT NULL CONSTRAINT df_assignments_description DEFAULT '',
    -- A frozen copy of the quiz, taken on publish. Always read and written whole, so it is one
    -- JSONB document instead of copies of the three quiz tables.
    quiz_snapshot  JSONB           NULL,
    public_code    VARCHAR(10)     NOT NULL,
    opens_at       TIMESTAMPTZ     NULL,
    due_at         TIMESTAMPTZ     NULL,
    reveal_policy  VARCHAR(20)     NOT NULL CONSTRAINT df_assignments_reveal_policy DEFAULT 'after_close',
    status         VARCHAR(20)     NOT NULL CONSTRAINT df_assignments_status DEFAULT 'draft',
    created_at     TIMESTAMPTZ     NOT NULL CONSTRAINT df_assignments_created_at DEFAULT now(),
    updated_at     TIMESTAMPTZ     NOT NULL CONSTRAINT df_assignments_updated_at DEFAULT now(),
    closed_at      TIMESTAMPTZ     NULL,
    CONSTRAINT fk_assignments_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignments_class FOREIGN KEY (class_id) REFERENCES classes (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignments_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id) ON DELETE SET NULL,
    CONSTRAINT uq_assignments_public_code UNIQUE (public_code),
    CONSTRAINT ck_assignments_title CHECK (char_length(trim(title)) BETWEEN 1 AND 100),
    CONSTRAINT ck_assignments_public_code CHECK (public_code ~ '^[0-9A-F]{10}$'),
    CONSTRAINT ck_assignments_schedule CHECK (opens_at IS NULL OR due_at IS NULL OR due_at > opens_at),
    CONSTRAINT ck_assignments_reveal_policy CHECK (reveal_policy IN ('immediate', 'after_close')),
    CONSTRAINT ck_assignments_status CHECK (status IN ('draft', 'published', 'closed')),
    CONSTRAINT ck_assignments_snapshot CHECK (status = 'draft' OR quiz_snapshot IS NOT NULL),
    CONSTRAINT ck_assignments_closed_at CHECK (
        (status = 'closed' AND closed_at IS NOT NULL) OR (status <> 'closed' AND closed_at IS NULL))
);
CREATE INDEX ix_assignments_owner_class ON assignments (owner_id, class_id, created_at DESC);
-- Cascade from a deleted class, and SET NULL from a deleted quiz.
CREATE INDEX ix_assignments_class ON assignments (class_id);
CREATE INDEX ix_assignments_quiz ON assignments (quiz_id);

CREATE TABLE attempts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_attempts PRIMARY KEY,
    assignment_id   BIGINT        NOT NULL,
    student_id      BIGINT        NOT NULL,
    status          VARCHAR(20)   NOT NULL CONSTRAINT df_attempts_status DEFAULT 'in_progress',
    started_at      TIMESTAMPTZ   NOT NULL CONSTRAINT df_attempts_started_at DEFAULT now(),
    submitted_at    TIMESTAMPTZ   NULL,
    score           INT           NULL,
    correct_count   INT           NULL,
    question_total  INT           NULL,
    CONSTRAINT fk_attempts_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE,
    -- Reached by two cascade paths (class -> students and class -> assignments); PostgreSQL allows that.
    CONSTRAINT fk_attempts_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT ck_attempts_status CHECK (status IN ('in_progress', 'submitted', 'invalidated')),
    CONSTRAINT ck_attempts_submitted CHECK (
        (status = 'submitted' AND submitted_at IS NOT NULL) OR (status <> 'submitted' AND submitted_at IS NULL)),
    CONSTRAINT ck_attempts_score CHECK (score IS NULL OR score BETWEEN 0 AND 100)
);
-- At most one open attempt per student per assignment: two concurrent "start" requests cannot
-- both insert one, whatever the application code does.
CREATE UNIQUE INDEX ux_attempts_one_in_progress
    ON attempts (assignment_id, student_id) WHERE status = 'in_progress';
-- Analytics and the cascade from a deleted assignment read every attempt of one assignment.
CREATE INDEX ix_attempts_assignment ON attempts (assignment_id);
CREATE INDEX ix_attempts_student ON attempts (student_id);
-- Used by the nightly 90-day retention job.
CREATE INDEX ix_attempts_started_at ON attempts (started_at);

CREATE TABLE attempt_answers (
    id          BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_attempt_answers PRIMARY KEY,
    attempt_id  BIGINT          NOT NULL,
    position    INT             NOT NULL,
    answer      VARCHAR(1000)   NOT NULL,
    is_correct  BOOLEAN         NULL,  -- filled in by the server on submit
    saved_at    TIMESTAMPTZ     NOT NULL CONSTRAINT df_attempt_answers_saved_at DEFAULT now(),
    CONSTRAINT fk_attempt_answers_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE,
    CONSTRAINT uq_attempt_answers_attempt_position UNIQUE (attempt_id, position)
);
