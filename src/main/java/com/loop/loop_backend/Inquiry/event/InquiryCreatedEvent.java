package com.loop.loop_backend.Inquiry.event;

import com.loop.loop_backend.Inquiry.domain.InquiryType;

public record InquiryCreatedEvent(
        Long inquiryId,
        String userNickname,
        InquiryType type,
        String title,
        String content,
        String email
) {
}