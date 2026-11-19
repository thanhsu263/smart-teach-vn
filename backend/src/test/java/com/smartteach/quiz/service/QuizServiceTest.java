package com.smartteach.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartteach.common.exception.ApiException;
import com.smartteach.common.exception.ValidationFailedException;
import com.smartteach.quiz.dto.OptionRequest;
import com.smartteach.quiz.dto.QuestionRequest;
import com.smartteach.quiz.dto.QuizRequest;
import com.smartteach.quiz.dto.QuizResponse;
import com.smartteach.quiz.entity.Question;
import com.smartteach.quiz.entity.QuestionKind;
import com.smartteach.quiz.entity.Quiz;
import com.smartteach.quiz.mapper.QuizMapperImpl;
import com.smartteach.quiz.repository.QuestionRepository;
import com.smartteach.quiz.repository.QuizRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T08:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-20T08:00:00Z");
    private static final long OWNER = 7L;
    private static final long QUIZ_ID = 3L;

    @Mock
    QuizRepository quizRepository;

    @Mock
    QuestionRepository questionRepository;

    private QuizService service;

    @BeforeEach
    void setUp() {
        service = new QuizService(quizRepository, questionRepository, new QuizMapperImpl(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createTrimsTextAndNumbersQuestionsAndOptions() {
        when(quizRepository.countByOwnerId(OWNER)).thenReturn(0L);
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(questionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        QuizResponse response = service.create(OWNER, new QuizRequest("  Địa lý  ", List.of(
                new QuestionRequest(QuestionKind.SINGLE, " Thủ đô? ", null, false,
                        List.of(new OptionRequest(" Hà Nội ", true), new OptionRequest("Huế", false)), null),
                new QuestionRequest(QuestionKind.SHORT_TEXT, "Sông dài nhất?", 30, true, null,
                        List.of(" Mê Kông ")))));

        assertThat(response.title()).isEqualTo("Địa lý");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.questions()).extracting("position").containsExactly(0, 1);
        assertThat(response.questions().getFirst().prompt()).isEqualTo("Thủ đô?");
        assertThat(response.questions().getFirst().timeLimitSeconds()).isEqualTo(20);
        assertThat(response.questions().getFirst().options()).extracting("label").containsExactly("Hà Nội", "Huế");
        assertThat(response.questions().getLast().acceptedAnswers()).containsExactly("Mê Kông");
    }

    @Test
    void createIsRejectedAtTheQuizLimit() {
        when(quizRepository.countByOwnerId(OWNER)).thenReturn((long) QuizService.MAX_QUIZZES_PER_TEACHER);

        assertThatThrownBy(() -> service.create(OWNER, new QuizRequest("Quiz", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(quizRepository, never()).save(any());
    }

    @Test
    void invalidQuizIsRejectedBeforeTouchingTheDatabase() {
        QuizRequest request = new QuizRequest("Quiz", List.of(new QuestionRequest(QuestionKind.SINGLE, "Câu?", 20,
                false, List.of(new OptionRequest("A", false), new OptionRequest("B", false)), null)));

        assertThatThrownBy(() -> service.update(OWNER, QUIZ_ID, request))
                .isInstanceOfSatisfying(ValidationFailedException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("questions[0].options"));
        verifyNoInteractions(quizRepository, questionRepository);
    }

    @Test
    void updateDeletesOldQuestionsBeforeSavingNewOnes() {
        Quiz quiz = withId(new Quiz(OWNER, "Cũ", CREATED));
        when(quizRepository.findByIdAndOwnerIdForUpdate(QUIZ_ID, OWNER)).thenReturn(Optional.of(quiz));
        when(questionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        QuizResponse response = service.update(OWNER, QUIZ_ID, new QuizRequest("Mới", List.of(
                new QuestionRequest(QuestionKind.TRUE_FALSE, "Trái đất tròn?", 10, false,
                        List.of(new OptionRequest("Đúng", true), new OptionRequest("Sai", false)), null))));

        InOrder order = inOrder(questionRepository);
        order.verify(questionRepository).deleteByQuizId(QUIZ_ID);
        order.verify(questionRepository).saveAll(anyList());
        assertThat(response.title()).isEqualTo("Mới");
        assertThat(response.createdAt()).isEqualTo(CREATED);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        assertThat(response.questions()).hasSize(1);
    }

    @Test
    void updateOfAnotherTeachersQuizIsNotFound() {
        when(quizRepository.findByIdAndOwnerIdForUpdate(QUIZ_ID, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(OWNER, QUIZ_ID, new QuizRequest("Quiz", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(questionRepository, never()).deleteByQuizId(any());
    }

    @Test
    void deleteOfAnotherTeachersQuizIsNotFound() {
        when(quizRepository.deleteOwned(QUIZ_ID, OWNER)).thenReturn(0);

        assertThatThrownBy(() -> service.delete(OWNER, QUIZ_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void duplicateCopiesEveryQuestionUnderANewTitle() {
        Quiz source = withId(new Quiz(OWNER, "Địa lý", CREATED));
        Question question = new Question(QUIZ_ID, 0, QuestionKind.MULTI, "Chọn số chẵn", 60, false);
        question.addOption("2", true);
        question.addOption("3", false);
        question.addOption("4", true);
        when(quizRepository.findByIdAndOwnerId(QUIZ_ID, OWNER)).thenReturn(Optional.of(source));
        when(questionRepository.findWithOptions(QUIZ_ID)).thenReturn(List.of(question));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(questionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        QuizResponse copy = service.duplicate(OWNER, QUIZ_ID);

        assertThat(copy.title()).isEqualTo("Địa lý (bản sao)");
        assertThat(copy.questions()).singleElement().satisfies(q -> {
            assertThat(q.kind()).isEqualTo(QuestionKind.MULTI);
            assertThat(q.timeLimitSeconds()).isEqualTo(60);
            assertThat(q.options()).extracting("label", "correct")
                    .containsExactly(
                            tuple("2", true),
                            tuple("3", false),
                            tuple("4", true));
        });
    }

    @Test
    void copyTitleStaysWithin100Characters() {
        String copy = QuizService.copyTitle("x".repeat(100));

        assertThat(copy).hasSize(100).endsWith(QuizService.COPY_SUFFIX);
    }

    private static Quiz withId(Quiz quiz) {
        ReflectionTestUtils.setField(quiz, "id", QUIZ_ID);
        return quiz;
    }
}
