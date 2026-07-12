package com.loop.loop_backend.CompanionPost.controller;

import com.loop.loop_backend.CompanionPost.domain.PreferredAgeGroup;
import com.loop.loop_backend.CompanionPost.domain.PreferredGender;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.dto.CompanionRequestDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companions")
@Tag(name = "Companion", description = "동행 프로필 API")
public class CompanionController {

    //동행 프로필 등록
    @Operation(summary = "동행 프로필 생성", description = """
            동행 프로필을 생성합니다.

            | 필드 | 값 (enum) | 설명 |
            |---|---|---|
            | watchDay | DAY1, DAY2, DAY3, DAY4 | 관람 일차 |
            | preferredGender | MALE, FEMALE, ANY | 선호하는 동행자 성별 |
            | preferredAgeGroups | NINETEEN_TO_TWENTY_FOUR, TWENTY_FIVE_TO_TWENTY_NINE, THIRTY_TO_THIRTY_FOUR, THIRTY_FIVE_TO_THIRTY_NINE, FORTY_PLUS, ANY | 선호하는 동행자 나이대 (복수 선택) |
            | activities | CONCERT(공연 관람), MEAL(식사), PHOTO(사진), GOODS(굿즈), TALK(대화) | 함께 하고 싶은 활동 (복수 선택) |
            | watchStyle | ENTHUSIASTIC(뗴창 열심히), NORMAL(보통), QUIET(조용히 관람) | 관람 스타일 |
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "동행 프로필 생성"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "409", description = "이미 동행프로필 존재",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"이미 해당 콘서트에 동행 프로필이 존재합니다.\",\"code\":409}")))
    })
    @PostMapping
    public ResponseEntity<CommonResponse<Void>> createCompanion(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CompanionRequestDto requestDto
    ) {
        return ResponseEntity.ok(CommonResponse.success("동행 프로필 생성", null));
    }


    //동행 프로필 전체 조회 (콘서트, day별, 필터는 성별, 나이)
    @Operation(summary = "동행 프로필 전체 조회",
            description = "콘서트/관람일 기준으로 동행 프로필 목록을 조회하고, 성별/나이대로 필터링합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<CommonResponse<List<CompanionResponseDto>>> getCompanions(
            @Parameter(description = "콘서트 PK")
            @RequestParam Long concertId,
            @Parameter(description = "관람 일차", schema = @Schema(allowableValues = {"DAY1", "DAY2", "DAY3", "DAY4"}))
            @RequestParam WatchDay watchDay,
            @Parameter(description = "성별 필터", schema = @Schema(allowableValues = {"MALE", "FEMALE", "ANY"}))
            @RequestParam(required = false) PreferredGender preferredGender,
            @Parameter(description = "나이대 필터 (다중 선택)",
                    array = @ArraySchema(schema = @Schema(allowableValues = {
                            "NINETEEN_TO_TWENTY_FOUR", "TWENTY_FIVE_TO_TWENTY_NINE", "THIRTY_TO_THIRTY_FOUR",
                            "THIRTY_FIVE_TO_THIRTY_NINE", "FORTY_PLUS", "ANY"})))
            @RequestParam(required = false) List<PreferredAgeGroup> preferredAgeGroups
    ) {
        return ResponseEntity.ok(CommonResponse.success(List.of()));
    }


    //동행 프로필 상세 조회
    @Operation(summary = "동행 프로필 상세 조회", description = "동행 프로필 PK로 상세 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "동행 프로필 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"동행 모집글을 찾을 수 없습니다.\",\"code\":404}")))
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponse<CompanionResponseDto>> getCompanion(
            @Parameter(description = "동행 프로필 PK") @PathVariable Long id
    ) {
        return ResponseEntity.ok(CommonResponse.success(null));
    }


    //내 프로필 공개, 비공개 (일단 id 값으로)
    @Operation(summary = "동행 프로필 공개 여부 변경", description = "내 동행 프로필의 공개/비공개 상태를 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "404", description = "동행 프로필 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"동행 모집글을 찾을 수 없습니다.\",\"code\":404}")))
    })
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<CommonResponse<Void>> updateVisibility(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "동행 프로필 PK") @PathVariable Long id,
            @Parameter(description = "공개 여부") @RequestParam boolean visible
    ) {
        return ResponseEntity.ok(CommonResponse.success("공개 여부가 변경되었습니다.", null));
    }

    //동행 프로필 등록했는지 확인 (채팅전 확인)
    @Operation(summary = "동행 프로필 등록 여부 확인", description = "채팅 신청 전, 해당 콘서트에 내 동행 프로필이 있는지 확인합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/me/exists")
    public ResponseEntity<CommonResponse<Boolean>> existsMyCompanion(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @RequestParam Long concertId,
            @Parameter(description = "관람 일차", schema = @Schema(allowableValues = {"DAY1", "DAY2", "DAY3", "DAY4"}))
            @RequestParam(required = false) WatchDay watchDay
    ) {
        return ResponseEntity.ok(CommonResponse.success(false));
    }
}