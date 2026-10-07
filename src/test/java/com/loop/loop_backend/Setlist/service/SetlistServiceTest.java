package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.dto.SetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistSaveRequest;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 셋리스트 관리(AD-07) 요구사항:
// - 지난 셋리스트(최근 공연·지난 내한)와 실제 셋리스트를 같은 방식으로 저장하고, 다시 저장하면 통째로 교체된다.
// - 곡은 공연 아티스트의 (삭제되지 않은) 곡만, 요청 순서·중복 그대로. 곡 수는 자동 계산.
// - 지난 셋리스트는 날짜·장소 필수, 실제 셋리스트는 공연에 예상 곡 수가 있어야 한다. 페스티벌은 거부.
// - 목록은 최근 공연 → 지난 내한 → 실제 순. 응답은 항상 그 공연의 셋리스트 전체.
@DataJpaTest
class SetlistServiceTest {

    @Autowired private SetlistRepository setlistRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private SongRepository songRepository;
    @Autowired private EntityManager em;

    private SetlistService service;
    private Artist yuuri;
    private Concert concert;
    private Song s1, s2, s3;

    @BeforeEach
    void setUp() {
        service = new SetlistService(setlistRepository, concertRepository, songRepository);
        yuuri = artistRepository.save(Artist.builder().name("Yuuri").build());
        concert = concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST).artist(yuuri).expectedSongCount(3)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20))
                .build());
        s1 = song(yuuri, "ドライフラワー", 1);
        s2 = song(yuuri, "ベテルギウス", 2);
        s3 = song(yuuri, "いかないで", 3);
    }

    private Song song(Artist artist, String title, int sortOrder) {
        return songRepository.save(Song.builder().artist(artist).titleOriginal(title).sortOrder(sortOrder).build());
    }

    private static SetlistSaveRequest past(Long... songIds) {
        return new SetlistSaveRequest("Arena Tour", LocalDate.of(2026, 5, 1), "Zepp Haneda", List.of(songIds));
    }

    private static SetlistSaveRequest actual(Long... songIds) {
        return new SetlistSaveRequest(null, null, null, List.of(songIds));
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    void 지난_셋리스트는_헤더와_곡을_순서대로_저장하고_곡_수를_센다() {
        List<SetlistResponse> list = service.save(concert.getId(), SetlistType.RECENT, past(s2.getId(), s1.getId()));

        assertThat(list).singleElement().satisfies(r -> {
            assertThat(r.type()).isEqualTo(SetlistType.RECENT);
            assertThat(r.tourName()).isEqualTo("Arena Tour");
            assertThat(r.performedOn()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(r.venueName()).isEqualTo("Zepp Haneda");
            assertThat(r.songCount()).isEqualTo(2);
            assertThat(r.songs()).extracting(s -> s.position() + ":" + s.titleOriginal())
                    .containsExactly("1:ベテルギウス", "2:ドライフラワー");
        });
    }

    @Test
    void 실제_셋리스트는_중복_곡을_그대로_담고_헤더는_비운다() {
        List<SetlistResponse> list = service.save(concert.getId(), SetlistType.ACTUAL,
                new SetlistSaveRequest("무시", LocalDate.of(2026, 1, 1), "무시", List.of(s1.getId(), s2.getId(), s1.getId())));

        SetlistResponse actual = list.get(0);
        assertThat(actual.tourName()).isNull();
        assertThat(actual.venueName()).isNull();
        assertThat(actual.songs()).extracting(s -> s.songId()).containsExactly(s1.getId(), s2.getId(), s1.getId());
    }

    @Test
    void 다시_저장하면_통째로_교체된다() {
        service.save(concert.getId(), SetlistType.ACTUAL, actual(s1.getId(), s2.getId()));
        em.clear();

        List<SetlistResponse> list = service.save(concert.getId(), SetlistType.ACTUAL, actual(s3.getId()));

        assertThat(list).singleElement().satisfies(r ->
                assertThat(r.songs()).extracting(s -> s.songId()).containsExactly(s3.getId()));
        assertThat(setlistRepository.count()).isEqualTo(1);
    }

    @Test
    void 목록은_최근_공연_지난_내한_실제_순이다() {
        service.save(concert.getId(), SetlistType.ACTUAL, actual(s1.getId()));
        service.save(concert.getId(), SetlistType.PREVIOUS_VISIT, past(s2.getId()));
        service.save(concert.getId(), SetlistType.RECENT, past(s3.getId()));
        em.clear();

        assertThat(service.list(concert.getId())).extracting(SetlistResponse::type)
                .containsExactly(SetlistType.RECENT, SetlistType.PREVIOUS_VISIT, SetlistType.ACTUAL);
    }

    @Test
    void 공연_아티스트의_곡이_아니거나_없는_곡이면_400() {
        Artist other = artistRepository.save(Artist.builder().name("Ado").build());
        Song otherSong = song(other, "うっせぇわ", 1);

        assertThatThrownBy(() -> service.save(concert.getId(), SetlistType.ACTUAL, actual(s1.getId(), otherSong.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(concert.getId(), SetlistType.ACTUAL, actual(9999L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(setlistRepository.count()).isZero();
    }

    @Test
    void 삭제된_곡으로는_저장할_수_없다() {
        songRepository.delete(s1);
        em.flush();
        em.clear();

        assertThatThrownBy(() -> service.save(concert.getId(), SetlistType.ACTUAL, actual(s1.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 저장_후_곡이_삭제되면_셋리스트에서_빠지고_순서를_다시_매긴다() {
        service.save(concert.getId(), SetlistType.ACTUAL, actual(s1.getId(), s2.getId(), s3.getId()));
        em.flush();
        em.clear();
        songRepository.delete(songRepository.findById(s2.getId()).orElseThrow());
        em.flush();
        em.clear();

        SetlistResponse actual = service.list(concert.getId()).get(0);
        assertThat(actual.songCount()).isEqualTo(2);
        assertThat(actual.songs()).extracting(s -> s.position() + ":" + s.songId())
                .containsExactly("1:" + s1.getId(), "2:" + s3.getId());
    }

    @Test
    void 지난_셋리스트에_날짜나_장소가_없으면_400() {
        assertThatThrownBy(() -> service.save(concert.getId(), SetlistType.RECENT,
                new SetlistSaveRequest(null, null, "Zepp", List.of(s1.getId()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(concert.getId(), SetlistType.PREVIOUS_VISIT,
                new SetlistSaveRequest(null, LocalDate.of(2026, 5, 1), " ", List.of(s1.getId()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 예상_곡_수가_없는_공연은_실제_셋리스트를_저장할_수_없지만_지난_셋리스트는_된다() {
        Concert noCount = concertRepository.save(Concert.builder()
                .title("미정").category(ConcertCategory.J_POP_ARTIST).artist(yuuri).build());

        assertThatThrownBy(() -> service.save(noCount.getId(), SetlistType.ACTUAL, actual(s1.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.save(noCount.getId(), SetlistType.RECENT, past(s1.getId()))).hasSize(1);
    }

    @Test
    void 페스티벌이나_아티스트_미지정_공연은_400() {
        Concert festival = concertRepository.save(Concert.builder()
                .title("SUMMER SONIC").category(ConcertCategory.JAPAN_FESTIVAL).artist(yuuri).build());
        Concert noArtist = concertRepository.save(Concert.builder()
                .title("미정").category(ConcertCategory.J_POP_ARTIST).expectedSongCount(3).build());

        assertThatThrownBy(() -> service.save(festival.getId(), SetlistType.RECENT, past(s1.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(noArtist.getId(), SetlistType.ACTUAL, actual(s1.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 삭제하면_목록에서_빠지고_곡은_남는다() {
        service.save(concert.getId(), SetlistType.ACTUAL, actual(s1.getId()));
        service.save(concert.getId(), SetlistType.RECENT, past(s2.getId()));

        List<SetlistResponse> list = service.delete(concert.getId(), SetlistType.ACTUAL);

        assertThat(list).extracting(SetlistResponse::type).containsExactly(SetlistType.RECENT);
        assertThat(songRepository.findById(s1.getId())).isPresent();
    }

    @Test
    void 없는_셋리스트_삭제는_404_없는_공연은_404() {
        assertErrorCode(() -> service.delete(concert.getId(), SetlistType.ACTUAL), ErrorCode.SETLIST_NOT_FOUND);
        assertErrorCode(() -> service.list(9999L), ErrorCode.CONCERT_NOT_FOUND);
        assertErrorCode(() -> service.save(9999L, SetlistType.ACTUAL, actual(s1.getId())), ErrorCode.CONCERT_NOT_FOUND);
    }
}
