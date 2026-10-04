package com.securevault.securevault_backend.controller;

import com.securevault.securevault_backend.entity.Credential;
import com.securevault.securevault_backend.service.CredentialService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Alias controller at /api/vault/credentials so the existing frontend
 * endpoints continue to work, while also exposing all new vault features.
 */
@RestController
@RequestMapping("/api/vault/credentials")
public class VaultCredentialController {

    @Autowired
    private CredentialService credentialService;

    // ── GET ALL ───────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<Credential>> getVaultCredentials() {
        return ResponseEntity.ok(credentialService.getCredentialsForUser(currentUser()));
    }

    // ── ADD ───────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Credential> addVaultCredential(@Valid @RequestBody Credential credential) {
        return ResponseEntity.ok(credentialService.addCredential(credential, currentUser()));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<?> updateVaultCredential(@PathVariable Long id,
                                                   @RequestBody Credential credential) {
        try {
            return ResponseEntity.ok(credentialService.updateCredential(id, credential, currentUser()));
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
    public ResponseEntity<?> deleteVaultCredential(@PathVariable Long id) {
        try {
            credentialService.deleteCredential(id, currentUser());
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
}
