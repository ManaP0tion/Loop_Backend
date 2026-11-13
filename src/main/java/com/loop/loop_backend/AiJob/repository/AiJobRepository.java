package com.loop.loop_backend.AiJob.repository;

import com.loop.loop_backend.AiJob.domain.AiJob;
import com.loop.loop_backend.AiJob.domain.AiJobStatus;
import com.loop.loop_backend.AiJob.domain.AiJobType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    /** 배정 후보: 요청한 종류의 PENDING을 오래된 순으로. 실제 배정은 claim()의 조건부 UPDATE가 정한다. */
    @Query("select j.id from AiJob j where j.status = :pending and j.type in :types order by j.createdAt asc, j.id asc")
    List<Long> findClaimCandidateIds(@Param("pending") AiJobStatus pending,
                                     @Param("types") Collection<AiJobType> types,
                                     Pageable pageable);

    /**
     * PENDING일 때만 RUNNING으로 바꾼다. 바뀐 행이 1이면 이 워커의 작업, 0이면 다른 워커가 먼저 가져갔다.
     * 동시에 같은 행을 바꾸려 하면 늦은 쪽은 앞 트랜잭션이 끝날 때까지 기다린 뒤 최신 상태로 조건을 다시 따져 0행이 된다
     * - 한 작업을 두 워커에 주지 않는다. H2(로컬)에서도 같은 의미로 동작해 SKIP LOCKED 대신 이 방식을 쓴다.
     * version도 올려, 같은 행을 엔티티로 고치던 쪽(결과 보고·만료 처리)이 충돌을 알아차리게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AiJob j set j.status = :running, j.workerId = :workerId, j.leaseUntil = :leaseUntil, " +
            "j.attempt = j.attempt + 1, j.startedAt = :now, j.version = j.version + 1 " +
            "where j.id = :id and j.status = :pending")
    int claim(@Param("id") Long id,
              @Param("workerId") String workerId,
              @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("pending") AiJobStatus pending,
              @Param("running") AiJobStatus running);

    /** 같은 대상의 진행 중인 작업(버튼 연타 방지). */
    Optional<AiJob> findFirstByTypeAndConcertIdAndStatusInOrderByIdDesc(
            AiJobType type, Long concertId, Collection<AiJobStatus> statuses);

    /** lease가 지난 RUNNING 작업(만료 처리 대상). */
    @Query("select j.id from AiJob j where j.status = :running and j.leaseUntil < :now")
    List<Long> findLeaseOverIds(@Param("running") AiJobStatus running, @Param("now") LocalDateTime now);
}