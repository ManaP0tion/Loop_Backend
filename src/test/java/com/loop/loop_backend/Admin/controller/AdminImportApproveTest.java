package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.ConcertImport.service.ConcertImportService;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 승인 응답 요구사항: 요청 경로의 id(검토 큐)와 승인으로 새로 만들어진 공연의 id는 다른 테이블 값이라
// 응답에서 importId / concertId로 구분돼야 한다. 연결된 공연장 id도 함께 준다(없으면 null).
@ExtendWith(MockitoExtension.class)
class AdminImportApproveTest {

    @Mock ConcertImportService concertImportService;
    @Mock AdminAccessLogService accessLog;
    @InjectMocks AdminController controller; // 승인에 쓰지 않는 의존성은 null로 둔다

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private static Concert concert(Long id, Venue venue) {
        Concert concert = Concert.builder()
                .title("YUURI LIVE [서울]")
                .category(ConcertCategory.J_POP_ARTIST)
                .linkedVenue(venue)
                .build();
        ReflectionTestUtils.setField(concert, "id", id);
        return concert;
    }

    @Test
    void 승인하면_검토_큐_id와_새로_만들어진_공연_id가_구분돼_나온다() throws Exception {
        Venue venue = Venue.builder().name("인스파이어 아레나").address("인천광역시 중구").build();
        ReflectionTestUtils.setField(venue, "id", 5L);
        when(concertImportService.approve(14L)).thenReturn(concert(31L, venue));

        mockMvc.perform(post("/api/admin/concert-imports/{id}/approve", 14L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importId").value(14))
                .andExpect(jsonPath("$.data.concertId").value(31))
                .andExpect(jsonPath("$.data.venueId").value(5))
                .andExpect(jsonPath("$.data.id").doesNotExist()); // 어느 쪽 id인지 모호한 필드는 없다
    }

    @Test
    void 공연장을_정하지_못했으면_venueId는_null이다() throws Exception {
        when(concertImportService.approve(14L)).thenReturn(concert(31L, null));

        mockMvc.perform(post("/api/admin/concert-imports/{id}/approve", 14L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.concertId").value(31))
                .andExpect(jsonPath("$.data.venueId").isEmpty());
    }
}
