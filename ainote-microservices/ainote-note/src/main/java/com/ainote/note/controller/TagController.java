package com.ainote.note.controller;

import com.ainote.note.model.TagAssignRequest;
import com.ainote.note.model.TagModel;
import com.ainote.note.model.TagRequest;
import com.ainote.note.service.TagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PostMapping
    public TagModel create(@RequestBody TagRequest request) {
        return tagService.create(request.getName());
    }

    @GetMapping
    public List<TagModel> list() {
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
