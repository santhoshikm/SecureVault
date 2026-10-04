package com.securevault.securevault_backend.service;

import com.securevault.securevault_backend.util.EncryptionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EncryptionService {

    @Autowired
    private EncryptionUtil encryptionUtil;

    public String encryptData(String plainText) {
        return encryptionUtil.encrypt(plainText);
    }

    public String decryptData(String cipherText) {
        return encryptionUtil.decrypt(cipherText);
    }
}
