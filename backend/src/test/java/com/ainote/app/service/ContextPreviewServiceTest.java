package com.ainote.app.service;

import com.ainote.app.model.context.ContextPreviewRequest;
import com.ainote.app.model.context.ContextPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextPreviewServiceTest {

    private ContextAssembler contextAssembler;
    private JiTokenService jiTokenService;
    private ContextPreviewService service;

    @BeforeEach
    void setUp() {
        contextAssembler = mock(ContextAssembler.class);
        jiTokenService = mock(JiTokenService.class);
        service = new ContextPreviewService(contextAssembler, jiTokenService);
    }

    @Test
    void previewUsesRealAssemblerOutputAndBuildsReadableSectionsAndFlow() {
        String assembled = """
                <user_memory>
                  <preference>Prefers concise answers</preference>
                </user_memory>

                <selected_notes default_operation_target="true">
                  <note index="1" id="note-1">
                    <title>Memory system plan</title>
                    <content><![CDATA[
                Selected note content
                    ]]></content>
                  </note>
                </selected_notes>

                <rag_context role="reference_only" operation_target="false">
                  <snippet noteId="note-2" title="RAG hit" operation_target="false">
                    Rag snippet
                  </snippet>
                </rag_context>
                """;
        when(contextAssembler.detectIntent("总结这篇笔记")).thenReturn(ContextAssembler.Intent.STANDARD);
        when(contextAssembler.assemble("总结这篇笔记", List.of("note-1"), "user-1")).thenReturn(assembled);
        when(jiTokenService.countTokens(assembled)).thenReturn(120);

        ContextPreviewResponse response = service.preview(
                "user-1",
                new ContextPreviewRequest("总结这篇笔记", List.of("note-1")));

        assertThat(response.intent()).isEqualTo("STANDARD");
        assertThat(response.finalContext()).isEqualTo(assembled);
        assertThat(response.estimatedTokens()).isEqualTo(120);
        assertThat(response.selectedNoteCount()).isEqualTo(1);
        assertThat(response.sections())
                .extracting(ContextPreviewResponse.Section::type)
                .containsExactly("semantic_memory", "selected_notes", "rag_context");
        assertThat(response.flow())
                .extracting(ContextPreviewResponse.FlowStep::title)
                .contains("判断问题类型", "加入选中笔记", "判断是否需要 RAG");
        verify(contextAssembler).assemble("总结这篇笔记", List.of("note-1"), "user-1");
    }
}
