-- EXPLAIN ANALYZE of the SQL behind GET /api/assignments/{id}/analytics and /question-stats,
-- on the data of analytics-seed.sql. Run with psql (it uses \gset); the queries are the ones
-- Hibernate sends (see AttemptRepository and AttemptAnswerRepository), with values inlined.
SELECT id AS aid FROM assignments ORDER BY id OFFSET 2500 LIMIT 1 \gset

\echo '== 1. analytics: the attempts of one assignment'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT * FROM attempts WHERE assignment_id = :aid AND status <> 'invalidated';

\echo '== 2. analytics: answers per question position'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT aa.position, count(aa.id), sum(CASE WHEN aa.is_correct = true THEN 1 ELSE 0 END)
FROM attempt_answers aa JOIN attempts a ON a.id = aa.attempt_id
WHERE a.assignment_id = :aid AND a.status = 'submitted'
GROUP BY aa.position;

\echo '== 3. question-stats: submitted count'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT count(*) FROM attempts WHERE assignment_id = :aid AND status = 'submitted';

\echo '== 4. question-stats: correct answers in the best and worst 27%'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY score DESC, id) AS score_rank, COUNT(*) OVER () AS total
    FROM attempts
    WHERE assignment_id = :aid AND status = 'submitted'
)
SELECT aa.position, COUNT(*) FILTER (WHERE aa.is_correct),
       COUNT(*) FILTER (WHERE aa.is_correct AND r.score_rank <= 10),
       COUNT(*) FILTER (WHERE aa.is_correct AND r.score_rank > r.total - 10)
FROM ranked r JOIN attempt_answers aa ON aa.attempt_id = r.id
GROUP BY aa.position;

\echo '== 5. question-stats: wrong answers'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT aa.position, aa.answer
FROM attempt_answers aa JOIN attempts a ON a.id = aa.attempt_id
WHERE a.assignment_id = :aid AND a.status = 'submitted' AND aa.is_correct = false
ORDER BY aa.id;
