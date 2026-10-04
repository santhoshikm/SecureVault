package com.securevault.securevault_backend.repository;

import com.securevault.securevault_backend.entity.AuditLog;
import com.securevault.securevault_backend.entity.AuditLog.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);

    List<AuditLog> findByTimestampAfterOrderByTimestampDesc(LocalDateTime since);

    List<AuditLog> findBySuspiciousTrueOrderByTimestampDesc();

    List<AuditLog> findByActionOrderByTimestampDesc(Action action);

    @Query("SELECT a FROM AuditLog a WHERE a.username = :username " +
           "AND a.timestamp BETWEEN :from AND :to ORDER BY a.timestamp DESC")
    List<AuditLog> findByUsernameBetween(@Param("username") String username,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    @Query("SELECT a.action, COUNT(a) FROM AuditLog a " +
           "WHERE a.timestamp > :since GROUP BY a.action")
    List<Object[]> countByActionSince(@Param("since") LocalDateTime since);

    @Query("SELECT a.username, COUNT(a) AS cnt FROM AuditLog a " +
           "WHERE a.action = 'LOGIN_FAILURE' AND a.timestamp > :since " +
           "GROUP BY a.username ORDER BY cnt DESC")
    List<Object[]> topFailedLoginUsers(@Param("since") LocalDateTime since);

    long countByTimestampAfter(LocalDateTime since);

    long countBySuspiciousTrueAndTimestampAfter(LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.action = :action AND a.timestamp > :since")
    long countByActionSince(@Param("action") Action action, @Param("since") LocalDateTime since);
}
