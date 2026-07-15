package com.loop.loop_backend.common.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

public final class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.none();
    private static final Document.OutputSettings OUTPUT = new Document.OutputSettings()
            .prettyPrint(false)
            .escapeMode(Entities.EscapeMode.xhtml);

    private HtmlSanitizer() {}

    public static String sanitize(String input) {
        if (input == null) return null;
        return Jsoup.clean(input, "", SAFELIST, OUTPUT);
    }
}
