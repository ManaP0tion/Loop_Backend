package com.loop.loop_backend.FavoriteArtist.controller;

import com.loop.loop_backend.FavoriteArtist.dto.FavoriteArtistCreateRequestDto;
import com.loop.loop_backend.FavoriteArtist.dto.FavoriteArtistResponseDto;
import com.loop.loop_backend.FavoriteArtist.service.FavoriteArtistService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "FavoriteArtist", description = "관심 아티스트 API")
public class FavoriteArtistController {

    private final FavoriteArtistService favoriteArtistService;

    @GetMapping("/{userId}/artists")
    @Operation(summary = "사용자 관심 아티스트 조회", description = "사용자 PK로 관심 아티스트 목록을 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"사용자를 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<List<FavoriteArtistResponseDto>>> getFavoriteArtistsByUserId(
            @Parameter(description = "사용자 PK") @PathVariable Long userId) {
        return ResponseEntity.ok(CommonResponse.success(favoriteArtistService.getFavoriteArtists(userId)));
    }

    @PostMapping("/me/artists")
    @Operation(summary = "관심 아티스트 추가", description = "내 관심 아티스트를 추가합니다 (최대 3개)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가 성공"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"아티스트를 찾을 수 없습니다.\",\"code\":404}"))),
            @ApiResponse(responseCode = "409", description = "이미 등록된 아티스트이거나 개수 초과",
                    content = @Content(examples = {
                            @ExampleObject(name = "중복 등록",
                                    value = "{\"success\":false,\"message\":\"이미 등록된 관심 아티스트입니다.\",\"code\":409}"),
                            @ExampleObject(name = "개수 초과",
                                    value = "{\"success\":false,\"message\":\"관심 아티스트는 최대 3개까지 등록할 수 있습니다.\",\"code\":409}")
                    }))
    })
    public ResponseEntity<CommonResponse<FavoriteArtistResponseDto>> addFavoriteArtist(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FavoriteArtistCreateRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(favoriteArtistService.addFavoriteArtist(userId, requestDto.getArtistId())));
    }

    @DeleteMapping("/me/artists/{favoriteArtistId}")
    @Operation(summary = "관심 아티스트 삭제", description = "내 관심 아티스트를 삭제합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "관심 아티스트를 찾을 수 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"관심 아티스트를 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<Void>> deleteFavoriteArtist(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long favoriteArtistId) {
        favoriteArtistService.deleteFavoriteArtist(userId, favoriteArtistId);
        return ResponseEntity.ok(CommonResponse.success("관심 아티스트 삭제성공", null));
    }
}
