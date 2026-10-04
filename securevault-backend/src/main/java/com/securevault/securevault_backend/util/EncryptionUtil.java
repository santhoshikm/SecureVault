package com.securevault.securevault_backend.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    @Value("${encryption.key}")
    private String encryptionKey;

    private SecretKeySpec getSecretKey() {

        byte[] keyBytes =
                encryptionKey.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "Encryption key must be exactly 32 bytes for AES-256"
            );
        }

        return new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) {

        try {

            byte[] iv = new byte[IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    getSecretKey(),
                    parameterSpec
            );

            byte[] encryptedBytes =
                    cipher.doFinal(
                            plainText.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            byte[] encryptedData =
                    new byte[iv.length + encryptedBytes.length];

            System.arraycopy(
                    iv,
                    0,
                    encryptedData,
                    0,
                    iv.length
            );

            System.arraycopy(
                    encryptedBytes,
                    0,
                    encryptedData,
                    iv.length,
                    encryptedBytes.length
            );

            return Base64.getEncoder()
                    .encodeToString(encryptedData);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to encrypt credential",
                    e
            );
        }
    }

    public String decrypt(String encryptedText) {

        try {

            byte[] encryptedData =
                    Base64.getDecoder()
                            .decode(encryptedText);

            byte[] iv = new byte[IV_LENGTH];

            System.arraycopy(
                    encryptedData,
                    0,
                    iv,
                    0,
                    IV_LENGTH
            );

            byte[] encryptedBytes =
                    new byte[
                            encryptedData.length
                                    - IV_LENGTH
                    ];

            System.arraycopy(
                    encryptedData,
                    IV_LENGTH,
                    encryptedBytes,
                    0,
                    encryptedBytes.length
            );

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(
                            GCM_TAG_LENGTH,
                            iv
                    );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getSecretKey(),
                    parameterSpec
            );

            byte[] decryptedBytes =
                    cipher.doFinal(encryptedBytes);

            return new String(
                    decryptedBytes,
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to decrypt credential",
                    e
            );
        }
    }
}
