package com.securevault.securevault_backend.controller;

import com.securevault.securevault_backend.entity.Credential;

import com.securevault.securevault_backend.service.CredentialService;
import com.securevault.securevault_backend.service.SecurityMonitoringService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final SecurityMonitoringService monitoringService;
    private final CredentialService credentialService;

    public ReportsController(SecurityMonitoringService monitoringService,
                             CredentialService credentialService) {
        this.monitoringService = monitoringService;
        this.credentialService = credentialService;
    }

    // ── GENERAL REPORT ANALYTICS ──────────────────────────────────────────────

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getReportAnalytics(
            @RequestParam(defaultValue = "security") String type) {
        String username = currentUser();
        List<Credential> credentials = credentialService.getCredentialsForUser(username);
        Map<String, Object> reportData = new LinkedHashMap<>();

        switch (type.toLowerCase()) {
            case "password-health":
                reportData = monitoringService.getPasswordHealthReport(username, credentials);
                reportData.put("reportType", "PASSWORD_HEALTH");
                break;

            case "audit":
                reportData = monitoringService.getAuditSummaryReport();
                reportData.put("reportType", "AUDIT_SUMMARY");
                break;

            case "user-activity":
                reportData = monitoringService.getLoginActivityReport(username);
                reportData.put("reportType", "USER_ACTIVITY");
                break;

            case "threat-monitoring":
                reportData = monitoringService.getThreatMonitoringSummary();
                reportData.put("reportType", "THREAT_MONITORING");
                break;

            case "security":
            default:
                reportData.put("reportType", "SECURITY_EXECUTIVE");
                reportData.put("dashboard", monitoringService.getDashboardSummary());
                reportData.put("riskAnalysis", monitoringService.getRiskAnalysis(credentials));
                break;
        }

        reportData.put("generatedAt", LocalDateTime.now());
        reportData.put("generatedBy", username);
        return ResponseEntity.ok(reportData);
    }

    // ── EXCEL / CSV EXPORT ───────────────────────────────────────────────────

    @GetMapping(value = "/export/excel", produces = "text/csv")
    public ResponseEntity<String> exportExcel(@RequestParam(defaultValue = "audit") String type) {
        String username = currentUser();
        String csvContent;
        String filename = "securevault-" + type + "-report.csv";

        if ("password-health".equalsIgnoreCase(type)) {
            List<Credential> credentials = credentialService.getCredentialsForUser(username);
            StringBuilder sb = new StringBuilder();
            sb.append("Credential ID,Title,Type,Username,Favorite,Created At,Updated At\n");
            for (Credential c : credentials) {
                sb.append(c.getId()).append(",")
                  .append("\"").append(c.getTitle()).append("\",")
                  .append("\"").append(c.getCredentialType()).append("\",")
                  .append("\"").append(c.getAccountUsername()).append("\",")
                  .append(c.isFavorite()).append(",")
                  .append("\"").append(c.getCreatedAt()).append("\",")
                  .append("\"").append(c.getUpdatedAt()).append("\"\n");
            }
            csvContent = sb.toString();

        } else if ("user-activity".equalsIgnoreCase(type)) {
            csvContent = monitoringService.exportAuditLogsCsv("login", username, null);

        } else if ("threat-monitoring".equalsIgnoreCase(type)) {
            csvContent = monitoringService.exportAuditLogsCsv("security", null, null);

        } else {
            csvContent = monitoringService.exportAuditLogsCsv("all", username, null);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csvContent);
    }

    // ── PDF PRINTABLE REPORT EXPORT ──────────────────────────────────────────

    @GetMapping(value = "/export/pdf", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> exportPdfReport(@RequestParam(defaultValue = "security") String type) {
        String username = currentUser();
        List<Credential> credentials = credentialService.getCredentialsForUser(username);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>SecureVault ").append(type.toUpperCase()).append(" Report</title>");
        html.append("<style>");
        html.append("body { font-family: 'Segoe UI', Arial, sans-serif; margin: 40px; color: #1e293b; background: #fff; }");
        html.append("h1 { color: #4f46e5; border-bottom: 2px solid #e2e8f0; padding-bottom: 10px; }");
        html.append(".meta { font-size: 0.9em; color: #64748b; margin-bottom: 30px; }");
        html.append(".card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; margin-bottom: 20px; }");
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 15px; }");
        html.append("th, td { border: 1px solid #cbd5e1; padding: 10px; text-align: left; font-size: 0.85em; }");
        html.append("th { background: #e0e7ff; color: #3730a3; }");
        html.append(".badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-weight: bold; font-size: 0.8em; }");
        html.append(".badge-success { background: #dcfce7; color: #166534; }");
        html.append(".badge-warning { background: #fef3c7; color: #92400e; }");
        html.append("</style></head><body>");

        html.append("<h1>🛡️ SecureVault Executive Report — ").append(type.replace("-", " ").toUpperCase()).append("</h1>");
        html.append("<div class='meta'>Generated at: ").append(LocalDateTime.now()).append(" | User: ").append(username).append("</div>");

        if ("password-health".equalsIgnoreCase(type)) {
            Map<String, Object> pwdReport = monitoringService.getPasswordHealthReport(username, credentials);
            html.append("<div class='card'><h2>Password Health Score: ").append(pwdReport.get("healthScore")).append(" / 100</h2>");
            html.append("<p>Strong Passwords: <strong>").append(pwdReport.get("strongPasswords")).append("</strong> | Weak Passwords: <strong>").append(pwdReport.get("weakPasswords")).append("</strong></p>");
            html.append("<p>Reused Passwords: <strong>").append(pwdReport.get("reusedPasswords")).append("</strong> | Outdated Passwords (&gt;90d): <strong>").append(pwdReport.get("oldPasswords")).append("</strong></p></div>");

            html.append("<h3>Credential Inventory</h3><table><thead><tr><th>Title</th><th>Username</th><th>Type</th><th>Favorite</th></tr></thead><tbody>");
            for (Credential c : credentials) {
                html.append("<tr><td>").append(c.getTitle()).append("</td><td>").append(c.getAccountUsername()).append("</td><td>").append(c.getCredentialType()).append("</td><td>").append(c.isFavorite() ? "Yes" : "No").append("</td></tr>");
            }
            html.append("</tbody></table>");

        } else {
            Map<String, Object> risk = monitoringService.getRiskAnalysis(credentials);
            html.append("<div class='card'><h2>System Security Rating: ").append(risk.get("securityGrade")).append(" (Risk Score: ").append(risk.get("systemRiskScore")).append(" / 100)</h2>");
            html.append("<p>Risk Classification: <span class='badge badge-success'>").append(risk.get("systemRiskRating")).append("</span></p></div>");

            html.append("<h3>Executive Security Summary</h3>");
            html.append("<table><thead><tr><th>Metric</th><th>Status / Value</th></tr></thead><tbody>");
            html.append("<tr><td>Vault Credentials Count</td><td>").append(credentials.size()).append("</td></tr>");
            html.append("<tr><td>Password Health Score</td><td>").append(risk.get("passwordHealthScore")).append(" / 100</td></tr>");
            html.append("<tr><td>System Security Grade</td><td>").append(risk.get("securityGrade")).append("</td></tr>");
            html.append("</tbody></table>");
        }

        html.append("<script>window.onload = function() { window.print(); };</script>");
        html.append("</body></html>");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"securevault-report.html\"")
                .body(html.toString());
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
