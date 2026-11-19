package com.smartteach.assignment.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AssignmentStatusConverter implements AttributeConverter<AssignmentStatus, String> {

    @Override
    public String convertToDatabaseColumn(AssignmentStatus item) {
        return item == null ? null : item.getValue();
    }

    @Override
    public AssignmentStatus convertToEntityAttribute(String value) {
        return value == null ? null : AssignmentStatus.fromValue(value);
    }
}
