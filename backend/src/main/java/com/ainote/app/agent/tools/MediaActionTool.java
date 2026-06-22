package com.ainote.app.agent.tools;

import com.ainote.app.entity.NoteMedia;
import com.ainote.app.repository.NoteMediaRepository;
import com.ainote.app.security.SecurityUtils;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MediaActionTool {

    private static final Logger log = LoggerFactory.getLogger(MediaActionTool.class);

    private final NoteMediaRepository mediaRepository;
    private final SecurityUtils securityUtils;

    public MediaActionTool(NoteMediaRepository mediaRepository, SecurityUtils securityUtils) {
        this.mediaRepository = mediaRepository;
        this.securityUtils = securityUtils;
    }

    @Tool("Search tables in user's notes by keyword. Returns matching table data in markdown format. Use this when user asks about data in tables or spreadsheets within their notes.")
    public String searchTables(
            @P("Search keyword to find in table content") String query) {
        String userId = securityUtils.getCurrentUserId();
        log.info("Searching tables for user {} with query: {}", userId, query);

        List<NoteMedia> tables = mediaRepository.findByUserId(userId).stream()
                .filter(m -> "table".equals(m.getMediaType()))
                .filter(m -> {
                    String searchable = (m.getTableMarkdown() != null ? m.getTableMarkdown() : "")
                            + (m.getTableJson() != null ? m.getTableJson() : "");
                    return searchable.toLowerCase().contains(query.toLowerCase());
                })
                .limit(5)
                .collect(Collectors.toList());

        if (tables.isEmpty()) {
            return "No tables found matching: " + query;
        }

        StringBuilder sb = new StringBuilder("Found " + tables.size() + " matching table(s):\n\n");
        for (NoteMedia t : tables) {
            sb.append("Note ID: ").append(t.getNoteId()).append("\n");
            sb.append(t.getTableMarkdown() != null ? t.getTableMarkdown() : "(no content)").append("\n\n");
        }
        return sb.toString();
    }
}
