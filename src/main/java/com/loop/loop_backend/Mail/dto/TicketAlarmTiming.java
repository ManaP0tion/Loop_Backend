package com.loop.loop_backend.Mail.dto;

/**
 * 예매 알림 메일의 발송 시점. 두 메일은 레이아웃이 같고 제목·안내 문구만 다르다.
 */
public enum TicketAlarmTiming {

    DAY_BEFORE, // 예매 하루 전 오후 9시
    SOON;       // 예매 시작 30분 전

    public String subject(TicketAlarmMail mail) {
        return switch (this) {
            case DAY_BEFORE -> "[Loop] 내일 " + mail.opensTime() + ", " + mail.concertTitle() + " "
                    + mail.saleTypeName() + "가 시작돼요";
            case SOON -> "[Loop] 30분 뒤 " + mail.concertTitle() + " " + mail.saleTypeName() + "가 시작돼요";
        };
    }

    public String headline(TicketAlarmMail mail) {
        return switch (this) {
            case DAY_BEFORE -> "내일 " + mail.saleTypeName() + "가 열려요 🎫";
            case SOON -> "곧 " + mail.saleTypeName() + "가 열려요 ⏰";
        };
    }

    // 닉네임 뒤에 이어지는 안내 문장
    public String intro(TicketAlarmMail mail) {
        return switch (this) {
            case DAY_BEFORE -> "알림을 신청한 공연의 " + mail.saleTypeName() + "가 내일 시작돼요.";
            case SOON -> "알림을 신청한 공연의 " + mail.saleTypeName() + "가 30분 뒤 시작돼요.";
        };
    }
}
