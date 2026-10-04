package com.securevault.securevault_backend.repository;

import com.securevault.securevault_backend.entity.TeamVault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamVaultRepository extends JpaRepository<TeamVault, Long> {

    List<TeamVault> findByOwnerId(Long ownerId);

    @Query("SELECT DISTINCT tv FROM TeamVault tv LEFT JOIN tv.members m WHERE tv.owner.id = :userId OR m.id = :userId")
    List<TeamVault> findByMemberOrOwnerId(@Param("userId") Long userId);
}
