package com.loop.loop_backend.Inquiry.domain;

public enum InquiryType {
    COMPANION_SEARCH("동행찾기 관련"),
    ACCOUNT_LOGIN("계정/로그인 문제"),
    APP_USAGE("앱 기능/사용 문의"),
    SUGGESTION("제안/피드백"),
    OTHER("기타");

    private final String label;

    InquiryType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}