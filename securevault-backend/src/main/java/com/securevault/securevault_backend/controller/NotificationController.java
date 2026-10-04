package com.securevault.securevault_backend.controller;

import com.securevault.securevault_backend.entity.Notification;
import com.securevault.securevault_backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // GET /api/notifications - Get user notifications
    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications() {
        return ResponseEntity.ok(notificationService.getNotificationsForUser(currentUser()));
    }

    // GET /api/notifications/unread-count - Get count of unread notifications
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        long count = notificationService.getUnreadCount(currentUser());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    // PATCH /api/notifications/{id}/read - Mark single notification as read
    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        try {
            Notification updated = notificationService.markAsRead(id, currentUser());
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // PATCH /api/notifications/read-all - Mark all as read
    @PatchMapping("/read-all")
    public ResponseEntity<?> markAllAsRead() {
        notificationService.markAllAsRead(currentUser());
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    // POST /api/notifications/test - Trigger test notification
    @PostMapping("/test")
    public ResponseEntity<?> sendTestNotification(@RequestBody Map<String, String> body) {
        String channel = body.getOrDefault("channel", "IN_APP");
        Map<String, Object> result = notificationService.sendTestNotification(currentUser(), channel);
        return ResponseEntity.ok(result);
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
