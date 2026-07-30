package com.loop.loop_backend.Inquiry.domain;

public enum InquiryStatus {
    PENDING("진행 중"),
    DONE("완료");

    private final String label;

    InquiryStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
