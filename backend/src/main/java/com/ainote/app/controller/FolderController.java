package com.ainote.app.controller;

import com.ainote.app.model.Folder;
import com.ainote.app.model.FolderRequest;
import com.ainote.app.service.FolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@Tag(name = "文件夹管理", description = "文件夹的创建、重命名、删除、嵌套等功能")
@SecurityRequirement(name = "Bearer Authentication")
public class FolderController {
    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public List<Folder> listAll() {
        return folderService.listAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Folder> getById(@PathVariable String id) {
        return folderService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Folder create(@Valid @RequestBody FolderRequest request) {
        return folderService.create(request.getName(), request.getParentId(), request.getColor());
    }

    @PutMapping("/{id}")
    public Folder update(@PathVariable String id, @Valid @RequestBody FolderRequest request) {
        return folderService.update(id, request.getName(), request.getParentId(), request.getColor());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        folderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/root")
    public List<Folder> getRootFolders() {
        return folderService.getRootFolders();
    }

    @GetMapping("/{id}/children")
    public List<Folder> getChildren(@PathVariable String id) {
        return folderService.getChildren(id);
    }
}
