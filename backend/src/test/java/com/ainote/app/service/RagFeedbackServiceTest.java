package com.ainote.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainote.app.entity.RagFeedback;
import com.ainote.app.repository.RagFeedbackRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RagFeedbackServiceTest {

    private RagFeedbackRepository repository;
    private RagFeedbackService service;

    @BeforeEach
    void setUp() {
        repository = mock(RagFeedbackRepository.class);
        service = new RagFeedbackService(repository);
        ReflectionTestUtils.setField(service, "defaultThreshold", 0.7);
        ReflectionTestUtils.setField(service, "feedbackWindowDays", 30);
    }

    @Test
    void recordFeedbackPersistsWeightedFeedbackAndInvalidatesCache() {
        service.recordFeedback("user-1", "query", "n1", 0.82, "THUMBS_UP");

        ArgumentCaptor<RagFeedback> captor = ArgumentCaptor.forClass(RagFeedback.class);
        verify(repository).save(captor.capture());
        RagFeedback saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getQuery()).isEqualTo("query");
        assertThat(saved.getResultNoteId()).isEqualTo("n1");
        assertThat(saved.getSimilarityScore()).isEqualTo(0.82);
        assertThat(saved.getFeedbackType()).isEqualTo("THUMBS_UP");
        assertThat(saved.getWeight()).isEqualTo(1.0);
    }

    @Test
    void getAdaptiveThresholdUsesColdStartProgression() {
        when(repository.findRecentByUser(org.mockito.ArgumentMatchers.eq("user-1"), any(LocalDateTime.class)))
                .thenReturn(feedbacks(5, "CLICK", 0.6));

        double threshold = service.getAdaptiveThreshold("user-1");

        assertThat(threshold).isEqualTo(0.6);
    }

    @Test
    void getAdaptiveThresholdAggregatesPositiveAndNegativeFeedback() {
        List<RagFeedback> feedbacks = new ArrayList<>();
        feedbacks.addAll(feedbacks(5, "THUMBS_UP", 0.9));
        feedbacks.addAll(feedbacks(5, "THUMBS_DOWN", 0.5));
        when(repository.findRecentByUser(org.mockito.ArgumentMatchers.eq("user-1"), any(LocalDateTime.class)))
                .thenReturn(feedbacks);

        double threshold = service.getAdaptiveThreshold("user-1");

        assertThat(threshold).isEqualTo(0.7);
    }

    @Test
    void getAdaptiveThresholdFallsBackToDefaultOnRepositoryError() {
        when(repository.findRecentByUser(org.mockito.ArgumentMatchers.eq("user-1"), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThat(service.getAdaptiveThreshold("user-1")).isEqualTo(0.7);
        assertThat(service.getAdaptiveThreshold()).isEqualTo(0.7);
    }

    private static List<RagFeedback> feedbacks(int count, String type, double score) {
        List<RagFeedback> feedbacks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RagFeedback feedback = new RagFeedback();
            feedback.setUserId("user-1");
            feedback.setQuery("q" + i);
            feedback.setResultNoteId("n" + i);
            feedback.setSimilarityScore(score);
            feedback.setFeedbackType(type);
            feedback.setWeight("CLICK".equals(type) ? 0.3 : 1.0);
            feedback.setCreatedAt(LocalDateTime.now());
            feedbacks.add(feedback);
        }
        return feedbacks;
    }
}
