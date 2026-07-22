package com.loop.loop_backend.Admin.service;

import com.loop.loop_backend.Admin.domain.AdminAccessLog;
import com.loop.loop_backend.Admin.repository.AdminAccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAccessLogService {

    private final AdminAccessLogRepository repository;

    @Transactional
    public void log(Long adminId, HttpServletRequest req, String action, String targetType, Long targetId, String description) {
        repository.save(AdminAccessLog.builder()
                .adminId(adminId)
                .ip(clientIp(req))
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .description(description)
                .build());
    }

    private String clientIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
