package com.loop.loop_backend.Setlist.event;

/** 실제 셋리스트가 처음 저장됐다(NO.69) - 커밋 후 결과 메일을 보낸다. */
public record SetlistResultSavedEvent(Long concertId, String concertTitle) {
}
