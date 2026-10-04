package com.securevault.securevault_backend.service;

import com.securevault.securevault_backend.entity.User;
import com.securevault.securevault_backend.repository.UserRepository;
import com.securevault.securevault_backend.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import com.securevault.securevault_backend.entity.PasswordResetToken;
import com.securevault.securevault_backend.repository.PasswordResetTokenRepository;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    public String registerUser(User user) {

        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Error: Username is already taken!");
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Error: Email is already in use!");
        }

        // Convert the user's actual password into a BCrypt hash
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Save user in PostgreSQL
        userRepository.save(user);

        return "User registered successfully!";
    }

    public String authenticateUser(String identifier, String rawPassword) {

        Optional<User> userOptional =
                userRepository.findByUsernameOrEmail(identifier, identifier);

        if (userOptional.isPresent()) {

            User user = userOptional.get();

            // Compare entered password with BCrypt password
            if (passwordEncoder.matches(
                    rawPassword,
                    user.getPassword())) {

                // Generate JWT token
                return jwtUtils.generateToken(user.getUsername());
            }
        }

        throw new RuntimeException(
                "Invalid username/email or password!"
        );
    }

    public void createPasswordResetTokenForUser(User user, String token) {
        PasswordResetToken myToken = new PasswordResetToken(token, user, LocalDateTime.now().plusMinutes(15));
        tokenRepository.save(myToken);
    }

    public void resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);
        if (tokenOpt.isEmpty()) {
            throw new RuntimeException("Invalid token");
        }

        PasswordResetToken passToken = tokenOpt.get();
        if (passToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token has expired");
        }

        User user = passToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Optional: delete token after use
        tokenRepository.delete(passToken);
    }
}
