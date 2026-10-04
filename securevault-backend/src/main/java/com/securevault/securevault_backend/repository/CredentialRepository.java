package com.securevault.securevault_backend.repository;

import com.securevault.securevault_backend.entity.Credential;
import com.securevault.securevault_backend.entity.CredentialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, Long> {

    List<Credential> findByUserId(Long userId);

    List<Credential> findByUserUsername(String username);

    List<Credential> findByUserIdAndCredentialType(Long userId, CredentialType type);

    List<Credential> findByUserIdAndFavoriteTrue(Long userId);

    List<Credential> findByUserIdAndCategoryIgnoreCase(Long userId, String category);

    @Query("SELECT c FROM Credential c WHERE c.user.id = :userId AND " +
           "(LOWER(c.siteName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(c.accountUsername) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(c.category) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(c.tags) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Credential> searchByUser(@Param("userId") Long userId, @Param("query") String query);

    @Query("SELECT c FROM Credential c WHERE c.user.id = :userId AND " +
           "(:type IS NULL OR c.credentialType = :type) AND " +
           "(:category IS NULL OR LOWER(c.category) = LOWER(:category)) AND " +
           "(:favorite IS NULL OR c.favorite = :favorite)")
    List<Credential> filterByUser(
            @Param("userId") Long userId,
            @Param("type") CredentialType type,
            @Param("category") String category,
            @Param("favorite") Boolean favorite);
}
