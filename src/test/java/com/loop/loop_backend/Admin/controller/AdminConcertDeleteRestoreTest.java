package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Chat.repository.ChatRoomRepository;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.ConcertImport.service.ConcertImportService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 공연 삭제 · 복원 요구사항: 공연을 지우면 대기열 행이 삭제됨 상태로 남고, 복원하면 선별 대기로 돌아간다.
@ExtendWith(MockitoExtension.class)
class AdminConcertDeleteRestoreTest {

    @Mock ConcertRepository concertRepository;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ConcertImportService concertImportService;
    @Mock AdminAccessLogService accessLog;
    @InjectMocks AdminController controller; // 여기에 쓰지 않는 의존성은 null로 둔다

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("공연을 삭제하면 대기열 행을 삭제됨으로 바꾼 뒤 공연을 지운다")
    void deleteConcert_WhenConcertExists_MarksImportDeletedThenDeletes() throws Exception {
        when(concertRepository.existsById(31L)).thenReturn(true);

        mockMvc.perform(delete("/api/admin/concerts/{id}", 31L))
                .andExpect(status().isOk());

        InOrder order = inOrder(concertImportService, concertRepository);
        order.verify(concertImportService).markDeletedByConcert(31L);
        order.verify(concertRepository).deleteById(31L);
    }

    @Test
    @DisplayName("복원 요청은 검토 큐 id로 복원을 처리한다")
    void restoreImport_WhenDeleted_Returns200() throws Exception {
        mockMvc.perform(post("/api/admin/concert-imports/{id}/restore", 14L))
                .andExpect(status().isOk());

        verify(concertImportService).restore(14L);
    }

    @Test
    @DisplayName("삭제 상태가 아닌 행을 복원하면 409를 반환한다")
    void restoreImport_WhenNotDeleted_Returns409() throws Exception {
        doThrow(new BusinessException(ErrorCode.IMPORT_NOT_DELETED)).when(concertImportService).restore(14L);

        mockMvc.perform(post("/api/admin/concert-imports/{id}/restore", 14L))
                .andExpect(status().isConflict());
    }
}
