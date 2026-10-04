package com.securevault.securevault_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores every auditable action performed in the vault.
 * Used for audit trail reports and suspicious-activity detection.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user", columnList = "username"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
public class AuditLog {

    public enum Action {
        LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT,
        REGISTER,
        PASSWORD_RESET_REQUEST, PASSWORD_RESET_SUCCESS,
        CREDENTIAL_CREATE, CREDENTIAL_UPDATE, CREDENTIAL_DELETE, CREDENTIAL_VIEW,
        CREDENTIAL_SHARE, CREDENTIAL_SHARE_REVOKE,
        MFA_ENABLED, MFA_DISABLED,
        ACCOUNT_LOCKED, ACCOUNT_UNLOCKED,
        SUSPICIOUS_ACTIVITY_DETECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    /** Human-readable summary of the event */
    @Column(columnDefinition = "TEXT")
    private String details;

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    /** Whether the action was deemed suspicious */
    @Column(nullable = false)
    private boolean suspicious = false;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    // ── constructors ──────────────────────────────────────────────────────────

    public AuditLog() {}

    public AuditLog(String username, Action action, String details,
                    String ipAddress, String userAgent) {
        this.username = username;
        this.action = action;
        this.details = details;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    // ── getters & setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public boolean isSuspicious() { return suspicious; }
    public void setSuspicious(boolean suspicious) { this.suspicious = suspicious; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
