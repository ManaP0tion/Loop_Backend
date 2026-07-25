package com.loop.loop_backend.Report.service;

import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.Block.service.BlockService;
import com.loop.loop_backend.Chat.domain.ChatRoom;
import com.loop.loop_backend.Chat.repository.ChatRoomRepository;
import com.loop.loop_backend.Chat.service.ChatService;
import com.loop.loop_backend.Report.domain.Report;
import com.loop.loop_backend.Report.domain.ReportImage;
import com.loop.loop_backend.Report.dto.SanctionHistoryListResponseDto;
import com.loop.loop_backend.Report.dto.SanctionHistoryResponseDto;
import com.loop.loop_backend.Report.event.ReportCreatedEvent;
import com.loop.loop_backend.Report.repository.ReportImageRepository;
import com.loop.loop_backend.Report.repository.ReportRepository;
import com.loop.loop_backend.Report.repository.SanctionRecordRepository;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final SanctionRecordRepository sanctionRecordRepository;
    private final ReportImageRepository reportImageRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final BlockService blockService;
    private final ChatService chatService;
    private final ChatRoomRepository chatRoomRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final S3StorageService s3StorageService;

    @Override
    @Transactional
    public void report(Long userId, Long targetUserId, String reason, String detail, boolean blockToo,
                        List<MultipartFile> images) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }

        User me = getUser(userId);
        User target = getUser(targetUserId);

        if (reportRepository.existsByReporterAndTargetUser(me, target)) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        // 관리자 콘솔이 신고 상세에서 관련 채팅을 열람할 수 있도록, 신고 시점의 DIRECT 방을 스냅샷.
        Long chatRoomId = chatRoomRepository.findDirectRoomsBetweenAnyStatus(userId, targetUserId)
                .stream().findFirst().map(ChatRoom::getId).orElse(null);

        Report report;
        try {
            report = reportRepository.saveAndFlush(Report.builder()
                    .reporter(me)
                    .targetUser(target)
                    .reason(reason)
                    .detail(detail)
                    .chatRoomId(chatRoomId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 사전 존재 체크와 저장 사이의 동시 요청 레이스 - DB 유니크 제약이 최종 방어선
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        List<String> imageKeys = uploadReportImages(userId, report, images);

        if (blockToo) {
            if (!blockRepository.existsByBlockerAndBlocked(me, target)) {
                try {
                    blockService.block(userId, targetUserId);
                } catch (BusinessException e) {
                    if (e.getErrorCode() != ErrorCode.ALREADY_BLOCKED) {
                        throw e;
                    }
                    // 신고 처리 중 동시에 들어온 다른 요청이 이미 차단을 완료한 경우 - 신고 자체는 그대로 성공 처리
                }
            }
            // 신고자 관점에서 채팅방 hide (방금 block()에서 이미 처리됐거나, 예전에 이미 차단된 상태라 이번엔 block()을 안 탄 경우 모두 대비한 idempotent 호출)
            // blockToo=false(단순 신고)인 경우는 채팅방을 그대로 유지해야 하므로 여기서 호출하지 않는다
            chatService.hideDirectRoomForUser(userId, targetUserId);
        }

        eventPublisher.publishEvent(new ReportCreatedEvent(report.getId(), me.getNickname(), target.getNickname(), reason, detail, imageKeys));
    }

    @Override
    public SanctionHistoryListResponseDto getMySanctionHistory(Long userId) {
        List<SanctionHistoryResponseDto> history = sanctionRecordRepository
                .findByTargetUser_IdOrderByProcessedAtDesc(userId)
                .stream()
                .map(SanctionHistoryResponseDto::new)
                .toList();

        return new SanctionHistoryListResponseDto(history);
    }

    private List<String> uploadReportImages(Long userId, Report report, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        List<String> keys = images.stream()
                .filter(image -> !image.isEmpty())
                .map(image -> s3StorageService.uploadPrivate("reports", userId, image))
                .toList();

        reportImageRepository.saveAll(keys.stream()
                .map(key -> ReportImage.builder().report(report).imageUrl(key).build())
                .toList());

        return keys;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
