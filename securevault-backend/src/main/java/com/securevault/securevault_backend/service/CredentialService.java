package com.securevault.securevault_backend.service;

import com.securevault.securevault_backend.entity.Credential;
import com.securevault.securevault_backend.entity.CredentialType;
import com.securevault.securevault_backend.entity.User;
import com.securevault.securevault_backend.repository.CredentialRepository;
import com.securevault.securevault_backend.repository.UserRepository;
import com.securevault.securevault_backend.util.EncryptionUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;

    public CredentialService(CredentialRepository credentialRepository,
                             UserRepository userRepository,
                             EncryptionUtil encryptionUtil) {
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.encryptionUtil = encryptionUtil;
    }

    // ── GET ALL ───────────────────────────────────────────────────────────────

    public List<Credential> getCredentialsForUser(String username) {
        User user = getUser(username);
        return decryptAll(credentialRepository.findByUserId(user.getId()));
    }

    // ── ADD ───────────────────────────────────────────────────────────────────

    public Credential addCredential(Credential credential, String username) {
        User user = getUser(username);
        encryptSensitiveFields(credential);
        credential.setUser(user);
        Credential saved = credentialRepository.save(credential);
        return decryptSensitiveFields(saved);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    public Credential updateCredential(Long id, Credential updated, String username) {
        Credential existing = getOwnedCredential(id, username);

        // Patch all mutable fields
        if (updated.getSiteName() != null && !updated.getSiteName().isBlank()) {
            existing.setSiteName(updated.getSiteName());
        }
        if (updated.getSiteUrl() != null) {
            existing.setSiteUrl(updated.getSiteUrl());
        }
        if (updated.getAccountUsername() != null) {
            existing.setAccountUsername(updated.getAccountUsername());
        }
        if (updated.getAccountPassword() != null && !updated.getAccountPassword().isBlank()) {
            existing.setAccountPassword(encryptionUtil.encrypt(updated.getAccountPassword()));
        }
        if (updated.getNotes() != null) {
            existing.setNotes(encryptionUtil.encrypt(updated.getNotes()));
        }
        if (updated.getCredentialType() != null) {
            existing.setCredentialType(updated.getCredentialType());
        }
        if (updated.getCategory() != null) {
            existing.setCategory(updated.getCategory());
        }
        if (updated.getTags() != null) {
            existing.setTags(updated.getTags());
        }
        existing.setFavorite(updated.isFavorite());

        return decryptSensitiveFields(credentialRepository.save(existing));
    }

    // ── TOGGLE FAVORITE ───────────────────────────────────────────────────────

    public Credential toggleFavorite(Long id, String username) {
        Credential credential = getOwnedCredential(id, username);
        credential.setFavorite(!credential.isFavorite());
        return decryptSensitiveFields(credentialRepository.save(credential));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    public void deleteCredential(Long id, String username) {
        Credential credential = getOwnedCredential(id, username);
        credentialRepository.delete(credential);
    }

    // ── SEARCH ────────────────────────────────────────────────────────────────

    public List<Credential> searchCredentials(String username, String query) {
        User user = getUser(username);
        return decryptAll(credentialRepository.searchByUser(user.getId(), query));
    }

    // ── FILTER ────────────────────────────────────────────────────────────────

    public List<Credential> filterCredentials(String username, String type,
                                              String category, Boolean favorite) {
        User user = getUser(username);
        CredentialType typeEnum = null;
        if (type != null && !type.isBlank()) {
            try { typeEnum = CredentialType.valueOf(type.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }
        String cat = (category != null && !category.isBlank()) ? category : null;
        Boolean fav = Boolean.TRUE.equals(favorite) ? Boolean.TRUE : null;
        return decryptAll(credentialRepository.filterByUser(user.getId(), typeEnum, cat, fav));
    }

    public List<Credential> getFavorites(String username) {
        User user = getUser(username);
        return decryptAll(credentialRepository.findByUserIdAndFavoriteTrue(user.getId()));
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Credential getOwnedCredential(Long id, String username) {
        Credential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Credential not found"));
        if (credential.getUser() == null || !credential.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Unauthorized access");
        }
        return credential;
    }

    private void encryptSensitiveFields(Credential c) {
        if (c.getAccountPassword() != null && !c.getAccountPassword().isBlank()) {
            c.setAccountPassword(encryptionUtil.encrypt(c.getAccountPassword()));
        }
        if (c.getNotes() != null && !c.getNotes().isBlank()) {
            c.setNotes(encryptionUtil.encrypt(c.getNotes()));
        }
    }

    private Credential decryptSensitiveFields(Credential c) {
        try {
            if (c.getAccountPassword() != null && !c.getAccountPassword().isBlank()) {
                c.setAccountPassword(encryptionUtil.decrypt(c.getAccountPassword()));
            }
        } catch (Exception ignored) {}
        try {
            if (c.getNotes() != null && !c.getNotes().isBlank()) {
                c.setNotes(encryptionUtil.decrypt(c.getNotes()));
            }
        } catch (Exception ignored) {}
        return c;
    }

    private List<Credential> decryptAll(List<Credential> list) {
        return list.stream().map(this::decryptSensitiveFields).collect(Collectors.toList());
    }
}
