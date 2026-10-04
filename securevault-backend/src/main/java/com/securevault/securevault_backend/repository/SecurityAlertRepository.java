package com.securevault.securevault_backend.repository;

import com.securevault.securevault_backend.entity.SecurityAlert;
import com.securevault.securevault_backend.entity.SecurityAlert.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {

    List<SecurityAlert> findByResolvedFalseOrderByCreatedAtDesc();

    List<SecurityAlert> findByUsernameOrderByCreatedAtDesc(String username);

    List<SecurityAlert> findByResolvedFalseAndSeverityOrderByCreatedAtDesc(Severity severity);

    List<SecurityAlert> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    long countByResolvedFalse();

    long countBySeverityAndResolvedFalse(Severity severity);

    @Query("SELECT a.alertType, COUNT(a) FROM SecurityAlert a " +
           "WHERE a.createdAt > :since GROUP BY a.alertType")
    List<Object[]> countByAlertTypeSince(@Param("since") LocalDateTime since);

    @Query("SELECT a.severity, COUNT(a) FROM SecurityAlert a " +
           "WHERE a.resolved = false GROUP BY a.severity")
    List<Object[]> unresolvedCountBySeverity();
}
