package com.loop.loop_backend.User.repository;

/** 알림 메일 수신자. 발송에 필요한 값만 조회한다. */
public record MailRecipient(String email, String nickname) {
}
