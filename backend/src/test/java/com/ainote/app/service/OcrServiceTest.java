package com.ainote.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainote.app.entity.NoteMedia;
import com.ainote.app.repository.NoteMediaRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OcrServiceTest {

    private NoteMediaRepository mediaRepository;
    private LangChain4jRagService ragService;
    private ITesseract tesseract;
    private TestableOcrService service;

    @BeforeEach
    void setUp() {
        mediaRepository = mock(NoteMediaRepository.class);
        ragService = mock(LangChain4jRagService.class);
        tesseract = mock(ITesseract.class);
        service = new TestableOcrService(mediaRepository, ragService, tesseract);
    }

    @Test
    void extractTextSavesTrimmedTextAndRefreshesEmbedding() throws Exception {
        NoteMedia media = media("m1", "n1", pngBase64());
        when(mediaRepository.findById("m1")).thenReturn(Optional.of(media));
        when(tesseract.doOCR(org.mockito.ArgumentMatchers.any(BufferedImage.class))).thenReturn(" extracted text \n");

        String result = service.extractText("m1").join();

        assertThat(result).isEqualTo("extracted text");
        assertThat(media.getOcrText()).isEqualTo("extracted text");
        verify(mediaRepository).save(media);
        verify(ragService).generateEmbeddingAsync("n1");
    }

    @Test
    void extractTextReturnsEmptyForMissingMedia() {
        when(mediaRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(service.extractText("missing").join()).isEmpty();
        verify(mediaRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void extractTextReturnsEmptyForUnreadableMedia() {
        when(mediaRepository.findById("m1")).thenReturn(Optional.of(media("m1", "n1", "bm90LWltYWdl")));

        assertThat(service.extractText("m1").join()).isEmpty();
        verify(mediaRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void extractTextReturnsEmptyWhenTesseractFails() throws Exception {
        when(mediaRepository.findById("m1")).thenReturn(Optional.of(media("m1", "n1", pngBase64())));
        when(tesseract.doOCR(org.mockito.ArgumentMatchers.any(BufferedImage.class)))
                .thenThrow(new TesseractException("ocr down"));

        assertThat(service.extractText("m1").join()).isEmpty();
        verify(mediaRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static NoteMedia media(String id, String noteId, String dataBase64) {
        NoteMedia media = new NoteMedia();
        media.setId(id);
        media.setNoteId(noteId);
        media.setDataBase64(dataBase64);
        return media;
    }

    private static String pngBase64() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static class TestableOcrService extends OcrService {
        private final ITesseract tesseract;

        TestableOcrService(NoteMediaRepository mediaRepository, LangChain4jRagService ragService, ITesseract tesseract) {
            super(mediaRepository, ragService);
            this.tesseract = tesseract;
        }

        @Override
        protected ITesseract createTesseract() {
            return tesseract;
        }
    }
}
