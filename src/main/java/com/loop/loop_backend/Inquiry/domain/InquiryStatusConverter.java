package com.loop.loop_backend.Inquiry.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class InquiryStatusConverter implements AttributeConverter<InquiryStatus, String> {

    @Override
    public String convertToDatabaseColumn(InquiryStatus status) {
        return status == null ? InquiryStatus.PENDING.name() : status.name();
    }

    @Override
    public InquiryStatus convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return InquiryStatus.PENDING; // 기존 문의(컬럼 없던 행)는 진행 중
        try {
            return InquiryStatus.valueOf(dbValue);
        } catch (IllegalArgumentException e) {
            return InquiryStatus.PENDING;
        }
    }
}
