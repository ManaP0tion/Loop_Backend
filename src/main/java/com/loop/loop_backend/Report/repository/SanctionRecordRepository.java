package com.loop.loop_backend.Report.repository;

import com.loop.loop_backend.Report.domain.SanctionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SanctionRecordRepository extends JpaRepository<SanctionRecord, Long> {

    List<SanctionRecord> findByTargetUser_IdOrderByProcessedAtDesc(Long targetUserId);
}