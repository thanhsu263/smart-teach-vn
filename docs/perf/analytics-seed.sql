-- Fake data for EXPLAIN ANALYZE of the analytics queries (see analytics-explain.sql).
-- Run on an empty database that has the Flyway schema, never on a real one:
--   100 teachers x 10 classes x 40 students = 40,000 students
--   5 assignments per class                  = 5,000 assignments
--   every student starts each assignment     = 200,000 attempts (1 in 20 still in progress)
--   10 questions per attempt                 = 2,000,000 answers
INSERT INTO users (email, password_hash, full_name)
SELECT 'perf' || g || '@example.com', 'not-a-hash', 'Teacher ' || g
FROM generate_series(1, 100) g;

INSERT INTO classes (owner_id, name)
SELECT u.id, 'Class ' || c FROM users u, generate_series(1, 10) c;

INSERT INTO students (class_id, name, position, access_code_hash, code_hint)
SELECT c.id, 'Student ' || s, s, md5(c.id || '-' || s), 'ABCD'
FROM classes c, generate_series(0, 39) s;

INSERT INTO assignments (owner_id, class_id, title, quiz_snapshot, public_code, status)
SELECT c.owner_id, c.id, 'Assignment ' || a, '{}'::jsonb, upper(substr(md5(c.id || '-' || a), 1, 10)), 'published'
FROM classes c, generate_series(1, 5) a;

INSERT INTO attempts (assignment_id, student_id, status, submitted_at, score, correct_count, question_total)
SELECT a.id, s.id,
       CASE WHEN s.position % 20 = 0 THEN 'in_progress' ELSE 'submitted' END,
       CASE WHEN s.position % 20 = 0 THEN NULL ELSE now() END,
       CASE WHEN s.position % 20 = 0 THEN NULL ELSE (abs(hashtext(a.id || '-' || s.id)) % 11) * 10 END,
       CASE WHEN s.position % 20 = 0 THEN NULL ELSE abs(hashtext(a.id || '-' || s.id)) % 11 END,
       CASE WHEN s.position % 20 = 0 THEN NULL ELSE 10 END
FROM assignments a JOIN students s ON s.class_id = a.class_id;

INSERT INTO attempt_answers (attempt_id, position, answer, is_correct)
SELECT t.id, p, (abs(hashtext(t.id || '-' || p)) % 4)::text,
       CASE WHEN t.status = 'submitted' THEN abs(hashtext(t.id || '-' || p)) % 4 = 0 END
FROM attempts t, generate_series(0, 9) p;

VACUUM ANALYZE;
