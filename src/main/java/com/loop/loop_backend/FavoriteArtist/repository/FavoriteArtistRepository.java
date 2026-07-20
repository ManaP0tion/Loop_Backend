package com.loop.loop_backend.FavoriteArtist.repository;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.FavoriteArtist.domain.FavoriteArtist;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteArtistRepository extends JpaRepository<FavoriteArtist, Long> {

    List<FavoriteArtist> findAllByUser(User user);

    int countByUser(User user);

    boolean existsByUserAndArtist(User user, Artist artist);

    Optional<FavoriteArtist> findByIdAndUser(Long id, User user);
}