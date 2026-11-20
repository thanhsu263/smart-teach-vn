# Database (ERD)

SQL Server, created by Flyway (`backend/src/main/resources/db/migration`). Text columns are
`NVARCHAR`, times are `DATETIME2` in UTC. Audit columns (`created_at`, `updated_at`) are left out
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
        nvarchar email UK
        nvarchar password_hash "BCrypt"
        nvarchar full_name
        nvarchar role "TEACHER | ADMIN"
    }
    refresh_tokens {
        bigint id PK
        bigint user_id FK
        nvarchar token_hash UK "SHA-256"
        uniqueidentifier family_id "rotation chain"
        datetime2 expires_at
        datetime2 revoked_at
    }
    classes {
        bigint id PK
        bigint owner_id FK
        nvarchar name
    }
    students {
        bigint id PK
        bigint class_id FK
        nvarchar name
        int position
        nvarchar access_code_hash "SHA-256, indexed"
        nvarchar code_hint "last 4 chars"
    }
    quizzes {
        bigint id PK
        bigint owner_id FK
        nvarchar title
    }
    questions {
        bigint id PK
        bigint quiz_id FK
        int position
        nvarchar kind "single | true_false | multi | short_text"
        nvarchar prompt
        int time_limit_s
        bit ignore_diacritics
    }
    options {
        bigint id PK
        bigint question_id FK
        int position
        nvarchar label
        bit is_correct
    }
    question_accepted_answers {
        bigint id PK
        bigint question_id FK
        nvarchar value
    }
    assignments {
        bigint id PK
        bigint owner_id FK
        bigint class_id FK
        bigint quiz_id FK "NULL after the quiz is deleted"
        nvarchar title
        nvarchar quiz_snapshot "JSON, frozen on publish"
        nvarchar public_code UK "10 hex chars"
        datetime2 opens_at
        datetime2 due_at
        nvarchar reveal_policy "immediate | after_close"
        nvarchar status "draft | published | closed"
        datetime2 closed_at
    }
    attempts {
        bigint id PK
        bigint assignment_id FK
        bigint student_id FK
        nvarchar status "in_progress | submitted | invalidated"
        nvarchar token_hash UK "SHA-256 of the attempt token"
        datetime2 started_at
        datetime2 submitted_at
        int score
        int correct_count
        int question_total
    }
    attempt_answers {
        bigint id PK
        bigint attempt_id FK
        int position
        nvarchar answer "JSON"
        bit is_correct "set on submit"
    }
```

Rules enforced by the database:

- `attempts`: filtered unique index on `(assignment_id, student_id) WHERE status = 'in_progress'`,
  so one student cannot have two attempts in progress for the same assignment.
- CHECK constraints for every enum column, text lengths, `due_at > opens_at`, the `public_code`
  format, a valid JSON snapshot once published and
  "`closed_at` is set only when the status is `closed`" (same idea for `submitted_at`).
- SQL Server allows only one cascade path to a table. Deleting a class cascades to its
  assignments and their attempts; deleting a student removes that student's attempts in the
  service first.
