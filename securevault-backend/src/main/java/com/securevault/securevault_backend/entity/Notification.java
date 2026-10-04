package com.securevault.securevault_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_user", columnList = "username"),
        @Index(name = "idx_notif_read", columnList = "is_read")
})
public class Notification {

    public enum NotificationType {
        LOGIN_ALERT,
        SECURITY_NOTIFICATION,
        SHARING_NOTIFICATION,
        PASSWORD_EXPIRATION_ALERT,
        EMAIL_NOTIFICATION,
        PUSH_NOTIFICATION,
        RISK_ALERT
    }

    public enum Channel {
        IN_APP,
        EMAIL,
        PUSH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel = Channel.IN_APP;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {}

    public Notification(String username, NotificationType type, Channel channel, String title, String message) {
        this.username = username;
        this.type = type;
        this.channel = channel;
        this.title = title;
        this.message = message;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
