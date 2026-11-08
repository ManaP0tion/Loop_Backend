package com.loop.loop_backend.Mail.domain;

public enum MailType {
    REPORT,            // 신고 접수 알림 (→ 관리자)
    INQUIRY,           // 문의 접수 알림 (→ 관리자)
    UNREAD_CHAT,       // 미확인 채팅 알림 (→ 유저)
    VERIFICATION,      // 이메일 인증 코드 (→ 유저)
    // 동행 프로필 기반 공연 하루 전 리마인드 (→ 유저). 더 이상 발송하지 않는다(예매 알림으로 대체 예정).
    // 지난 발송 기록(mail_logs.type)을 어드민 메일 로그에서 읽어야 하므로 값은 남긴다.
    CONCERT_REMINDER,
    NEW_CHAT,          // 신규 채팅 알림 (→ 유저)
    SETLIST_RESULT     // 예상 셋리스트 결과 (→ 유저)
}
