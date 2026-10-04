package com.securevault.securevault_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks every login attempt (successful or failed) per IP address.
 * Feeds into the brute-force / suspicious-login detection logic.
 */
@Entity
@Table(name = "login_attempts", indexes = {
        @Index(name = "idx_login_ip", columnList = "ip_address"),
        @Index(name = "idx_login_user", columnList = "username"),
        @Index(name = "idx_login_time", columnList = "attempt_time")
})
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column
    private String userAgent;

    @Column(nullable = false)
    private boolean success;

    /** Failure reason when success = false */
    @Column
    private String failureReason;

    @Column(name = "attempt_time", nullable = false)
    private LocalDateTime attemptTime = LocalDateTime.now();

    /** Country/city derived from the IP (kept simple; no GeoIP lib needed) */
    @Column
    private String geoLocation;

    // ── constructors ──────────────────────────────────────────────────────────

    public LoginAttempt() {}

    public LoginAttempt(String username, String ipAddress, String userAgent,
                        boolean success, String failureReason) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.success = success;
        this.failureReason = failureReason;
    }

    // ── getters & setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public LocalDateTime getAttemptTime() { return attemptTime; }
    public void setAttemptTime(LocalDateTime attemptTime) { this.attemptTime = attemptTime; }

    public String getGeoLocation() { return geoLocation; }
    public void setGeoLocation(String geoLocation) { this.geoLocation = geoLocation; }
}
