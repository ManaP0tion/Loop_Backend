package com.loop.loop_backend.Artist.init;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArtistDataInitializer implements ApplicationRunner {

    private final ArtistRepository artistRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (artistRepository.count() > 0) return;

        List<Artist> artists = List.of(
                // autoFetchConcerts = true (KOPIS 동기화 대상)
                artist("Kenshi Yonezu", "요네즈 켄시", null, true),
                artist("Official髭男dism", "오피셜히게단디즘", "히게단", true),
                artist("YOASOBI", "요아소비", null, true),
                artist("Ado", "아도", null, true),
                artist("Yuuri", "유우리", null, true),
                artist("Fujii Kaze", "후지이 카제", null, true),
                artist("King Gnu", "킹누", null, true),
                artist("Mrs. GREEN APPLE", "미세스그린애플", "미세스", true),
                artist("Vaundy", "바운디", null, true),
                artist("RADWIMPS", "래드윔프스", "랏도", true),
                artist("Aimyon", "아이묭", null, true),
                artist("Aimer", "에메", null, true),
                artist("Eve", "이브", null, true),
                artist("back number", "백넘버", null, true),
                artist("ヨルシカ", "요루시카", null, true),
                artist("LiSA", "리사", null, true),
                artist("ONE OK ROCK", "원오크록", null, true),
                artist("SEKAI NO OWARI", "세카이노오와리", null, true),
                artist("Creepy Nuts", "크리피 너츠", null, true),
                artist("호시노 겐", "호시노 겐", null, true),
                artist("ZUTOMAYO", "즛토마요 / 계속 한밤중이면 좋을 텐데.", "즈토마요", true),
                artist("natori", "나토리", null, true),
                artist("tuki.", "츠키", null, true),
                artist("키타니 타츠야", "키타니 타츠야", "Tatsuya Kitani", true),
                artist("ロクデナシ", "로쿠데나시", null, true),
                artist("Yuika", "유이카", null, true),
                artist("히츠지분가쿠", null, "Hitsujibungaku", true),
                artist("Uru", "우루", null, true),
                artist("amazarashi", "아마자라시", null, true),
                artist("KANA-BOON", "카나분", null, true),
                artist("BUMP OF CHICKEN", "범프오브치킨", null, true),
                artist("ASIAN KUNG-FU GENERATION", "아시안 쿵푸 제너레이션", null, true),
                artist("SPYAIR", "스파이에어", null, true),
                artist("SUPER BEAVER", "슈퍼 비버", null, true),
                artist("MY FIRST STORY", "마이 퍼스트 스토리", null, true),
                artist("ReoNa", "레오나", null, true),
                artist("BLUE ENCOUNT", "블루엔카운트", null, true),
                artist("THE ORAL CIGARETTES", "오랄시가렛", null, true),
                artist("MAN WITH A MISSION", "맨위드어미션", null, true),
                artist("Saucy Dog", "사우시 도그", null, true),
                artist("Novelbright", "노벨브라이트", null, true),
                artist("Omoinotake", "오모이노타케", null, true),
                artist("yama", "야마", null, true),
                artist("iri", "이리", null, true),
                artist("녹황색사회", null, null, true),
                artist("milet", "밀레", null, true),
                artist("ELLEGARDEN", "엘르가든", null, true),
                artist("10-FEET", "텐피트", null, true),
                artist("ACIDMAN", "애시드맨", null, true),
                artist("MONGOL800", "몽골800", null, true),
                artist("UVERworld", "유버월드", null, true),
                artist("SID", "시드", null, true),
                artist("SCANDAL", "스캔들", null, true),
                artist("린토시테시구레", null, "Ling Tosite Sigure", true),
                artist("TK(린토시테시구레)", null, null, true),

                // autoFetchConcerts = false (KOPIS 동기화 제외)
                artist("DOES", "도즈", null, false),
                artist("Aqua Timez", "아쿠아타임즈", null, false),
                artist("WANIMA", "와니마", null, false),
                artist("DISH//", "디시", null, false),
                artist("GRe4N BOYZ", "그린 보이즈", null, false),
                artist("People 1", "피플 원", null, false),
                artist("Chilli Beans.", "칠리 빈즈", null, false),
                artist("indigo la End", "인디고라엔드", null, false),
                artist("Kroi", "크로이", null, false),
                artist("Galileo Galilei", "갈릴레오 갈릴레이", null, false),
                artist("flumpool", "플럼풀", null, false),
                artist("이키모노가카리", null, "Ikimonogakari", false),
                artist("시이나 링고", null, "Sheena Ringo", false),
                artist("도쿄지헨", null, "Tokyo Incidents", false),
                artist("AAA", "트리플에이", null, false),
                artist("Mr.Children", "미스터 칠드런", null, false),
                artist("Spitz", "스피츠", null, false),
                artist("L'Arc~en~Ciel", "라르크앙시엘", null, false),
                artist("X JAPAN", "엑스 재팬", null, false),
                artist("마카로니 엔피츠", null, "Macaroni Empitsu", false),
                artist("go!go!vanillas", "고고바닐라스", null, false)
        );

        artistRepository.saveAll(artists);
        log.info("Initialized {} artists", artists.size());
    }

    private Artist artist(String name, String nameKo, String nameAlias, boolean autoFetchConcerts) {
        return Artist.builder()
                .name(name)
                .nameKo(nameKo)
                .nameAlias(nameAlias)
                .autoFetchConcerts(autoFetchConcerts)
                .build();
    }
}
