package com.securevault.securevault_backend.service;

import com.securevault.securevault_backend.entity.Credential;
import com.securevault.securevault_backend.entity.Notification;
import com.securevault.securevault_backend.entity.Notification.Channel;
import com.securevault.securevault_backend.entity.Notification.NotificationType;
import com.securevault.securevault_backend.repository.CredentialRepository;
import com.securevault.securevault_backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import com.securevault.securevault_backend.entity.User;
import com.securevault.securevault_backend.repository.UserRepository;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    public NotificationService(NotificationRepository notificationRepository,
                               CredentialRepository credentialRepository,
                               UserRepository userRepository,
                               JavaMailSender mailSender) {
        this.notificationRepository = notificationRepository;
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    public Notification createNotification(String username, NotificationType type,
                                           Channel channel, String title, String message) {
        Notification notification = new Notification(username, type, channel, title, message);
        Notification saved = notificationRepository.save(notification);

        // Simulate Email / Push notification delivery
        if (channel == Channel.EMAIL) {
            System.out.println("=======================================================================");
            System.out.println("NOTIFICATION DISPATCH (" + channel + "): " + title);
            System.out.println("To User: " + username + " | Message: " + message);
            System.out.println("=======================================================================");
            
            try {
                User user = userRepository.findByUsername(username).orElse(null);
                if (user != null && user.getEmail() != null) {
                    SimpleMailMessage mailMessage = new SimpleMailMessage();
                    mailMessage.setTo(user.getEmail());
                    mailMessage.setSubject("SecureVault Notification: " + title);
                    mailMessage.setText(message + "\n\nBest Regards,\nSecureVault Team");
                    mailSender.send(mailMessage);
                    System.out.println("Email successfully sent to: " + user.getEmail());
                } else {
                    System.err.println("Failed to send email: User or email not found.");
                }
            } catch (Exception e) {
                System.err.println("Failed to send email. Check SMTP configuration. Error: " + e.getMessage());
            }
        } else if (channel == Channel.PUSH) {
            System.out.println("=======================================================================");
            System.out.println("NOTIFICATION DISPATCH (" + channel + "): " + title);
            System.out.println("To User: " + username + " | Message: " + message);
            System.out.println("=======================================================================");
        }

        return saved;
    }

    public List<Notification> getNotificationsForUser(String username) {
        // Auto-check for password expiration alerts before returning
        checkPasswordExpirationAlerts(username);
        return notificationRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public List<Notification> getUnreadNotificationsForUser(String username) {
        return notificationRepository.findByUsernameAndReadFalseOrderByCreatedAtDesc(username);
    }

    public long getUnreadCount(String username) {
        return notificationRepository.countByUsernameAndReadFalse(username);
    }

    public Notification markAsRead(Long notificationId, String username) {
        Notification notif = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        if (!notif.getUsername().equalsIgnoreCase(username)) {
            throw new RuntimeException("Access denied to notification");
        }
        notif.setRead(true);
        return notificationRepository.save(notif);
    }

    public void markAllAsRead(String username) {
        List<Notification> unread = notificationRepository.findByUsernameAndReadFalseOrderByCreatedAtDesc(username);
        for (Notification n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);
    }

    public void checkPasswordExpirationAlerts(String username) {
        if (username == null || username.isBlank()) return;
        List<Credential> credentials = credentialRepository.findByUserUsername(username);
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        for (Credential c : credentials) {
            if (c.getUpdatedAt() != null && c.getUpdatedAt().isBefore(threeMonthsAgo)) {
                String title = "Password Expiration Alert: " + c.getTitle();
                boolean exists = notificationRepository.findByUsernameOrderByCreatedAtDesc(username)
                        .stream().anyMatch(n -> n.getTitle().equalsIgnoreCase(title));

                if (!exists) {
                    createNotification(
                            username,
                            NotificationType.PASSWORD_EXPIRATION_ALERT,
                            Channel.IN_APP,
                            title,
                            "Credential '" + c.getTitle() + "' has not been updated in over 90 days. We recommend rotating this password."
                    );
                }
            }
        }
    }

    public Map<String, Object> sendTestNotification(String username, String channelType) {
        Channel channel = "EMAIL".equalsIgnoreCase(channelType) ? Channel.EMAIL : ("PUSH".equalsIgnoreCase(channelType) ? Channel.PUSH : Channel.IN_APP);
        NotificationType type = "EMAIL".equalsIgnoreCase(channelType) ? NotificationType.EMAIL_NOTIFICATION : ("PUSH".equalsIgnoreCase(channelType) ? NotificationType.PUSH_NOTIFICATION : NotificationType.SECURITY_NOTIFICATION);

        Notification n = createNotification(
                username,
                type,
                channel,
                "Test Security Notification (" + channel + ")",
                "This is a test notification dispatched via " + channel + " channel for user " + username
        );

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", "SUCCESS");
        res.put("message", "Notification delivered via " + channel);
        res.put("notification", n);
        return res;
    }
}
