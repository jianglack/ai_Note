package com.ainote.app.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ainote.app.entity.NoteMedia;
import com.ainote.app.repository.NoteMediaRepository;
import com.ainote.app.security.SecurityUtils;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediaActionToolTest {

    private NoteMediaRepository mediaRepository;
    private SecurityUtils securityUtils;
    private MediaActionTool tool;

    @BeforeEach
    void setUp() {
        mediaRepository = mock(NoteMediaRepository.class);
        securityUtils = mock(SecurityUtils.class);
        tool = new MediaActionTool(mediaRepository, securityUtils);

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
    }

    @Test
    void searchTablesReturnsMatchingTable() {
        NoteMedia table = media("n1", "table", "| 项目 | 金额 |\n| 预算 | 100 |", null);
        when(mediaRepository.findByUserId("user-1"))
                .thenReturn(List.of(table));

        String result = tool.searchTables("预算");

        assertThat(result).contains("Found 1 matching table").contains("Note ID: n1").contains("预算");
    }

    @Test
    void searchTablesReturnsNoMatchesMessage() {
        NoteMedia table = media("n1", "table", "| 项目 | 金额 |", null);
        when(mediaRepository.findByUserId("user-1"))
                .thenReturn(List.of(table));

        String result = tool.searchTables("不存在");

        assertThat(result).contains("No tables found");
    }

    @Test
    void searchTablesFiltersNonTableMedia() {
        NoteMedia image = media("n1", "image", "| 预算 | 100 |", null);
        when(mediaRepository.findByUserId("user-1"))
                .thenReturn(List.of(image));

        String result = tool.searchTables("预算");

        assertThat(result).contains("No tables found");
    }

    @Test
    void searchTablesIsCaseInsensitive() {
        NoteMedia table = media("n1", "table", "| Project | Budget |", null);
        when(mediaRepository.findByUserId("user-1"))
                .thenReturn(List.of(table));

        String result = tool.searchTables("budget");

        assertThat(result).contains("Found 1 matching table").contains("Budget");
    }

    @Test
    void searchTablesLimitsResultsToFive() {
        List<NoteMedia> media = IntStream.rangeClosed(1, 6)
                .mapToObj(i -> media("n" + i, "table", "| keyword | " + i + " |", null))
                .toList();
        when(mediaRepository.findByUserId("user-1")).thenReturn(media);

        String result = tool.searchTables("keyword");

        assertThat(result).contains("Found 5 matching table");
        assertThat(result).contains("Note ID: n1").contains("Note ID: n5");
        assertThat(result).doesNotContain("Note ID: n6");
    }

    private static NoteMedia media(String noteId, String mediaType, String markdown, String json) {
        NoteMedia media = mock(NoteMedia.class);
        when(media.getNoteId()).thenReturn(noteId);
        when(media.getMediaType()).thenReturn(mediaType);
        when(media.getTableMarkdown()).thenReturn(markdown);
        when(media.getTableJson()).thenReturn(json);
        return media;
    }
}
