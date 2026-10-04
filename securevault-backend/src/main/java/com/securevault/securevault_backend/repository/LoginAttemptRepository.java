package com.securevault.securevault_backend.repository;

import com.securevault.securevault_backend.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    List<LoginAttempt> findByUsernameOrderByAttemptTimeDesc(String username);

    List<LoginAttempt> findByIpAddressOrderByAttemptTimeDesc(String ipAddress);

    // Count failed attempts for a username within a window
    long countByUsernameAndSuccessFalseAndAttemptTimeAfter(
            String username, LocalDateTime since);

    // Count failed attempts from an IP within a window
    long countByIpAddressAndSuccessFalseAndAttemptTimeAfter(
            String ipAddress, LocalDateTime since);

    List<LoginAttempt> findByAttemptTimeAfterOrderByAttemptTimeDesc(LocalDateTime since);

    @Query("SELECT l.username, COUNT(l) AS cnt FROM LoginAttempt l " +
           "WHERE l.success = false AND l.attemptTime > :since " +
           "GROUP BY l.username ORDER BY cnt DESC")
    List<Object[]> topAttackedUsers(@Param("since") LocalDateTime since);

    @Query("SELECT l.ipAddress, COUNT(l) AS cnt FROM LoginAttempt l " +
           "WHERE l.success = false AND l.attemptTime > :since " +
           "GROUP BY l.ipAddress ORDER BY cnt DESC")
    List<Object[]> topAttackingIps(@Param("since") LocalDateTime since);

    @Query("SELECT FUNCTION('HOUR', l.attemptTime) AS hr, COUNT(l) FROM LoginAttempt l " +
           "WHERE l.attemptTime > :since GROUP BY FUNCTION('HOUR', l.attemptTime) ORDER BY hr")
    List<Object[]> loginsByHour(@Param("since") LocalDateTime since);

    long countBySuccessTrueAndAttemptTimeAfter(LocalDateTime since);

    long countBySuccessFalseAndAttemptTimeAfter(LocalDateTime since);
}
