package com.ainote.app.controller;

import com.ainote.app.entity.Notification;
import com.ainote.app.repository.NotificationRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerOwnershipTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SecurityUtils securityUtils;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
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
}
