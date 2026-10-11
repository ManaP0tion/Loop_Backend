package com.loop.loop_backend.TicketAlarm.controller;

import com.loop.loop_backend.TicketAlarm.domain.TicketAlarmType;
import com.loop.loop_backend.TicketAlarm.dto.TicketAlarmResponse;
import com.loop.loop_backend.TicketAlarm.service.TicketAlarmService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// /api/users/me/** 는 인증 필수(스크랩·셋리스트 투표와 같은 이유). 토글 하나에 요청 하나 - 켜기 PUT, 끄기 DELETE.
@RestController
@RequestMapping("/api/users/me/ticket-alarms/{concertId}/{type}")
@RequiredArgsConstructor
@Tag(name = "TicketAlarm", description = "공연 예매 알림 토글 (로그인 필수). 스크랩과 별개, 기본 꺼짐")
public class TicketAlarmController {

    private static final String NO_SCHEDULE_EXAMPLE =
            "{\"success\":false,\"message\":\"예매 일정이 없어 알림을 켤 수 없습니다.\",\"code\":400}";

    private final TicketAlarmService ticketAlarmService;

    @PutMapping
    @Operation(summary = "예매 알림 켜기",
            description = "type: PRESALE(선예매) / GENERAL_SALE(일반예매). 이미 켜져 있어도 200.\n\n" +
                    "그 유형에 예매 일시가 정해진 일정이 하나도 없으면 400(TICKET_SCHEDULE_NOT_FOUND) - " +
                    "공연 상세의 presales / generalSales가 빈 목록이면 토글을 켤 수 없다.\n\n" +
                    "응답은 이 공연의 알림 상태 전체(토글 두 개).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "켜짐"),
            @ApiResponse(responseCode = "400", description = "예매 일정 없음 - TICKET_SCHEDULE_NOT_FOUND",
                    content = @Content(examples = @ExampleObject(value = NO_SCHEDULE_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "오픈 예정(비공개) 공연 - CONCERT_NOT_OPEN"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<TicketAlarmResponse>> turnOn(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId,
            @Parameter(description = "알림 종류") @PathVariable TicketAlarmType type) {
        return ResponseEntity.ok(CommonResponse.success(ticketAlarmService.turnOn(userId, concertId, type)));
    }

    @DeleteMapping
    @Operation(summary = "예매 알림 끄기",
            description = "type: PRESALE(선예매) / GENERAL_SALE(일반예매). 이미 꺼져 있어도 200, 예매 일정이 없어도 끌 수 있다.\n\n" +
                    "응답은 이 공연의 알림 상태 전체(토글 두 개).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "꺼짐"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<TicketAlarmResponse>> turnOff(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId,
            @Parameter(description = "알림 종류") @PathVariable TicketAlarmType type) {
        return ResponseEntity.ok(CommonResponse.success(ticketAlarmService.turnOff(userId, concertId, type)));
    }
}