package com.loop.loop_backend.Concert.kopis;

public final class JapanFestivalMatcher {

    private static final String[] WHITELIST = {
            "FUJI ROCK",
            "SUMMER SONIC",
            "ROCK IN JAPAN",
            "JAPAN JAM",
            "COUNTDOWN JAPAN",
            "RISING SUN ROCK FESTIVAL",
            "VIVA LA ROCK",
            "MIYAKO ISLAND ROCK FESTIVAL",
            "ARABAKI",
            "METROCK",
            "JOIN ALIVE",
            "TREASURE ISLAND"
    };

    private static final String[] FESTIVAL_MARKERS = {
            "페스티벌", "FESTIVAL", "FEST", "ROCK IN", "JAM"
    };

    private JapanFestivalMatcher() {}

    public static boolean isJapanFestival(String title) {
        if (title == null || title.isBlank()) return false;
        String upper = title.toUpperCase();

        for (String name : WHITELIST) {
            if (upper.contains(name)) return true;
        }

        if (title.contains("[일본")) {
            for (String marker : FESTIVAL_MARKERS) {
                if (upper.contains(marker.toUpperCase())) return true;
            }
        }
        return false;
    }
}
