package com.loop.loop_backend.Inquiry.service;

import com.loop.loop_backend.Inquiry.domain.Inquiry;
import com.loop.loop_backend.Inquiry.domain.InquiryType;
import com.loop.loop_backend.Inquiry.dto.InquiryRequestDto;
import com.loop.loop_backend.Inquiry.event.InquiryCreatedEvent;
import com.loop.loop_backend.Inquiry.repository.InquiryRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InquiryServiceImplTest {

    @Mock InquiryRepository inquiryRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks InquiryServiceImpl inquiryService;

    private static final Long USER_ID = 1L;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-1")
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.updateUserProfile("루퍼", null);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(invocation -> {
            Inquiry saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });
    }

    private InquiryRequestDto requestDto(InquiryType type, String title, String content) {
        InquiryRequestDto dto = new InquiryRequestDto();
        ReflectionTestUtils.setField(dto, "type", type);
        ReflectionTestUtils.setField(dto, "title", title);
        ReflectionTestUtils.setField(dto, "content", content);
        return dto;
    }

    @Test
    void 문의를_등록하면_저장되고_생성_이벤트가_발행된다() {
        inquiryService.submitInquiry(USER_ID, requestDto(InquiryType.OTHER, "제목", "내용입니다"));

        ArgumentCaptor<InquiryCreatedEvent> captor = ArgumentCaptor.forClass(InquiryCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        InquiryCreatedEvent event = captor.getValue();
        assertThat(event.inquiryId()).isEqualTo(100L);
        assertThat(event.userNickname()).isEqualTo("루퍼");
        assertThat(event.type()).isEqualTo(InquiryType.OTHER);
        assertThat(event.title()).isEqualTo("제목");
        assertThat(event.content()).isEqualTo("내용입니다");
    }

    @Test
    void 존재하지_않는_사용자면_USER_NOT_FOUND_예외를_던지고_이벤트도_발행되지_않는다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.submitInquiry(USER_ID, requestDto(InquiryType.OTHER, "제목", "내용")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(inquiryRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}