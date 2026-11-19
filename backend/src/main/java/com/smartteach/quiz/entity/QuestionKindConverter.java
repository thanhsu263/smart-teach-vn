package com.smartteach.quiz.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class QuestionKindConverter implements AttributeConverter<QuestionKind, String> {

    @Override
    public String convertToDatabaseColumn(QuestionKind kind) {
        return kind == null ? null : kind.getValue();
    }

    @Override
    public QuestionKind convertToEntityAttribute(String value) {
        return value == null ? null : QuestionKind.fromValue(value);
    }
}
