package com.tech.newbie.m3u8downloader.core.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j

public class CurlParser {

    public static class CurlRequest {
        private String url;
        private Map<String, String> headers = new HashMap<>();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void addHeader(String key, String value) {
            this.headers.put(key, value);
        }
    }

    public static CurlRequest parse(String curlCommand) {
        if (StringUtils.isBlank(curlCommand)) {
            return null;
        }

        log.info("Parsing cURL command (length: {} chars)", curlCommand.length());

        // Clean up escaped newlines first (common when copying multiline bash curls)
        String command = curlCommand
                .replaceAll("\\\\\\n", " ")
                .replaceAll("\\\\\\r", " ")
                .trim();

        if (!command.startsWith("curl ")) {
            log.warn("Not a curl command, starts with: {}", command.substring(0, Math.min(50, command.length())));
            return null; // Not a curl command
        }

        List<String> tokens = tokenize(command);
        if (tokens.isEmpty()) {
            log.warn("No tokens extracted from curl command");
            return null;
        }

        log.debug("Extracted {} tokens from curl command", tokens.size());
        CurlRequest request = new CurlRequest();

        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("-H".equals(token) || "--header".equals(token)) {
                if (i + 1 < tokens.size()) {
                    String headerFull = tokens.get(++i); // Consume next token as the header value
                    int colonIndex = headerFull.indexOf(":");
                    if (colonIndex > 0) {
                        String key = headerFull.substring(0, colonIndex).trim();
                        String value = headerFull.substring(colonIndex + 1).trim();
                        request.addHeader(key, value);
                    }
                }
            } else if (!token.startsWith("-") && request.getUrl() == null
                    && (token.startsWith("http://") || token.startsWith("https://"))) {
                // If it's not a flag and we haven't set the URL yet, this is likely the URL
                request.setUrl(token);
            }
        }

        // Fallback for URL if it doesn't clearly start with http/https but might just
        // be a domain literal
        if (request.getUrl() == null) {
            for (int i = 1; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if (!token.startsWith("-") && !"-H".equals(tokens.get(i - 1))
                        && !"--header".equals(tokens.get(i - 1))) {
                    request.setUrl(token);
                    break;
                }
            }
        }

        // Log parsing results
        log.info("✓ Parsed cURL - URL: {}", request.getUrl());
        log.info("✓ Extracted {} headers:", request.getHeaders().size());
        request.getHeaders().forEach((key, value) -> {
            String displayValue = value.length() > 50 ? value.substring(0, 50) + "..." : value;
            log.info("  - {}: {}", key, displayValue);
        });

        if (request.getHeaders().isEmpty()) {
            log.warn("⚠ No headers extracted! This may cause anti-bot detection!");
        }

        return request;
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new java.util.ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escapeNext = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escapeNext) {
                currentToken.append(c);
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                // Don't append the quote itself to the token
                continue;
            }

            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                // Don't append the quote itself to the token
                continue;
            }

            if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
                continue;
            }

            currentToken.append(c);
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }
}
