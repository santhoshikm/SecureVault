package com.securevault.securevault_backend.controller;

import com.securevault.securevault_backend.entity.AuditLog.Action;
import com.securevault.securevault_backend.entity.User;
import com.securevault.securevault_backend.service.SecurityMonitoringService;
import com.securevault.securevault_backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import com.securevault.securevault_backend.repository.UserRepository;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SecurityMonitoringService monitoringService;

    // REGISTER USER
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @RequestBody User user, HttpServletRequest request) {

        try {

            String message = userService.registerUser(user);

            monitoringService.recordAuditLog(
                    user.getUsername(), Action.REGISTER,
                    "New user registration",
                    resolveIp(request), request.getHeader("User-Agent"));

            Map<String, String> response = new HashMap<>();
            response.put("message", message);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // LOGIN USER
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(
            @RequestBody Map<String, String> loginRequest,
            HttpServletRequest request) {

        String identifier = loginRequest.get("username");
        if (identifier == null || identifier.trim().isEmpty()) {
            identifier = loginRequest.get("email");
        }
        String password = loginRequest.get("password");
        String ip        = resolveIp(request);
        String userAgent = request.getHeader("User-Agent");

        try {
            String token = userService.authenticateUser(identifier, password);

            // Record successful login
            monitoringService.recordLoginAttempt(identifier, ip, userAgent, true, null);

            // Resolve actual username from DB for response
            String username = userRepository.findByUsernameOrEmail(identifier, identifier)
                    .map(u -> u.getUsername()).orElse(identifier);

            Map<String, String> response = new HashMap<>();
            response.put("token",    token);
            response.put("username", username);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            // Record failed login
            monitoringService.recordLoginAttempt(identifier, ip, userAgent, false, e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // FORGOT PASSWORD
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body,
                                            HttpServletRequest request) {
        String email = body.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required."));
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found with this email."));
        }

        User user = userOpt.get();
        String token = UUID.randomUUID().toString();
        userService.createPasswordResetTokenForUser(user, token);

        monitoringService.recordAuditLog(
                user.getUsername(), Action.PASSWORD_RESET_REQUEST,
                "Password reset requested",
                resolveIp(request), request.getHeader("User-Agent"));

        System.out.println("=======================================================================");
        System.out.println("PASSWORD RESET LINK (SIMULATED EMAIL):");
        System.out.println("http://localhost:5173/reset-password?token=" + token);
        System.out.println("=======================================================================");

        return ResponseEntity.ok(Map.of("message", "Password reset email sent."));
    }

    // RESET PASSWORD
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body,
                                           HttpServletRequest request) {
        String token = body.get("token");
        String newPassword = body.get("password");

        if (token == null || newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token and new password are required."));
        }

        try {
            userService.resetPassword(token, newPassword);
            // Audit: find the username from the token if possible
            monitoringService.recordAuditLog(
                    "system", Action.PASSWORD_RESET_SUCCESS,
                    "Password successfully reset via token",
                    resolveIp(request), request.getHeader("User-Agent"));
            return ResponseEntity.ok(Map.of("message", "Password successfully reset."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET CURRENT USER PROFILE (role, MFA status, email)
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Optional<com.securevault.securevault_backend.entity.User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found."));
            }
            com.securevault.securevault_backend.entity.User u = userOpt.get();
            Map<String, Object> profile = new HashMap<>();
            profile.put("id", u.getId());
            profile.put("username", u.getUsername());
            profile.put("email", u.getEmail());
            profile.put("role", u.getRole().name());
            profile.put("mfaEnabled", u.isMfaEnabled());
            profile.put("authProvider", u.getAuthProvider());
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // TOGGLE MFA — PATCH /api/auth/mfa/toggle
    @PatchMapping("/mfa/toggle")
    public ResponseEntity<?> toggleMfa(HttpServletRequest request) {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Optional<com.securevault.securevault_backend.entity.User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found."));
            }
            com.securevault.securevault_backend.entity.User u = userOpt.get();
            boolean newState = !u.isMfaEnabled();
            u.setMfaEnabled(newState);
            userRepository.save(u);

            monitoringService.recordAuditLog(
                    username,
                    newState ? Action.MFA_ENABLED : Action.MFA_DISABLED,
                    "MFA " + (newState ? "enabled" : "disabled") + " by user",
                    resolveIp(request), request.getHeader("User-Agent"));

            return ResponseEntity.ok(Map.of(
                    "mfaEnabled", newState,
                    "message", "MFA " + (newState ? "enabled" : "disabled") + " successfully."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────


    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
