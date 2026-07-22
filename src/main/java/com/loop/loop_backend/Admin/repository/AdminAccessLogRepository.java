package com.loop.loop_backend.Admin.repository;

import com.loop.loop_backend.Admin.domain.AdminAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AdminAccessLogRepository extends JpaRepository<AdminAccessLog, Long> {

    @Query("""
        select l from AdminAccessLog l
        where (:adminId is null or l.adminId = :adminId)
          and (:action is null or l.action = :action)
          and (:from is null or l.createdAt >= :from)
          and (:to is null or l.createdAt < :to)
        order by l.createdAt desc
        """)
    Page<AdminAccessLog> search(@Param("adminId") Long adminId,
                                @Param("action") String action,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                Pageable pageable);
}
