package com.tech.newbie.m3u8downloader.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionKey {
    private String method;  // AES-128, etc.
    private String uri;     // Key file URL
    private String iv;      // Initialization Vector (optional)
    private byte[] keyBytes; // Downloaded key bytes

    public boolean isEncrypted() {
        return method != null && !method.equalsIgnoreCase("NONE");
    }
}
