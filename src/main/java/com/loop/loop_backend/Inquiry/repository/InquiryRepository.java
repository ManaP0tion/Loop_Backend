package com.loop.loop_backend.Inquiry.repository;

import com.loop.loop_backend.Inquiry.domain.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    long countByCreatedAtAfter(java.time.LocalDateTime cutoff); // 대시보드: 주간 신규 문의
}