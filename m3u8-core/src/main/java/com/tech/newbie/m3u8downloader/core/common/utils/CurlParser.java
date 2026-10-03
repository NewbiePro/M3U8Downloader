package com.tech.newbie.m3u8downloader.core.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses the output of DevTools "Copy as cURL" from Chrome and Firefox.
 * <p>
 * Supported formats:
 * <ul>
 * <li>POSIX / bash: {@code 'single quotes'}, {@code $'ANSI-C quotes'} (used when a value
 * contains {@code '}, {@code !} or control chars), {@code \} line continuations</li>
 * <li>Windows cmd: values wrapped in {@code ^"...^"}, {@code ^} escapes, {@code ^} line
 * continuations, {@code \"} / {@code \\} inside quotes</li>
 * </ul>
 * Chrome passes cookies via {@code -b}; Firefox passes them via {@code -H 'Cookie: ...'}.
 */
@Slf4j
public class CurlParser {

    /**
     * Headers dropped before building the request:
     * <ul>
     * <li>connection, content-length, expect, host, upgrade: java.net.http.HttpClient throws
     * "restricted header name" for these and manages them itself</li>
     * <li>accept-encoding: HttpClient never decompresses, so a gzip/br/zstd body would reach the
     * m3u8 parser / ffmpeg as garbage. Firefox always copies this header.</li>
     * </ul>
     */
    private static final Set<String> DROPPED_HEADERS = Set.of(
            "connection", "content-length", "expect", "host", "upgrade", "accept-encoding");

    /** Flags whose next token is a value, never the URL. */
    private static final Set<String> VALUE_FLAGS = Set.of(
            "-H", "--header", "-b", "--cookie", "-A", "--user-agent", "-e", "--referer",
            "-X", "--request", "-d", "--data", "--data-raw", "--data-binary", "--data-urlencode",
            "-u", "--user", "-x", "--proxy", "-o", "--output");

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
            if (DROPPED_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                log.info("Skipping header not supported by HttpClient: {}", key);
                return;
            }
            this.headers.put(key, value);
        }
    }

    public static CurlRequest parse(String curlCommand) {
        if (StringUtils.isBlank(curlCommand)) {
            return null;
        }

        log.info("Parsing cURL command (length: {} chars)", curlCommand.length());

        String command = curlCommand.trim();
        if (!command.startsWith("curl ")) {
            log.warn("Not a curl command, starts with: {}", command.substring(0, Math.min(50, command.length())));
            return null;
        }

        boolean windowsCmd = command.contains("^\"");
        List<String> tokens = windowsCmd ? tokenizeCmd(command) : tokenizePosix(command);
        if (tokens.isEmpty()) {
            log.warn("No tokens extracted from curl command");
            return null;
        }

        log.debug("Extracted {} tokens from curl command ({} format)", tokens.size(), windowsCmd ? "cmd" : "posix");
        CurlRequest request = new CurlRequest();

        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            boolean hasValue = i + 1 < tokens.size();
            if (("-H".equals(token) || "--header".equals(token)) && hasValue) {
                String headerFull = tokens.get(++i);
                int colonIndex = headerFull.indexOf(':');
                if (colonIndex > 0) {
                    request.addHeader(headerFull.substring(0, colonIndex).trim(),
                            headerFull.substring(colonIndex + 1).trim());
                }
            } else if (("-b".equals(token) || "--cookie".equals(token)) && hasValue) {
                request.addHeader("Cookie", tokens.get(++i));
            } else if (("-A".equals(token) || "--user-agent".equals(token)) && hasValue) {
                request.addHeader("User-Agent", tokens.get(++i));
            } else if (("-e".equals(token) || "--referer".equals(token)) && hasValue) {
                request.addHeader("Referer", tokens.get(++i));
            } else if ("--url".equals(token) && hasValue) {
                request.setUrl(tokens.get(++i));
            } else if (VALUE_FLAGS.contains(token) && hasValue) {
                i++; // skip the value of flags we don't use
            } else if (!token.startsWith("-") && request.getUrl() == null) {
                request.setUrl(token);
            }
        }

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

    /** bash/POSIX shell word splitting: '...', "...", $'...', backslash escapes and continuations. */
    private static List<String> tokenizePosix(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inToken = false;
        int n = input.length();

        for (int i = 0; i < n; i++) {
            char c = input.charAt(i);

            if (c == '\\') {
                if (i + 1 < n && (input.charAt(i + 1) == '\n' || input.charAt(i + 1) == '\r')) {
                    // line continuation
                    i++;
                    if (input.charAt(i) == '\r' && i + 1 < n && input.charAt(i + 1) == '\n') {
                        i++;
                    }
                    continue;
                }
                if (i + 1 < n) {
                    current.append(input.charAt(++i));
                    inToken = true;
                }
                continue;
            }

            if (c == '$' && i + 1 < n && input.charAt(i + 1) == '\'') {
                i = readAnsiC(input, i + 2, current);
                inToken = true;
                continue;
            }

            if (c == '\'') {
                int end = input.indexOf('\'', i + 1);
                if (end < 0) {
                    end = n;
                }
                current.append(input, i + 1, end);
                i = end;
                inToken = true;
                continue;
            }

            if (c == '"') {
                i++;
                while (i < n && input.charAt(i) != '"') {
                    char d = input.charAt(i);
                    if (d == '\\' && i + 1 < n && "\"\\$`".indexOf(input.charAt(i + 1)) >= 0) {
                        d = input.charAt(++i);
                    }
                    current.append(d);
                    i++;
                }
                inToken = true;
                continue;
            }

            if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
                continue;
            }

            current.append(c);
            inToken = true;
        }

        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /**
     * Reads the body of a $'...' string starting at {@code start}; returns the index of the
     * closing quote.
     */
    private static int readAnsiC(String input, int start, StringBuilder out) {
        int n = input.length();
        int i = start;
        while (i < n) {
            char c = input.charAt(i);
            if (c == '\'') {
                return i;
            }
            if (c == '\\' && i + 1 < n) {
                char e = input.charAt(++i);
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'x' -> {
                        int len = hexLength(input, i + 1, 2);
                        out.append((char) Integer.parseInt(input.substring(i + 1, i + 1 + len), 16));
                        i += len;
                    }
                    case 'u' -> {
                        int len = hexLength(input, i + 1, 4);
                        out.append((char) Integer.parseInt(input.substring(i + 1, i + 1 + len), 16));
                        i += len;
                    }
                    default -> out.append(e); // \\ \' \" and anything else
                }
                i++;
                continue;
            }
            out.append(c);
            i++;
        }
        return n;
    }

    private static int hexLength(String s, int from, int max) {
        int len = 0;
        while (len < max && from + len < s.length() && Character.digit(s.charAt(from + len), 16) >= 0) {
            len++;
        }
        return len;
    }

    /**
     * Windows cmd format produced by Chrome/Firefox "Copy as cURL (cmd)": first undo cmd's
     * {@code ^} escaping, then split like the MSVC runtime ({@code "} quoting, {@code \"} and
     * {@code \\} escapes).
     */
    private static List<String> tokenizeCmd(String input) {
        // 1) cmd layer: ^<newline> is a continuation, ^X is a literal X, %^ guards env-var expansion
        StringBuilder unescaped = new StringBuilder();
        int n = input.length();
        for (int i = 0; i < n; i++) {
            char c = input.charAt(i);
            if (c == '^' && i + 1 < n) {
                char next = input.charAt(++i);
                if (next == '\r' || next == '\n') {
                    // continuation; the browser may emit an extra blank line after it
                    while (i + 1 < n && (input.charAt(i + 1) == '\r' || input.charAt(i + 1) == '\n')) {
                        i++;
                    }
                    unescaped.append(' ');
                } else {
                    unescaped.append(next);
                }
                continue;
            }
            unescaped.append(c);
        }

        // 2) argv layer
        String s = unescaped.toString();
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        boolean inToken = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && (s.charAt(i + 1) == '"' || s.charAt(i + 1) == '\\')) {
                current.append(s.charAt(++i));
                inToken = true;
            } else if (c == '"') {
                inQuote = !inQuote;
                inToken = true;
            } else if (Character.isWhitespace(c) && !inQuote) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
