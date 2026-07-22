package com.loop.loop_backend.FavoriteArtist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.FavoriteArtist.domain.FavoriteArtist;
import com.loop.loop_backend.FavoriteArtist.dto.FavoriteArtistResponseDto;
import com.loop.loop_backend.FavoriteArtist.repository.FavoriteArtistRepository;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteArtistServiceImpl implements FavoriteArtistService {

    private static final int MAX_FAVORITE_ARTIST_COUNT = 3;

    private final FavoriteArtistRepository favoriteArtistRepository;
    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;

    @Override
    public List<FavoriteArtistResponseDto> getFavoriteArtists(Long userId) {
        User user = getUser(userId);
        if (user.getStatus() == Status.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }
        return favoriteArtistRepository.findAllByUser(user).stream()
                .map(FavoriteArtistResponseDto::from)
                .toList();
    }

    @Override
    @Transactional
    public FavoriteArtistResponseDto addFavoriteArtist(Long userId, Long artistId) {
        User user = getUser(userId);
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));

        if (favoriteArtistRepository.countByUser(user) >= MAX_FAVORITE_ARTIST_COUNT) {
            throw new BusinessException(ErrorCode.LIMIT_FAVORITE_ARTIST);
        }
        if (favoriteArtistRepository.existsByUserAndArtist(user, artist)) {
            throw new BusinessException(ErrorCode.DUPLICATE_FAVORITE_ARTIST);
        }

        FavoriteArtist saved = favoriteArtistRepository.save(
                FavoriteArtist.builder()
                        .user(user)
                        .artist(artist)
                        .build()
        );
        return FavoriteArtistResponseDto.from(saved);
    }

    @Override
    @Transactional
    public void deleteFavoriteArtist(Long userId, Long favoriteArtistId) {
        User user = getUser(userId);
        FavoriteArtist favoriteArtist = favoriteArtistRepository.findByIdAndUser(favoriteArtistId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_ARTIST_NOT_FOUND));
        favoriteArtistRepository.delete(favoriteArtist);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}