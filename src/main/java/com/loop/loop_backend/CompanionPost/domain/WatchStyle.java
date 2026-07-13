package com.loop.loop_backend.CompanionPost.domain;

public enum WatchStyle {
    ENTHUSIASTIC("뗴창 열심히"),
    NORMAL("보통"),
    QUIET("조용히 관람");

    private final String label;

    WatchStyle(String label){ this.label = label; }

    public String getLabel(){ return label; }
}
