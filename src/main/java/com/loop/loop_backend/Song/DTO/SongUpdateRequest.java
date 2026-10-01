package com.loop.loop_backend.Song.DTO;

import jakarta.validation.constraints.NotBlank;

public record SongUpdateRequest(
        @NotBlank String titleOriginal,
        String titleRomanized,
        String titleKo,
        String albumArtUrl) {}
