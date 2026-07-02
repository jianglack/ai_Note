package com.ainote.app.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.model.Folder;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.FolderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FolderActionToolTest {

    private FolderService folderService;
    private FolderRepository folderRepository;
    private SecurityUtils securityUtils;
    private ToolExecutionPipeline pipeline;
    private FolderActionTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        folderService = mock(FolderService.class);
        folderRepository = mock(FolderRepository.class);
        securityUtils = mock(SecurityUtils.class);
        pipeline = mock(ToolExecutionPipeline.class);
        tool = new FolderActionTool(folderService, folderRepository, securityUtils, pipeline, new ObjectMapper());

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(pipeline.execute(anyString(), anyString(), anyString(), any(JsonNode.class), any(Supplier.class)))
                .thenAnswer(inv -> inv.<Supplier<String>>getArgument(4).get());
    }

    @Test
    void createPersistsWhenNoDuplicate() {
        when(folderRepository.findByUserIdAndName("user-1", "工作")).thenReturn(List.of());
        Folder created = folder("f1", "工作");
        when(folderService.create("工作", null)).thenReturn(created);

        String result = tool.folderAction("create", "{\"name\":\"工作\"}");

        assertThat(result).contains("已在根目录创建文件夹").contains("工作");
        verify(folderService).create("工作", null);
    }

    @Test
    void createRejectsMissingName() {
        String result = tool.folderAction("create", "{}");

        assertThat(result).contains("缺少 name");
        verify(folderService, never()).create(anyString(), any());
    }

    @Test
    void createIsIdempotentForSameName() {
        com.ainote.app.entity.Folder existing = mock(com.ainote.app.entity.Folder.class);
        when(existing.getName()).thenReturn("工作");
        when(existing.getId()).thenReturn("f1");
        when(folderRepository.findByUserIdAndName("user-1", "工作")).thenReturn(List.of(existing));

        String result = tool.folderAction("create", "{\"name\":\"工作\"}");

        assertThat(result).contains("同名文件夹已存在").contains("工作");
        verify(folderService, never()).create(anyString(), any());
    }

    @Test
    void renameInvokesUpdate() {
        when(folderService.update("f1", "资料", null)).thenReturn(folder("f1", "资料"));

        String result = tool.folderAction("rename", "{\"folderId\":\"f1\",\"name\":\"资料\"}");

        assertThat(result).contains("已将文件夹重命名").contains("资料");
        verify(folderService).update("f1", "资料", null);
    }

    @Test
    void deleteReturnsPendingAction() {
        String result = tool.folderAction("delete", "{\"folderId\":\"f1\"}");

        assertThat(result).contains("PENDING_ACTION").contains("DELETE_FOLDER");
        verify(folderService, never()).delete(anyString());
    }

    @Test
    void confirmDeleteInvokesDelete() {
        String result = tool.folderAction("confirmDelete", "{\"folderId\":\"f1\"}");

        assertThat(result).contains("文件夹已删除");
        verify(folderService).delete("f1");
    }

    @Test
    void listEmptyReturnsEmptyMessage() {
        when(folderService.listAll()).thenReturn(List.of());

        String result = tool.folderAction("list", "{}");

        assertThat(result).contains("目前没有任何文件夹");
    }

    @Test
    void unknownActionReturnsUnknownMessage() {
        assertThat(tool.folderAction("missing", "{}")).contains("未知操作");
    }

    private static Folder folder(String id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        return folder;
    }
}
