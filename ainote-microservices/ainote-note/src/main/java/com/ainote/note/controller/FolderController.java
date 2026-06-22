package com.ainote.note.controller;

import com.ainote.note.model.FolderModel;
import com.ainote.note.service.FolderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public List<FolderModel> listAll() {
        return folderService.listAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderModel> getById(@PathVariable String id) {
        return folderService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public FolderModel create(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String parentId = request.get("parentId");
        return folderService.create(name, parentId);
    }

    @PutMapping("/{id}")
    public FolderModel update(@PathVariable String id, @RequestBody Map<String, String> request) {
        String name = request.get("name");
        String parentId = request.get("parentId");
        return folderService.update(id, name, parentId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        folderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/root")
    public List<FolderModel> getRootFolders() {
        return folderService.getRootFolders();
    }

    @GetMapping("/{id}/children")
    public List<FolderModel> getChildren(@PathVariable String id) {
        return folderService.getChildren(id);
    }
}
