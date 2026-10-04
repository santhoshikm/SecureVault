package com.securevault.securevault_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shared_credentials")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SharedCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "credential_id", nullable = false)
    private Credential credential;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnoreProperties({"credentials", "password"})
    private User owner;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shared_with_user_id", nullable = true)
    @JsonIgnoreProperties({"credentials", "password"})
    private User sharedWithUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = true)
    private Role targetRole;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "team_vault_id", nullable = true)
    private TeamVault teamVault;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SharePermission permissionLevel = SharePermission.VIEW_ONLY;

    private LocalDateTime sharedAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = true)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    public SharedCredential() {
    }

    public SharedCredential(Credential credential, User owner, User sharedWithUser, SharePermission permissionLevel) {
        this.credential = credential;
        this.owner = owner;
        this.sharedWithUser = sharedWithUser;
        if (permissionLevel != null) {
            this.permissionLevel = permissionLevel;
        }
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Credential getCredential() {
        return credential;
    }

    public void setCredential(Credential credential) {
        this.credential = credential;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public User getSharedWithUser() {
        return sharedWithUser;
    }

    public void setSharedWithUser(User sharedWithUser) {
        this.sharedWithUser = sharedWithUser;
    }

    public Role getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(Role targetRole) {
        this.targetRole = targetRole;
    }

    public TeamVault getTeamVault() {
        return teamVault;
    }

    public void setTeamVault(TeamVault teamVault) {
        this.teamVault = teamVault;
    }

    public SharePermission getPermissionLevel() {
        return permissionLevel;
    }

    public void setPermissionLevel(SharePermission permissionLevel) {
        this.permissionLevel = permissionLevel;
    }

    public LocalDateTime getSharedAt() {
        return sharedAt;
    }

    public void setSharedAt(LocalDateTime sharedAt) {
        this.sharedAt = sharedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }
}
