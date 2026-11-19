package com.smartteach.quiz.service;

import com.smartteach.common.dto.PageResponse;
import com.smartteach.common.exception.ApiException;
import com.smartteach.common.exception.ValidationFailedException;
import com.smartteach.quiz.dto.OptionRequest;
import com.smartteach.quiz.dto.QuestionRequest;
import com.smartteach.quiz.dto.QuizRequest;
import com.smartteach.quiz.dto.QuizResponse;
import com.smartteach.quiz.dto.QuizSummaryResponse;
import com.smartteach.quiz.entity.Question;
import com.smartteach.quiz.entity.Quiz;
import com.smartteach.quiz.mapper.QuizMapper;
import com.smartteach.quiz.repository.QuestionRepository;
import com.smartteach.quiz.repository.QuizRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A quiz is read and saved as a whole (title + questions + options). Saving replaces all questions:
 * they are not referenced from anywhere else (assignments keep a JSON snapshot), so there is no need
 * to match old and new questions one by one. Like classes, another teacher's quiz is a 404.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    static final int MAX_QUIZZES_PER_TEACHER = 50;
    static final String COPY_SUFFIX = " (bản sao)";
    private static final int MAX_TITLE_LENGTH = 100;
    private static final Sort RECENTLY_UPDATED = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuizMapper mapper;
    private final Clock clock;

    public PageResponse<QuizSummaryResponse> list(Long ownerId, Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), RECENTLY_UPDATED);
        return PageResponse.from(quizRepository.findSummaries(ownerId, page));
    }

    /** Always three queries (quiz, questions + options, accepted answers), whatever the quiz size. */
    public QuizResponse get(Long ownerId, Long quizId) {
        Quiz quiz = findOwned(ownerId, quizId);
        return mapper.toResponse(quiz, loadQuestions(quizId));
    }

    @Transactional
    public QuizResponse create(Long ownerId, QuizRequest request) {
        checkRules(request);
        // Not locked, same reasoning as classes: a race only lets the teacher go one quiz over.
        if (quizRepository.countByOwnerId(ownerId) >= MAX_QUIZZES_PER_TEACHER) {
            throw ApiException.conflict("A teacher can have at most " + MAX_QUIZZES_PER_TEACHER + " quizzes");
        }
        Quiz quiz = quizRepository.save(new Quiz(ownerId, request.title().strip(), clock.instant()));
        return mapper.toResponse(quiz, saveQuestions(quiz, request.questions()));
    }

    @Transactional
    public QuizResponse update(Long ownerId, Long quizId, QuizRequest request) {
        checkRules(request);
        Quiz quiz = quizRepository.findByIdAndOwnerIdForUpdate(quizId, ownerId)
                .orElseThrow(QuizService::quizNotFound);
        quiz.setTitle(request.title().strip());
        quiz.setUpdatedAt(clock.instant());
        // Delete first: the new questions reuse the positions, which are unique per quiz.
        questionRepository.deleteByQuizId(quizId);
        return mapper.toResponse(quiz, saveQuestions(quiz, request.questions()));
    }

    @Transactional
    public void delete(Long ownerId, Long quizId) {
        quizRepository.detachAssignments(quizId, ownerId);
        if (quizRepository.deleteOwned(quizId, ownerId) == 0) {
            throw quizNotFound();  // rolls back the detach as well
        }
    }

    @Transactional
    public QuizResponse duplicate(Long ownerId, Long quizId) {
        Quiz source = findOwned(ownerId, quizId);
        List<QuestionRequest> questions = loadQuestions(quizId).stream().map(mapper::toRequest).toList();
        return create(ownerId, new QuizRequest(copyTitle(source.getTitle()), questions));
    }

    static String copyTitle(String title) {
        int room = MAX_TITLE_LENGTH - COPY_SUFFIX.length();
        String base = title.length() > room ? title.substring(0, room).strip() : title;
        return base + COPY_SUFFIX;
    }

    private Quiz findOwned(Long ownerId, Long quizId) {
        return quizRepository.findByIdAndOwnerId(quizId, ownerId).orElseThrow(QuizService::quizNotFound);
    }

    private List<Question> loadQuestions(Long quizId) {
        List<Question> questions = questionRepository.findWithOptions(quizId);
        if (!questions.isEmpty()) {
            questionRepository.fetchAcceptedAnswers(quizId);
        }
        return questions;
    }

    private List<Question> saveQuestions(Quiz quiz, List<QuestionRequest> requests) {
        List<Question> questions = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            QuestionRequest request = requests.get(i);
            Question question = new Question(quiz.getId(), i, request.kind(), request.prompt().strip(),
                    request.timeLimitSeconds(), request.ignoreDiacritics());
            for (OptionRequest option : request.options()) {
                question.addOption(option.label().strip(), option.correct());
            }
            for (String answer : request.acceptedAnswers()) {
                question.addAcceptedAnswer(answer.strip());
            }
            questions.add(question);
        }
        return questionRepository.saveAll(questions);
    }

    private static void checkRules(QuizRequest request) {
        Map<String, String> errors = QuizRules.check(request);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
    }

    static ApiException quizNotFound() {
        return ApiException.notFound("Quiz not found");
    }
}
