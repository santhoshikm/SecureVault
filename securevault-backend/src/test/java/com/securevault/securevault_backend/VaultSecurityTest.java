package com.securevault.securevault_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securevault.securevault_backend.entity.Credential;
import com.securevault.securevault_backend.entity.User;
import com.securevault.securevault_backend.repository.CredentialRepository;
import com.securevault.securevault_backend.repository.UserRepository;
import com.securevault.securevault_backend.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VaultSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    private User user1;
    private User user2;
    private String token1;
    private String token2;

    @BeforeEach
    void setUp() {
        credentialRepository.deleteAll();
        userRepository.deleteAll();

        user1 = new User();
        user1.setUsername("alice");
        user1.setEmail("alice@vault.com");
        user1.setPassword(passwordEncoder.encode("password123"));
        user1 = userRepository.save(user1);

        user2 = new User();
        user2.setUsername("bob");
        user2.setEmail("bob@vault.com");
        user2.setPassword(passwordEncoder.encode("password123"));
        user2 = userRepository.save(user2);

        token1 = jwtUtils.generateToken("alice");
        token2 = jwtUtils.generateToken("bob");
    }

    @Test
    void unauthenticatedUserCannotAccessVault() throws Exception {
        mockMvc.perform(get("/api/vault/credentials"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCanAddAndFetchCredentials() throws Exception {
        Credential cred = new Credential("Google", "https://google.com", "alice@gmail.com", "secret123", null);

        mockMvc.perform(post("/api/vault/credentials")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cred)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.siteName", is("Google")))
                .andExpect(jsonPath("$.accountUsername", is("alice@gmail.com")));

        mockMvc.perform(get("/api/vault/credentials")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].siteName", is("Google")));
    }

    @Test
    void userCannotAccessAnotherUsersCredentials() throws Exception {
        Credential cred1 = new Credential("Google", "https://google.com", "alice@gmail.com", "secret123", user1);
        credentialRepository.save(cred1);

        Credential cred2 = new Credential("Github", "https://github.com", "bob_dev", "gitpass123", user2);
        credentialRepository.save(cred2);

        mockMvc.perform(get("/api/vault/credentials")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].siteName", is("Google")));

        mockMvc.perform(get("/api/vault/credentials")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].siteName", is("Github")));
    }

    @Test
    void validationFailsForMissingRequiredFields() throws Exception {
        Credential invalidCred = new Credential("", "", "", "", null);

        mockMvc.perform(post("/api/vault/credentials")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCred)))
                .andExpect(status().isBadRequest());
    }
}
