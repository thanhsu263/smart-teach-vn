# Database (ERD)

PostgreSQL 18, created by Flyway (`backend/src/main/resources/db/migration`). Text columns are
`VARCHAR` (UTF-8), times are `TIMESTAMPTZ` and the quiz snapshot is `JSONB`. Audit columns (`created_at`, `updated_at`) are left out
of the diagram.

```mermaid
erDiagram
    users ||--o{ refresh_tokens : "has"
    users ||--o{ classes : "owns"
    users ||--o{ quizzes : "owns"
    users ||--o{ assignments : "owns"
    classes ||--o{ students : "has"
    classes ||--o{ assignments : "receives"
    quizzes ||--o{ questions : "has"
    quizzes |o--o{ assignments : "is given as"
    questions ||--o{ options : "has"
    questions ||--o{ question_accepted_answers : "accepts"
    assignments ||--o{ attempts : "has"
    students ||--o{ attempts : "makes"
    attempts ||--o{ attempt_answers : "saves"

    users {
        bigint id PK
        varchar email UK
        varchar password_hash "BCrypt"
        varchar full_name
        varchar role "TEACHER | ADMIN"
    }
    refresh_tokens {
        bigint id PK
        bigint user_id FK
        varchar token_hash UK "SHA-256"
        uuid family_id "rotation chain"
        timestamptz expires_at
        timestamptz revoked_at
    }
    classes {
        bigint id PK
        bigint owner_id FK
        varchar name
    }
    students {
        bigint id PK
        bigint class_id FK
        varchar name
        int position
        varchar access_code_hash "SHA-256, indexed"
        varchar code_hint "last 4 chars"
    }
    quizzes {
        bigint id PK
        bigint owner_id FK
        varchar title
    }
    questions {
        bigint id PK
        bigint quiz_id FK
        int position
        varchar kind "single | true_false | multi | short_text"
        varchar prompt
        int time_limit_s
        boolean ignore_diacritics
    }
    options {
        bigint id PK
        bigint question_id FK
        int position
        varchar label
        boolean is_correct
    }
    question_accepted_answers {
        bigint id PK
        bigint question_id FK
        varchar value
    }
    assignments {
        bigint id PK
        bigint owner_id FK
        bigint class_id FK
        bigint quiz_id FK "NULL after the quiz is deleted"
        varchar title
        jsonb quiz_snapshot "frozen on publish"
        varchar public_code UK "10 hex chars"
        timestamptz opens_at
        timestamptz due_at
        varchar reveal_policy "immediate | after_close"
        varchar status "draft | published | closed"
        timestamptz closed_at
    }
    attempts {
        bigint id PK
        bigint assignment_id FK
        bigint student_id FK
        varchar status "in_progress | submitted | invalidated"
        varchar token_hash UK "SHA-256 of the attempt token"
        timestamptz started_at
        timestamptz submitted_at
        int score
        int correct_count
        int question_total
    }
    attempt_answers {
        bigint id PK
        bigint attempt_id FK
        int position
        varchar answer "JSON"
        boolean is_correct "set on submit"
    }
```

Rules enforced by the database:

- `users`: unique index on `lower(email)`, so two emails that differ only in case cannot both
  be registered (the service also lower-cases emails before saving them).
- `attempts`: partial unique index on `(assignment_id, student_id) WHERE status = 'in_progress'`,
  so one student cannot have two attempts in progress for the same assignment, even when two
  "start" requests arrive at the same time.
- CHECK constraints for every enum column, text lengths, `due_at > opens_at`, the `public_code`
  format (`^[0-9A-F]{10}$`), a snapshot once published and
  "`closed_at` is set only when the status is `closed`" (same idea for `submitted_at`).
- Deletes cascade: deleting a class removes its students, assignments and their attempts;
  deleting a student removes that student's attempts; deleting a quiz keeps the assignments made
  from it (`quiz_id` becomes `NULL`, the snapshot stays).
- PostgreSQL does not index foreign keys by itself, so every foreign key used in a lookup or a
  cascade has its own index.
