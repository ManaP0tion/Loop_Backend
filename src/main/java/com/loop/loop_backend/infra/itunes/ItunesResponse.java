package com.loop.loop_backend.infra.itunes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItunesResponse<T>(int resultCount, List<T> results) {}
