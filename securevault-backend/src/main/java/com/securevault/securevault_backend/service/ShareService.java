package com.securevault.securevault_backend.service;

import com.securevault.securevault_backend.entity.*;
import com.securevault.securevault_backend.repository.CredentialRepository;
import com.securevault.securevault_backend.repository.SharedCredentialRepository;
import com.securevault.securevault_backend.repository.TeamVaultRepository;
import com.securevault.securevault_backend.repository.UserRepository;
import com.securevault.securevault_backend.util.EncryptionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShareService {

    private final SharedCredentialRepository sharedCredentialRepository;
    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final TeamVaultRepository teamVaultRepository;
    private final EncryptionUtil encryptionUtil;

    public ShareService(
            SharedCredentialRepository sharedCredentialRepository,
            CredentialRepository credentialRepository,
            UserRepository userRepository,
            TeamVaultRepository teamVaultRepository,
            EncryptionUtil encryptionUtil) {
        this.sharedCredentialRepository = sharedCredentialRepository;
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.teamVaultRepository = teamVaultRepository;
        this.encryptionUtil = encryptionUtil;
    }

    // ── Share credential ───────────────────────────────────────────────────────
    @Transactional
    public SharedCredential shareCredential(
            Long credentialId,
            String targetUsernameOrEmail,
            String permissionStr,
            Long durationHours,
            LocalDateTime explicitExpiresAt,
            String targetRoleStr,
            Long teamVaultId,
            String ownerUsername) {

        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new RuntimeException("Owner user not found"));

        Credential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new RuntimeException("Credential not found"));

        if (!credential.getUser().getId().equals(owner.getId())) {
            throw new RuntimeException("Unauthorized: You do not own this credential");
        }

        SharePermission permission = SharePermission.fromString(permissionStr);

        SharedCredential share = new SharedCredential();
        share.setCredential(credential);
        share.setOwner(owner);
        share.setPermissionLevel(permission);
        share.setSharedAt(LocalDateTime.now());

        // Calculate expiration if temporary
        if (explicitExpiresAt != null) {
            share.setExpiresAt(explicitExpiresAt);
        } else if (durationHours != null && durationHours > 0) {
            share.setExpiresAt(LocalDateTime.now().plusHours(durationHours));
        }

        // 1. Team Vault sharing
        if (teamVaultId != null) {
            TeamVault vault = teamVaultRepository.findById(teamVaultId)
                    .orElseThrow(() -> new RuntimeException("Team vault not found"));
            share.setTeamVault(vault);
        }
        // 2. Role-based sharing
        else if (targetRoleStr != null && !targetRoleStr.trim().isEmpty()) {
            try {
                Role role = Role.valueOf(targetRoleStr.trim().toUpperCase());
                share.setTargetRole(role);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid target role: " + targetRoleStr);
            }
        }
        // 3. Direct user sharing
        else if (targetUsernameOrEmail != null && !targetUsernameOrEmail.trim().isEmpty()) {
            User targetUser = userRepository.findByUsername(targetUsernameOrEmail)
                    .orElseGet(() -> userRepository.findByEmail(targetUsernameOrEmail)
                            .orElseThrow(() -> new RuntimeException("User to share with not found: " + targetUsernameOrEmail)));
            share.setSharedWithUser(targetUser);
        } else {
            throw new RuntimeException("Must specify a target user, role, or team vault to share with.");
        }

        return sharedCredentialRepository.save(share);
    }

    // ── Get credentials shared with the user ───────────────────────────────────
    public List<SharedCredential> getSharedWithMe(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<Long> seenShareIds = new HashSet<>();
        List<SharedCredential> results = new ArrayList<>();

        // 1. Direct active shares
        List<SharedCredential> direct = sharedCredentialRepository.findActiveDirectSharesForUser(user.getId());
        for (SharedCredential s : direct) {
            if (s.isValid() && seenShareIds.add(s.getId())) {
                results.add(decryptForShare(s));
            }
        }

        // 2. Role-based active shares
        if (user.getRole() != null) {
            List<SharedCredential> roleShares = sharedCredentialRepository.findActiveRoleShares(user.getRole());
            for (SharedCredential s : roleShares) {
                if (s.isValid() && seenShareIds.add(s.getId())) {
                    results.add(decryptForShare(s));
                }
            }
        }

        // 3. Team Vault active shares
        List<TeamVault> vaults = teamVaultRepository.findByMemberOrOwnerId(user.getId());
        if (!vaults.isEmpty()) {
            List<Long> vaultIds = vaults.stream().map(TeamVault::getId).collect(Collectors.toList());
            List<SharedCredential> vaultShares = sharedCredentialRepository.findActiveTeamVaultShares(vaultIds);
            for (SharedCredential s : vaultShares) {
                if (s.isValid() && seenShareIds.add(s.getId())) {
                    results.add(decryptForShare(s));
                }
            }
        }

        return results;
    }

    // ── Get outgoing shares created by the user ───────────────────────────────
    public List<SharedCredential> getMyOutgoingShares(String ownerUsername) {
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return sharedCredentialRepository.findAllByOwnerIdOrderBySharedAtDesc(owner.getId())
                .stream()
                .map(this::decryptForShare)
                .collect(Collectors.toList());
    }

    // ── Update a shared credential ────────────────────────────────────────────
    @Transactional
    public Credential updateSharedCredential(Long shareId, Map<String, String> updates, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SharedCredential share = sharedCredentialRepository.findById(shareId)
                .orElseThrow(() -> new RuntimeException("Share record not found"));

        if (!share.isValid()) {
            throw new RuntimeException("Cannot edit: This share has expired or been revoked");
        }

        boolean isOwner = share.getOwner().getId().equals(user.getId());
        boolean canEdit = share.getPermissionLevel() == SharePermission.EDIT_ACCESS ||
                          share.getPermissionLevel() == SharePermission.FULL_MANAGEMENT;

        if (!isOwner && !canEdit) {
            throw new RuntimeException("Permission denied: You do not have edit rights for this shared credential");
        }

        Credential cred = share.getCredential();
        if (updates.containsKey("siteName") && updates.get("siteName") != null) {
            cred.setSiteName(updates.get("siteName"));
        }
        if (updates.containsKey("siteUrl")) {
            cred.setSiteUrl(updates.get("siteUrl"));
        }
        if (updates.containsKey("accountUsername")) {
            cred.setAccountUsername(updates.get("accountUsername"));
        }
        if (updates.containsKey("accountPassword") && updates.get("accountPassword") != null && !updates.get("accountPassword").isEmpty()) {
            cred.setAccountPassword(encryptionUtil.encrypt(updates.get("accountPassword")));
        }
        if (updates.containsKey("notes") && updates.get("notes") != null) {
            cred.setNotes(encryptionUtil.encrypt(updates.get("notes")));
        }
        cred.setUpdatedAt(LocalDateTime.now());

        return credentialRepository.save(cred);
    }

    // ── Revoke share ──────────────────────────────────────────────────────────
    @Transactional
    public void revokeShare(Long shareId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SharedCredential share = sharedCredentialRepository.findById(shareId)
                .orElseThrow(() -> new RuntimeException("Share record not found"));

        boolean isOwner = share.getOwner().getId().equals(user.getId());
        boolean isFullManager = share.getPermissionLevel() == SharePermission.FULL_MANAGEMENT &&
                                share.getSharedWithUser() != null &&
                                share.getSharedWithUser().getId().equals(user.getId());

        if (!isOwner && !isFullManager) {
            throw new RuntimeException("Unauthorized: Only the owner or a Full Management recipient can revoke this share");
        }

        share.setRevoked(true);
        sharedCredentialRepository.save(share);
    }

    // ── Team Vault Management ─────────────────────────────────────────────────
    @Transactional
    public TeamVault createTeamVault(String name, String description, List<String> memberUsernames, String ownerUsername) {
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        TeamVault vault = new TeamVault(name, description, owner);

        if (memberUsernames != null) {
            for (String u : memberUsernames) {
                userRepository.findByUsername(u.trim())
                        .or(() -> userRepository.findByEmail(u.trim()))
                        .ifPresent(m -> vault.getMembers().add(m));
            }
        }

        return teamVaultRepository.save(vault);
    }

    public List<TeamVault> getMyTeamVaults(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return teamVaultRepository.findByMemberOrOwnerId(user.getId());
    }

    @Transactional
    public TeamVault addMemberToTeamVault(Long vaultId, String usernameOrEmail, String ownerUsername) {
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        TeamVault vault = teamVaultRepository.findById(vaultId)
                .orElseThrow(() -> new RuntimeException("Team vault not found"));

        if (!vault.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("Unauthorized: Only the vault owner can add members");
        }

        User newMember = userRepository.findByUsername(usernameOrEmail)
                .orElseGet(() -> userRepository.findByEmail(usernameOrEmail)
                        .orElseThrow(() -> new RuntimeException("User not found: " + usernameOrEmail)));

        vault.getMembers().add(newMember);
        return teamVaultRepository.save(vault);
    }

    @Transactional
    public TeamVault removeMemberFromTeamVault(Long vaultId, Long memberUserId, String ownerUsername) {
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        TeamVault vault = teamVaultRepository.findById(vaultId)
                .orElseThrow(() -> new RuntimeException("Team vault not found"));

        if (!vault.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("Unauthorized: Only the vault owner can remove members");
        }

        vault.getMembers().removeIf(m -> m.getId().equals(memberUserId));
        return teamVaultRepository.save(vault);
    }

    public List<SharedCredential> getTeamVaultCredentials(Long vaultId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        TeamVault vault = teamVaultRepository.findById(vaultId)
                .orElseThrow(() -> new RuntimeException("Team vault not found"));

        boolean isMember = vault.getOwner().getId().equals(user.getId()) ||
                vault.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()));

        if (!isMember) {
            throw new RuntimeException("Unauthorized: You are not a member of this team vault");
        }

        return sharedCredentialRepository.findByTeamVaultId(vaultId)
                .stream()
                .filter(SharedCredential::isValid)
                .map(this::decryptForShare)
                .collect(Collectors.toList());
    }

    // ── Helper to decrypt for safe viewing ─────────────────────────────────────
    private SharedCredential decryptForShare(SharedCredential share) {
        Credential c = share.getCredential();
        if (c != null) {
            Credential clone = new Credential();
            clone.setId(c.getId());
            clone.setSiteName(c.getSiteName());
            clone.setSiteUrl(c.getSiteUrl());
            clone.setAccountUsername(c.getAccountUsername());
            clone.setCredentialType(c.getCredentialType());
            clone.setCategory(c.getCategory());
            clone.setTags(c.getTags());
            clone.setCreatedAt(c.getCreatedAt());
            clone.setUpdatedAt(c.getUpdatedAt());

            try {
                clone.setAccountPassword(encryptionUtil.decrypt(c.getAccountPassword()));
            } catch (Exception ignored) {
                clone.setAccountPassword(c.getAccountPassword());
            }

            try {
                if (c.getNotes() != null && !c.getNotes().isEmpty()) {
                    clone.setNotes(encryptionUtil.decrypt(c.getNotes()));
                }
            } catch (Exception ignored) {
                clone.setNotes(c.getNotes());
            }

            share.setCredential(clone);
        }
        return share;
    }
}
