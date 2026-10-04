package com.securevault.securevault_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A security alert raised by the monitoring engine when suspicious
 * activity patterns are detected (brute-force, new-location login, etc.)
 */
@Entity
@Table(name = "security_alerts", indexes = {
        @Index(name = "idx_alert_user", columnList = "username"),
        @Index(name = "idx_alert_severity", columnList = "severity"),
        @Index(name = "idx_alert_resolved", columnList = "resolved")
})
public class SecurityAlert {

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum AlertType {
        BRUTE_FORCE_ATTACK,
        MULTIPLE_FAILED_LOGINS,
        UNUSUAL_LOCATION_LOGIN,
        ACCOUNT_LOCKOUT,
        RAPID_CREDENTIAL_ACCESS,
        OFF_HOURS_ACCESS,
        CONCURRENT_SESSIONS,
        PASSWORD_SPRAY_DETECTED,
        UNRECOGNIZED_DEVICE_LOGIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity = Severity.MEDIUM;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column
    private String ipAddress;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column
    private String resolvedBy;

    @Column
    private LocalDateTime resolvedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── constructors ──────────────────────────────────────────────────────────

    public SecurityAlert() {}

    public SecurityAlert(String username, AlertType alertType,
                         Severity severity, String message, String ipAddress) {
        this.username = username;
        this.alertType = alertType;
        this.severity = severity;
        this.message = message;
        this.ipAddress = ipAddress;
    }

    // ── getters & setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public AlertType getAlertType() { return alertType; }
    public void setAlertType(AlertType alertType) { this.alertType = alertType; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
