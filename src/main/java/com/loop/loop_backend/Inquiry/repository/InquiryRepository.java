package com.loop.loop_backend.Inquiry.repository;

import com.loop.loop_backend.Inquiry.domain.Inquiry;
import com.loop.loop_backend.Inquiry.domain.InquiryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    long countByCreatedAtAfter(java.time.LocalDateTime cutoff); // 대시보드: 주간 신규 문의

    long countByStatus(InquiryStatus status);

    // 대시보드: 완료(DONE) 제외한 진행 중 문의 수 (레거시 null = 진행 중 포함)
    default long countPending() {
        return count() - countByStatus(InquiryStatus.DONE);
    }
}