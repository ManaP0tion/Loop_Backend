package com.loop.loop_backend.Concert.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImportStatus {
    PENDING("검토 대기"),
    APPROVED("승인 (게시됨)"),
    REJECTED("거절");

    private final String displayName;
}
