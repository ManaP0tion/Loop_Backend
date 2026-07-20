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
    void 이미_3개_등록된_경우_LIMIT_FAVORITE_ARTIST_예외를_던지고_저장하지_않는다() {
        User user = testUser();
        Artist artist = testArtist(ARTIST_ID, "아이유");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.of(artist));
        when(favoriteArtistRepository.countByUser(user)).thenReturn(3);

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
}