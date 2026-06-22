package com.ainote.search.util;

import java.util.regex.Pattern;

public class HtmlStripper {
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern MARKDOWN = Pattern.compile("[#*_`~\\[\\]()>]");

    public static String strip(String content) {
        if (content == null) return "";
        String text = HTML_TAG.matcher(content).replaceAll(" ");
        text = MARKDOWN.matcher(text).replaceAll("");
        return text.replaceAll("\\s+", " ").trim();
    }
}
