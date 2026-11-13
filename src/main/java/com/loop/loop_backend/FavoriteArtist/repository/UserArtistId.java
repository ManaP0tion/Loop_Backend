package com.loop.loop_backend.FavoriteArtist.repository;

/** 관심 아티스트 한 건을 (사용자 ID, 아티스트 ID)로만 담는다 - 엔티티를 읽지 않고 공통 아티스트 수만 셀 때 쓴다. */
public record UserArtistId(Long userId, Long artistId) {
}
