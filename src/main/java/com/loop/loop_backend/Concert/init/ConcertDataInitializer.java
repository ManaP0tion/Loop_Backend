package com.loop.loop_backend.Concert.init;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class ConcertDataInitializer implements ApplicationRunner {

    private final ConcertRepository concertRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (concertRepository.count() > 0) return;

        List<Concert> concerts = List.of(
                // ===== J-POP 아티스트 (내한) =====
                seed("SPYAIR JUST LIKE THIS [고양]", ConcertCategory.J_POP_ARTIST),
                seed("후지이 카제 Prema 월드 투어 [서울]", ConcertCategory.J_POP_ARTIST),
                seed("Novelbright ASIA TOUR: PYRAMID [서울]", ConcertCategory.J_POP_ARTIST),
                seed("오피셜히게단디즘 아시아 투어 in SEOUL", ConcertCategory.J_POP_ARTIST),
                seed("back number 내한공연: Grateful Yesterdays Tour [서울]", ConcertCategory.J_POP_ARTIST),
                seed("Vaundy ASIA ARENA TOUR HORO IN SEOUL", ConcertCategory.J_POP_ARTIST),
                seed("LiSA ASIA TOUR: LiVE is Smile Always ~15~ [서울]", ConcertCategory.J_POP_ARTIST),
                seed("Omoinotake One Man Tour in Seoul", ConcertCategory.J_POP_ARTIST),

                // ===== 국내 아티스트 =====
                seed("SERIES.L, 한로로: Promenade", ConcertCategory.DOMESTIC_ARTIST),
                seed("Silica Gel Asia Tour, Syn.THE.Size: Ballad of You", ConcertCategory.DOMESTIC_ARTIST),
                seed("쏜애플 콘서트: 나의 세기 [서울]", ConcertCategory.DOMESTIC_ARTIST),

                // ===== 일본 페스티벌 =====
                seed("Summer Sonic [일본 치바현]", ConcertCategory.JAPAN_FESTIVAL),
                seed("FUJI ROCK FESTIVAL [일본]", ConcertCategory.JAPAN_FESTIVAL),

                // ===== 국내 페스티벌 =====
                seed("인천펜타포트 락 페스티벌", ConcertCategory.DOMESTIC_FESTIVAL),
                seed("JUMF, 전주얼티밋뮤직페스티벌", ConcertCategory.DOMESTIC_FESTIVAL),
                seed("사운드 플래닛 페스티벌 [인천]", ConcertCategory.DOMESTIC_FESTIVAL),
                seed("제18회 서울재즈페스티벌", ConcertCategory.DOMESTIC_FESTIVAL)
        );

        concertRepository.saveAll(concerts);
        log.info("Initialized {} seed concerts", concerts.size());
    }

    private Concert seed(String title, ConcertCategory category) {
        return Concert.builder()
                .title(title)
                .category(category)
                .build();
    }
}
