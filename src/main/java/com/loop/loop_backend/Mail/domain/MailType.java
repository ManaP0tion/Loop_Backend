package com.loop.loop_backend.Mail.domain;

public enum MailType {
    REPORT,            // 신고 접수 알림 (→ 관리자)
    INQUIRY,           // 문의 접수 알림 (→ 관리자)
    UNREAD_CHAT,       // 미확인 채팅 알림 (→ 유저)
    VERIFICATION,      // 이메일 인증 코드 (→ 유저)
    CONCERT_REMINDER,  // 공연 하루전 리마인드 (→ 유저)
    NEW_CHAT,          // 신규 채팅 알림 (→ 유저)
    SETLIST_RESULT     // 예상 셋리스트 결과 (→ 유저)
}
