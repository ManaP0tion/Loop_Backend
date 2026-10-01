package com.loop.loop_backend.infra.itunes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItunesArtist(
        String wrapperType,
        Long artistId,
        String artistName,
        String artistLinkUrl,
        String primaryGenreName) {}
