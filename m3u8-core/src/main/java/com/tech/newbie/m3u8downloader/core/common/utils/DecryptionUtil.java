package com.tech.newbie.m3u8downloader.core.common.utils;

import com.tech.newbie.m3u8downloader.core.model.EncryptionKey;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;

@Slf4j
public class DecryptionUtil {

    private DecryptionUtil() {
        // Utility class
    }

    /**
     * Decrypt AES-128 encrypted data
     *
     * @param encryptedData Encrypted ts file data
     * @param key Encryption key information
     * @param sequence Sequence number (used as IV if not specified)
     * @return Decrypted data
     */
    public static byte[] decryptAES128(byte[] encryptedData, EncryptionKey key, int sequence) {
        try {
            if (key == null || key.getKeyBytes() == null) {
                log.warn("No encryption key provided, returning original data");
                return encryptedData;
            }

            SecretKeySpec secretKey = new SecretKeySpec(key.getKeyBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            // Get or generate IV
            byte[] ivBytes = getIV(key, sequence);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            log.error("Failed to decrypt data: {}", e.getMessage(), e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Get IV bytes. If IV is specified in key, use it; otherwise use sequence number
     */
    private static byte[] getIV(EncryptionKey key, int sequence) {
        if (key.getIv() != null && !key.getIv().isEmpty()) {
            // Remove "0x" prefix if present
            String ivHex = key.getIv().startsWith("0x") || key.getIv().startsWith("0X")
                    ? key.getIv().substring(2)
                    : key.getIv();
            return hexStringToByteArray(ivHex);
        } else {
            // Use sequence number as IV (16 bytes, big-endian)
            byte[] iv = new byte[16];
            BigInteger seqBigInt = BigInteger.valueOf(sequence);
            byte[] seqBytes = seqBigInt.toByteArray();
            System.arraycopy(seqBytes, 0, iv, 16 - seqBytes.length, seqBytes.length);
            return iv;
        }
    }

    /**
     * Convert hex string to byte array
     */
    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
