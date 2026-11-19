package com.smartteach.assignment.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RevealPolicyConverter implements AttributeConverter<RevealPolicy, String> {

    @Override
    public String convertToDatabaseColumn(RevealPolicy item) {
        return item == null ? null : item.getValue();
    }

    @Override
    public RevealPolicy convertToEntityAttribute(String value) {
        return value == null ? null : RevealPolicy.fromValue(value);
    }
}
