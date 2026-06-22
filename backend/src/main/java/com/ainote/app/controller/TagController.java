package com.ainote.app.controller;

import com.ainote.app.model.Tag;
import com.ainote.app.model.TagAssignRequest;
import com.ainote.app.model.TagRequest;
import com.ainote.app.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理", description = "标签的创建、查询、分配等功能")
@SecurityRequirement(name = "Bearer Authentication")
public class TagController {
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PostMapping
    public Tag create(@Valid @RequestBody TagRequest request) {
        return tagService.create(request.getName());
    }

    @GetMapping
    public List<Tag> list() {
        return tagService.listAll();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assign")
    public ResponseEntity<Void> assign(@RequestBody TagAssignRequest request) {
        tagService.assign(request.getNoteId(), request.getTagIds());
        return ResponseEntity.noContent().build();
    }
}
