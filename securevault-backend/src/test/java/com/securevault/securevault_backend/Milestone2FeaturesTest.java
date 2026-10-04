package com.securevault.securevault_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securevault.securevault_backend.entity.Credential;
import com.securevault.securevault_backend.entity.User;
import com.securevault.securevault_backend.repository.CredentialRepository;
import com.securevault.securevault_backend.repository.SharedCredentialRepository;
import com.securevault.securevault_backend.repository.UserRepository;
import com.securevault.securevault_backend.util.EncryptionUtil;
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

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Milestone2FeaturesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private SharedCredentialRepository sharedCredentialRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private User owner;
    private User recipient;
    private String ownerToken;
    private String recipientToken;

    @BeforeEach
    void setUp() {
        sharedCredentialRepository.deleteAll();
        credentialRepository.deleteAll();
        userRepository.deleteAll();

        owner = new User();
        owner.setUsername("owner_user");
        owner.setEmail("owner@vault.com");
        owner.setPassword(passwordEncoder.encode("password123"));
        owner = userRepository.save(owner);

        recipient = new User();
        recipient.setUsername("recipient_user");
        recipient.setEmail("recipient@vault.com");
        recipient.setPassword(passwordEncoder.encode("password123"));
        recipient = userRepository.save(recipient);

        ownerToken = jwtUtils.generateToken("owner_user");
        recipientToken = jwtUtils.generateToken("recipient_user");
    }

    @Test
    void verifyAES256EncryptionAtRest() throws Exception {
        Credential cred = new Credential("Bank Vault", "https://bank.com", "my_account", "SuperSecretPass123", null);

        String responseStr = mockMvc.perform(post("/api/vault/credentials")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cred)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Credential returnedCred = objectMapper.readValue(responseStr, Credential.class);

        // Fetch direct raw entity from database to verify encryption
        Credential rawFromDb = credentialRepository.findById(returnedCred.getId()).orElseThrow();
        assertNotEquals("SuperSecretPass123", rawFromDb.getAccountPassword());
    }

    @Test
    void verifyCredentialSharingAndPermissionControl() throws Exception {
        Credential cred = new Credential("Shared AWS", "https://aws.amazon.com", "admin_aws", "aws_pass_999", owner);
        cred = credentialRepository.save(cred);

        Map<String, String> shareReq = new HashMap<>();
        shareReq.put("credentialId", String.valueOf(cred.getId()));
        shareReq.put("targetUser", "recipient_user");
        shareReq.put("permissionLevel", "READ_ONLY");

        mockMvc.perform(post("/api/vault/share")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionLevel", is("READ_ONLY")))
                .andExpect(jsonPath("$.sharedWithUser.username", is("recipient_user")));

        mockMvc.perform(get("/api/vault/share/shared-with-me")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].credential.siteName", is("Shared AWS")));
    }

    @Test
    void testPasswordGenerationAndHealthAPIs() throws Exception {
        mockMvc.perform(get("/api/vault/password/generate?length=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password", notNullValue()));

        Map<String, String> healthReq = new HashMap<>();
        healthReq.put("password", "P@ssw0rd12345678");

        mockMvc.perform(post("/api/vault/password/health")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(healthReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating", is("VERY STRONG")));
    }
}
