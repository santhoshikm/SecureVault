package com.securevault.securevault_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Entity
@Table(name = "credentials")
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title / Site Name is required")
    @Column(nullable = false)
    private String siteName;

    @Column(nullable = true)
    private String siteUrl;

    @Column(nullable = true)
    private String accountUsername;

    @Column(nullable = true)
    private String accountPassword;

    // ── NEW FIELDS ────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialType credentialType = CredentialType.WEBSITE_LOGIN;

    /** Free-form folder/group for vault organization */
    @Column
    private String category;

    /** Star/favourite toggle */
    @Column(nullable = false)
    private boolean favorite = false;

    /** Encrypted notes or primary content for SECURE_NOTE type */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Comma-separated tags for extra filtering */
    @Column
    private String tags;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── RELATIONSHIP ──────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    // ── CONSTRUCTORS ──────────────────────────────────────────────────────────

    public Credential() {
    }

    public Credential(String siteName, String siteUrl, String accountUsername,
                      String accountPassword, User user) {
        this.siteName = siteName;
        this.siteUrl = siteUrl;
        this.accountUsername = accountUsername;
        this.accountPassword = accountPassword;
        this.user = user;
    }

    // ── LIFECYCLE ─────────────────────────────────────────────────────────────

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── GETTERS & SETTERS ─────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    @JsonProperty("title")
    public String getTitle() {
        return siteName;
    }

    @JsonProperty("title")
    public void setTitle(String title) {
        if (title != null && !title.trim().isEmpty()) {
            this.siteName = title;
        }
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl;
    }

    public String getAccountUsername() {
        return accountUsername;
    }

    public void setAccountUsername(String accountUsername) {
        this.accountUsername = accountUsername;
    }

    public String getAccountPassword() {
        return accountPassword;
    }

    public void setAccountPassword(String accountPassword) {
        this.accountPassword = accountPassword;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
