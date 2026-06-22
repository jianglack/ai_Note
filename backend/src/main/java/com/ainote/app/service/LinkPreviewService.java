package com.ainote.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LinkPreviewService {

    private static final Logger log = LoggerFactory.getLogger(LinkPreviewService.class);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final int MAX_CACHE_ENTRIES = 500;

    private final UrlSafetyValidator urlSafetyValidator;
    private final Map<String, Map<String, String>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, String>> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            });

    private static final Pattern OG_TITLE = Pattern.compile("<meta[^>]*property=[\"']og:title[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_DESC = Pattern.compile("<meta[^>]*property=[\"']og:description[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_IMAGE = Pattern.compile("<meta[^>]*property=[\"']og:image[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_SITE = Pattern.compile("<meta[^>]*property=[\"']og:site_name[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_TAG = Pattern.compile("<title[^>]*>([^<]*)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESC_META = Pattern.compile("<meta[^>]*name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    public LinkPreviewService(UrlSafetyValidator urlSafetyValidator) {
        this.urlSafetyValidator = urlSafetyValidator;
    }

    public Map<String, String> fetchPreview(String url) {
        if (cache.containsKey(url)) return cache.get(url);

        Map<String, String> preview = new HashMap<>();
        preview.put("url", url);

        try {
            URI safeUri = urlSafetyValidator.requirePublicHttpUri(url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(safeUri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (compatible; AINote/1.0)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String html = response.body();

            // Extract OpenGraph meta
            preview.put("title", extract(OG_TITLE, html, extract(TITLE_TAG, html, url)));
            preview.put("description", extract(OG_DESC, html, extract(DESC_META, html, "")));
            preview.put("imageUrl", extract(OG_IMAGE, html, ""));
            preview.put("siteName", extract(OG_SITE, html, safeUri.getHost()));

            // Extract favicon
            preview.put("faviconUrl", "https://" + safeUri.getHost() + "/favicon.ico");

        } catch (Exception e) {
            log.warn("Failed to fetch preview for {}: {}", url, e.getMessage());
            preview.put("title", url);
            preview.put("description", "");
            preview.put("imageUrl", "");
            preview.put("siteName", "");
            preview.put("faviconUrl", "");
        }

        cache.put(url, preview);
        return preview;
    }

    private String extract(Pattern pattern, String html, String fallback) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }
}
