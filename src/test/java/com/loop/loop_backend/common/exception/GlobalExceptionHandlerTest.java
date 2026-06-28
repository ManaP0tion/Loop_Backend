package com.loop.loop_backend.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;



@WebMvcTest(
        controllers = GlobalExceptionTestController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    // ── 테스트 케이스 ──────────────────────────────────────────────────────────
    @Test
    void BusinessException은_ErrorCode의_status와_message로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())                               // 404
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("채팅방을 찾을 수 없습니다."));
    }

    @Test
    void IllegalArgumentException은_400으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/illegal"))
                .andExpect(status().isBadRequest())                             // 400
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("잘못된 인자입니다."));
    }

    @Test
    void 예상치_못한_예외는_500으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())                    // 500
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    @Test
    void Validation_실패시_필드명을_키로_에러메시지를_응답한다() throws Exception {
        // validation 핸들러는 Map<fieldName, message> 구조 → $.name 으로 접근
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())                             // 400
                .andExpect(jsonPath("$.name").value("이름은 필수입니다."));
    }
}

class GlobalExceptionTestDto{
    @NotBlank(message = "이름은 필수입니다.")
    private String name;
    public String getName(){
        return name;
    }
}

// ── 테스트용 컨트롤러 ──────────────────────────────────────────────────────
@RestController
class GlobalExceptionTestController {

    @GetMapping("/test/business")
    public void throwBusiness() {
        throw new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @GetMapping("/test/illegal")
    public void throwIllegal() {
        throw new IllegalArgumentException("잘못된 인자입니다.");
    }

    @GetMapping("/test/unexpected")
    public void throwUnexpected() {
        throw new RuntimeException("예상치 못한 오류");
    }

    @PostMapping("/test/valid")
    public ResponseEntity<Void> withValidation(@Valid @RequestBody GlobalExceptionTestDto dto) {
        return ResponseEntity.ok().build();
    }
}

