package com.loop.loop_backend.FavoriteArtist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.FavoriteArtist.domain.FavoriteArtist;
import com.loop.loop_backend.FavoriteArtist.dto.FavoriteArtistResponseDto;
import com.loop.loop_backend.FavoriteArtist.repository.FavoriteArtistRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FavoriteArtistServiceImplTest {

    @Mock FavoriteArtistRepository favoriteArtistRepository;
    @Mock UserRepository userRepository;
    @Mock ArtistRepository artistRepository;
    @InjectMocks FavoriteArtistServiceImpl favoriteArtistService;

    private static final Long USER_ID = 1L;
    private static final Long ARTIST_ID = 10L;

    private User testUser() {
        return User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-1")
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
    }

    private Artist testArtist(long id, String name) {
        return Artist.builder().id(id).name(name).imageUrl("http://img/" + id).build();
    }

    // ── getFavoriteArtists ──────────────────────────────────────────────────

    @Test
    void 등록된_관심_아티스트_목록을_그대로_반환한다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(favoriteArtistRepository.findAllByUser(user)).thenReturn(List.of(
                FavoriteArtist.builder().user(user).artist(testArtist(10L, "아이유")).build(),
                FavoriteArtist.builder().user(user).artist(testArtist(11L, "IVE")).build()
        ));

        List<FavoriteArtistResponseDto> result = favoriteArtistService.getFavoriteArtists(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FavoriteArtistResponseDto::getArtistName)
                .containsExactly("아이유", "IVE");
    }

    @Test
    void 관심_아티스트가_하나도_없으면_빈_목록을_반환한다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(favoriteArtistRepository.findAllByUser(user)).thenReturn(List.of());

        List<FavoriteArtistResponseDto> result = favoriteArtistService.getFavoriteArtists(USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void 존재하지_않는_유저_조회시_USER_NOT_FOUND_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteArtistService.getFavoriteArtists(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 탈퇴한_유저의_관심_아티스트_조회시_WITHDRAWN_USER_예외를_던진다() {
        User user = testUser();
        user.withdraw();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> favoriteArtistService.getFavoriteArtists(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWN_USER);
    }

    // ── addFavoriteArtist ─────────────────────────────────────────────────

    @Test
    void 관심_아티스트를_추가하면_저장된_아티스트_정보를_반환한다() {
        User user = testUser();
        Artist artist = testArtist(ARTIST_ID, "아이유");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.of(artist));
        when(favoriteArtistRepository.countByUser(user)).thenReturn(0);
        when(favoriteArtistRepository.existsByUserAndArtist(user, artist)).thenReturn(false);
        when(favoriteArtistRepository.save(any(FavoriteArtist.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FavoriteArtistResponseDto result = favoriteArtistService.addFavoriteArtist(USER_ID, ARTIST_ID);

        assertThat(result.getArtistId()).isEqualTo(ARTIST_ID);
        assertThat(result.getArtistName()).isEqualTo("아이유");
    }

    @Test
    void 이미_5개_등록된_경우_LIMIT_FAVORITE_ARTIST_예외를_던지고_저장하지_않는다() {
        User user = testUser();
        Artist artist = testArtist(ARTIST_ID, "아이유");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.of(artist));
        when(favoriteArtistRepository.countByUser(user)).thenReturn(5);

        assertThatThrownBy(() -> favoriteArtistService.addFavoriteArtist(USER_ID, ARTIST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LIMIT_FAVORITE_ARTIST);

        verify(favoriteArtistRepository, never()).save(any());
    }

    @Test
    void 이미_등록된_아티스트를_추가하면_DUPLICATE_FAVORITE_ARTIST_예외를_던지고_저장하지_않는다() {
        User user = testUser();
        Artist artist = testArtist(ARTIST_ID, "아이유");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.of(artist));
        when(favoriteArtistRepository.countByUser(user)).thenReturn(1);
        when(favoriteArtistRepository.existsByUserAndArtist(user, artist)).thenReturn(true);

        assertThatThrownBy(() -> favoriteArtistService.addFavoriteArtist(USER_ID, ARTIST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_FAVORITE_ARTIST);

        verify(favoriteArtistRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_아티스트_추가시_ARTIST_NOT_FOUND_예외를_던지고_저장하지_않는다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteArtistService.addFavoriteArtist(USER_ID, ARTIST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ARTIST_NOT_FOUND);

        verify(favoriteArtistRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_유저에게_추가시_USER_NOT_FOUND_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteArtistService.addFavoriteArtist(USER_ID, ARTIST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(favoriteArtistRepository, never()).save(any());
    }

    // ── deleteFavoriteArtist ──────────────────────────────────────────────

    @Test
    void 본인_소유_관심_아티스트는_정상적으로_삭제된다() {
        User user = testUser();
        FavoriteArtist favoriteArtist = FavoriteArtist.builder().user(user).artist(testArtist(ARTIST_ID, "아이유")).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(favoriteArtistRepository.findByIdAndUser(100L, user)).thenReturn(Optional.of(favoriteArtist));

        favoriteArtistService.deleteFavoriteArtist(USER_ID, 100L);

        verify(favoriteArtistRepository).delete(favoriteArtist);
    }

    @Test
    void 존재하지_않거나_본인_소유가_아닌_관심_아티스트_삭제시_FAVORITE_ARTIST_NOT_FOUND_예외를_던진다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(favoriteArtistRepository.findByIdAndUser(999L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteArtistService.deleteFavoriteArtist(USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FAVORITE_ARTIST_NOT_FOUND);

        verify(favoriteArtistRepository, never()).delete(any());
    }

    @Test
    void 존재하지_않는_유저의_관심_아티스트_삭제시_USER_NOT_FOUND_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteArtistService.deleteFavoriteArtist(USER_ID, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(favoriteArtistRepository, never()).delete(any());
    }

    // ── replaceFavoriteArtists ────────────────────────────────────────────

    private FavoriteArtist favorite(User user, Artist artist) {
        return FavoriteArtist.builder().user(user).artist(artist).build();
    }

    @Test
    @DisplayName("일괄 교체 시 목록에 없는 기존 아티스트는 삭제하고 새 아티스트만 추가한다")
    void replaceFavoriteArtists_WithDiff_DeletesRemovedAndSavesOnlyNew() {
        User user = testUser();
        Artist a1 = testArtist(1L, "A1");
        Artist a2 = testArtist(2L, "A2");
        Artist a3 = testArtist(3L, "A3");
        FavoriteArtist fa1 = favorite(user, a1);
        FavoriteArtist fa2 = favorite(user, a2);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(artistRepository.findAllById(anyCollection())).thenReturn(List.of(a2, a3));
        when(favoriteArtistRepository.findAllByUser(user))
                .thenReturn(List.of(fa1, fa2))
                .thenReturn(List.of(fa2, favorite(user, a3)));

        List<FavoriteArtistResponseDto> result = favoriteArtistService.replaceFavoriteArtists(USER_ID, List.of(2L, 3L));

        verify(favoriteArtistRepository).delete(fa1);
        verify(favoriteArtistRepository, never()).delete(fa2);
        verify(favoriteArtistRepository).saveAll(argThat(saved -> {
            List<FavoriteArtist> list = (List<FavoriteArtist>) saved;
            return list.size() == 1 && list.get(0).getArtist() == a3;
        }));
        assertThat(result).extracting(FavoriteArtistResponseDto::getArtistId).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("중복 id가 섞여 있으면 하나로 처리하여 5개 제한을 판단한다")
    void replaceFavoriteArtists_WithDuplicateIds_DeduplicatesBeforeLimitCheck() {
        User user = testUser();
        Artist a1 = testArtist(1L, "A1");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(artistRepository.findAllById(anyCollection())).thenReturn(List.of(a1));
        when(favoriteArtistRepository.findAllByUser(user)).thenReturn(List.of());

        favoriteArtistService.replaceFavoriteArtists(USER_ID, List.of(1L, 1L, 1L, 1L, 1L, 1L));

        verify(favoriteArtistRepository).saveAll(argThat(saved -> ((List<?>) saved).size() == 1));
    }

    @Test
    @DisplayName("서로 다른 아티스트가 5개를 초과하면 LIMIT_FAVORITE_ARTIST 예외를 던지고 변경하지 않는다")
    void replaceFavoriteArtists_WithMoreThanFive_ThrowsLimit() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser()));

        assertThatThrownBy(() -> favoriteArtistService.replaceFavoriteArtists(USER_ID, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LIMIT_FAVORITE_ARTIST);

        verify(favoriteArtistRepository, never()).delete(any());
        verify(favoriteArtistRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("존재하지 않는 아티스트가 포함되면 ARTIST_NOT_FOUND 예외를 던지고 변경하지 않는다")
    void replaceFavoriteArtists_WithUnknownArtist_ThrowsArtistNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser()));
        when(artistRepository.findAllById(anyCollection())).thenReturn(List.of(testArtist(1L, "A1")));

        assertThatThrownBy(() -> favoriteArtistService.replaceFavoriteArtists(USER_ID, List.of(1L, 999L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ARTIST_NOT_FOUND);

        verify(favoriteArtistRepository, never()).delete(any());
        verify(favoriteArtistRepository, never()).saveAll(anyList());
    }
}
