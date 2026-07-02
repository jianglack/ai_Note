package com.ainote.app.chunking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTypeDetectorTest {

    private ContentTypeDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ContentTypeDetector();
    }

    @Test
    void detect_markdownFeatures_returnsMarkdown() {
        ContentType type = detector.detect("""
                # Title
                - item
                [link](https://example.com)
                """);

        assertThat(type).isEqualTo(ContentType.MARKDOWN);
    }

    @Test
    void detect_codeFeatures_returnsCode() {
        ContentType type = detector.detect("""
                import java.util.List;
                class Demo {
                  public run() {
                    return;
                  }
                }
                """);

        assertThat(type).isEqualTo(ContentType.CODE);
    }

    @Test
    void detect_singleWeakFeature_returnsMixed() {
        assertThat(detector.detect("# Title only")).isEqualTo(ContentType.MIXED);
    }

    @Test
    void detect_plainOrBlank_returnsPlainText() {
        assertThat(detector.detect("just plain notes")).isEqualTo(ContentType.PLAIN_TEXT);
        assertThat(detector.detect("   ")).isEqualTo(ContentType.PLAIN_TEXT);
        assertThat(detector.detect(null)).isEqualTo(ContentType.PLAIN_TEXT);
    }
}
