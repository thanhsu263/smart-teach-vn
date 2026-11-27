package com.smartteach.assignment.service;

import com.smartteach.assignment.dto.QuestionStatsResponse;
import com.smartteach.assignment.dto.QuestionStatsResponse.QuestionStat;
import com.smartteach.assignment.dto.QuestionStatsResponse.WrongAnswer;
import com.smartteach.assignment.entity.Assignment;
import com.smartteach.assignment.entity.AttemptStatus;
import com.smartteach.assignment.repository.AttemptAnswerRepository;
import com.smartteach.assignment.repository.AttemptRepository;
import com.smartteach.assignment.repository.GroupCorrectCounts;
import com.smartteach.assignment.repository.WrongAnswerRow;
import com.smartteach.quiz.dto.QuestionResponse;
import com.smartteach.quiz.entity.QuestionKind;
import com.smartteach.quiz.service.Grading;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Item analysis of an assignment, the classic way of checking a test:
 * <ul>
 *   <li>difficulty: the share of submitted attempts that got the question right;</li>
 *   <li>discrimination: correct rate of the best 27% attempts minus that of the worst 27%. A good
 *       question is answered right by strong students and wrong by weak ones (close to 1); near 0
 *       it does not separate them, below 0 weak students do better (often a wrong answer key);</li>
 *   <li>the wrong answer chosen most often, which shows the usual misconception.</li>
 * </ul>
 * Four queries whatever the class size: assignment, submitted count, group counts, wrong answers.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionStatsService {

    /** Below this, 27% is one or two students and the discrimination would be noise. */
    static final int MIN_SUBMISSIONS = 10;
    static final double GROUP_SHARE = 0.27;
    static final double HARD_BELOW = 30;
    /** A common rule of thumb in test analysis: below 0.2 a question separates students poorly. */
    static final double LOW_DISCRIMINATION_BELOW = 0.2;

    private final AssignmentService assignmentService;
    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final QuizSnapshots snapshots;

    public QuestionStatsResponse questionStats(Long ownerId, Long assignmentId) {
        Assignment assignment = assignmentService.findOwned(ownerId, assignmentId);
        int submitted = (int) attemptRepository.countByAssignmentIdAndStatus(assignmentId, AttemptStatus.SUBMITTED);
        // From 10 attempts on the group has at least 3, and two groups of 27% never overlap.
        int groupSize = submitted < MIN_SUBMISSIONS ? 0 : (int) Math.round(submitted * GROUP_SHARE);
        if (assignment.getQuizSnapshot() == null) {
            return new QuestionStatsResponse(submitted, MIN_SUBMISSIONS, groupSize, List.of());  // draft
        }

        List<QuestionResponse> questions = snapshots.read(assignment.getQuizSnapshot()).questions();
        Map<Integer, GroupCorrectCounts> counts = answerRepository
                .countCorrectByScoreGroup(assignmentId, groupSize).stream()
                .collect(Collectors.toMap(GroupCorrectCounts::getPosition, Function.identity()));
        Map<Integer, WrongAnswer> wrongAnswers = mostCommonWrongAnswers(assignmentId, questions);

        List<QuestionStat> stats = new ArrayList<>();
        for (QuestionResponse q : questions) {
            GroupCorrectCounts count = counts.get(q.position());
            int correct = count == null ? 0 : (int) count.getCorrect();
            // Unanswered questions count as wrong, the same as in the analytics and the score.
            Double rate = submitted == 0 ? null : round(100.0 * correct / submitted, 1);
            Double discrimination = groupSize == 0 ? null
                    : count == null ? 0.0
                    : round((double) (count.getUpperCorrect() - count.getLowerCorrect()) / groupSize, 2);
            stats.add(new QuestionStat(q.position(), q.kind(), q.prompt(), correct, rate, discrimination,
                    wrongAnswers.get(q.position()),
                    rate != null && rate < HARD_BELOW,
                    discrimination != null && discrimination < LOW_DISCRIMINATION_BELOW));
        }
        return new QuestionStatsResponse(submitted, MIN_SUBMISSIONS, groupSize, stats);
    }

    /**
     * Counted in Java rather than SQL because equal answers can be saved differently: a multi
     * answer {@code [2,0]} is the same choice as {@code [0,2]}, and "Hà Nội " the same text as
     * "hà nội" (with the question's own diacritics setting).
     */
    private Map<Integer, WrongAnswer> mostCommonWrongAnswers(Long assignmentId, List<QuestionResponse> questions) {
        Map<Integer, QuestionResponse> byPosition = questions.stream()
                .collect(Collectors.toMap(QuestionResponse::position, Function.identity()));
        // Insertion order keeps the first seen answer ahead on a tie.
        Map<Integer, Map<String, Tally>> tallies = new HashMap<>();
        for (WrongAnswerRow row : answerRepository.findWrongAnswers(assignmentId, AttemptStatus.SUBMITTED)) {
            QuestionResponse q = byPosition.get(row.position());
            JsonNode answer = snapshots.readAnswer(row.answer());
            String key = q.kind() == QuestionKind.SHORT_TEXT
                    ? Grading.normalize(answer.stringValue(), q.ignoreDiacritics())
                    : optionLabels(q, answer);
            String label = q.kind() == QuestionKind.SHORT_TEXT ? answer.stringValue().strip() : key;
            tallies.computeIfAbsent(row.position(), p -> new LinkedHashMap<>())
                    .computeIfAbsent(key, k -> new Tally(label)).count++;
        }

        Map<Integer, WrongAnswer> result = new HashMap<>();
        tallies.forEach((position, answers) -> {
            Tally best = null;
            for (Tally tally : answers.values()) {
                if (best == null || tally.count > best.count) {
                    best = tally;
                }
            }
            result.put(position, new WrongAnswer(best.label, best.count));
        });
        return result;
    }

    /** "B" for a single choice, "A, C" for a multi choice (in option order). */
    private static String optionLabels(QuestionResponse q, JsonNode answer) {
        if (!answer.isArray()) {
            return q.options().get(answer.intValue()).label();
        }
        return StreamSupport.stream(answer.spliterator(), false)
                .map(JsonNode::intValue)
                .sorted()
                .map(i -> q.options().get(i).label())
                .collect(Collectors.joining(", "));
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static final class Tally {
        private final String label;
        private int count;

        private Tally(String label) {
            this.label = label;
        }
    }
}
