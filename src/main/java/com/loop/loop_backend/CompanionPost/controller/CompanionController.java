package com.loop.loop_backend.CompanionPost.controller;

import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.dto.CompanionDetailResponseDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionRequestDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import com.loop.loop_backend.CompanionPost.service.CompanionService;
import com.loop.loop_backend.User.domain.AgeGroup;
import com.loop.loop_backend.User.domain.Gender;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companions")
@RequiredArgsConstructor
@Tag(name = "Companion", description = "동행 프로필 API")
public class CompanionController {

    private final CompanionService companionService;

    //동행 프로필 등록
    @Operation(summary = "동행 프로필 생성", description = """
            동행 프로필을 생성합니다.

            | 필드 | 값 (enum) | 설명 |
            |---|---|---|
            | watchDay | DAY1, DAY2, DAY3, DAY4 | 관람 일차 |
            | activities | CONCERT(공연 관람), MEAL(식사), PHOTO(사진), GOODS(굿즈), TALK(대화) | 함께 하고 싶은 활동 (복수 선택) |
            | watchStyle | ENTHUSIASTIC(뗴창 열심히), NORMAL(보통), QUIET(조용히 관람) | 관람 스타일 |
            | sameGenderOnly | true, false | 같은 성별에게만 연락받기 |
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "동행 프로필 생성"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "404", description = "콘서트 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"콘서트를 찾을 수 없습니다.\",\"code\":404}"))),
            @ApiResponse(responseCode = "409", description = "이미 동행프로필 존재",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"이미 해당 콘서트에 동행 프로필이 존재합니다.\",\"code\":409}")))
    })
    @PostMapping
    public ResponseEntity<CommonResponse<Void>> createCompanion(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CompanionRequestDto requestDto
    ) {
        companionService.createCompanion(userId, requestDto);
        return ResponseEntity.ok(CommonResponse.success("동행 프로필 생성", null));
    }


    //동행 프로필 전체 조회 공연관람 O (콘서트, day별)
    @Operation(summary = "공연 관람 동행 프로필 전체 조회",
            description = """
                    콘서트/관람일 기준으로 공연을 관람하는 동행 프로필 목록을 조회합니다. \
                    작성자의 성별/나이대로 필터링할 수 있습니다. 스크롤용 페이지네이션(page, size)을 지원하며 size 기본값은 20입니다.

                    정렬 기준은 내 상태에 따라 다릅니다.
                    - 내 프로필이 있고 공연 관람을 선택했으면: 관람 스타일이 같은 사람 우선 + 그 안에서 공통 활동 많은 순
                    - 내 프로필이 있고 공연 관람을 선택 안 했으면: 공통 활동 많은 순
                    - 내 프로필이 없으면: 등록일자 최신순
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("watching")
    public ResponseEntity<CommonResponse<List<CompanionResponseDto>>> getWatchingCompanions(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK")
            @RequestParam Long concertId,
            @Parameter(description = "관람 일차", schema = @Schema(allowableValues = {"DAY1", "DAY2", "DAY3", "DAY4"}))
            @RequestParam WatchDay watchDay,
            @Parameter(description = "작성자 성별 필터", schema = @Schema(allowableValues = {"MALE", "FEMALE", "OTHER"}))
            @RequestParam(required = false) Gender gender,
            @Parameter(description = "작성자 나이대 필터 (다중 선택)",
                    array = @ArraySchema(schema = @Schema(allowableValues = {
                            "TEENS", "EARLY_TWENTIES", "LATE_TWENTIES", "EARLY_THIRTIES", "LATE_THIRTIES",
                            "EARLY_FORTIES", "LATE_FORTIES", "FIFTIES_OVER"})))
            @RequestParam(required = false) List<AgeGroup> ageGroups,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                companionService.getWatchingCompanions(userId, concertId, watchDay, gender, ageGroups, pageable)));
    }

    //동행 프로필 전체 조회 공연관람 X (콘서트, day별)
    @Operation(summary = "공연 미관람 동행 프로필 전체 조회",
            description = "콘서트/관람일 기준으로 공연 관람을 안하는 동행 프로필 목록을 조회합니다. " +
                    "스크롤용 페이지네이션(page, size)을 지원하며 size 기본값은 20입니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/not-watching")
    public ResponseEntity<CommonResponse<List<CompanionResponseDto>>> getNotWatchingCompanions(
            @Parameter(description = "콘서트 PK")
            @RequestParam Long concertId,
            @Parameter(description = "관람 일차", schema = @Schema(allowableValues = {"DAY1", "DAY2", "DAY3", "DAY4"}))
            @RequestParam WatchDay watchDay,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(CommonResponse.success(List.of()));
    }


    //동행 프로필 상세 조회
    @Operation(summary = "동행 프로필 상세 조회",
            description = "동행 프로필 PK로 상세 정보를 조회합니다. 비공개 프로필은 작성자 본인만 조회할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "비공개 프로필이며 본인이 아님",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"접근 권한이 없습니다.\",\"code\":403}"))),
            @ApiResponse(responseCode = "404", description = "동행 프로필 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"동행 모집글을 찾을 수 없습니다.\",\"code\":404}")))
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponse<CompanionDetailResponseDto>> getCompanion(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "동행 프로필 PK") @PathVariable Long id
    ) {
        return ResponseEntity.ok(CommonResponse.success(companionService.getCompanion(userId, id)));
    }


    //내 동행 프로필 전체 조회
    @Operation(summary = "내 동행 프로필 전체 조회", description = "내가 등록한 동행 프로필을 모두 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "등록한 동행 프로필 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"동행 모집글을 찾을 수 없습니다.\",\"code\":404}")))
    })
    @GetMapping("/me")
    public ResponseEntity<CommonResponse<List<CompanionResponseDto>>> getMyCompanions(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(companionService.getMyCompanions(userId)));
    }


    //내 프로필 공개, 비공개 (일단 id 값으로)
    @Operation(summary = "동행 프로필 공개 여부 변경", description = "내 동행 프로필의 공개/비공개 상태를 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "403", description = "본인 프로필이 아님",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"접근 권한이 없습니다.\",\"code\":403}"))),
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
        companionService.updateVisibility(userId, id, visible);
        return ResponseEntity.ok(CommonResponse.success("공개 여부가 변경되었습니다.", null));
    }


    //동행 프로필 등록했는지 확인 (채팅전 확인)
    @Operation(summary = "동행 프로필 등록 여부 확인",
            description = "채팅 신청 전, 보고 있는 동행 프로필과 같은 콘서트/관람일에 내 동행 프로필이 있는지 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "대상 동행 프로필 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"동행 모집글을 찾을 수 없습니다.\",\"code\":404}")))
    })
    @GetMapping("/me/exists")
    public ResponseEntity<CommonResponse<Boolean>> existsMyCompanion(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "보고 있는 동행 프로필 PK") @RequestParam Long companionId
    ) {
        return ResponseEntity.ok(CommonResponse.success(companionService.existsMyCompanion(userId, companionId)));
    }



    //내 동행 프로필 수정
    @Operation(summary = "동행 프로필 수정", description = """
            내 동행 프로필을 수정합니다.

            | 필드 | 값 (enum) | 설명 |
            |---|---|---|
            | activities | CONCERT(공연 관람), MEAL(식사), PHOTO(사진), GOODS(굿즈), TALK(대화) | 함께 하고 싶은 활동 (복수 선택) |
            | watchStyle | ENTHUSIASTIC(뗴창 열심히), NORMAL(보통), QUIET(조용히 관람) | 관람 스타일 |
            | sameGenderOnly | true, false | 같은 성별에게만 연락받기 |
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "403", description = "본인 프로필이 아님",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"접근 권한이 없습니다.\",\"code\":403}"))),
            @ApiResponse(responseCode = "404", description = "동행 프로필 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"동행 모집글을 찾을 수 없습니다.\",\"code\":404}")))
    })
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponse<CompanionResponseDto>> updateCompanion(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "동행 프로필 PK") @PathVariable Long id,
            @Valid @RequestBody CompanionRequestDto requestDto
    ) {
        return ResponseEntity.ok(CommonResponse.success("동행 프로필이 수정되었습니다.",
                companionService.updateCompanion(userId, id, requestDto)));
    }

    //동행 프로필 삭제 (비공개 전환과는 별개로, 완전 삭제)
    @Operation(summary = "동행 프로필 삭제", description = "내 동행 프로필을 완전히 삭제합니다. (공개/비공개 전환과는 별개로 데이터 자체를 삭제합니다)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "본인 프로필이 아님",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"접근 권한이 없습니다.\",\"code\":403}"))),
            @ApiResponse(responseCode = "404", description = "동행 프로필 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"동행 모집글을 찾을 수 없습니다.\",\"code\":404}")))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponse<Void>> deleteCompanion(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "동행 프로필 PK") @PathVariable Long id
    ) {
        companionService.deleteCompanion(userId, id);
        return ResponseEntity.ok(CommonResponse.success("동행 프로필이 삭제되었습니다.", null));
    }
}