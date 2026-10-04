package com.securevault.securevault_backend.controller;

import com.securevault.securevault_backend.entity.AuditLog.Action;
import com.securevault.securevault_backend.entity.Credential;
import com.securevault.securevault_backend.entity.SharedCredential;
import com.securevault.securevault_backend.entity.TeamVault;
import com.securevault.securevault_backend.service.SecurityMonitoringService;
import com.securevault.securevault_backend.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vault/share")
public class ShareController {

    @Autowired
    private ShareService shareService;

    @Autowired
    private SecurityMonitoringService monitoringService;

    // POST /api/vault/share - Share credential (direct, role-based, or team vault with optional expiration)
    @PostMapping
    public ResponseEntity<?> shareCredential(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String ownerUsername = auth.getName();

            Long credentialId = Long.parseLong(request.get("credentialId").toString());
            String targetUsername = request.get("targetUser") != null ? request.get("targetUser").toString() : null;
            String permission = request.getOrDefault("permissionLevel", "VIEW_ONLY").toString();
            String targetRole = request.get("targetRole") != null ? request.get("targetRole").toString() : null;

            Long teamVaultId = null;
            if (request.get("teamVaultId") != null && !request.get("teamVaultId").toString().isEmpty()) {
                teamVaultId = Long.parseLong(request.get("teamVaultId").toString());
            }

            Long durationHours = null;
            if (request.get("durationHours") != null && !request.get("durationHours").toString().isEmpty()) {
                durationHours = Long.parseLong(request.get("durationHours").toString());
            }

            LocalDateTime expiresAt = null;
            if (request.get("expiresAt") != null && !request.get("expiresAt").toString().isEmpty()) {
                expiresAt = LocalDateTime.parse(request.get("expiresAt").toString());
            }

            SharedCredential shared = shareService.shareCredential(
                    credentialId,
                    targetUsername,
                    permission,
                    durationHours,
                    expiresAt,
                    targetRole,
                    teamVaultId,
                    ownerUsername
            );

            String targetDesc = targetUsername != null ? targetUsername : (targetRole != null ? "Role:" + targetRole : "TeamVault:" + teamVaultId);
            monitoringService.recordAuditLog(
                    ownerUsername, Action.CREDENTIAL_SHARE,
                    "Shared credential ID " + credentialId + " with " + targetDesc + " (Permission: " + permission + ")",
                    resolveIp(httpRequest), httpRequest.getHeader("User-Agent"));

            return ResponseEntity.ok(shared);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/vault/share/shared-with-me - Fetch active credentials shared with logged in user
    @GetMapping("/shared-with-me")
    public ResponseEntity<List<SharedCredential>> getSharedWithMe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return ResponseEntity.ok(shareService.getSharedWithMe(username));
    }

    // GET /api/vault/share/my-shares - Fetch credentials shared by logged in user
    @GetMapping("/my-shares")
    public ResponseEntity<List<SharedCredential>> getMyShares() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return ResponseEntity.ok(shareService.getMyOutgoingShares(username));
    }

    // PUT /api/vault/share/{shareId}/credential - Edit a shared credential (requires EDIT_ACCESS or FULL_MANAGEMENT)
    @PutMapping("/{shareId}/credential")
    public ResponseEntity<?> updateSharedCredential(
            @PathVariable Long shareId,
            @RequestBody Map<String, String> updates) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Credential updated = shareService.updateSharedCredential(shareId, updates, username);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // DELETE /api/vault/share/{id} - Revoke share
    @DeleteMapping("/{id}")
    public ResponseEntity<?> revokeShare(@PathVariable Long id, HttpServletRequest httpRequest) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String ownerUsername = auth.getName();
            shareService.revokeShare(id, ownerUsername);
            monitoringService.recordAuditLog(
                    ownerUsername, Action.CREDENTIAL_SHARE_REVOKE,
                    "Revoked share ID: " + id,
                    resolveIp(httpRequest), httpRequest.getHeader("User-Agent"));
            return ResponseEntity.ok(Map.of("message", "Share revoked successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Team Vault Endpoints ──────────────────────────────────────────────────

    // POST /api/vault/share/teams - Create a new team vault
    @PostMapping("/teams")
    public ResponseEntity<?> createTeamVault(@RequestBody Map<String, Object> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            String name = request.get("name").toString();
            String description = request.getOrDefault("description", "").toString();
            @SuppressWarnings("unchecked")
            List<String> members = (List<String>) request.get("members");

            TeamVault vault = shareService.createTeamVault(name, description, members, username);
            return ResponseEntity.ok(vault);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/vault/share/teams - Get team vaults user belongs to or owns
    @GetMapping("/teams")
    public ResponseEntity<List<TeamVault>> getMyTeamVaults() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return ResponseEntity.ok(shareService.getMyTeamVaults(username));
    }

    // POST /api/vault/share/teams/{id}/members - Add member to team vault
    @PostMapping("/teams/{id}/members")
    public ResponseEntity<?> addMemberToTeamVault(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            String memberIdentifier = request.get("user");
            TeamVault updated = shareService.addMemberToTeamVault(id, memberIdentifier, username);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // DELETE /api/vault/share/teams/{id}/members/{userId} - Remove member from team vault
    @DeleteMapping("/teams/{id}/members/{userId}")
    public ResponseEntity<?> removeMemberFromTeamVault(
            @PathVariable Long id,
            @PathVariable Long userId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            TeamVault updated = shareService.removeMemberFromTeamVault(id, userId, username);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/vault/share/teams/{id}/credentials - Get credentials shared in team vault
    @GetMapping("/teams/{id}/credentials")
    public ResponseEntity<?> getTeamVaultCredentials(@PathVariable Long id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            List<SharedCredential> list = shareService.getTeamVaultCredentials(id, username);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
