package com.ainote.app.service;

import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.User;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FolderService 单元测试")
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private KnowledgeGraphService knowledgeGraphService;

    private FolderService folderService;

    private User testUser;
    private Folder testFolder;

    @BeforeEach
    void setUp() {
        folderService = new FolderService(folderRepository, noteRepository, securityUtils, knowledgeGraphService);

        testUser = new User();
        testUser.setId("user-123");
        testUser.setEmail("test@example.com");

        testFolder = new Folder();
        testFolder.setId("folder-456");
        testFolder.setName("Test Folder");
        testFolder.setParentId(null);
        testFolder.setUser(testUser);
        testFolder.setCreatedAt(LocalDateTime.now());
        testFolder.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("listAll 应返回当前用户的所有文件夹")
    void shouldListAllFolders() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByUserId("user-123")).thenReturn(List.of(testFolder));

        List<com.ainote.app.model.Folder> result = folderService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("folder-456");
        assertThat(result.get(0).getName()).isEqualTo("Test Folder");
    }

    @Test
    @DisplayName("getById 应返回文件夹")
    void shouldGetFolderById() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByIdAndUserId("folder-456", "user-123")).thenReturn(Optional.of(testFolder));

        Optional<com.ainote.app.model.Folder> result = folderService.getById("folder-456");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Folder");
    }

    @Test
    @DisplayName("getById 不存在时应返回空")
    void shouldReturnEmptyWhenFolderNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByIdAndUserId("nonexistent", "user-123")).thenReturn(Optional.empty());

        Optional<com.ainote.app.model.Folder> result = folderService.getById("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("create 应创建新文件夹")
    void shouldCreateFolder() {
        when(securityUtils.getCurrentUser()).thenReturn(testUser);
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder folder = invocation.getArgument(0);
            folder.setCreatedAt(LocalDateTime.now());
            folder.setUpdatedAt(LocalDateTime.now());
            return folder;
        });

        com.ainote.app.model.Folder result = folderService.create("New Folder", null, "#b8452e");

        assertThat(result.getName()).isEqualTo("New Folder");
        assertThat(result.getParentId()).isNull();
        assertThat(result.getColor()).isEqualTo("#b8452e");

        ArgumentCaptor<Folder> captor = ArgumentCaptor.forClass(Folder.class);
        verify(folderRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(testUser);
        assertThat(captor.getValue().getColor()).isEqualTo("#b8452e");
    }

    @Test
    @DisplayName("create 应支持子文件夹")
    void shouldCreateSubfolder() {
        when(securityUtils.getCurrentUser()).thenReturn(testUser);
        when(folderRepository.findByIdAndUserId("parent-123", "user-123")).thenReturn(Optional.of(testFolder));
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder folder = invocation.getArgument(0);
            folder.setCreatedAt(LocalDateTime.now());
            folder.setUpdatedAt(LocalDateTime.now());
            return folder;
        });

        com.ainote.app.model.Folder result = folderService.create("Subfolder", "parent-123");

        assertThat(result.getName()).isEqualTo("Subfolder");
        assertThat(result.getParentId()).isEqualTo("parent-123");
    }

    @Test
    @DisplayName("update 应更新文件夹")
    void shouldUpdateFolder() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByIdAndUserId("folder-456", "user-123")).thenReturn(Optional.of(testFolder));
        when(folderRepository.findByIdAndUserId("new-parent", "user-123")).thenReturn(Optional.of(testFolder));
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.ainote.app.model.Folder result = folderService.update("folder-456", "Updated Name", "new-parent", "#6b7a5a");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getParentId()).isEqualTo("new-parent");
        assertThat(result.getColor()).isEqualTo("#6b7a5a");
    }

    @Test
    @DisplayName("update 不存在时应返回 null")
    void shouldReturnNullWhenUpdateNonexistent() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByIdAndUserId("nonexistent", "user-123")).thenReturn(Optional.empty());

        com.ainote.app.model.Folder result = folderService.update("nonexistent", "Name", null);

        assertThat(result).isNull();
        verify(folderRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete 应移除笔记关联并删除文件夹")
    void shouldDeleteFolderAndUnlinkNotes() {
        Note note = new Note();
        note.setId("note-1");
        note.setFolder(testFolder);

        Folder childFolder = new Folder();
        childFolder.setId("child-folder");
        childFolder.setParentId("folder-456");
        childFolder.setCreatedAt(LocalDateTime.now());
        childFolder.setUpdatedAt(LocalDateTime.now());

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByIdAndUserId("folder-456", "user-123")).thenReturn(Optional.of(testFolder));
        when(noteRepository.findByFolderIdAndUserId("folder-456", "user-123")).thenReturn(List.of(note));
        when(folderRepository.findByParentIdAndUserId("folder-456", "user-123")).thenReturn(List.of(childFolder));

        folderService.delete("folder-456");

        // 验证笔记被移出文件夹
        verify(noteRepository).save(argThat(n -> n.getFolder() == null));
        // 验证子文件夹被移到根目录
        verify(folderRepository).save(argThat(f -> f.getParentId() == null));
        // 验证文件夹被删除
        verify(folderRepository).deleteById("folder-456");
        verify(noteRepository, never()).findByFolderId("folder-456");
        verify(folderRepository, never()).findByParentId("folder-456");
    }

    @Test
    @DisplayName("delete 无笔记时应直接删除")
    void shouldDeleteFolderWithoutNotes() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByIdAndUserId("folder-456", "user-123")).thenReturn(Optional.of(testFolder));
        when(noteRepository.findByFolderIdAndUserId("folder-456", "user-123")).thenReturn(List.of());
        when(folderRepository.findByParentIdAndUserId("folder-456", "user-123")).thenReturn(List.of());

        folderService.delete("folder-456");

        verify(noteRepository, never()).save(any());
        verify(folderRepository).deleteById("folder-456");
    }

    @Test
    @DisplayName("getRootFolders 应返回根文件夹")
    void shouldGetRootFolders() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByUserIdAndParentIdIsNull("user-123")).thenReturn(List.of(testFolder));

        List<com.ainote.app.model.Folder> result = folderService.getRootFolders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Folder");
    }

    @Test
    @DisplayName("getChildren 应返回子文件夹")
    void shouldGetChildFolders() {
        Folder childFolder = new Folder();
        childFolder.setId("child-1");
        childFolder.setName("Child Folder");
        childFolder.setParentId("folder-456");
        childFolder.setCreatedAt(LocalDateTime.now());
        childFolder.setUpdatedAt(LocalDateTime.now());

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByParentIdAndUserId("folder-456", "user-123")).thenReturn(List.of(childFolder));

        List<com.ainote.app.model.Folder> result = folderService.getChildren("folder-456");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Child Folder");
        assertThat(result.get(0).getParentId()).isEqualTo("folder-456");
    }

    @Test
    @DisplayName("getChildren 无子文件夹时返回空列表")
    void shouldReturnEmptyListWhenNoChildren() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByParentIdAndUserId("folder-456", "user-123")).thenReturn(List.of());

        List<com.ainote.app.model.Folder> result = folderService.getChildren("folder-456");

        assertThat(result).isEmpty();
    }
}
