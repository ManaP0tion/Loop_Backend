package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleRequest;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleResponse;
import com.loop.loop_backend.Concert.service.AdminTicketSaleService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 선예매·일반예매(AD-01 예매 유형). 화면에서 두 블록의 추가 버튼이 따로라 경로도 나눴다 - 요청·응답 형태는 같다.
 * 수정은 PUT(블록 전체 교체)이라 예매 일시를 null로 보내면 지워진다. 목록은 공연 상세(GET /api/admin/concerts/{id})에 포함된다.
 */
@RestController
@RequestMapping("/api/admin/concerts/{concertId}")
@RequiredArgsConstructor
@Tag(name = "Admin Concert", description = "공연 관리 (ADMIN 전용)")
public class AdminTicketSaleController {

    private static final String INVALID_INPUT_EXAMPLE =
            "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}";
    private static final String NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"예매 정보를 찾을 수 없습니다.\",\"code\":404}";
    private static final String SALE_RULES = "\n\n한 건 = 예매 일시 1개 + 예매처(이름 + 링크) 여러 개. " +
            "예매 일시는 선택(한국 시간, 일시가 없는 건은 사용자 화면에 미노출), 예매처 이름은 필수, 링크는 선택, 예매처 0개도 허용.";

    private final AdminTicketSaleService ticketSaleService;
    private final AdminAccessLogService accessLog;

    // ---------- 선예매 ----------

    @PostMapping("/presales")
    @Operation(summary = "선예매 추가", description = "공연에 선예매 1건을 추가한다." + SALE_RULES)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추가 성공"),
            @ApiResponse(responseCode = "400", description = "예매처 이름 누락 등",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "공연 없음")
    })
    public ResponseEntity<CommonResponse<TicketSaleResponse>> addPresale(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 id") @PathVariable Long concertId,
            @Valid @RequestBody TicketSaleRequest body) {
        TicketSaleResponse added = ticketSaleService.addPresale(concertId, body);
        accessLog.log(adminId, req, "ADD_PRESALE", "CONCERT", concertId, "선예매 추가 #" + added.id());
        return ResponseEntity.ok(CommonResponse.success(added));
    }

    @PutMapping("/presales/{presaleId}")
    @Operation(summary = "선예매 수정 (블록 전체 교체)",
            description = "보낸 내용으로 선예매 1건을 통째로 바꾼다. **opensAt을 null로 보내면 예매 일시가 지워지고, " +
                    "vendors를 빼거나 []로 보내면 예매처가 비워진다.**" + SALE_RULES)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "예매처 이름 누락 등",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "공연 없음, 또는 이 공연의 선예매가 아님",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<TicketSaleResponse>> updatePresale(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 id") @PathVariable Long concertId,
            @Parameter(description = "선예매 id") @PathVariable Long presaleId,
            @Valid @RequestBody TicketSaleRequest body) {
        TicketSaleResponse updated = ticketSaleService.updatePresale(concertId, presaleId, body);
        accessLog.log(adminId, req, "UPDATE_PRESALE", "CONCERT", concertId, "선예매 수정 #" + presaleId);
        return ResponseEntity.ok(CommonResponse.success(updated));
    }

    @DeleteMapping("/presales/{presaleId}")
    @Operation(summary = "선예매 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "공연 없음, 또는 이 공연의 선예매가 아님",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<Void>> deletePresale(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 id") @PathVariable Long concertId,
            @Parameter(description = "선예매 id") @PathVariable Long presaleId) {
        ticketSaleService.deletePresale(concertId, presaleId);
        accessLog.log(adminId, req, "DELETE_PRESALE", "CONCERT", concertId, "선예매 삭제 #" + presaleId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    // ---------- 일반예매 ----------

    @PostMapping("/general-sales")
    @Operation(summary = "일반예매 추가", description = "공연에 일반예매 1건을 추가한다." + SALE_RULES)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추가 성공"),
            @ApiResponse(responseCode = "400", description = "예매처 이름 누락 등",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "공연 없음")
    })
    public ResponseEntity<CommonResponse<TicketSaleResponse>> addGeneralSale(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 id") @PathVariable Long concertId,
            @Valid @RequestBody TicketSaleRequest body) {
        TicketSaleResponse added = ticketSaleService.addGeneralSale(concertId, body);
        accessLog.log(adminId, req, "ADD_GENERAL_SALE", "CONCERT", concertId, "일반예매 추가 #" + added.id());
        return ResponseEntity.ok(CommonResponse.success(added));
    }

    @PutMapping("/general-sales/{saleId}")
    @Operation(summary = "일반예매 수정 (블록 전체 교체)",
            description = "보낸 내용으로 일반예매 1건을 통째로 바꾼다. **opensAt을 null로 보내면 예매 일시가 지워지고, " +
                    "vendors를 빼거나 []로 보내면 예매처가 비워진다.**" + SALE_RULES)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "예매처 이름 누락 등",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "공연 없음, 또는 이 공연의 일반예매가 아님",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<TicketSaleResponse>> updateGeneralSale(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 id") @PathVariable Long concertId,
            @Parameter(description = "일반예매 id") @PathVariable Long saleId,
            @Valid @RequestBody TicketSaleRequest body) {
        TicketSaleResponse updated = ticketSaleService.updateGeneralSale(concertId, saleId, body);
        accessLog.log(adminId, req, "UPDATE_GENERAL_SALE", "CONCERT", concertId, "일반예매 수정 #" + saleId);
        return ResponseEntity.ok(CommonResponse.success(updated));
    }

    @DeleteMapping("/general-sales/{saleId}")
    @Operation(summary = "일반예매 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "공연 없음, 또는 이 공연의 일반예매가 아님",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<Void>> deleteGeneralSale(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 id") @PathVariable Long concertId,
            @Parameter(description = "일반예매 id") @PathVariable Long saleId) {
        ticketSaleService.deleteGeneralSale(concertId, saleId);
        accessLog.log(adminId, req, "DELETE_GENERAL_SALE", "CONCERT", concertId, "일반예매 삭제 #" + saleId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}