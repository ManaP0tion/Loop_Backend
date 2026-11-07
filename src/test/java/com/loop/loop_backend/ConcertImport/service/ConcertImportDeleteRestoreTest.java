package com.loop.loop_backend.ConcertImport.service;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.ConcertImport.domain.ConcertImport;
import com.loop.loop_backend.ConcertImport.domain.ImportStatus;
import com.loop.loop_backend.ConcertImport.repository.ConcertImportRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 공연 삭제 · 복원 요구사항: 공연을 지우면 대기열 행이 삭제됨 상태로 남고, 복원하면 검토 대기로 돌아간다.
@ExtendWith(MockitoExtension.class)
class ConcertImportDeleteRestoreTest {

    @Mock ConcertImportRepository importRepository;
    @Mock ConcertRepository concertRepository;
    @InjectMocks ConcertImportService service; // 여기에 쓰지 않는 의존성은 null로 둔다

    private final ConcertImport pendingImport = ConcertImport.builder()
            .kopisId("PF287093")
            .title("Vaundy ASIA ARENA TOUR HORO IN SEOUL")
            .suggestedCategory(ConcertCategory.STAGE)
            .status(ImportStatus.PENDING)
            .build();

    private ConcertImport deletedImport() {
        pendingImport.markApproved(31L);
        pendingImport.markDeleted();
        return pendingImport;
    }

    @Test
    @DisplayName("공연을 삭제하면 그 공연을 만든 대기열 행이 삭제됨 상태가 되고 공연 참조가 비워진다")
    void markDeletedByConcert_WhenImportExists_MarksDeleted() {
        pendingImport.markApproved(31L);
        when(importRepository.findByPublishedConcertId(31L)).thenReturn(List.of(pendingImport));

        service.markDeletedByConcert(31L);

        assertThat(pendingImport.getStatus()).isEqualTo(ImportStatus.DELETED);
        assertThat(pendingImport.getPublishedConcertId()).isNull();
        assertThat(pendingImport.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("대기열 행 없이 직접 만든 공연을 삭제해도 예외가 나지 않는다")
    void markDeletedByConcert_WhenNoImport_DoesNothing() {
        when(importRepository.findByPublishedConcertId(31L)).thenReturn(List.of());

        service.markDeletedByConcert(31L);

        verify(importRepository).findByPublishedConcertId(31L);
    }

    @Test
    @DisplayName("삭제된 공연을 복원하면 검토 대기로 돌아가고 다시 승인할 수 있다")
    void restore_WhenDeleted_ReturnsToPending() {
        when(importRepository.findById(14L)).thenReturn(Optional.of(deletedImport()));
        when(concertRepository.existsByKopisId("PF287093")).thenReturn(false);

        service.restore(14L);

        assertThat(pendingImport.getStatus()).isEqualTo(ImportStatus.PENDING);
        assertThat(pendingImport.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("삭제 상태가 아닌 행은 복원할 수 없다")
    void restore_WhenNotDeleted_ThrowsImportNotDeleted() {
        pendingImport.markRejected("");
        when(importRepository.findById(14L)).thenReturn(Optional.of(pendingImport));

        assertThatThrownBy(() -> service.restore(14L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IMPORT_NOT_DELETED));
        assertThat(pendingImport.getStatus()).isEqualTo(ImportStatus.REJECTED);
    }

    @Test
    @DisplayName("같은 KOPIS 공연이 이미 등록돼 있으면 복원할 수 없다")
    void restore_WhenConcertAlreadyRegistered_ThrowsConflict() {
        when(importRepository.findById(14L)).thenReturn(Optional.of(deletedImport()));
        when(concertRepository.existsByKopisId("PF287093")).thenReturn(true);

        assertThatThrownBy(() -> service.restore(14L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONCERT_ALREADY_REGISTERED));
        assertThat(pendingImport.getStatus()).isEqualTo(ImportStatus.DELETED);
    }

    @Test
    @DisplayName("삭제된 행은 복원 전에 승인할 수 없다")
    void approve_WhenDeleted_ThrowsAlreadyProcessed() {
        when(importRepository.findById(14L)).thenReturn(Optional.of(deletedImport()));

        assertThatThrownBy(() -> service.approve(14L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IMPORT_ALREADY_PROCESSED));
    }
}
