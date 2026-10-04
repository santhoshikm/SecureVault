package com.securevault.securevault_backend.repository;

import com.securevault.securevault_backend.entity.Role;
import com.securevault.securevault_backend.entity.SharedCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SharedCredentialRepository extends JpaRepository<SharedCredential, Long> {

    List<SharedCredential> findBySharedWithUserId(Long sharedWithUserId);

    List<SharedCredential> findByOwnerId(Long ownerId);

    List<SharedCredential> findByCredentialId(Long credentialId);

    List<SharedCredential> findByTeamVaultId(Long teamVaultId);

    @Query("SELECT s FROM SharedCredential s WHERE s.revoked = false AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP) AND s.sharedWithUser.id = :userId")
    List<SharedCredential> findActiveDirectSharesForUser(@Param("userId") Long userId);

    @Query("SELECT s FROM SharedCredential s WHERE s.revoked = false AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP) AND s.targetRole = :role")
    List<SharedCredential> findActiveRoleShares(@Param("role") Role role);

    @Query("SELECT s FROM SharedCredential s WHERE s.revoked = false AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP) AND s.teamVault.id IN :vaultIds")
    List<SharedCredential> findActiveTeamVaultShares(@Param("vaultIds") Collection<Long> vaultIds);

    @Query("SELECT s FROM SharedCredential s WHERE s.owner.id = :ownerId ORDER BY s.sharedAt DESC")
    List<SharedCredential> findAllByOwnerIdOrderBySharedAtDesc(@Param("ownerId") Long ownerId);
}
