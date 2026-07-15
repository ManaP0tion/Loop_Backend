package com.loop.loop_backend.Artist.init;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
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
                // ===== J-POP 아티스트 (autoFetchConcerts = true) =====
                jpop("Kenshi Yonezu", "요네즈 켄시", null, true),
                jpop("Official髭男dism", "오피셜히게단디즘", "히게단", true),
                jpop("YOASOBI", "요아소비", null, true),
                jpop("Ado", "아도", null, true),
                jpop("Yuuri", "유우리", null, true),
                jpop("Fujii Kaze", "후지이 카제", null, true),
                jpop("King Gnu", "킹누", null, true),
                jpop("Mrs. GREEN APPLE", "미세스그린애플", "미세스", true),
                jpop("Vaundy", "바운디", null, true),
                jpop("RADWIMPS", "래드윔프스", "랏도", true),
                jpop("Aimyon", "아이묭", null, true),
                jpop("Aimer", "에메", null, true),
                jpop("Eve", "이브", null, true),
                jpop("back number", "백넘버", null, true),
                jpop("ヨルシカ", "요루시카", null, true),
                jpop("LiSA", "리사", null, true),
                jpop("ONE OK ROCK", "원오크록", null, true),
                jpop("SEKAI NO OWARI", "세카이노오와리", null, true),
                jpop("Creepy Nuts", "크리피 너츠", null, true),
                jpop("호시노 겐", "호시노 겐", null, true),
                jpop("ZUTOMAYO", "즛토마요 / 계속 한밤중이면 좋을 텐데.", "즈토마요", true),
                jpop("natori", "나토리", null, true),
                jpop("tuki.", "츠키", null, true),
                jpop("키타니 타츠야", "키타니 타츠야", "Tatsuya Kitani", true),
                jpop("ロクデナシ", "로쿠데나시", null, true),
                jpop("Yuika", "유이카", null, true),
                jpop("히츠지분가쿠", null, "Hitsujibungaku", true),
                jpop("Uru", "우루", null, true),
                jpop("amazarashi", "아마자라시", null, true),
                jpop("KANA-BOON", "카나분", null, true),
                jpop("BUMP OF CHICKEN", "범프오브치킨", null, true),
                jpop("ASIAN KUNG-FU GENERATION", "아시안 쿵푸 제너레이션", null, true),
                jpop("SPYAIR", "스파이에어", null, true),
                jpop("SUPER BEAVER", "슈퍼 비버", null, true),
                jpop("MY FIRST STORY", "마이 퍼스트 스토리", null, true),
                jpop("ReoNa", "레오나", null, true),
                jpop("BLUE ENCOUNT", "블루엔카운트", null, true),
                jpop("THE ORAL CIGARETTES", "오랄시가렛", null, true),
                jpop("MAN WITH A MISSION", "맨위드어미션", null, true),
                jpop("Saucy Dog", "사우시 도그", null, true),
                jpop("Novelbright", "노벨브라이트", null, true),
                jpop("Omoinotake", "오모이노타케", null, true),
                jpop("yama", "야마", null, true),
                jpop("iri", "이리", null, true),
                jpop("녹황색사회", null, null, true),
                jpop("milet", "밀레", null, true),
                jpop("ELLEGARDEN", "엘르가든", null, true),
                jpop("10-FEET", "텐피트", null, true),
                jpop("ACIDMAN", "애시드맨", null, true),
                jpop("MONGOL800", "몽골800", null, true),
                jpop("UVERworld", "유버월드", null, true),
                jpop("SID", "시드", null, true),
                jpop("SCANDAL", "스캔들", null, true),
                jpop("린토시테시구레", null, "Ling Tosite Sigure", true),
                jpop("TK(린토시테시구레)", null, null, true),

                // ===== J-POP 아티스트 (autoFetchConcerts = false) =====
                jpop("DOES", "도즈", null, false),
                jpop("Aqua Timez", "아쿠아타임즈", null, false),
                jpop("WANIMA", "와니마", null, false),
                jpop("DISH//", "디시", null, false),
                jpop("GRe4N BOYZ", "그린 보이즈", null, false),
                jpop("People 1", "피플 원", null, false),
                jpop("Chilli Beans.", "칠리 빈즈", null, false),
                jpop("indigo la End", "인디고라엔드", null, false),
                jpop("Kroi", "크로이", null, false),
                jpop("Galileo Galilei", "갈릴레오 갈릴레이", null, false),
                jpop("flumpool", "플럼풀", null, false),
                jpop("이키모노가카리", null, "Ikimonogakari", false),
                jpop("시이나 링고", null, "Sheena Ringo", false),
                jpop("도쿄지헨", null, "Tokyo Incidents", false),
                jpop("AAA", "트리플에이", null, false),
                jpop("Mr.Children", "미스터 칠드런", null, false),
                jpop("Spitz", "스피츠", null, false),
                jpop("L'Arc~en~Ciel", "라르크앙시엘", null, false),
                jpop("X JAPAN", "엑스 재팬", null, false),
                jpop("마카로니 엔피츠", null, "Macaroni Empitsu", false),
                jpop("go!go!vanillas", "고고바닐라스", null, false),

                // ===== 국내 아티스트 (autoFetchConcerts = true) =====
                domestic("Nell", "넬", null),
                domestic("새소년", null, null),
                domestic("쏜애플", null, null),
                domestic("LUCY", "루시", null),
                domestic("SURL", "설", null),
                domestic("N.Flying", "엔플라잉", null),
                domestic("너드커넥션", null, null),
                domestic("Lacuna", "라쿠나", null),
                domestic("소란", null, null),
                domestic("ADOY", "아도이", null),
                domestic("O3ohn", "오존", null),
                domestic("카더가든", null, null),
                domestic("Balming Tiger", "발밍타이거", null),
                domestic("윤마치", null, null),
                domestic("오이스터", null, null),
                domestic("다브다", null, null),
                domestic("오월오일", null, null),
                domestic("잔나비", null, null),
                domestic("혁오", null, null),
                domestic("실리카겔", null, null),
                domestic("검정치마", null, null),
                domestic("wave to earth", "웨이브투어스", null),
                domestic("터치드", null, null),
                domestic("The Volunteers", "더 볼런티어스", null),
                domestic("The Rose", "더 로즈", null),
                domestic("한로로", null, null),
                domestic("이승윤", null, null),
                domestic("하현상", null, null),
                domestic("나상현씨밴드", null, null),
                domestic("DAY6", "데이식스", null),
                domestic("YB", "와이비 / 윤도현밴드", null),
                domestic("국카스텐", null, null),
                domestic("지소쿠리클럽", null, null),
                domestic("달담", null, null),
                domestic("KARDI", "카디", null),
                domestic("유다빈밴드", null, null),
                domestic("10cm", "십센치", null),
                domestic("자우림", null, null),
                domestic("크라잉넛", null, null)
        );

        artistRepository.saveAll(artists);
        log.info("Initialized {} artists", artists.size());
    }

    private Artist jpop(String name, String nameKo, String nameAlias, boolean autoFetchConcerts) {
        return build(name, nameKo, nameAlias, autoFetchConcerts, ConcertCategory.J_POP_ARTIST);
    }

    private Artist domestic(String name, String nameKo, String nameAlias) {
        return build(name, nameKo, nameAlias, true, ConcertCategory.DOMESTIC_ARTIST);
    }

    private Artist build(String name, String nameKo, String nameAlias, boolean autoFetchConcerts, ConcertCategory category) {
        return Artist.builder()
                .name(name)
                .nameKo(nameKo)
                .nameAlias(nameAlias)
                .autoFetchConcerts(autoFetchConcerts)
                .category(category)
                .build();
    }
}
