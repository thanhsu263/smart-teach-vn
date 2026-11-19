package com.smartteach.quiz.mapper;

import com.smartteach.quiz.dto.OptionRequest;
import com.smartteach.quiz.dto.OptionResponse;
import com.smartteach.quiz.dto.QuestionRequest;
import com.smartteach.quiz.dto.QuestionResponse;
import com.smartteach.quiz.dto.QuizResponse;
import com.smartteach.quiz.entity.AcceptedAnswer;
import com.smartteach.quiz.entity.Question;
import com.smartteach.quiz.entity.QuestionOption;
import com.smartteach.quiz.entity.Quiz;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper
public interface QuizMapper {

    QuizResponse toResponse(Quiz quiz, List<Question> questions);

    QuestionResponse toResponse(Question question);

    OptionResponse toResponse(QuestionOption option);

    /** Used by "duplicate": a copy is created exactly like a quiz sent by the client. */
    QuestionRequest toRequest(Question question);

    OptionRequest toRequest(QuestionOption option);

    default String toValue(AcceptedAnswer answer) {
        return answer.getValue();
    }
}
