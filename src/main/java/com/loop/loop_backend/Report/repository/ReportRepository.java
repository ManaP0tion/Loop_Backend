package com.loop.loop_backend.Report.repository;

import com.loop.loop_backend.Report.domain.AppealStatus;
import com.loop.loop_backend.Report.domain.Report;
import com.loop.loop_backend.Report.domain.ReportStatus;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterAndTargetUser(User reporter, User targetUser);

    boolean existsByReporter_IdAndTargetUser_Id(Long reporterId, Long targetUserId);

    @Query("""
        select r from Report r
        where (:status is null or r.status = :status)
          and (:appeal is null or r.appealStatus = :appeal)
        order by r.createdAt desc
        """)
    Page<Report> searchForAdmin(@Param("status") ReportStatus status,
                                @Param("appeal") AppealStatus appeal,
                                Pageable pageable);
}