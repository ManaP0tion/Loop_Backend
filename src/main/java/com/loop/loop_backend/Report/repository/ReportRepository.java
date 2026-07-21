package com.loop.loop_backend.Report.repository;

import com.loop.loop_backend.Report.domain.Report;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterAndTargetUser(User reporter, User targetUser);

    boolean existsByReporter_IdAndTargetUser_Id(Long reporterId, Long targetUserId);
}