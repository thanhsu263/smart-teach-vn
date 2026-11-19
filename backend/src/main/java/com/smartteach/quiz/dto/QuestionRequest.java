package com.smartteach.quiz.dto;

import com.smartteach.quiz.entity.QuestionKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Optional fields: timeLimitSeconds (20), ignoreDiacritics (false), options and acceptedAnswers (empty). */
public record QuestionRequest(
        @NotNull QuestionKind kind,
        @NotBlank @Size(max = 500) String prompt,
        Integer timeLimitSeconds,
        Boolean ignoreDiacritics,
        @Size(max = 6, message = "A question can have at most 6 options")
        List<@NotNull @Valid OptionRequest> options,
        @Size(max = 10, message = "A question can have at most 10 accepted answers")
        List<@NotBlank @Size(max = 100) String> acceptedAnswers) {

    public static final int DEFAULT_TIME_LIMIT = 20;

    public QuestionRequest {
        timeLimitSeconds = timeLimitSeconds == null ? DEFAULT_TIME_LIMIT : timeLimitSeconds;
        ignoreDiacritics = Boolean.TRUE.equals(ignoreDiacritics);
        options = options == null ? List.of() : options;
        acceptedAnswers = acceptedAnswers == null ? List.of() : acceptedAnswers;
    }
}
