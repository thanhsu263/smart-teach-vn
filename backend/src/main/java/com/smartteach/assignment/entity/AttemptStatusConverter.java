package com.smartteach.assignment.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AttemptStatusConverter implements AttributeConverter<AttemptStatus, String> {

    @Override
    public String convertToDatabaseColumn(AttemptStatus item) {
        return item == null ? null : item.getValue();
    }

    @Override
    public AttemptStatus convertToEntityAttribute(String value) {
        return value == null ? null : AttemptStatus.fromValue(value);
    }
}
