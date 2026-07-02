package com.ainote.app.controller;

import com.ainote.app.entity.Notification;
import com.ainote.app.repository.NotificationRepository;
import com.ainote.app.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
class NotificationControllerOwnershipTest {
    private NotificationRepository notificationRepository;    private SecurityUtils securityUtils;
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        securityUtils = mock(SecurityUtils.class);
        controller = new NotificationController(notificationRepository, securityUtils);
    }

    @Test
    void markAsRead_usesCurrentUserOwnershipLookup() {
        Notification notification = new Notification();
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(notificationRepository.findByIdAndUserId(10L, "user-1")).thenReturn(Optional.of(notification));

        controller.markAsRead(10L);

        verify(notificationRepository).findByIdAndUserId(10L, "user-1");
        verify(notificationRepository, never()).findById(10L);
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_doesNotSaveNotificationNotOwnedByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(notificationRepository.findByIdAndUserId(10L, "user-1")).thenReturn(Optional.empty());

        controller.markAsRead(10L);

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notificationRepository, never()).findById(10L);
    }

    @Test
    void getUnread_returnsDtoWithoutUserId() throws Exception {
        Notification notification = new Notification();
        notification.setId(10L);
        notification.setUserId("user-secret");
        notification.setType("reminder");
        notification.setTitle("title");
        notification.setContent("content");
        notification.setSource("schedule");
        notification.setRelatedId("schedule-1");
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.of(2026, 6, 23, 10, 0));

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(notification));

        List<?> response = controller.getUnread();
        String json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(response);

        assertThat(json).contains("\"id\":10");
        assertThat(json).contains("\"relatedId\":\"schedule-1\"");
        assertThat(json).doesNotContain("userId");
        assertThat(json).doesNotContain("user-secret");
    }
}
