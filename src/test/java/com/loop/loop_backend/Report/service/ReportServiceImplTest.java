package com.loop.loop_backend.Report.service;

import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.Block.service.BlockService;
import com.loop.loop_backend.Chat.repository.ChatRoomRepository;
import com.loop.loop_backend.Chat.service.ChatService;
import com.loop.loop_backend.Report.domain.Report;
import com.loop.loop_backend.Report.repository.ReportImageRepository;
import com.loop.loop_backend.Report.repository.ReportRepository;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceImplTest {

    @Mock ReportRepository reportRepository;
    @Mock ReportImageRepository reportImageRepository;
    @Mock BlockRepository blockRepository;
    @Mock UserRepository userRepository;
    @Mock BlockService blockService;
    @Mock ChatService chatService;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock S3StorageService s3StorageService;
    @InjectMocks ReportServiceImpl reportService;

    private static final Long REPORTER_ID = 1L;
    private static final Long TARGET_ID = 2L;

    private User reporter;
    private User target;

    @BeforeEach
    void setUp() {
        reporter = testUser(REPORTER_ID);
        target = testUser(TARGET_ID);

        when(userRepository.findById(REPORTER_ID)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(chatRoomRepository.findDirectRoomsBetweenAnyStatus(REPORTER_ID, TARGET_ID)).thenReturn(List.of());
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private User testUser(long id) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-" + id)
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void 차단_없이_신고만_하면_차단도_채팅방_숨김도_일어나지_않는다() {
        reportService.report(REPORTER_ID, TARGET_ID, "SPAM", "상세", false, null);

        verify(blockService, never()).block(any(), any());
        verify(chatService, never()).hideDirectRoomForUser(any(), any());
    }

    @Test
    void 차단도_함께_체크한_신고는_차단과_채팅방_숨김이_모두_일어난다() {
        when(blockRepository.existsByBlockerAndBlocked(reporter, target)).thenReturn(false);

        reportService.report(REPORTER_ID, TARGET_ID, "SPAM", "상세", true, null);

        verify(blockService).block(REPORTER_ID, TARGET_ID);
        verify(chatService).hideDirectRoomForUser(REPORTER_ID, TARGET_ID);
    }

    @Test
    void 이미_차단된_상대를_차단_체크해서_재신고해도_채팅방_숨김은_다시_호출된다() {
        when(blockRepository.existsByBlockerAndBlocked(reporter, target)).thenReturn(true);

        reportService.report(REPORTER_ID, TARGET_ID, "SPAM", "상세", true, null);

        verify(blockService, never()).block(any(), any());
        verify(chatService).hideDirectRoomForUser(REPORTER_ID, TARGET_ID);
    }

    @Test
    void 차단_중_동시요청으로_이미_차단된_경우에도_신고는_성공하고_채팅방은_숨겨진다() {
        when(blockRepository.existsByBlockerAndBlocked(reporter, target)).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.ALREADY_BLOCKED))
                .when(blockService).block(REPORTER_ID, TARGET_ID);

        reportService.report(REPORTER_ID, TARGET_ID, "SPAM", "상세", true, null);

        verify(chatService).hideDirectRoomForUser(REPORTER_ID, TARGET_ID);
    }

    @Test
    void 차단_체크_후_차단_처리_중_차단관련이_아닌_예외는_그대로_전파된다() {
        when(blockRepository.existsByBlockerAndBlocked(reporter, target)).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.USER_NOT_FOUND))
                .when(blockService).block(REPORTER_ID, TARGET_ID);

        assertThatThrownBy(() -> reportService.report(REPORTER_ID, TARGET_ID, "SPAM", "상세", true, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(chatService, never()).hideDirectRoomForUser(any(), any());
    }

    @Test
    void 본인을_신고하면_SELF_REPORT_NOT_ALLOWED_예외를_던진다() {
        assertThatThrownBy(() -> reportService.report(REPORTER_ID, REPORTER_ID, "SPAM", "상세", false, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELF_REPORT_NOT_ALLOWED);

        verifyNoInteractions(reportRepository, chatService);
    }

    @Test
    void 이미_신고한_대상을_다시_신고하면_ALREADY_REPORTED_예외를_던진다() {
        when(reportRepository.existsByReporterAndTargetUser(reporter, target)).thenReturn(true);

        assertThatThrownBy(() -> reportService.report(REPORTER_ID, TARGET_ID, "SPAM", "상세", false, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_REPORTED);

        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void 신고_시점의_DIRECT_채팅방을_스냅샷으로_저장한다() {
        com.loop.loop_backend.Chat.domain.ChatRoom room = mock(com.loop.loop_backend.Chat.domain.ChatRoom.class);
        when(room.getId()).thenReturn(99L);
        when(chatRoomRepository.findDirectRoomsBetweenAnyStatus(REPORTER_ID, TARGET_ID)).thenReturn(List.of(room));

        reportService.report(REPORTER_ID, TARGET_ID, "SPAM", "상세", false, null);

        verify(reportRepository).saveAndFlush(argThat(report -> report.getChatRoomId().equals(99L)));
    }
}
