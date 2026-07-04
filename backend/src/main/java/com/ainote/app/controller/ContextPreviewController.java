package com.ainote.app.controller;

import com.ainote.app.model.context.ContextPreviewRequest;
import com.ainote.app.model.context.ContextPreviewResponse;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.ContextPreviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/context-preview")
@Tag(name = "Context Preview", description = "Preview the assembled context that would be sent to the model")
@SecurityRequirement(name = "Bearer Authentication")
public class ContextPreviewController {

    private final ContextPreviewService contextPreviewService;
    private final SecurityUtils securityUtils;

    public ContextPreviewController(ContextPreviewService contextPreviewService,
                                    SecurityUtils securityUtils) {
        this.contextPreviewService = contextPreviewService;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    public ContextPreviewResponse preview(@Valid @RequestBody ContextPreviewRequest request) {
        return contextPreviewService.preview(securityUtils.getCurrentUserId(), request);
    }
}
