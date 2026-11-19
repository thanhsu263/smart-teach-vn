-- SmartTeach v1 schema (SQL Server 2022).
-- Times are UTC (DATETIME2). Text is NVARCHAR for Vietnamese.
-- SQL Server rejects "multiple cascade paths", so some FKs are NO ACTION on purpose;
-- the service layer deletes those children first (noted per FK below).

CREATE TABLE users (
    id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_users PRIMARY KEY,
    email         NVARCHAR(254)  NOT NULL,
    password_hash NVARCHAR(100)  NOT NULL,
    full_name     NVARCHAR(100)  NOT NULL,
    role          NVARCHAR(20)   NOT NULL CONSTRAINT df_users_role DEFAULT N'TEACHER',
    created_at    DATETIME2      NOT NULL CONSTRAINT df_users_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN (N'TEACHER', N'ADMIN')),
    CONSTRAINT ck_users_full_name CHECK (LEN(TRIM(full_name)) BETWEEN 1 AND 100)
);

CREATE TABLE refresh_tokens (
    id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_refresh_tokens PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    token_hash  NVARCHAR(64)  NOT NULL,
    -- Rotation chain: every token issued from one login shares a family id,
    -- so reuse of a revoked token can revoke the whole chain.
    family_id   UNIQUEIDENTIFIER NOT NULL,
    created_at  DATETIME2     NOT NULL CONSTRAINT df_refresh_tokens_created_at DEFAULT SYSUTCDATETIME(),
    expires_at  DATETIME2     NOT NULL,
    revoked_at  DATETIME2     NULL,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE classes (
    id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_classes PRIMARY KEY,
    owner_id    BIGINT         NOT NULL,
    name        NVARCHAR(100)  NOT NULL,
    created_at  DATETIME2      NOT NULL CONSTRAINT df_classes_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_classes_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_classes_name CHECK (LEN(TRIM(name)) BETWEEN 1 AND 100)
);
CREATE INDEX ix_classes_owner ON classes (owner_id);

CREATE TABLE students (
    id               BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_students PRIMARY KEY,
    class_id         BIGINT         NOT NULL,
    name             NVARCHAR(100)  NOT NULL,
    position         INT            NOT NULL CONSTRAINT df_students_position DEFAULT 0,
    access_code_hash NVARCHAR(100)  NOT NULL,
    code_hint        NVARCHAR(4)    NOT NULL,
    created_at       DATETIME2      NOT NULL CONSTRAINT df_students_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_students_class FOREIGN KEY (class_id) REFERENCES classes (id) ON DELETE CASCADE,
    CONSTRAINT ck_students_name CHECK (LEN(TRIM(name)) BETWEEN 1 AND 100)
);
CREATE INDEX ix_students_class ON students (class_id, position);

CREATE TABLE quizzes (
    id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_quizzes PRIMARY KEY,
    owner_id    BIGINT         NOT NULL,
    title       NVARCHAR(100)  NOT NULL,
    created_at  DATETIME2      NOT NULL CONSTRAINT df_quizzes_created_at DEFAULT SYSUTCDATETIME(),
    updated_at  DATETIME2      NOT NULL CONSTRAINT df_quizzes_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_quizzes_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_quizzes_title CHECK (LEN(TRIM(title)) BETWEEN 1 AND 100)
);
CREATE INDEX ix_quizzes_owner ON quizzes (owner_id, updated_at DESC);

CREATE TABLE questions (
    id                BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_questions PRIMARY KEY,
    quiz_id           BIGINT         NOT NULL,
    position          INT            NOT NULL,
    kind              NVARCHAR(20)   NOT NULL,
    prompt            NVARCHAR(500)  NOT NULL,
    time_limit_s      INT            NOT NULL CONSTRAINT df_questions_time_limit DEFAULT 20,
    ignore_diacritics BIT            NOT NULL CONSTRAINT df_questions_ignore_diacritics DEFAULT 0,
    CONSTRAINT fk_questions_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id) ON DELETE CASCADE,
    CONSTRAINT uq_questions_quiz_position UNIQUE (quiz_id, position),
    CONSTRAINT ck_questions_kind CHECK (kind IN (N'single', N'true_false', N'multi', N'short_text')),
    CONSTRAINT ck_questions_prompt CHECK (LEN(TRIM(prompt)) BETWEEN 1 AND 500),
    CONSTRAINT ck_questions_time_limit CHECK (time_limit_s IN (10, 20, 30, 60, 90))
);

-- Only used by short_text questions (enforced in the service layer).
CREATE TABLE question_accepted_answers (
    id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_question_accepted_answers PRIMARY KEY,
    question_id BIGINT         NOT NULL,
    value       NVARCHAR(100)  NOT NULL,
    CONSTRAINT fk_accepted_answers_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
    CONSTRAINT ck_accepted_answers_value CHECK (LEN(TRIM(value)) BETWEEN 1 AND 100)
);
CREATE INDEX ix_accepted_answers_question ON question_accepted_answers (question_id);

-- "Exactly one correct" (single/true_false) and ">= 1 correct" (multi) are checked in the service layer.
CREATE TABLE options (
    id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_options PRIMARY KEY,
    question_id BIGINT         NOT NULL,
    position    INT            NOT NULL,
    label       NVARCHAR(150)  NOT NULL,
    is_correct  BIT            NOT NULL CONSTRAINT df_options_is_correct DEFAULT 0,
    CONSTRAINT fk_options_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
    CONSTRAINT uq_options_question_position UNIQUE (question_id, position),
    CONSTRAINT ck_options_label CHECK (LEN(TRIM(label)) BETWEEN 1 AND 150)
);

CREATE TABLE assignments (
    id             BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_assignments PRIMARY KEY,
    -- NO ACTION: users -> classes -> assignments already cascades.
    owner_id       BIGINT          NOT NULL,
    class_id       BIGINT          NOT NULL,
    -- NO ACTION: deleting a quiz must first set quiz_id = NULL here (the snapshot keeps the content).
    quiz_id        BIGINT          NULL,
    title          NVARCHAR(100)   NOT NULL,
    description    NVARCHAR(1000)  NOT NULL CONSTRAINT df_assignments_description DEFAULT N'',
    quiz_snapshot  NVARCHAR(MAX)   NULL,
    public_code    NVARCHAR(10)    NOT NULL,
    opens_at       DATETIME2       NULL,
    due_at         DATETIME2       NULL,
    reveal_policy  NVARCHAR(20)    NOT NULL CONSTRAINT df_assignments_reveal_policy DEFAULT N'after_close',
    status         NVARCHAR(20)    NOT NULL CONSTRAINT df_assignments_status DEFAULT N'draft',
    created_at     DATETIME2       NOT NULL CONSTRAINT df_assignments_created_at DEFAULT SYSUTCDATETIME(),
    updated_at     DATETIME2       NOT NULL CONSTRAINT df_assignments_updated_at DEFAULT SYSUTCDATETIME(),
    closed_at      DATETIME2       NULL,
    CONSTRAINT fk_assignments_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_assignments_class FOREIGN KEY (class_id) REFERENCES classes (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignments_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id),
    CONSTRAINT uq_assignments_public_code UNIQUE (public_code),
    CONSTRAINT ck_assignments_title CHECK (LEN(TRIM(title)) BETWEEN 1 AND 100),
    -- Binary collation so lowercase hex is rejected even on a case-insensitive database.
    CONSTRAINT ck_assignments_public_code CHECK (
        LEN(public_code) = 10 AND public_code COLLATE Latin1_General_BIN NOT LIKE N'%[^0-9A-F]%'),
    CONSTRAINT ck_assignments_schedule CHECK (opens_at IS NULL OR due_at IS NULL OR due_at > opens_at),
    CONSTRAINT ck_assignments_reveal_policy CHECK (reveal_policy IN (N'immediate', N'after_close')),
    CONSTRAINT ck_assignments_status CHECK (status IN (N'draft', N'published', N'closed')),
    CONSTRAINT ck_assignments_snapshot CHECK (status = N'draft' OR quiz_snapshot IS NOT NULL),
    CONSTRAINT ck_assignments_snapshot_json CHECK (quiz_snapshot IS NULL OR ISJSON(quiz_snapshot) = 1),
    CONSTRAINT ck_assignments_closed_at CHECK (
        (status = N'closed' AND closed_at IS NOT NULL) OR (status <> N'closed' AND closed_at IS NULL))
);
CREATE INDEX ix_assignments_owner_class ON assignments (owner_id, class_id, created_at DESC);

CREATE TABLE attempts (
    id              BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_attempts PRIMARY KEY,
    assignment_id   BIGINT        NOT NULL,
    -- NO ACTION: classes -> assignments -> attempts already cascades;
    -- deleting a student must delete their attempts first.
    student_id      BIGINT        NOT NULL,
    status          NVARCHAR(20)  NOT NULL CONSTRAINT df_attempts_status DEFAULT N'in_progress',
    started_at      DATETIME2     NOT NULL CONSTRAINT df_attempts_started_at DEFAULT SYSUTCDATETIME(),
    submitted_at    DATETIME2     NULL,
    score           INT           NULL,
    correct_count   INT           NULL,
    question_total  INT           NULL,
    CONSTRAINT fk_attempts_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_attempts_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT ck_attempts_status CHECK (status IN (N'in_progress', N'submitted', N'invalidated')),
    CONSTRAINT ck_attempts_submitted CHECK (
        (status = N'submitted' AND submitted_at IS NOT NULL) OR (status <> N'submitted' AND submitted_at IS NULL)),
    CONSTRAINT ck_attempts_score CHECK (score IS NULL OR score BETWEEN 0 AND 100)
);
-- At most one open attempt per student per assignment.
CREATE UNIQUE INDEX ux_attempts_one_in_progress
    ON attempts (assignment_id, student_id) WHERE status = N'in_progress';
CREATE INDEX ix_attempts_student ON attempts (student_id);
-- Used by the nightly 90-day retention job.
CREATE INDEX ix_attempts_started_at ON attempts (started_at);

CREATE TABLE attempt_answers (
    id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_attempt_answers PRIMARY KEY,
    attempt_id  BIGINT          NOT NULL,
    position    INT             NOT NULL,
    answer      NVARCHAR(1000)  NOT NULL,
    is_correct  BIT             NULL,  -- filled in by the server on submit
    saved_at    DATETIME2       NOT NULL CONSTRAINT df_attempt_answers_saved_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_attempt_answers_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE,
    CONSTRAINT uq_attempt_answers_attempt_position UNIQUE (attempt_id, position)
);
