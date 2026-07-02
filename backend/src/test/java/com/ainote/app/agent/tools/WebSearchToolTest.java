package com.ainote.app.agent.tools;

import com.ainote.app.service.UrlSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSearchToolTest {

    private UrlSafetyValidator urlSafetyValidator;
    private WebSearchTool tool;

    @BeforeEach
    void setUp() {
        urlSafetyValidator = mock(UrlSafetyValidator.class);
        tool = new WebSearchTool(urlSafetyValidator);
    }

    @Test
    void webSearchRejectsBlankQueryWithoutNetwork() {
        assertThat(tool.webSearch(" ")).contains("不能为空");
    }

    @Test
    void fetchWebPageRejectsBlankAndUnsafeUrlsBeforeNetwork() {
        assertThat(tool.fetchWebPage(" ")).contains("不能为空");

        when(urlSafetyValidator.requirePublicHttpUri("http://127.0.0.1/private"))
                .thenThrow(new IllegalArgumentException("blocked private address"));

        assertThat(tool.fetchWebPage("http://127.0.0.1/private"))
                .contains("获取网页失败")
                .contains("blocked");
    }

    @Test
    void parseResultsCleansRedirectUrlsAndHtmlEntities() throws Exception {
        String html = """
                <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa%3Fx%3D1&amp;rut=abc">
                  <b>Example</b> &amp; Docs
                </a>
                <a class="result__snippet">Learn &lt;fast&gt; with&nbsp;examples</a>
                """;

        List<?> results = parseResults(html);
        Object first = results.get(0);

        assertThat(results).hasSize(1);
        assertThat(recordValue(first, "title")).isEqualTo("Example & Docs");
        assertThat(recordValue(first, "snippet")).isEqualTo("Learn <fast> with examples");
        assertThat(recordValue(first, "url")).isEqualTo("https://example.com/a?x=1");
    }

    @Test
    void parseResultsFallsBackToLooseDuckDuckGoMarkup() throws Exception {
        String html = """
                <a rel="nofollow" href="https://example.com/loose"><span>Loose</span> Result</a>
                <td class="result-snippet">Fallback snippet</td>
                """;

        List<?> results = parseResults(html);

        assertThat(results).hasSize(1);
        assertThat(recordValue(results.get(0), "title")).isEqualTo("Loose Result");
        assertThat(recordValue(results.get(0), "snippet")).isEqualTo("Fallback snippet");
        assertThat(recordValue(results.get(0), "url")).isEqualTo("https://example.com/loose");
    }

    @Test
    void parsingHelpersHandleEmptyHtmlPlainUrlsAndMalformedRedirects() throws Exception {
        assertThat(parseResults("<html>No results</html>")).isEmpty();
        assertThat(cleanUrl(null)).isEmpty();
        assertThat(cleanUrl("https://example.com/plain")).isEqualTo("https://example.com/plain");
        assertThat(cleanUrl("//duckduckgo.com/l/?uddg=%E0%A4%A")).isEqualTo("%E0%A4%A");
        assertThat(stripHtml(null)).isEmpty();
        assertThat(stripHtml("<p>A&nbsp;&amp;&nbsp;B</p>")).isEqualTo("A & B");
    }

    private List<?> parseResults(String html) throws Exception {
        Method method = WebSearchTool.class.getDeclaredMethod("parseResults", String.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(tool, html);
    }

    private String cleanUrl(String url) throws Exception {
        Method method = WebSearchTool.class.getDeclaredMethod("cleanUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(tool, url);
    }

    private String stripHtml(String html) throws Exception {
        Method method = WebSearchTool.class.getDeclaredMethod("stripHtml", String.class);
        method.setAccessible(true);
        return (String) method.invoke(tool, html);
    }

    private static Object recordValue(Object record, String accessor) throws Exception {
        Method method = record.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return method.invoke(record);
    }
}
