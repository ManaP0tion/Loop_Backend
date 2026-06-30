package com.loop.loop_backend.Report.repository;

import com.loop.loop_backend.Report.domain.Report;
import com.loop.loop_backend.Report.domain.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM Report r
            WHERE r.type = :type
            AND (
                (r.reporter.id = :userId AND r.targetUser.id IN :otherIds)
                OR
                (r.reporter.id IN :otherIds AND r.targetUser.id = :userId)
            )
            """)
    boolean existsBlockBetween(
            @Param("userId") Long userId,
            @Param("otherIds") List<Long> otherIds,
            @Param("type") ReportType type
    );
}
