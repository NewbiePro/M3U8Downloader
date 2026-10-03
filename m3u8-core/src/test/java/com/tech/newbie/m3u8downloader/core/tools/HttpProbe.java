package com.tech.newbie.m3u8downloader.core.tools;

import com.tech.newbie.m3u8downloader.core.common.utils.CurlParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Manual diagnostic: sends the request from a pasted "Copy as cURL" through java.net.http.HttpClient
 * (same parser and client the app uses) over HTTP/1.1 and HTTP/2, and prints status, CDN-related
 * response headers and the start of the body.
 * <p>
 * Run the main method from the IDE, paste the whole cURL command into the console, then enter an
 * empty line. If both versions return 403 while the browser plays fine, the server is rejecting
 * non-browser clients (e.g. TLS fingerprinting), not the headers.
 */
public class HttpProbe {

    private static final String[] INTERESTING_HEADERS = {
            "server", "via", "x-cache", "cf-ray", "cf-mitigated", "x-amz-cf-id", "x-served-by",
            "content-type", "content-length", "location"
    };

    public static void main(String[] args) throws Exception {
        System.out.println("Paste the cURL command, then press Enter on an empty line:");
        String curl = readUntilBlankLine();

        CurlParser.CurlRequest parsed = CurlParser.parse(curl);
        if (parsed == null || parsed.getUrl() == null) {
            System.out.println("Could not parse a URL from the input.");
            return;
        }

        System.out.println();
        System.out.println("URL: " + parsed.getUrl());
        System.out.println("Headers sent (" + parsed.getHeaders().size() + "):");
        parsed.getHeaders().forEach((k, v) -> System.out.println("  " + k + ": " + mask(k, v)));

        for (HttpClient.Version version : HttpClient.Version.values()) {
            System.out.println();
            System.out.println("=== " + version + " ===");
            HttpClient client = HttpClient.newBuilder()
                    .version(version)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(parsed.getUrl()))
                    .timeout(Duration.ofSeconds(30));
            parsed.getHeaders().forEach(builder::header);
            try {
                HttpResponse<byte[]> res = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                System.out.println("status:   " + res.statusCode() + " (negotiated " + res.version() + ")");
                for (String h : INTERESTING_HEADERS) {
                    res.headers().firstValue(h).ifPresent(v -> System.out.println(h + ": " + v));
                }
                byte[] body = res.body();
                System.out.println("body:     " + body.length + " bytes, starts with: "
                        + preview(body));
            } catch (Exception e) {
                System.out.println("request failed: " + e);
            }
        }
    }

    private static String readUntilBlankLine() throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank() && sb.length() > 0) {
                break;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String mask(String key, String value) {
        String k = key.toLowerCase();
        if (k.equals("cookie") || k.equals("authorization")) {
            return value.length() > 8 ? value.substring(0, 8) + "...(" + value.length() + " chars)" : "***";
        }
        return value;
    }

    private static String preview(byte[] body) {
        int n = Math.min(body.length, 80);
        String text = new String(body, 0, n, StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(body.length, 8); i++) {
            hex.append(String.format("%02X ", body[i]));
        }
        return "[" + text + "]  hex: " + hex.toString().trim();
    }
}
