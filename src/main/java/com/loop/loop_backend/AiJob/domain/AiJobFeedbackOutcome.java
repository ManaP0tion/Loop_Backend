package com.loop.loop_backend.AiJob.domain;

/**
 * 관리자가 AI가 채운 항목 하나를 어떻게 처리했는지. 어드민 화면의 행 표시와 1:1로 맞춘다.
 * 정확도 같은 숫자는 여기 저장하지 않고, AI 결과(result)의 review와 합쳐 나중에 계산한다.
 */
public enum AiJobFeedbackOutcome {
    MATCHED,       // AI 값이 기존 값과 같음(따로 할 일 없음)
    APPLIED,       // AI 값을 그대로 적용해 저장
    EDITED,        // 적용한 뒤 관리자가 고쳐서 저장(finalValue에 최종값)
    NOT_APPLIED,   // 채울 수 있었지만 적용하지 않음
    KEPT_EXISTING  // 기존(KOPIS) 값과 달라 기존 값을 유지
}
