package com.loop.loop_backend.Artist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.domain.Nationality;
import com.loop.loop_backend.Artist.dto.ArtistResponseDto;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.infra.itunes.ItunesClient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// 아티스트 검색 요구사항 (GET /api/artists/search, 아티스트 선택 화면):
// - 검색어(query)와 국적(nationality)은 모두 선택이다. 둘 다 없으면 전체 아티스트.
// - 검색어는 서비스명/기본명칭/한국어/별칭에서 대소문자 없이 부분 일치. 빈 검색어는 검색어 없음과 같다.
// - 국적을 지정하면 그 국적만 나오고, 국적 미입력 아티스트는 빠진다.
@DataJpaTest
class ArtistSearchTest {

    @Autowired private ArtistRepository artistRepository;
    @Autowired private EntityManager em;

    private ArtistService service;

    @BeforeEach
    void setUp() {
        service = new ArtistServiceImpl(artistRepository, mock(ItunesClient.class));
        save("YOASOBI", "요아소비", Nationality.JP);
        save("Yuuri", "유우리", Nationality.JP);
        save("YB", "윤도현밴드", Nationality.KR);
        save("Vaundy", "바운디", null);
        em.flush();
        em.clear();
    }

    private void save(String name, String nameKo, Nationality nationality) {
        artistRepository.save(Artist.builder().name(name).nameKo(nameKo).nationality(nationality).build());
    }

    private Iterable<String> names(String query, Nationality nationality) {
        return service.searchArtists(query, nationality).stream().map(ArtistResponseDto::getName).toList();
    }

    @Test
    @DisplayName("검색어도 국적도 없으면 전체 아티스트가 나온다")
    void search_WithNothing_ReturnsAll() {
        assertThat(names(null, null)).containsExactlyInAnyOrder("YOASOBI", "Yuuri", "YB", "Vaundy");
        assertThat(names("  ", null)).containsExactlyInAnyOrder("YOASOBI", "Yuuri", "YB", "Vaundy");
    }

    @Test
    @DisplayName("국적만 지정하면 그 국적 아티스트만 나오고 국적 미입력은 빠진다")
    void search_WithNationalityOnly_ReturnsThatNationality() {
        assertThat(names(null, Nationality.JP)).containsExactlyInAnyOrder("YOASOBI", "Yuuri");
        assertThat(names(null, Nationality.KR)).containsExactly("YB");
    }

    @Test
    @DisplayName("검색어만 주면 이름(한국어 포함)에서 대소문자 없이 부분 일치로 찾는다")
    void search_WithQueryOnly_MatchesNames() {
        assertThat(names("y", null)).containsExactlyInAnyOrder("YOASOBI", "Yuuri", "YB", "Vaundy");
        assertThat(names("유우리", null)).containsExactly("Yuuri");
    }

    @Test
    @DisplayName("검색어와 국적을 함께 주면 두 조건을 모두 만족하는 아티스트만 나온다")
    void search_WithQueryAndNationality_MatchesBoth() {
        assertThat(names("y", Nationality.JP)).containsExactlyInAnyOrder("YOASOBI", "Yuuri");
        assertThat(names("y", Nationality.KR)).containsExactly("YB");
    }
}