package com.ainote.common.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NoteDTO {
    private Long id;
    private String title;
    private String content;
    private Long userId;
    private Long folderId;
    private String folderName;
    private List<String> tags;
    private boolean pinned;
    private boolean starred;
    private boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
