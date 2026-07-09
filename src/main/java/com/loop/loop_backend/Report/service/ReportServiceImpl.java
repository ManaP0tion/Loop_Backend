package com.loop.loop_backend.Report.service;

import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.Block.service.BlockService;
import com.loop.loop_backend.Report.domain.Report;
import com.loop.loop_backend.Report.domain.ReportImage;
import com.loop.loop_backend.Report.event.ReportCreatedEvent;
import com.loop.loop_backend.Report.repository.ReportImageRepository;
import com.loop.loop_backend.Report.repository.ReportRepository;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ReportImageRepository reportImageRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final BlockService blockService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void report(Long userId, Long targetUserId, String reason, boolean blockToo, List<String> imageUrls) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }

        User me = getUser(userId);
        User target = getUser(targetUserId);

        if (reportRepository.existsByReporterAndTargetUser(me, target)) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        Report report;
        try {
            report = reportRepository.saveAndFlush(Report.builder()
                    .reporter(me)
                    .targetUser(target)
                    .reason(reason)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 사전 존재 체크와 저장 사이의 동시 요청 레이스 - DB 유니크 제약이 최종 방어선
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        List<String> urls = imageUrls == null ? List.of() : imageUrls;
        if (!urls.isEmpty()) {
            reportImageRepository.saveAll(urls.stream()
                    .map(url -> ReportImage.builder().report(report).imageUrl(url).build())
                    .toList());
        }

        if (blockToo && !blockRepository.existsByBlockerAndBlocked(me, target)) {
            try {
                blockService.block(userId, targetUserId);
            } catch (BusinessException e) {
                if (e.getErrorCode() != ErrorCode.ALREADY_BLOCKED) {
                    throw e;
                }
                // 신고 처리 중 동시에 들어온 다른 요청이 이미 차단을 완료한 경우 - 신고 자체는 그대로 성공 처리
            }
        }

        eventPublisher.publishEvent(new ReportCreatedEvent(report.getId(), me.getNickname(), target.getNickname(), reason, urls));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
