package com.securevault.securevault_backend.repository;

import com.securevault.securevault_backend.entity.Notification;
import com.securevault.securevault_backend.entity.Notification.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUsernameOrderByCreatedAtDesc(String username);

    List<Notification> findByUsernameAndReadFalseOrderByCreatedAtDesc(String username);

    long countByUsernameAndReadFalse(String username);

    List<Notification> findByUsernameAndTypeOrderByCreatedAtDesc(String username, NotificationType type);
}
