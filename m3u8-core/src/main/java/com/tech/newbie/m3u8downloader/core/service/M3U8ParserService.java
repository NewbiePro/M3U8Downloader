package com.tech.newbie.m3u8downloader.core.service;

import com.tech.newbie.m3u8downloader.core.common.callback.UpdateCallback;
import com.tech.newbie.m3u8downloader.core.model.EncryptionKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tech.newbie.m3u8downloader.core.common.constant.Constant.M3U8_HEADER;

@Slf4j
public class M3U8ParserService {

    private final UpdateCallback<String> strategy;
    private EncryptionKey encryptionKey;

    public M3U8ParserService(UpdateCallback<String> strategy) {
        this.strategy = strategy;
    }

    public EncryptionKey getEncryptionKey() {
        return encryptionKey;
    }

    public List<String> parseM3U8Content(String content, String requestUrl) {
        strategy.update("parsing M3U8..........");
        if (!content.contains(M3U8_HEADER)) {
            strategy.update("invalid m3u8 url");
            return Collections.emptyList();
        }

        log.info("Parsing m3u8 content, length: {} bytes", content.length());
        log.debug("M3U8 content preview:\n{}", content.substring(0, Math.min(500, content.length())));

        // Parse encryption key if present
        parseEncryptionKey(content, requestUrl);

        String urlPath = requestUrl.substring(0, requestUrl.lastIndexOf("/") + 1);
        List<String> tsFiles = content.lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("/"))
                .map(line -> line.startsWith("https") || line.startsWith("http") ? line : urlPath + line)
                .toList();

        if (encryptionKey != null && encryptionKey.isEncrypted()) {
            log.info("✓ M3U8 is ENCRYPTED with {}, key URI: {}", encryptionKey.getMethod(), encryptionKey.getUri());
            strategy.update("Encrypted m3u8 detected - " + encryptionKey.getMethod());
        } else {
            log.info("✗ M3U8 is NOT encrypted");
        }

        strategy.update("There are " + tsFiles.size() + " files");
        return tsFiles;
    }

    private void parseEncryptionKey(String content, String baseUrl) {
        // Parse #EXT-X-KEY tag
        // Example formats:
        // #EXT-X-KEY:METHOD=AES-128,URI="https://example.com/key.key",IV=0x12345678901234567890123456789012
        // #EXT-X-KEY:METHOD=AES-128,URI="key.key"
        // #EXT-X-KEY:METHOD=AES-128,URI=https://example.com/key.key

        log.debug("Searching for EXT-X-KEY in m3u8 content");

        // More flexible pattern - search line by line
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("#EXT-X-KEY:")) {
                log.info("Found EXT-X-KEY line: {}", trimmedLine);
                String keyLine = trimmedLine.substring("#EXT-X-KEY:".length());
                encryptionKey = new EncryptionKey();

                // Parse METHOD - more flexible pattern
                Pattern methodPattern = Pattern.compile("METHOD\\s*=\\s*([^,\\s]+)", Pattern.CASE_INSENSITIVE);
                Matcher methodMatcher = methodPattern.matcher(keyLine);
                if (methodMatcher.find()) {
                    encryptionKey.setMethod(methodMatcher.group(1).trim());
                    log.info("Parsed METHOD: {}", encryptionKey.getMethod());
                }

                // Parse URI - support both quoted and unquoted URIs
                Pattern uriPattern = Pattern.compile("URI\\s*=\\s*\"?([^,\"\\s]+)\"?", Pattern.CASE_INSENSITIVE);
                Matcher uriMatcher = uriPattern.matcher(keyLine);
                if (uriMatcher.find()) {
                    String uri = uriMatcher.group(1).trim();
                    // Convert relative URL to absolute URL
                    if (!uri.startsWith("http")) {
                        String urlPath = baseUrl.substring(0, baseUrl.lastIndexOf("/") + 1);
                        uri = urlPath + uri;
                    }
                    encryptionKey.setUri(uri);
                    log.info("Parsed URI: {}", encryptionKey.getUri());
                }

                // Parse IV (optional)
                Pattern ivPattern = Pattern.compile("IV\\s*=\\s*(0[xX][0-9a-fA-F]+)", Pattern.CASE_INSENSITIVE);
                Matcher ivMatcher = ivPattern.matcher(keyLine);
                if (ivMatcher.find()) {
                    encryptionKey.setIv(ivMatcher.group(1).trim());
                    log.info("Parsed IV: {}", encryptionKey.getIv());
                } else {
                    log.info("No IV specified, will use sequence number");
                }

                log.info("Successfully parsed encryption key: METHOD={}, URI={}, IV={}",
                        encryptionKey.getMethod(), encryptionKey.getUri(), encryptionKey.getIv());
                return; // Found and parsed, exit
            }
        }

        log.info("No EXT-X-KEY tag found in m3u8 content");
    }

}
