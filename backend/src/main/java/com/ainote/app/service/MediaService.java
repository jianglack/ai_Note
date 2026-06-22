package com.ainote.app.service;

import com.ainote.app.entity.NoteMedia;
import com.ainote.app.repository.NoteMediaRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "image/png", "image/jpeg", "image/gif", "image/webp"
    );

    private final NoteMediaRepository mediaRepository;
    private final NoteRepository noteRepository;
    private final SecurityUtils securityUtils;
    private final LangChain4jRagService ragService;
    private final OcrService ocrService;

    public MediaService(NoteMediaRepository mediaRepository,
                        NoteRepository noteRepository,
                        SecurityUtils securityUtils,
                        LangChain4jRagService ragService,
                        OcrService ocrService) {
        this.mediaRepository = mediaRepository;
        this.noteRepository = noteRepository;
        this.securityUtils = securityUtils;
        this.ragService = ragService;
        this.ocrService = ocrService;
    }

    public NoteMedia uploadImage(String noteId, MultipartFile file, String alt) throws IOException {
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Image size exceeds 5MB limit");
        }
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("Unsupported image type: " + mimeType);
        }

        String base64 = Base64.getEncoder().encodeToString(file.getBytes());
        String userId = securityUtils.getCurrentUserId();
        requireOwnedNote(noteId, userId);

        NoteMedia media = new NoteMedia();
        media.setNoteId(noteId);
        media.setUserId(userId);
        media.setMediaType("image");
        media.setFilename(file.getOriginalFilename());
        media.setMimeType(mimeType);
        media.setDataBase64(base64);
        media.setFileSizeBytes((int) file.getSize());
        media.setMetadata("{\"alt\":\"" + (alt != null ? alt.replace("\"", "\\\"") : "") + "\"}");

        NoteMedia saved = mediaRepository.save(media);
        log.info("Image uploaded: id={}, noteId={}, size={}", saved.getId(), noteId, file.getSize());

        // Trigger async OCR
        ocrService.extractText(saved.getId());

        return saved;
    }

    public NoteMedia saveTable(String noteId, String tableHtml, String tableJson, String caption) {
        String userId = securityUtils.getCurrentUserId();
        requireOwnedNote(noteId, userId);
        String tableMarkdown = htmlTableToMarkdown(tableHtml);

        NoteMedia media = new NoteMedia();
        media.setNoteId(noteId);
        media.setUserId(userId);
        media.setMediaType("table");
        media.setDataBase64(Base64.getEncoder().encodeToString(
            (tableHtml != null ? tableHtml : "").getBytes()));
        media.setTableJson(tableJson);
        media.setTableMarkdown(tableMarkdown);
        media.setMetadata("{\"caption\":\"" + (caption != null ? caption.replace("\"", "\\\"") : "") + "\"}");

        NoteMedia saved = mediaRepository.save(media);
        log.info("Table saved: id={}, noteId={}", saved.getId(), noteId);

        // Trigger re-index for RAG
        ragService.generateEmbeddingAsync(noteId);

        return saved;
    }

    public NoteMedia getById(String id) {
        String userId = securityUtils.getCurrentUserId();
        return mediaRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("Media not found: " + id));
    }

    public List<NoteMedia> getByNoteId(String noteId) {
        String userId = securityUtils.getCurrentUserId();
        return mediaRepository.findByNoteIdAndUserId(noteId, userId);
    }

    public byte[] getImageBytes(String id) {
        NoteMedia media = getById(id);
        return Base64.getDecoder().decode(media.getDataBase64());
    }

    private String htmlTableToMarkdown(String html) {
        if (html == null || html.isEmpty()) return "";
        String text = html.replaceAll("</?table[^>]*>", "")
                          .replaceAll("</?thead[^>]*>", "")
                          .replaceAll("</?tbody[^>]*>", "");

        StringBuilder md = new StringBuilder();
        String[] rows = text.split("</tr>");
        boolean firstRow = true;
        for (String row : rows) {
            if (row.trim().isEmpty()) continue;
            String[] cells = row.split("</t[hd]>");
            List<String> cellTexts = new ArrayList<>();
            for (String cell : cells) {
                String cellText = cell.replaceAll("<[^>]+>", "").trim();
                if (!cellText.isEmpty()) cellTexts.add(cellText);
            }
            if (cellTexts.isEmpty()) continue;
            md.append("| ").append(String.join(" | ", cellTexts)).append(" |\n");
            if (firstRow) {
                md.append("| ").append(cellTexts.stream().map(c -> "---")
                    .collect(Collectors.joining(" | "))).append(" |\n");
                firstRow = false;
            }
        }
        return md.toString();
    }

    private void requireOwnedNote(String noteId, String userId) {
        if (noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId).isEmpty()) {
            throw new NoSuchElementException("Note not found: " + noteId);
        }
    }
}
