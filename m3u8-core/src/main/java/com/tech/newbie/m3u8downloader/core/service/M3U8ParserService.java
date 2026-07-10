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

        // Trim content and check for m3u8 header (case-insensitive)
        String trimmedContent = content.trim();
        if (!trimmedContent.toUpperCase().contains(M3U8_HEADER.toUpperCase())) {
            log.error("Invalid m3u8 content. First 100 chars: [{}]",
                trimmedContent.substring(0, Math.min(100, trimmedContent.length())));
            strategy.update("invalid m3u8 url");
            return Collections.emptyList();
        }

        // Use trimmed content for parsing
        content = trimmedContent;

        log.info("Parsing m3u8 content, length: {} bytes", content.length());

        // Save m3u8 content to file for debugging
        try {
            java.io.File debugFile = new java.io.File(System.getProperty("user.home"), "m3u8_debug.txt");
            java.nio.file.Files.writeString(debugFile.toPath(), content);
            log.info("M3U8 content saved to: {}", debugFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to save m3u8 debug file: {}", e.getMessage());
        }

        // Log first 30 lines for inspection
        String[] lines = content.split("\n");
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < Math.min(30, lines.length); i++) {
            preview.append(String.format("Line %2d: %s\n", i+1, lines[i]));
        }
        log.info("M3U8 content first 30 lines:\n{}", preview);

        // Parse encryption key if present
        parseEncryptionKey(content, requestUrl);

        // Check if requestUrl is valid for constructing ts URLs
        final boolean canConstructUrls = requestUrl.startsWith("http://") || requestUrl.startsWith("https://");

        final String urlPath;
        if (canConstructUrls) {
            urlPath = requestUrl.substring(0, requestUrl.lastIndexOf("/") + 1);
            log.info("Using base URL for ts files: {}", urlPath);
        } else {
            urlPath = null;
            log.warn("⚠ Cannot construct ts URLs from file:// path - ts URLs must be absolute or BASE_URL must be provided");
        }

        List<String> tsFiles = content.lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("/"))
                .map(line -> {
                    // If ts URL is already absolute, use it
                    if (line.startsWith("https") || line.startsWith("http")) {
                        return line;
                    }
                    // If we cannot construct URLs and ts URL is relative, throw error
                    if (!canConstructUrls) {
                        log.error("✗ Relative ts URL found but cannot construct full URL: {}", line);
                        log.error("✗ Please provide BASE_URL in input (on a new line): BASE_URL=https://domain/path/");
                        throw new RuntimeException("本地 m3u8 文件包含相對路徑的 ts URL，無法下載。\n請在輸入框中添加一行：\nBASE_URL=https://原始網域/路徑/");
                    }
                    // Normal case: construct absolute URL from relative path
                    return urlPath + line;
                })
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
        // #EXT-X-KEY:METHOOD=AES-128,URI="/videos/xxx/ts.key?query"  (typo in server)
        // #EXT-X-KEY:METHOD=AES-128,URI="key.key"

        log.debug("Searching for EXT-X-KEY in m3u8 content");

        // More flexible pattern - search line by line
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.toUpperCase().startsWith("#EXT-X-KEY:")) {
                log.info("Found EXT-X-KEY line: {}", trimmedLine);
                String keyLine = trimmedLine.substring(trimmedLine.indexOf(":") + 1);
                encryptionKey = new EncryptionKey();

                // Parse METHOD/METHOOD - support common typos
                Pattern methodPattern = Pattern.compile("(METHOD|METHOOD)\\s*=\\s*([^,\\s]+)", Pattern.CASE_INSENSITIVE);
                Matcher methodMatcher = methodPattern.matcher(keyLine);
                if (methodMatcher.find()) {
                    encryptionKey.setMethod(methodMatcher.group(2).trim());
                    log.info("Parsed METHOD: {} (from key: {})", encryptionKey.getMethod(), methodMatcher.group(1));
                }

                // Parse URI - support quoted URIs with any content including query strings
                Pattern uriPattern = Pattern.compile("URI\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
                Matcher uriMatcher = uriPattern.matcher(keyLine);
                if (uriMatcher.find()) {
                    String uri = uriMatcher.group(1).trim();
                    log.info("Raw URI from m3u8: {}", uri);

                    // Convert relative URL to absolute URL
                    if (!uri.startsWith("http")) {
                        // Extract base URL (protocol + domain + path)
                        if (uri.startsWith("/")) {
                            // Absolute path: /videos/xxx/ts.key
                            // Extract protocol and domain from baseUrl
                            java.net.URI baseUri = java.net.URI.create(baseUrl);
                            uri = baseUri.getScheme() + "://" + baseUri.getAuthority() + uri;
                        } else {
                            // Relative path: ts.key
                            String urlPath = baseUrl.substring(0, baseUrl.lastIndexOf("/") + 1);
                            uri = urlPath + uri;
                        }
                    }
                    encryptionKey.setUri(uri);
                    log.info("Resolved absolute URI: {}", encryptionKey.getUri());
                } else {
                    log.warn("Failed to parse URI from line: {}", keyLine);
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
