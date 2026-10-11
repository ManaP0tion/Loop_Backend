package com.loop.loop_backend.common.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 관리자가 직접 입력하는 외부 링크(공식 사이트·SNS) 정리.
 * 앱·웹이 이 값을 그대로 열기 때문에 http/https 주소만 받는다.
 * - javascript: 같은 값이 저장되면 링크를 누른 사용자 쪽에서 실행된다.
 * - https:// 없이 넣은 주소(instagram.com/xxx)는 웹에서 상대 경로로 해석돼 깨진 링크가 된다.
 */
public final class WebUrls {

    private WebUrls() {
    }

    /** 빈 값이면 null(비움), 앞뒤 공백 제거. http/https 주소가 아니면 IllegalArgumentException(400). */
    public static String normalize(String url) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.trim();
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null) {
                return trimmed;
            }
        } catch (URISyntaxException ignored) {
            // 아래에서 같은 예외로 응답한다
        }
        throw new IllegalArgumentException("http 또는 https 주소만 입력할 수 있다: " + trimmed);
    }
}