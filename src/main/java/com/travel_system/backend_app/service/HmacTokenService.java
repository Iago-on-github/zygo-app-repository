package com.travel_system.backend_app.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Service
public class HmacTokenService {

    // calcula o hashtoken a partir do token puro gerado e da secret key
    public static String calculateTokenHMAC(String secretKey, String pureToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(pureToken.getBytes(StandardCharsets.UTF_8));

            return bytesToHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao calcular HMAC", e);
        }
    }

    // gera um token puro com SecureRandom
    public static String generateRandomPureToken() {
        SecureRandom secureRandom = new SecureRandom();

        // tamanho, em bytes, do token
        int byteLength = 32;
        byte[] tokenBytes = new byte[byteLength];

        secureRandom.nextBytes(tokenBytes);

        // converte para str hex
        return bytesToHex(tokenBytes);
    }

    // converte bytes para string hexadecimal
    private static String bytesToHex(byte[] bytes) {
        StringBuilder strBuilder = new StringBuilder();

        for (byte b : bytes) {
            strBuilder.append(String.format("%02x", b));
        }

        return strBuilder.toString();
    }
}
