package com.ainote.app.controller;

import com.ainote.app.entity.Notification;
import com.ainote.app.model.NotificationResponse;
import com.ainote.app.repository.NotificationRepository;
import com.ainote.app.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final SecurityUtils securityUtils;

    public NotificationController(NotificationRepository notificationRepository,
                                   SecurityUtils securityUtils) {
        this.notificationRepository = notificationRepository;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public List<NotificationResponse> getUnread() {
        String userId = securityUtils.getCurrentUserId();
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/count")
    public Map<String, Long> getUnreadCount() {
        String userId = securityUtils.getCurrentUserId();
        return Map.of("count", notificationRepository.countByUserIdAndIsReadFalse(userId));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        String userId = securityUtils.getCurrentUserId();
        notificationRepository.findByIdAndUserId(id, userId).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
        });
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        String userId = securityUtils.getCurrentUserId();
        List<Notification> unread = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok().build();
    }
}
