package com.loop.loop_backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    //임의로 예외 생성해놨습니다! 수정, 생성 해서 사용하시면 됩니다!

    // 공통
    INVALID_INPUT(400, "잘못된 요청입니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),


    // User 도메인
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다."),
    DUPLICATE_USER_ID(409, "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(409, "이미 사용 중인 이메일입니다."),

    // CompanionPost 도메인
    COMPANION_POST_NOT_FOUND(404, "동행 모집글을 찾을 수 없습니다."),
    COMPANION_POST_ALREADY_CLOSED(409, "이미 마감된 모집글입니다."),
    SELF_CHAT_NOT_ALLOWED(400, "본인 게시글에는 채팅 신청을 할 수 없습니다."),

    // Chat 도메인
    CHAT_ROOM_NOT_FOUND(404, "채팅방을 찾을 수 없습니다."),
    CHAT_ROOM_ALREADY_EXISTS(409, "이미 존재하는 채팅방입니다."),
    NOT_CHAT_PARTICIPANT(403, "채팅방 참여자가 아닙니다."),
    ALREADY_LEFT_CHAT(400, "이미 나간 채팅방입니다."),

    // Report/Block 도메인
    BLOCKED_USER(403, "차단된 사용자입니다."),
    ALREADY_REPORTED(409, "이미 신고한 사용자입니다."),
    ALREADY_BLOCKED(409, "이미 차단한 사용자입니다."),
    BLOCK_NOT_FOUND(404, "차단 내역을 찾을 수 없습니다."),
    SELF_REPORT_NOT_ALLOWED(400, "자기 자신을 신고/차단할 수 없습니다.");

    private final int status;
    private final String message;
}