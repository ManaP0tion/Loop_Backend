package com.loop.loop_backend.AiJob.repository;

import com.loop.loop_backend.AiJob.domain.AiJobFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiJobFeedbackRepository extends JpaRepository<AiJobFeedback, Long> {

    List<AiJobFeedback> findByJobIdOrderByIdAsc(Long jobId);

    /**
     * 그 작업의 피드백을 바로 지운다(다시 저장 = 통째로 교체).
     * 벌크 삭제로 즉시 실행한다 - deleteAll 뒤 saveAll이면 Hibernate가 flush 때 INSERT를 DELETE보다 먼저 보내
     * 같은 키가 (job_id, item_key) 유일 제약에 걸린다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AiJobFeedback f where f.jobId = :jobId")
    int deleteByJobId(@Param("jobId") Long jobId);
}
