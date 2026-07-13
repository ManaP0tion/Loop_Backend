package com.loop.loop_backend.CompanionPost.domain;

public enum CompanionActivity {
    CONCERT("공연 관람"),
    MEAL("식사"),
    PHOTO("사진"),
    GOODS("굿즈"),
    TALK("대화");

    private final String label;

    CompanionActivity(String label){ this.label = label;}

    public String getLabel(){ return label; }
}
