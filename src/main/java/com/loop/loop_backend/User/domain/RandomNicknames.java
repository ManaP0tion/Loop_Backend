package com.loop.loop_backend.User.domain;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 카카오 첫 로그인 때 주는 랜덤 닉네임 = 앞 단어 + 뒤 단어 (띄어쓰기 없이, 51 × 51 = 2,601개).
 * 가장 긴 조합이 7자라 숫자 한 자리를 붙여도 닉네임 최대 길이(8자)를 넘지 않는다.
 * 닉네임 규칙(한글·영문·숫자만)에 맞도록 단어는 한글만 쓴다.
 */
public final class RandomNicknames {

    static final List<String> FRONT = List.of(
            "새벽", "달빛", "별빛", "노을", "여름", "겨울", "봄날", "가을", "바람", "파도",
            "햇살", "윤슬", "이슬", "안개", "여명", "황혼", "꽃비", "은하", "유성", "물결",
            "하늘", "아침", "오후", "저녁", "한낮", "한밤", "밤비", "빗물", "청춘", "낭만",
            "설렘", "추억", "미소", "온기", "숨결", "꿈결", "꽃잎", "벚꽃", "물빛", "구름",
            "바다", "인연", "약속", "향기", "여운", "숲길", "풀잎", "샛별", "초원", "달밤",
            "하트");

    static final List<String> BACK = List.of(
            "라일락", "군청", "수평선", "스파클", "마리골드", "레몬", "프리텐더", "백일", "로빈슨", "체리",
            "피스사인", "만월", "소라닌", "오렌지", "베텔기우스", "블루버드", "폴라리스", "괴수", "꽃다발", "불꽃",
            "스타더스트", "인페르노", "댄스홀", "리라이트", "아이돌", "용사", "꽃의탑", "해바라기", "사무라이", "숙명",
            "도쿄", "신데렐라", "주근깨", "타임머신", "완전감각", "앙코르", "기타", "베이스", "드럼", "리프",
            "코러스", "멜로디", "리듬", "시계", "셋리스트", "비기닝", "시티팝", "피날레", "라이브", "풋사과",
            "플래시");

    private RandomNicknames() {
    }

    /** 앞·뒤 단어를 하나씩 무작위로 골라 붙인다. */
    public static String pick(RandomGenerator random) {
        return FRONT.get(random.nextInt(FRONT.size())) + BACK.get(random.nextInt(BACK.size()));
    }

    /** 조합이 모두 차 있을 때 쓰는 숫자 한 자리(1~9) 붙인 닉네임. */
    public static String pickWithDigit(RandomGenerator random) {
        return pick(random) + (1 + random.nextInt(9));
    }
}