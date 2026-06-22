package com.ainote.app.controller;

import com.ainote.app.service.LinkPreviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/link-preview")
public class LinkPreviewController {

    private final LinkPreviewService linkPreviewService;

    public LinkPreviewController(LinkPreviewService linkPreviewService) {
        this.linkPreviewService = linkPreviewService;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getPreview(@RequestParam String url) {
        Map<String, String> preview = linkPreviewService.fetchPreview(url);
        return ResponseEntity.ok(preview);
    }
}
