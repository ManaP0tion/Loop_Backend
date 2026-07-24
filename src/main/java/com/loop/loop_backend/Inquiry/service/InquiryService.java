package com.loop.loop_backend.Inquiry.service;

import com.loop.loop_backend.Inquiry.dto.InquiryRequestDto;

public interface InquiryService {

    void submitInquiry(Long userId, InquiryRequestDto requestDto);
}