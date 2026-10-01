package com.loop.loop_backend.infra.itunes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItunesTrack(
        String wrapperType,
        Long trackId,
        String trackName,
        String collectionName,
        String artworkUrl100) {}
