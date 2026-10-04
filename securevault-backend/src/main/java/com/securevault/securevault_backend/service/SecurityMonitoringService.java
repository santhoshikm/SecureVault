package com.securevault.securevault_backend.service;

import com.securevault.securevault_backend.entity.AuditLog;
import com.securevault.securevault_backend.entity.AuditLog.Action;
import com.securevault.securevault_backend.entity.LoginAttempt;
import com.securevault.securevault_backend.entity.SecurityAlert;
import com.securevault.securevault_backend.entity.SecurityAlert.AlertType;
import com.securevault.securevault_backend.entity.SecurityAlert.Severity;
import com.securevault.securevault_backend.repository.AuditLogRepository;
import com.securevault.securevault_backend.repository.LoginAttemptRepository;
import com.securevault.securevault_backend.repository.SecurityAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Core security monitoring service.
 *
 * Responsibilities:
 *  1. Record login attempts and audit logs
 *  2. Detect suspicious patterns (brute-force, rapid access, off-hours)
 *  3. Raise and resolve security alerts
 *  4. Provide analytics data for the dashboard
 */
@Service
@Transactional
public class SecurityMonitoringService {

    // Thresholds
    private static final int BRUTE_FORCE_THRESHOLD       = 5;   // failed attempts
    private static final int BRUTE_FORCE_WINDOW_MINUTES  = 10;
    private static final int IP_BLOCK_THRESHOLD          = 20;  // failed from same IP
    private static final int IP_BLOCK_WINDOW_MINUTES     = 30;
    private static final int OFF_HOURS_START             = 0;   // midnight
    private static final int OFF_HOURS_END               = 5;   // 5 AM

    private final AuditLogRepository    auditLogRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final SecurityAlertRepository alertRepository;

    public SecurityMonitoringService(AuditLogRepository auditLogRepository,
                                     LoginAttemptRepository loginAttemptRepository,
                                     SecurityAlertRepository alertRepository) {
        this.auditLogRepository  = auditLogRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.alertRepository     = alertRepository;
    }

    // ── LOGIN MONITORING & DEVICE TRACKING ────────────────────────────────────

    /**
     * Record a login attempt and run suspicious-activity / anomaly checks.
     */
    public void recordLoginAttempt(String username, String ipAddress,
                                   String userAgent, boolean success,
                                   String failureReason) {
        LoginAttempt attempt = new LoginAttempt(username, ipAddress, userAgent,
                success, failureReason);
        Map<String, String> devInfo = parseUserAgent(userAgent);
        attempt.setGeoLocation(devInfo.get("os") + " (" + devInfo.get("browser") + ")");
        loginAttemptRepository.save(attempt);

        Action auditAction = success ? Action.LOGIN_SUCCESS : Action.LOGIN_FAILURE;
        recordAuditLog(username, auditAction,
                success ? "Successful login via " + devInfo.get("browser") + " on " + devInfo.get("os")
                        : "Failed login: " + failureReason,
                ipAddress, userAgent);

        if (!success) {
            runBruteForceCheck(username, ipAddress);
        }
        if (success) {
            runOffHoursCheck(username, ipAddress, userAgent);
            runDeviceTrackingCheck(username, ipAddress, userAgent, devInfo);
        }
    }

    /**
     * User agent parser for device category, OS, and browser identification.
     */
    public Map<String, String> parseUserAgent(String userAgent) {
        Map<String, String> device = new LinkedHashMap<>();
        if (userAgent == null || userAgent.isBlank()) {
            device.put("deviceType", "Unknown");
            device.put("browser", "Unknown Browser");
            device.put("os", "Unknown OS");
            return device;
        }

        String ua = userAgent.toLowerCase();

        // Device Category
        if (ua.contains("mobile") || ua.contains("iphone") || (ua.contains("android") && !ua.contains("tablet"))) {
            device.put("deviceType", "Mobile");
        } else if (ua.contains("ipad") || ua.contains("tablet")) {
            device.put("deviceType", "Tablet");
        } else {
            device.put("deviceType", "Desktop");
        }

        // Operating System
        if (ua.contains("windows")) device.put("os", "Windows");
        else if (ua.contains("mac os") || ua.contains("macintosh")) device.put("os", "macOS");
        else if (ua.contains("linux")) device.put("os", "Linux");
        else if (ua.contains("android")) device.put("os", "Android");
        else if (ua.contains("iphone") || ua.contains("ipad")) device.put("os", "iOS");
        else device.put("os", "Unknown OS");

        // Browser
        if (ua.contains("edg/") || ua.contains("edge/")) device.put("browser", "Edge");
        else if (ua.contains("chrome/") || ua.contains("crios/")) device.put("browser", "Chrome");
        else if (ua.contains("firefox/") || ua.contains("fxios/")) device.put("browser", "Firefox");
        else if (ua.contains("safari/")) device.put("browser", "Safari");
        else if (ua.contains("opera/") || ua.contains("opr/")) device.put("browser", "Opera");
        else device.put("browser", "Web Browser");

        return device;
    }

    private void runDeviceTrackingCheck(String username, String ipAddress, String userAgent, Map<String, String> devInfo) {
        List<LoginAttempt> pastSuccesses = loginAttemptRepository
                .findByUsernameOrderByAttemptTimeDesc(username).stream()
                .filter(LoginAttempt::isSuccess)
                .filter(a -> !a.getAttemptTime().isAfter(LocalDateTime.now().minusSeconds(5)))
                .toList();

        if (!pastSuccesses.isEmpty()) {
            String currentProfile = devInfo.get("os") + "|" + devInfo.get("browser");
            boolean knownDevice = pastSuccesses.stream().anyMatch(a -> {
                Map<String, String> prevInfo = parseUserAgent(a.getUserAgent());
                return (prevInfo.get("os") + "|" + prevInfo.get("browser")).equalsIgnoreCase(currentProfile);
            });

            if (!knownDevice) {
                raiseAlert(username, AlertType.UNRECOGNIZED_DEVICE_LOGIN, Severity.MEDIUM,
                        String.format("Unrecognized device login for '%s': %s on %s from IP %s",
                                username, devInfo.get("browser"), devInfo.get("os"), ipAddress),
                        ipAddress);
                markAuditSuspicious(username);
            }
        }
    }

    // ── AUDIT LOG ─────────────────────────────────────────────────────────────

    /**
     * Generic audit event recorder used by controllers/services.
     */
    public AuditLog recordAuditLog(String username, Action action,
                                   String details, String ipAddress,
                                   String userAgent) {
        AuditLog log = new AuditLog(username, action, details, ipAddress, userAgent);
        return auditLogRepository.save(log);
    }

    // ── SUSPICIOUS ACTIVITY DETECTION ────────────────────────────────────────

    private void runBruteForceCheck(String username, String ipAddress) {
        LocalDateTime window = LocalDateTime.now().minusMinutes(BRUTE_FORCE_WINDOW_MINUTES);

        long userFailures = loginAttemptRepository
                .countByUsernameAndSuccessFalseAndAttemptTimeAfter(username, window);

        if (userFailures >= BRUTE_FORCE_THRESHOLD) {
            raiseAlert(username, AlertType.BRUTE_FORCE_ATTACK, Severity.HIGH,
                    String.format("Brute-force detected: %d failed logins for user '%s' " +
                                    "in the last %d minutes from IP %s",
                            userFailures, username, BRUTE_FORCE_WINDOW_MINUTES, ipAddress),
                    ipAddress);
            markAuditSuspicious(username);
        }

        if (ipAddress != null) {
            LocalDateTime ipWindow = LocalDateTime.now().minusMinutes(IP_BLOCK_WINDOW_MINUTES);
            long ipFailures = loginAttemptRepository
                    .countByIpAddressAndSuccessFalseAndAttemptTimeAfter(ipAddress, ipWindow);
            if (ipFailures >= IP_BLOCK_THRESHOLD) {
                raiseAlert(username, AlertType.PASSWORD_SPRAY_DETECTED, Severity.CRITICAL,
                        String.format("Password spray suspected: %d failed attempts from IP %s " +
                                        "in the last %d minutes",
                                ipFailures, ipAddress, IP_BLOCK_WINDOW_MINUTES),
                        ipAddress);
            }
        }
    }

    private void runOffHoursCheck(String username, String ipAddress, String userAgent) {
        int hour = LocalDateTime.now().getHour();
        if (hour >= OFF_HOURS_START && hour < OFF_HOURS_END) {
            raiseAlert(username, AlertType.OFF_HOURS_ACCESS, Severity.MEDIUM,
                    String.format("Off-hours login detected for user '%s' at %02d:00 from IP %s",
                            username, hour, ipAddress),
                    ipAddress);
        }
    }

    // ── ALERT MANAGEMENT ─────────────────────────────────────────────────────

    public SecurityAlert raiseAlert(String username, AlertType type,
                                    Severity severity, String message,
                                    String ipAddress) {
        SecurityAlert alert = new SecurityAlert(username, type, severity, message, ipAddress);
        return alertRepository.save(alert);
    }

    public SecurityAlert resolveAlert(Long alertId, String resolvedBy) {
        SecurityAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
        alert.setResolved(true);
        alert.setResolvedBy(resolvedBy);
        alert.setResolvedAt(LocalDateTime.now());
        return alertRepository.save(alert);
    }

    // ── AUDIT HELPERS ─────────────────────────────────────────────────────────

    private void markAuditSuspicious(String username) {
        List<AuditLog> recentLogs = auditLogRepository
                .findByUsernameOrderByTimestampDesc(username);
        if (!recentLogs.isEmpty()) {
            AuditLog latest = recentLogs.get(0);
            latest.setSuspicious(true);
            auditLogRepository.save(latest);
        }
    }

    // ── DEVICE TRACKING SUMMARY ──────────────────────────────────────────────

    public List<Map<String, Object>> getDeviceTrackingSummary(String username) {
        List<LoginAttempt> attempts = (username != null && !username.isBlank())
                ? loginAttemptRepository.findByUsernameOrderByAttemptTimeDesc(username)
                : loginAttemptRepository.findByAttemptTimeAfterOrderByAttemptTimeDesc(LocalDateTime.now().minusDays(30));

        Map<String, Map<String, Object>> deviceMap = new LinkedHashMap<>();

        for (LoginAttempt a : attempts) {
            Map<String, String> parsed = parseUserAgent(a.getUserAgent());
            String key = (a.getUsername() != null ? a.getUsername() : "unknown") + "|" +
                    parsed.get("deviceType") + "|" + parsed.get("os") + "|" + parsed.get("browser");

            if (!deviceMap.containsKey(key)) {
                Map<String, Object> dev = new LinkedHashMap<>();
                dev.put("username", a.getUsername());
                dev.put("deviceType", parsed.get("deviceType"));
                dev.put("os", parsed.get("os"));
                dev.put("browser", parsed.get("browser"));
                dev.put("lastIpAddress", a.getIpAddress());
                dev.put("lastSeen", a.getAttemptTime());
                dev.put("loginCount", 1);
                dev.put("trusted", a.isSuccess());
                deviceMap.put(key, dev);
            } else {
                Map<String, Object> dev = deviceMap.get(key);
                dev.put("loginCount", (int) dev.get("loginCount") + 1);
                if (a.isSuccess()) dev.put("trusted", true);
            }
        }

        return new ArrayList<>(deviceMap.values());
    }

    // ── THREAT MONITORING SUMMARY ────────────────────────────────────────────

    public Map<String, Object> getThreatMonitoringSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        LocalDateTime last24h = LocalDateTime.now().minusHours(24);

        long activeAlerts = alertRepository.countByResolvedFalse();
        long criticalAlerts = alertRepository.countBySeverityAndResolvedFalse(Severity.CRITICAL);
        long highAlerts = alertRepository.countBySeverityAndResolvedFalse(Severity.HIGH);
        long suspiciousEvents = auditLogRepository.countBySuspiciousTrueAndTimestampAfter(last24h);

        // Threat Level Determination
        String threatLevel = "LOW";
        int threatScore = 15;
        if (criticalAlerts > 0 || suspiciousEvents > 10) {
            threatLevel = "CRITICAL";
            threatScore = 90;
        } else if (highAlerts > 0 || suspiciousEvents > 5) {
            threatLevel = "HIGH";
            threatScore = 70;
        } else if (activeAlerts > 0 || suspiciousEvents > 0) {
            threatLevel = "ELEVATED";
            threatScore = 40;
        }

        summary.put("threatLevel", threatLevel);
        summary.put("threatScore", threatScore);
        summary.put("activeAlertsCount", activeAlerts);
        summary.put("criticalAlertsCount", criticalAlerts);
        summary.put("highAlertsCount", highAlerts);
        summary.put("suspiciousEvents24h", suspiciousEvents);

        // Indicators of Compromise (IOC)
        List<Object[]> topIps = loginAttemptRepository.topAttackingIps(last24h);
        List<String> suspiciousIps = topIps.stream().map(r -> (String) r[0]).filter(Objects::nonNull).toList();
        summary.put("suspiciousIps", suspiciousIps);

        List<Object[]> topUsers = loginAttemptRepository.topAttackedUsers(last24h);
        List<String> targetedUsers = topUsers.stream().map(r -> (String) r[0]).filter(Objects::nonNull).toList();
        summary.put("targetedUsers", targetedUsers);

        // Threat Feed (Recent Security Alerts)
        summary.put("recentThreatAlerts", alertRepository.findByResolvedFalseOrderByCreatedAtDesc().stream().limit(10).toList());

        return summary;
    }

    // ── RISK ANALYSIS ENGINE ────────────────────────────────────────────────

    public Map<String, Object> getRiskAnalysis(List<com.securevault.securevault_backend.entity.Credential> credentials) {
        Map<String, Object> riskData = new LinkedHashMap<>();
        LocalDateTime last7d = LocalDateTime.now().minusDays(7);

        long activeAlerts = alertRepository.countByResolvedFalse();
        long failedLogins24h = loginAttemptRepository.countBySuccessFalseAndAttemptTimeAfter(LocalDateTime.now().minusHours(24));
        long suspicious24h = auditLogRepository.countBySuspiciousTrueAndTimestampAfter(LocalDateTime.now().minusHours(24));

        // Evaluate Password Health Penalty
        int pwdHealthScore = 100;
        if (credentials != null && !credentials.isEmpty()) {
            Map<String, Object> pwdReport = getPasswordHealthReport("system", credentials);
            pwdHealthScore = (int) pwdReport.getOrDefault("healthScore", 100);
        }

        // System Risk Score Calculation (0-100, higher = riskier)
        int riskScore = 10; // baseline
        riskScore += (100 - pwdHealthScore) * 0.35;
        riskScore += Math.min(30, activeAlerts * 10);
        riskScore += Math.min(20, failedLogins24h * 2);
        riskScore += Math.min(15, suspicious24h * 3);
        riskScore = Math.min(100, Math.max(0, riskScore));

        String riskRating;
        String systemGrade;
        if (riskScore >= 75) { riskRating = "CRITICAL"; systemGrade = "F"; }
        else if (riskScore >= 50) { riskRating = "HIGH"; systemGrade = "D"; }
        else if (riskScore >= 30) { riskRating = "MEDIUM"; systemGrade = "B"; }
        else { riskRating = "LOW"; systemGrade = "A+"; }

        riskData.put("systemRiskScore", riskScore);
        riskData.put("systemRiskRating", riskRating);
        riskData.put("securityGrade", systemGrade);
        riskData.put("passwordHealthScore", pwdHealthScore);
        riskData.put("activeAlertsPenalty", Math.min(30, activeAlerts * 10));
        riskData.put("failedLoginPenalty", Math.min(20, failedLogins24h * 2));
        riskData.put("suspiciousActivityPenalty", Math.min(15, suspicious24h * 3));

        // Risk factors list
        List<Map<String, String>> factors = new ArrayList<>();
        if (pwdHealthScore < 70) factors.add(Map.of("factor", "Weak/Reused Passwords", "impact", "HIGH", "advice", "Improve credential strength across vault"));
        if (activeAlerts > 0) factors.add(Map.of("factor", "Unresolved Security Alerts", "impact", "CRITICAL", "advice", "Review and resolve open security alerts"));
        if (failedLogins24h > 5) factors.add(Map.of("factor", "Elevated Authentication Failures", "impact", "MEDIUM", "advice", "Monitor failed logins for brute-force attacks"));
        if (suspicious24h > 0) factors.add(Map.of("factor", "Suspicious Activity Detected", "impact", "HIGH", "advice", "Inspect flagged suspicious audit logs"));
        if (factors.isEmpty()) factors.add(Map.of("factor", "Optimal Security Posture", "impact", "LOW", "advice", "No critical security risks identified"));
        riskData.put("riskFactors", factors);

        return riskData;
    }

    // ── LOGIN ANOMALY DETECTION ──────────────────────────────────────────────

    public List<Map<String, Object>> getLoginAnomalies() {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        LocalDateTime last7d = LocalDateTime.now().minusDays(7);

        // 1. Off-hours login anomalies
        List<LoginAttempt> recentLogins = loginAttemptRepository.findByAttemptTimeAfterOrderByAttemptTimeDesc(last7d);
        for (LoginAttempt a : recentLogins) {
            int hr = a.getAttemptTime().getHour();
            if (hr >= OFF_HOURS_START && hr < OFF_HOURS_END) {
                Map<String, Object> anomaly = new LinkedHashMap<>();
                anomaly.put("type", "OFF_HOURS_LOGIN");
                anomaly.put("username", a.getUsername());
                anomaly.put("ipAddress", a.getIpAddress());
                anomaly.put("timestamp", a.getAttemptTime());
                anomaly.put("riskWeight", "MEDIUM");
                anomaly.put("details", String.format("Login recorded during off-hours at %02d:00", hr));
                anomalies.add(anomaly);
            }
        }

        // 2. Failed login spikes
        List<Object[]> topAttacked = loginAttemptRepository.topAttackedUsers(last7d);
        for (Object[] row : topAttacked) {
            Long count = (Long) row[1];
            if (count >= BRUTE_FORCE_THRESHOLD) {
                Map<String, Object> anomaly = new LinkedHashMap<>();
                anomaly.put("type", "FAILED_LOGIN_SPIKE");
                anomaly.put("username", row[0]);
                anomaly.put("ipAddress", "Multiple / Unknown");
                anomaly.put("timestamp", LocalDateTime.now());
                anomaly.put("riskWeight", "HIGH");
                anomaly.put("details", String.format("%d failed login attempts detected in recent window", count));
                anomalies.add(anomaly);
            }
        }

        return anomalies;
    }

    // ── AUDIT LOG CATEGORIZATION & SEARCH ────────────────────────────────────

    public List<AuditLog> getFilteredAuditLogs(String category, String username, String search) {
        List<AuditLog> logs = (username != null && !username.isBlank())
                ? auditLogRepository.findByUsernameOrderByTimestampDesc(username)
                : auditLogRepository.findByTimestampAfterOrderByTimestampDesc(LocalDateTime.now().minusDays(30));

        return logs.stream().filter(l -> {
            // Category filter
            if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
                if (!matchesCategory(l.getAction(), category)) return false;
            }
            // Search filter
            if (search != null && !search.isBlank()) {
                String s = search.toLowerCase();
                boolean matchAction = l.getAction() != null && l.getAction().name().toLowerCase().contains(s);
                boolean matchDetails = l.getDetails() != null && l.getDetails().toLowerCase().contains(s);
                boolean matchIp = l.getIpAddress() != null && l.getIpAddress().contains(s);
                boolean matchUser = l.getUsername() != null && l.getUsername().toLowerCase().contains(s);
                if (!matchAction && !matchDetails && !matchIp && !matchUser) return false;
            }
            return true;
        }).toList();
    }

    private boolean matchesCategory(Action action, String category) {
        if (action == null) return false;
        return switch (category.toLowerCase()) {
            case "login" -> action == Action.LOGIN_SUCCESS || action == Action.LOGIN_FAILURE || action == Action.LOGOUT;
            case "vault" -> action == Action.CREDENTIAL_CREATE || action == Action.CREDENTIAL_UPDATE
                    || action == Action.CREDENTIAL_DELETE || action == Action.CREDENTIAL_VIEW;
            case "sharing" -> action == Action.CREDENTIAL_SHARE || action == Action.CREDENTIAL_SHARE_REVOKE;
            case "security" -> action == Action.MFA_ENABLED || action == Action.MFA_DISABLED
                    || action == Action.ACCOUNT_LOCKED || action == Action.ACCOUNT_UNLOCKED
                    || action == Action.SUSPICIOUS_ACTIVITY_DETECTED;
            case "system" -> action == Action.REGISTER || action == Action.PASSWORD_RESET_REQUEST
                    || action == Action.PASSWORD_RESET_SUCCESS;
            default -> true;
        };
    }

    // ── AUDIT REPORTS & CSV EXPORT ───────────────────────────────────────────

    public String exportAuditLogsCsv(String category, String username, String search) {
        List<AuditLog> logs = getFilteredAuditLogs(category, username, search);
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Timestamp,Username,Action,Category,IP Address,Suspicious,Details\n");
        for (AuditLog l : logs) {
            String cat = getCategoryNameForAction(l.getAction());
            sb.append(l.getId()).append(",")
              .append("\"").append(l.getTimestamp()).append("\",")
              .append("\"").append(l.getUsername() != null ? l.getUsername() : "").append("\",")
              .append("\"").append(l.getAction() != null ? l.getAction().name() : "").append("\",")
              .append("\"").append(cat).append("\",")
              .append("\"").append(l.getIpAddress() != null ? l.getIpAddress() : "").append("\",")
              .append(l.isSuspicious()).append(",")
              .append("\"").append(l.getDetails() != null ? l.getDetails().replace("\"", "'") : "").append("\"\n");
        }
        return sb.toString();
    }

    public Map<String, Object> getAuditSummaryReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        LocalDateTime last30d = LocalDateTime.now().minusDays(30);

        List<AuditLog> logs = auditLogRepository.findByTimestampAfterOrderByTimestampDesc(last30d);
        long totalEvents = logs.size();
        long suspiciousCount = logs.stream().filter(AuditLog::isSuspicious).count();

        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        categoryCounts.put("login", 0);
        categoryCounts.put("vault", 0);
        categoryCounts.put("sharing", 0);
        categoryCounts.put("security", 0);
        categoryCounts.put("system", 0);

        for (AuditLog l : logs) {
            String cat = getCategoryNameForAction(l.getAction()).toLowerCase();
            categoryCounts.merge(cat, 1, Integer::sum);
        }

        report.put("reportTitle", "SecureVault Executive Audit Summary Report");
        report.put("generatedAt", LocalDateTime.now());
        report.put("periodDays", 30);
        report.put("totalEvents", totalEvents);
        report.put("suspiciousEventsCount", suspiciousCount);
        report.put("eventsByCategory", categoryCounts);

        return report;
    }

    private String getCategoryNameForAction(Action action) {
        if (action == null) return "System";
        if (action == Action.LOGIN_SUCCESS || action == Action.LOGIN_FAILURE || action == Action.LOGOUT) return "Login";
        if (action == Action.CREDENTIAL_CREATE || action == Action.CREDENTIAL_UPDATE || action == Action.CREDENTIAL_DELETE || action == Action.CREDENTIAL_VIEW) return "Vault";
        if (action == Action.CREDENTIAL_SHARE || action == Action.CREDENTIAL_SHARE_REVOKE) return "Sharing";
        if (action == Action.MFA_ENABLED || action == Action.MFA_DISABLED || action == Action.ACCOUNT_LOCKED || action == Action.ACCOUNT_UNLOCKED || action == Action.SUSPICIOUS_ACTIVITY_DETECTED) return "Security";
        return "System";
    }

    // ── ANALYTICS DATA ───────────────────────────────────────────────────────

    /** Summary statistics for the security dashboard */
    public Map<String, Object> getDashboardSummary() {
        LocalDateTime last24h = LocalDateTime.now().minusHours(24);
        LocalDateTime last7d  = LocalDateTime.now().minusDays(7);

        Map<String, Object> summary = new LinkedHashMap<>();

        // Login counts
        long successfulLogins = loginAttemptRepository.countBySuccessTrueAndAttemptTimeAfter(last24h);
        long failedLogins     = loginAttemptRepository.countBySuccessFalseAndAttemptTimeAfter(last24h);
        summary.put("successfulLogins24h", successfulLogins);
        summary.put("failedLogins24h",     failedLogins);
        summary.put("loginSuccessRate",    successfulLogins + failedLogins > 0
                ? Math.round((double) successfulLogins / (successfulLogins + failedLogins) * 100)
                : 100);

        // Alert counts
        summary.put("activeAlerts",        alertRepository.countByResolvedFalse());
        summary.put("criticalAlerts",      alertRepository.countBySeverityAndResolvedFalse(Severity.CRITICAL));
        summary.put("highAlerts",          alertRepository.countBySeverityAndResolvedFalse(Severity.HIGH));

        // Audit counts
        summary.put("totalAuditEvents24h", auditLogRepository.countByTimestampAfter(last24h));
        summary.put("suspiciousEvents24h", auditLogRepository.countBySuspiciousTrueAndTimestampAfter(last24h));

        // Top attacked users (last 24 h)
        List<Object[]> topAttacked = loginAttemptRepository.topAttackedUsers(last24h);
        List<Map<String, Object>> attackedList = new ArrayList<>();
        for (Object[] row : topAttacked) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", row[0]);
            m.put("failedAttempts", row[1]);
            attackedList.add(m);
        }
        summary.put("topAttackedUsers", attackedList);

        // Top attacking IPs (last 24 h)
        List<Object[]> topIps = loginAttemptRepository.topAttackingIps(last24h);
        List<Map<String, Object>> ipList = new ArrayList<>();
        for (Object[] row : topIps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ip", row[0]);
            m.put("failedAttempts", row[1]);
            ipList.add(m);
        }
        summary.put("topAttackingIps", ipList);

        // Alerts by type (last 7 days)
        List<Object[]> alertsByType = alertRepository.countByAlertTypeSince(last7d);
        Map<String, Long> alertTypeMap = new LinkedHashMap<>();
        for (Object[] row : alertsByType) {
            alertTypeMap.put(row[0].toString(), (Long) row[1]);
        }
        summary.put("alertsByType7d", alertTypeMap);

        // Unresolved by severity
        List<Object[]> unresolvedBySeverity = alertRepository.unresolvedCountBySeverity();
        Map<String, Long> severityMap = new LinkedHashMap<>();
        for (Object[] row : unresolvedBySeverity) {
            severityMap.put(row[0].toString(), (Long) row[1]);
        }
        summary.put("unresolvedBySeverity", severityMap);

        // Hourly login distribution (last 24 h)
        List<Object[]> hourly = loginAttemptRepository.loginsByHour(last24h);
        List<Map<String, Object>> hourlyList = new ArrayList<>();
        for (Object[] row : hourly) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hour", row[0]);
            m.put("count", row[1]);
            hourlyList.add(m);
        }
        summary.put("loginsByHour", hourlyList);

        return summary;
    }

    /** Password health report for the current user */
    public Map<String, Object> getPasswordHealthReport(String username,
                                                        List<com.securevault.securevault_backend.entity.Credential> credentials) {
        Map<String, Object> report = new LinkedHashMap<>();

        int total        = credentials != null ? credentials.size() : 0;
        int weak         = 0;
        int reused       = 0;
        int old          = 0;
        int strong       = 0;

        Map<String, Integer> passwordCount = new HashMap<>();
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        if (credentials != null) {
            for (var cred : credentials) {
                String pwd = cred.getAccountPassword();
                if (pwd == null || pwd.isBlank()) continue;

                int strength = evaluatePasswordStrength(pwd);
                if (strength < 2) weak++;
                else if (strength >= 4) strong++;

                passwordCount.merge(pwd, 1, Integer::sum);

                if (cred.getUpdatedAt() != null && cred.getUpdatedAt().isBefore(threeMonthsAgo)) {
                    old++;
                }
            }
        }

        for (int cnt : passwordCount.values()) {
            if (cnt > 1) reused += cnt;
        }

        int healthScore = 100;
        if (total > 0) {
            healthScore -= (int) ((double) weak   / total * 30);
            healthScore -= (int) ((double) reused / total * 25);
            healthScore -= (int) ((double) old    / total * 20);
        }
        healthScore = Math.max(0, healthScore);

        report.put("totalPasswords",  total);
        report.put("strongPasswords", strong);
        report.put("weakPasswords",   weak);
        report.put("reusedPasswords", reused);
        report.put("oldPasswords",    old);
        report.put("healthScore",     healthScore);

        List<String> recommendations = new ArrayList<>();
        if (weak   > 0) recommendations.add("Update " + weak + " weak password(s) with stronger alternatives");
        if (reused > 0) recommendations.add("Replace " + reused + " reused password(s) with unique ones");
        if (old    > 0) recommendations.add("Rotate " + old + " password(s) not changed in 3+ months");
        if (recommendations.isEmpty()) recommendations.add("All passwords look healthy! Keep it up.");
        report.put("recommendations", recommendations);

        return report;
    }

    /** Login activity report for a specific user */
    public Map<String, Object> getLoginActivityReport(String username) {
        Map<String, Object> report = new LinkedHashMap<>();

        List<LoginAttempt> attempts = loginAttemptRepository
                .findByUsernameOrderByAttemptTimeDesc(username);

        long totalAttempts    = attempts.size();
        long successfulCount  = attempts.stream().filter(LoginAttempt::isSuccess).count();
        long failedCount      = totalAttempts - successfulCount;

        List<Map<String, Object>> recentList = new ArrayList<>();
        attempts.stream().limit(10).forEach(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("timestamp",     a.getAttemptTime());
            m.put("success",       a.isSuccess());
            m.put("ipAddress",     a.getIpAddress());
            m.put("failureReason", a.getFailureReason());
            recentList.add(m);
        });

        report.put("username",         username);
        report.put("totalAttempts",    totalAttempts);
        report.put("successfulLogins", successfulCount);
        report.put("failedLogins",     failedCount);
        report.put("recentAttempts",   recentList);

        Set<String> uniqueIps = new HashSet<>();
        attempts.forEach(a -> { if (a.getIpAddress() != null) uniqueIps.add(a.getIpAddress()); });
        report.put("uniqueIpAddresses", uniqueIps);

        return report;
    }

    private int evaluatePasswordStrength(String password) {
        if (password == null || password.length() < 6) return 0;
        int score = 0;
        if (password.length() >= 8)  score++;
        if (password.length() >= 12) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) score++;
        return score;
    }

    public List<AuditLog> getAuditLogs(String username) {
        if (username != null && !username.isBlank()) {
            return auditLogRepository.findByUsernameOrderByTimestampDesc(username);
        }
        return auditLogRepository.findByTimestampAfterOrderByTimestampDesc(
                LocalDateTime.now().minusDays(7));
    }

    public List<AuditLog> getSuspiciousAuditLogs() {
        return auditLogRepository.findBySuspiciousTrueOrderByTimestampDesc();
    }

    public List<SecurityAlert> getActiveAlerts() {
        return alertRepository.findByResolvedFalseOrderByCreatedAtDesc();
    }

    public List<SecurityAlert> getAllAlerts() {
        return alertRepository.findAll().stream()
                .sorted(Comparator.comparing(SecurityAlert::getCreatedAt).reversed())
                .collect(java.util.stream.Collectors.toList());
    }

    public List<LoginAttempt> getRecentLoginAttempts(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return loginAttemptRepository.findByAttemptTimeAfterOrderByAttemptTimeDesc(since);
    }
}
