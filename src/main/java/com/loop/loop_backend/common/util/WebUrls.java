package com.loop.loop_backend.common.util;

import java.util.regex.Pattern;

/**
 * 관리자가 직접 입력하는 외부 링크(공식 사이트·SNS) 정리.
 * 앱·웹이 이 값을 그대로 열기 때문에 http/https 주소만 받는다.
 * - javascript: 같은 값이 저장되면 링크를 누른 사용자 쪽에서 실행된다.
 * - https:// 없이 넣은 주소(instagram.com/xxx)는 웹에서 상대 경로로 해석돼 깨진 링크가 된다.
 * 스킴과 호스트 자리만 본다 - 일본어 도메인이나 인코딩 안 된 문자가 섞인 실제 주소까지 막지 않으려고 URI 파싱은 하지 않는다.
 */
public final class WebUrls {

    /** 링크 컬럼 길이(official_site_url, instagram_url, x_url, homepage_url). 넘으면 DB 오류 대신 400. */
    public static final int MAX_LENGTH = 500;

    // http:// 또는 https:// + 비어 있지 않은 호스트, 공백 없음
    private static final Pattern HTTP_URL = Pattern.compile("(?i)^https?://[^\\s/?#]+\\S*$");

    private WebUrls() {
    }

    /** 빈 값이면 null(비움), 앞뒤 공백 제거. http/https 주소가 아니거나 너무 길면 IllegalArgumentException(400). */
    public static String normalize(String url) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.trim();
        if (!HTTP_URL.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("http 또는 https 주소만 입력할 수 있다: " + trimmed);
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("링크는 " + MAX_LENGTH + "자까지 입력할 수 있다");
        }
        return trimmed;
    }
}