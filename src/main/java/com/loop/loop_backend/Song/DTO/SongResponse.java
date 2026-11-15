package com.loop.loop_backend.Song.DTO;

import com.loop.loop_backend.Song.domain.Song;

public record SongResponse(
        Long id, Long artistId, Long trackId,
        String titleOriginal, String titleRomanized, String titleKo,
        String albumArtUrl, Integer sortOrder) {

    public static SongResponse from(Song s) {
        return new SongResponse(s.getId(), s.getArtist().getId(), s.getTrackId(),
                s.getTitleOriginal(), s.getTitleRomanized(), s.getTitleKo(),
                s.getAlbumArtUrl(), s.getSortOrder());
    }
}
