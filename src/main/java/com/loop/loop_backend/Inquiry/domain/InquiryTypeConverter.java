package com.loop.loop_backend.Inquiry.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class InquiryTypeConverter implements AttributeConverter<InquiryType, String> {

    @Override
    public String convertToDatabaseColumn(InquiryType type) {
        return type == null ? null : type.name();
    }

    @Override
    public InquiryType convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        try {
            return InquiryType.valueOf(dbValue);
        } catch (IllegalArgumentException e) {
            return InquiryType.OTHER; // 레거시/알 수 없는 값은 OTHER로
        }
    }
}
