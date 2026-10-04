package com.securevault.securevault_backend.controller;

import com.securevault.securevault_backend.entity.AuditLog.Action;
import com.securevault.securevault_backend.entity.Credential;
import com.securevault.securevault_backend.service.CredentialService;
import com.securevault.securevault_backend.service.SecurityMonitoringService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credentials")
public class CredentialController {

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private SecurityMonitoringService monitoringService;

    // ── GET ALL ───────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Credential>> getAllCredentials() {
        return ResponseEntity.ok(credentialService.getCredentialsForUser(currentUser()));
    }

    // ── ADD ───────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Credential> addCredential(@Valid @RequestBody Credential credential,
                                                    HttpServletRequest request) {
        String username = currentUser();
        Credential created = credentialService.addCredential(credential, username);
        monitoringService.recordAuditLog(
                username, Action.CREDENTIAL_CREATE,
                "Created credential: " + created.getTitle() + " (" + created.getCredentialType() + ")",
                resolveIp(request), request.getHeader("User-Agent"));
        return ResponseEntity.ok(created);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCredential(@PathVariable Long id,
                                              @RequestBody Credential credential,
                                              HttpServletRequest request) {
        try {
            String username = currentUser();
            Credential updated = credentialService.updateCredential(id, credential, username);
            monitoringService.recordAuditLog(
                    username, Action.CREDENTIAL_UPDATE,
                    "Updated credential ID: " + id + " (" + updated.getTitle() + ")",
                    resolveIp(request), request.getHeader("User-Agent"));
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── TOGGLE FAVOURITE ──────────────────────────────────────────────────────

    @PatchMapping("/{id}/favorite")
    public ResponseEntity<?> toggleFavorite(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(credentialService.toggleFavorite(id, currentUser()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCredential(@PathVariable Long id,
                                              HttpServletRequest request) {
        try {
            String username = currentUser();
            credentialService.deleteCredential(id, username);
            monitoringService.recordAuditLog(
                    username, Action.CREDENTIAL_DELETE,
                    "Deleted credential ID: " + id,
                    resolveIp(request), request.getHeader("User-Agent"));
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── SEARCH ────────────────────────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<List<Credential>> search(@RequestParam String q) {
        return ResponseEntity.ok(credentialService.searchCredentials(currentUser(), q));
    }

    // ── FILTER ────────────────────────────────────────────────────────────────

    @GetMapping("/filter")
    public ResponseEntity<List<Credential>> filter(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean favorite) {
        return ResponseEntity.ok(credentialService.filterCredentials(currentUser(), type, category, favorite));
    }

    // ── FAVOURITES ────────────────────────────────────────────────────────────

    @GetMapping("/favorites")
    public ResponseEntity<List<Credential>> getFavorites() {
        return ResponseEntity.ok(credentialService.getFavorites(currentUser()));
    }

    // ── HELPER ───────────────────────────────────────────────────────────────

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
