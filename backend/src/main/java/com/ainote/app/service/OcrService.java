package com.ainote.app.service;

import com.ainote.app.entity.NoteMedia;
import com.ainote.app.repository.NoteMediaRepository;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final NoteMediaRepository mediaRepository;
    private final LangChain4jRagService ragService;

    @Value("${app.ocr.tessdata-path:#{null}}")
    private String tessdataPath;

    @Value("${app.ocr.language:chi_sim+eng}")
    private String ocrLanguage;

    public OcrService(NoteMediaRepository mediaRepository, LangChain4jRagService ragService) {
        this.mediaRepository = mediaRepository;
        this.ragService = ragService;
    }

    @Async("taskExecutor")
    public CompletableFuture<String> extractText(String mediaId) {
        log.info("Starting OCR for media: {}", mediaId);
        try {
            NoteMedia media = mediaRepository.findById(mediaId)
                    .orElseThrow(() -> new RuntimeException("Media not found: " + mediaId));

            byte[] imageBytes = Base64.getDecoder().decode(media.getDataBase64());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

            if (image == null) {
                log.warn("Cannot read image for media: {}", mediaId);
                return CompletableFuture.completedFuture("");
            }

            ITesseract tesseract = new Tesseract();
            if (tessdataPath != null && !tessdataPath.isEmpty()) {
                tesseract.setDatapath(tessdataPath);
            }
            tesseract.setLanguage(ocrLanguage);

            String result = tesseract.doOCR(image);
            log.info("OCR completed for media {}: {} chars extracted", mediaId, result.length());

            media.setOcrText(result.trim());
            mediaRepository.save(media);

            ragService.generateEmbeddingAsync(media.getNoteId());

            return CompletableFuture.completedFuture(result.trim());

        } catch (TesseractException e) {
            log.error("OCR failed for media {}: {}", mediaId, e.getMessage());
            return CompletableFuture.completedFuture("");
        } catch (Exception e) {
            log.error("Error during OCR for media {}: {}", mediaId, e.getMessage());
            return CompletableFuture.completedFuture("");
        }
    }
}
