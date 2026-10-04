package com.securevault.securevault_backend.controller;

import com.securevault.securevault_backend.entity.AuditLog;
import com.securevault.securevault_backend.entity.AuditLog.Action;
import com.securevault.securevault_backend.entity.Credential;
import com.securevault.securevault_backend.entity.LoginAttempt;
import com.securevault.securevault_backend.entity.SecurityAlert;
import com.securevault.securevault_backend.repository.CredentialRepository;
import com.securevault.securevault_backend.repository.UserRepository;
import com.securevault.securevault_backend.service.CredentialService;
import com.securevault.securevault_backend.service.SecurityMonitoringService;
import com.securevault.securevault_backend.util.EncryptionUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for security monitoring and analytics.
 *
 * Base path: /api/security
 */
@RestController
@RequestMapping("/api/security")
public class SecurityAnalyticsController {

    private final SecurityMonitoringService monitoringService;
    private final CredentialService         credentialService;
    private final UserRepository            userRepository;

    public SecurityAnalyticsController(SecurityMonitoringService monitoringService,
                                       CredentialService credentialService,
                                       UserRepository userRepository) {
        this.monitoringService = monitoringService;
        this.credentialService = credentialService;
        this.userRepository    = userRepository;
    }

    // ── DASHBOARD ─────────────────────────────────────────────────────────────

    /**
     * GET /api/security/dashboard
     * Returns aggregated security statistics for the analytics dashboard.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(monitoringService.getDashboardSummary());
    }

    // ── DEVICE TRACKING ───────────────────────────────────────────────────────

    /**
     * GET /api/security/devices
     * Returns device tracking summary (browsers, OS, device categories, IP history).
     */
    @GetMapping("/devices")
    public ResponseEntity<List<Map<String, Object>>> getDeviceTracking(
            @RequestParam(required = false) String username) {
        String target = (username != null && !username.isBlank()) ? username : currentUser();
        return ResponseEntity.ok(monitoringService.getDeviceTrackingSummary(target));
    }

    // ── THREAT MONITORING ─────────────────────────────────────────────────────

    /**
     * GET /api/security/threats
     * Returns real-time threat monitoring status, IOC indicators, and threat level.
     */
    @GetMapping("/threats")
    public ResponseEntity<Map<String, Object>> getThreatMonitoring() {
        return ResponseEntity.ok(monitoringService.getThreatMonitoringSummary());
    }

    // ── RISK ANALYSIS ENGINE ──────────────────────────────────────────────────

    /**
     * GET /api/security/risk-analysis
     * Returns system risk score, security grade, and user risk factor evaluations.
     */
    @GetMapping("/risk-analysis")
    public ResponseEntity<Map<String, Object>> getRiskAnalysis() {
        String username = currentUser();
        List<Credential> credentials = credentialService.getCredentialsForUser(username);
        return ResponseEntity.ok(monitoringService.getRiskAnalysis(credentials));
    }

    // ── LOGIN ANOMALY DETECTION ──────────────────────────────────────────────

    /**
     * GET /api/security/anomalies
     * Returns detected login anomalies (off-hours, failure spikes, unrecognized devices).
     */
    @GetMapping("/anomalies")
    public ResponseEntity<List<Map<String, Object>>> getLoginAnomalies() {
        return ResponseEntity.ok(monitoringService.getLoginAnomalies());
    }

    // ── AUDIT LOGS (CATEGORIZED & FILTERABLE) ─────────────────────────────────

    /**
     * GET /api/security/audit-logs
     * Filterable audit trail by category (login, vault, sharing, security, system), username, or search term.
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(monitoringService.getFilteredAuditLogs(category, username, search));
    }

    /**
     * GET /api/security/audit-logs/me
     * Returns the current user's own audit trail.
     */
    @GetMapping("/audit-logs/me")
    public ResponseEntity<List<AuditLog>> getMyAuditLogs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(monitoringService.getFilteredAuditLogs(category, currentUser(), search));
    }

    /**
     * GET /api/security/audit-logs/suspicious
     * Returns only audit events flagged as suspicious.
     */
    @GetMapping("/audit-logs/suspicious")
    public ResponseEntity<List<AuditLog>> getSuspiciousLogs() {
        return ResponseEntity.ok(monitoringService.getSuspiciousAuditLogs());
    }

    /**
     * GET /api/security/audit-logs/export
     * Exports audit logs as downloadable CSV content.
     */
    @GetMapping(value = "/audit-logs/export", produces = "text/csv")
    public ResponseEntity<String> exportAuditLogsCsv(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String search) {
        String csvContent = monitoringService.exportAuditLogsCsv(category, username, search);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"audit-log-report.csv\"")
                .body(csvContent);
    }

    /**
     * GET /api/security/audit-logs/report
     * Executive audit summary report.
     */
    @GetMapping("/audit-logs/report")
    public ResponseEntity<Map<String, Object>> getAuditSummaryReport() {
        return ResponseEntity.ok(monitoringService.getAuditSummaryReport());
    }

    // ── ALERTS ────────────────────────────────────────────────────────────────

    /**
     * GET /api/security/alerts
     * Returns all active (unresolved) security alerts.
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<SecurityAlert>> getActiveAlerts() {
        return ResponseEntity.ok(monitoringService.getActiveAlerts());
    }

    /**
     * GET /api/security/alerts/all
     * Returns all security alerts (including resolved).
     */
    @GetMapping("/alerts/all")
    public ResponseEntity<List<SecurityAlert>> getAllAlerts() {
        return ResponseEntity.ok(monitoringService.getAllAlerts());
    }

    /**
     * PATCH /api/security/alerts/{id}/resolve
     * Resolve a security alert.
     */
    @PatchMapping("/alerts/{id}/resolve")
    public ResponseEntity<?> resolveAlert(@PathVariable Long id) {
        try {
            SecurityAlert resolved = monitoringService.resolveAlert(id, currentUser());
            return ResponseEntity.ok(resolved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── LOGIN ATTEMPTS ────────────────────────────────────────────────────────

    /**
     * GET /api/security/login-attempts?hours=24
     * Returns recent login attempts.
     */
    @GetMapping("/login-attempts")
    public ResponseEntity<List<LoginAttempt>> getLoginAttempts(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(monitoringService.getRecentLoginAttempts(hours));
    }

    // ── REPORTS ───────────────────────────────────────────────────────────────

    /**
     * GET /api/security/reports/password-health
     * Returns password health report for the current user.
     */
    @GetMapping("/reports/password-health")
    public ResponseEntity<Map<String, Object>> getPasswordHealthReport() {
        String username = currentUser();
        List<Credential> credentials = credentialService.getCredentialsForUser(username);
        return ResponseEntity.ok(monitoringService.getPasswordHealthReport(username, credentials));
    }

    /**
     * GET /api/security/reports/login-activity
     * Returns login activity report for the current user.
     */
    @GetMapping("/reports/login-activity")
    public ResponseEntity<Map<String, Object>> getLoginActivityReport() {
        return ResponseEntity.ok(monitoringService.getLoginActivityReport(currentUser()));
    }

    // ── MANUAL AUDIT LOG ─────────────────────────────────────────────────────

    /**
     * POST /api/security/audit-log
     * Allows trusted internal clients to record a custom audit event.
     * Body: { "action": "CREDENTIAL_VIEW", "details": "...", "ipAddress": "..." }
     */
    @PostMapping("/audit-log")
    public ResponseEntity<AuditLog> recordAuditEvent(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String actionStr = body.getOrDefault("action", "CREDENTIAL_VIEW");
        Action action;
        try {
            action = Action.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            action = Action.CREDENTIAL_VIEW;
        }
        String ip = resolveClientIp(request);
        AuditLog log = monitoringService.recordAuditLog(
                currentUser(), action,
                body.getOrDefault("details", "Manual event"),
                ip,
                request.getHeader("User-Agent"));
        return ResponseEntity.ok(log);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
