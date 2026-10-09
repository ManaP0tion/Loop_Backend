package com.loop.loop_backend.User.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "이메일 알림 on/off 설정 요청 DTO (null 필드는 변경 없음)")
public class NotificationSettingsRequestDto {

    @Schema(description = "공연 하루전 리마인더 이메일 수신 여부", example = "true")
    private Boolean concertReminderEmail;

    @Schema(description = "미확인 채팅 일일 다이제스트 이메일 수신 여부", example = "false")
    private Boolean chatNotificationEmail;

    @Schema(description = "예상 셋리스트 결과 이메일 수신 여부", example = "true")
    private Boolean setlistResultEmail;
}
