package com.ainote.app.agent.tools;

import com.ainote.app.service.UrlSafetyValidator;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页搜索工具 — 使用 DuckDuckGo HTML Lite（免费，无需 API Key）
 */
@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final int MAX_RESULTS = 5;

    // DuckDuckGo HTML Lite 结果解析正则
    private static final Pattern RESULT_BLOCK = Pattern.compile(
            "<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>(.+?)</a>.*?" +
            "<a[^>]+class=\"result__snippet\"[^>]*>(.+?)</a>",
            Pattern.DOTALL);

    // 备用：更宽松的匹配
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "<a[^>]+rel=\"nofollow\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.DOTALL);

    private static final Pattern SNIPPET_PATTERN = Pattern.compile(
            "<td[^>]*class=\"result-snippet\"[^>]*>(.*?)</td>",
            Pattern.DOTALL);

    private final UrlSafetyValidator urlSafetyValidator;

    public WebSearchTool(UrlSafetyValidator urlSafetyValidator) {
        this.urlSafetyValidator = urlSafetyValidator;
    }

    @Tool("搜索网络获取最新信息。当用户询问时事新闻、实时数据、百科知识或笔记中没有的信息时使用此工具。")
    public String webSearch(@P("搜索关键词") String query) {
        log.info("Web search: {}", query);

        if (query == null || query.isBlank()) {
            return "搜索关键词不能为空";
        }

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://html.duckduckgo.com/html/?q=" + encoded;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("DuckDuckGo returned status: {}", response.statusCode());
                return "搜索服务暂时不可用（HTTP " + response.statusCode() + "）";
            }

            String html = response.body();
            List<SearchResult> results = parseResults(html);

            if (results.isEmpty()) {
                log.info("No results found for: {}", query);
                return "未找到与「" + query + "」相关的搜索结果";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("搜索「").append(query).append("」找到以下结果：\n\n");
            for (int i = 0; i < results.size(); i++) {
                SearchResult r = results.get(i);
                sb.append(i + 1).append(". **").append(r.title).append("**\n");
                sb.append("   ").append(r.snippet).append("\n");
                sb.append("   链接: ").append(r.url).append("\n\n");
            }

            log.info("Web search returned {} results for: {}", results.size(), query);
            return sb.toString();

        } catch (Exception e) {
            log.error("Web search failed: {}", e.getMessage(), e);
            return "网络搜索失败: " + e.getMessage();
        }
    }

    @Tool("获取指定网页的文本内容。当需要查看搜索结果中某个链接的详细内容时使用。")
    public String fetchWebPage(@P("要获取的网页URL") String url) {
        log.info("Fetching web page: {}", url);

        if (url == null || url.isBlank()) {
            return "URL 不能为空";
        }

        try {
            URI safeUri = urlSafetyValidator.requirePublicHttpUri(url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(safeUri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String html = response.body();

            // 提取纯文本
            String text = html
                    .replaceAll("(?s)<script[^>]*>.*?</script>", "")
                    .replaceAll("(?s)<style[^>]*>.*?</style>", "")
                    .replaceAll("(?s)<nav[^>]*>.*?</nav>", "")
                    .replaceAll("(?s)<footer[^>]*>.*?</footer>", "")
                    .replaceAll("(?s)<header[^>]*>.*?</header>", "")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("&amp;", "&")
                    .replaceAll("&lt;", "<")
                    .replaceAll("&gt;", ">")
                    .replaceAll("&quot;", "\"")
                    .replaceAll("[ \\t]+", " ")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();

            if (text.length() > 3000) {
                text = text.substring(0, 3000) + "\n...(内容已截断)";
            }

            return text.isEmpty() ? "该网页没有可提取的文本内容" : text;

        } catch (Exception e) {
            log.error("Fetch failed for {}: {}", url, e.getMessage());
            return "获取网页失败: " + e.getMessage();
        }
    }

    /**
     * 解析 DuckDuckGo HTML Lite 搜索结果
     */
    private List<SearchResult> parseResults(String html) {
        List<SearchResult> results = new ArrayList<>();

        // 方式1：标准 result__a + result__snippet 匹配
        Matcher m = RESULT_BLOCK.matcher(html);
        while (m.find() && results.size() < MAX_RESULTS) {
            String url = cleanUrl(m.group(1));
            String title = stripHtml(m.group(2));
            String snippet = stripHtml(m.group(3));
            if (!url.isEmpty() && !title.isEmpty()) {
                results.add(new SearchResult(title, snippet, url));
            }
        }

        // 方式2：如果方式1没匹配到，用更宽松的模式
        if (results.isEmpty()) {
            Matcher linkMatcher = LINK_PATTERN.matcher(html);
            Matcher snippetMatcher = SNIPPET_PATTERN.matcher(html);
            while (linkMatcher.find() && results.size() < MAX_RESULTS) {
                String url = cleanUrl(linkMatcher.group(1));
                String title = stripHtml(linkMatcher.group(2));
                String snippet = snippetMatcher.find() ? stripHtml(snippetMatcher.group(1)) : "";
                if (!url.isEmpty() && !title.isEmpty() && url.startsWith("http")) {
                    results.add(new SearchResult(title, snippet, url));
                }
            }
        }

        return results;
    }

    /**
     * DuckDuckGo 的链接是重定向格式，提取真实 URL
     */
    private String cleanUrl(String url) {
        if (url == null) return "";
        // DuckDuckGo redirect: //duckduckgo.com/l/?uddg=https%3A%2F%2F...
        if (url.contains("uddg=")) {
            int idx = url.indexOf("uddg=");
            String encoded = url.substring(idx + 5);
            int ampIdx = encoded.indexOf("&");
            if (ampIdx > 0) encoded = encoded.substring(0, ampIdx);
            try {
                return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return encoded;
            }
        }
        return url;
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record SearchResult(String title, String snippet, String url) {}
}
