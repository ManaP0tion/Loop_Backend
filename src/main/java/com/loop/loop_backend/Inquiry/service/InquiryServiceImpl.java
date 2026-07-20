package com.loop.loop_backend.Inquiry.service;

import com.loop.loop_backend.Inquiry.domain.Inquiry;
import com.loop.loop_backend.Inquiry.dto.InquiryRequestDto;
import com.loop.loop_backend.Inquiry.repository.InquiryRepository;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryServiceImpl implements InquiryService {

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void submitInquiry(Long userId, InquiryRequestDto requestDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        inquiryRepository.save(Inquiry.builder()
                .user(user)
                .type(requestDto.getType())
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .build());
    }
}