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

    @Schema(description = "공연 하루전 리마인더 이메일 수신 여부. 현재 미사용 - 저장만 되고 메일은 발송하지 않는다(예매 알림으로 대체 예정)",
            example = "true")
    private Boolean concertReminderEmail;

    @Schema(description = "미확인 채팅 일일 다이제스트 이메일 수신 여부", example = "false")
    private Boolean chatNotificationEmail;

    @Schema(description = "예상 셋리스트 결과 이메일 수신 여부", example = "true")
    private Boolean setlistResultEmail;

    @Schema(description = "새 공연 알림 이메일 수신 여부(기본 false). 공연이 공개되면 알림 메일을 받는다", example = "true")
    private Boolean newConcertEmail;
}
