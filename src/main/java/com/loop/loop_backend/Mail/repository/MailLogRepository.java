package com.loop.loop_backend.Mail.repository;

import com.loop.loop_backend.Mail.domain.MailLog;
import com.loop.loop_backend.Mail.domain.MailType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailLogRepository extends JpaRepository<MailLog, Long> {

    @Query("""
        select m from MailLog m
        where (:type is null or m.type = :type)
          and (:success is null or m.success = :success)
        order by m.sentAt desc
        """)
    Page<MailLog> searchForAdmin(@Param("type") MailType type,
                                 @Param("success") Boolean success,
                                 Pageable pageable);
}
