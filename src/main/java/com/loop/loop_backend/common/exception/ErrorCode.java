package com.loop.loop_backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    //임의로 예외 생성해놨습니다! 수정, 생성 해서 사용하시면 됩니다!

    // 공통
    INVALID_INPUT(400, "입력값이 올바르지 않습니다."),
    INVALID_REQUEST_FORMAT(400, "요청 형식이 올바르지 않습니다."),
    DATA_INTEGRITY_VIOLATION(400, "요청 데이터가 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 오류가 발생했습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    WITHDRAWN_USER(410, "탈퇴한 사용자입니다."),
    USER_SUSPENDED(403, "이용정지된 계정입니다."),

    // User 도메인
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다."),
    DUPLICATE_USER_ID(409, "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(409, "이미 사용 중인 이메일입니다."),
    DUPLICATE_SOCIAL_ACCOUNT(409, "이미 가입된 소셜 계정입니다."),
    DUPLICATE_NICKNAME(409, "이미 사용 중인 닉네임입니다."),
    INVALID_PASSWORD(401, "현재 비밀번호가 올바르지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(400, "현재 비밀번호와 동일합니다."),
    PASSWORD_CONFIRM_MISMATCH(400, "새 비밀번호가 일치하지 않습니다."),
    SOCIAL_LOGIN_NO_PASSWORD(400, "소셜 로그인 사용자는 비밀번호를 변경할 수 없습니다."),
    ONBOARDING_ALREADY_COMPLETED(409, "이미 온보딩을 완료했습니다."),
    AGREEMENT_REQUIRED(400, "필수 약관에 모두 동의해야 합니다."),
    EMAIL_VERIFICATION_CODE_MISMATCH(400, "인증 코드가 올바르지 않거나 만료되었습니다."),
    EMAIL_SEND_FAILED(500, "이메일 발송에 실패했습니다."),
    EMAIL_VERIFICATION_SEND_LIMIT_EXCEEDED(429, "이메일 인증 코드 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

    // Hashtag
    DUPLICATE_HASHTAG(409,"이미 사용중인 해시태그입니다."),
    LIMIT_HASHTAG(409, "해시태그는 최대 3개까지 등록할 수 있습니다."),
    HASHTAG_NOT_FOUND(404, "해시태그를 찾을 수 없습니다."),

    //auth
    LOGIN_LOCKED(429, "최대 인증 가능 횟수에 도달했어요. 1시간 뒤에 다시 시도해 주세"),
    INVALID_CREDENTIALS(401, "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(401, "유효하지 않은 Refresh Token입니다."),
    INVALID_KAKAO_CODE(400, "유효하지 않거나 만료된 카카오 인가 코드입니다."),
    INVALID_REDIRECT_URI(400, "허용되지 않는 리다이렉트 주소입니다."),
    ADMIN_2FA_EMAIL_MISSING(403, "관리자 이메일이 설정되어 있지 않아 2단계 인증을 진행할 수 없습니다."),

    // Artist 도메인
    ARTIST_NOT_FOUND(404, "아티스트를 찾을 수 없습니다."),
    DUPLICATE_FAVORITE_ARTIST(409, "이미 등록된 관심 아티스트입니다."),
    LIMIT_FAVORITE_ARTIST(409, "관심 아티스트는 최대 3개까지 등록할 수 있습니다."),
    FAVORITE_ARTIST_NOT_FOUND(404, "관심 아티스트를 찾을 수 없습니다."),

    // Concert 도메인
    CONCERT_NOT_FOUND(404, "콘서트를 찾을 수 없습니다."),

    // CompanionPost 도메인
    COMPANION_POST_NOT_FOUND(404, "동행 모집글을 찾을 수 없습니다."),
    COMPANION_POST_ALREADY_CLOSED(409, "이미 마감된 모집글입니다."),
    COMPANION_POST_ALREADY_EXISTS(409, "이미 해당 콘서트에 동행 프로필이 존재합니다."),
    SELF_CHAT_NOT_ALLOWED(400, "본인 게시글에는 채팅 신청을 할 수 없습니다."),
    SELF_HEART_NOT_ALLOWED(400, "본인 게시글에는 하트를 누를 수 없습니다."),

    // Chat 도메인
    CHAT_ROOM_NOT_FOUND(404, "채팅방을 찾을 수 없습니다."),
    CHAT_ROOM_ALREADY_EXISTS(409, "이미 존재하는 채팅방입니다."),
    NOT_CHAT_PARTICIPANT(403, "채팅방 참여자가 아닙니다."),
    ALREADY_LEFT_CHAT(400, "이미 나간 채팅방입니다."),
    CHAT_ROOM_ACCESS_DENIED(403, "해당 채팅방에 접근할 권한이 없습니다."),
    MESSAGE_CONTENT_INVALID(400, "메시지 내용이 올바르지 않습니다."),
    OTHER_USER_WITHDRAWN(410, "상대방이 탈퇴하여 메시지를 전송할 수 없습니다."),
    OTHER_USER_LEFT(410, "상대가 채팅방을 나가 메시지를 전송할 수 없습니다."),


    // Report/Block 도메인
    BLOCKED_USER(403, "차단된 사용자입니다."),
    ALREADY_REPORTED(409, "이미 신고한 사용자입니다."),
    ALREADY_BLOCKED(409, "이미 차단한 사용자입니다."),
    BLOCK_NOT_FOUND(404, "차단 내역을 찾을 수 없습니다."),
    SELF_REPORT_NOT_ALLOWED(400, "자기 자신을 신고/차단할 수 없습니다."),

    // Admin 도메인
    REPORT_NOT_FOUND(404, "신고를 찾을 수 없습니다."),
    INQUIRY_NOT_FOUND(404, "고객센터 문의를 찾을 수 없습니다."),
    ADMIN_ACCESS_LOG_NOT_FOUND(404, "접속 기록을 찾을 수 없습니다."),
    APPEAL_INVALID_STATE(400, "이의제기 상태가 유효하지 않습니다."),

    // Storage(S3) 도메인
    EMPTY_FILE(400, "빈 파일은 업로드할 수 없습니다."),
    FILE_TOO_LARGE(400, "파일 크기는 5MB를 초과할 수 없습니다."),
    INVALID_FILE_EXTENSION(400, "허용되지 않는 파일 형식입니다. (jpg, jpeg, png, webp만 가능)"),
    FILE_UPLOAD_FAILED(500, "파일 업로드에 실패했습니다.");

    private final int status;
    private final String message;
}
