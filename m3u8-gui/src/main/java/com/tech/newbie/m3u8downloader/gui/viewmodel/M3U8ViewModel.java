package com.tech.newbie.m3u8downloader.gui.viewmodel;

import com.tech.newbie.m3u8downloader.core.common.utils.HttpClientFactory;
import com.tech.newbie.m3u8downloader.core.common.utils.TimeUtil;
import com.tech.newbie.m3u8downloader.core.model.EncryptionKey;
import com.tech.newbie.m3u8downloader.core.model.Video;
import com.tech.newbie.m3u8downloader.core.service.M3U8ParserService;
import com.tech.newbie.m3u8downloader.gui.service.MediaService;
import com.tech.newbie.m3u8downloader.core.service.MergeService;
import com.tech.newbie.m3u8downloader.core.service.strategy.download.VirtualThreadDownloadService;
import com.tech.newbie.m3u8downloader.gui.strategy.ui.AlertUpdateStrategy;
import com.tech.newbie.m3u8downloader.gui.strategy.ui.ProgressBarUpdateStrategy;
import com.tech.newbie.m3u8downloader.gui.strategy.ui.StatusTextUpdateStrategy;
import com.tech.newbie.m3u8downloader.core.common.callback.UpdateCallback;
import com.tech.newbie.m3u8downloader.gui.strategy.ui.TimeLabelUpdateStrategy;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Getter
@Setter
public class M3U8ViewModel {

    // Properties for binding to the UI
    private final StringProperty statusText = new SimpleStringProperty();
    private final DoubleProperty progressBar = new SimpleDoubleProperty();
    private final StringProperty timeLabel = new SimpleStringProperty();
    private final StringProperty inputArea = new SimpleStringProperty();
    private final StringProperty fileName = new SimpleStringProperty();
    private final ObjectProperty<Map<String, Long>> phaseTimes = new SimpleObjectProperty<>();
    private String path;
    // UI strategy
    private final UpdateCallback<String> statusUpdateStrategy = new StatusTextUpdateStrategy(statusText);
    private final UpdateCallback<Double> progressBarUpdateStrategy = new ProgressBarUpdateStrategy(progressBar);
    private final UpdateCallback<String> alertUpdateStrategy = new AlertUpdateStrategy();
    private final UpdateCallback<Long> timeLabelUpdateStrategy = new TimeLabelUpdateStrategy(timeLabel);

    // dependency
    private final M3U8ParserService m3U8ParserService = new M3U8ParserService(statusUpdateStrategy);

    private final VirtualThreadDownloadService downloadService = new VirtualThreadDownloadService(statusUpdateStrategy,
            progressBarUpdateStrategy);

    private final MergeService mergeService = new MergeService(statusUpdateStrategy, alertUpdateStrategy);

    private final MediaService mediaService = new MediaService();

    public void startDownload() {
        Thread thread = new Thread(this::performDownload);
        // Set as a daemon thread to ensure it terminates once the JAVAFX application
        // thread(UI) terminate
        thread.setDaemon(true);
        thread.start();
    }

    private void performDownload() {
        try {
            long start = System.currentTimeMillis();

            String rawInput = inputArea.get().trim();
            String m3u8Url = rawInput;
            String m3u8Content = null;
            Map<String, String> headers = null;
            String baseUrlOverride = null;

            // Check if input contains BASE_URL for local m3u8 files
            if (rawInput.contains("BASE_URL=")) {
                String[] lines = rawInput.split("\\n");
                for (String line : lines) {
                    if (line.trim().startsWith("BASE_URL=")) {
                        baseUrlOverride = line.trim().substring("BASE_URL=".length()).trim();
                        log.info("Found BASE_URL override: {}", baseUrlOverride);
                        // Remove BASE_URL line from input
                        rawInput = rawInput.replace(line, "").trim();
                        m3u8Url = rawInput;
                        break;
                    }
                }
            }

            // Check if input is a local file path or m3u8 content
            if (rawInput.startsWith("#EXTM3U") || rawInput.startsWith("#extm3u")) {
                // Direct m3u8 content pasted
                log.info("Detected direct m3u8 content input");
                m3u8Content = rawInput;
                statusUpdateStrategy.update("Parsing pasted m3u8 content...");
                alertUpdateStrategy.update("請在輸入框下方輸入完整的 m3u8 URL（用於解析相對路徑）");
                // We need the base URL for relative paths, ask user or try to extract from content
                m3u8Url = "https://example.com/video.m3u8"; // Placeholder
            } else if (rawInput.startsWith("file://") || new java.io.File(rawInput).exists()) {
                // Local file path
                log.info("Detected local file path: {}", rawInput);
                File m3u8File;
                if (rawInput.startsWith("file://")) {
                    // Convert file:// URI to File
                    try {
                        m3u8File = new File(new URI(rawInput));
                    } catch (Exception e) {
                        // Fallback: strip file:// and try as path
                        m3u8File = new File(rawInput.substring(7));
                    }
                } else {
                    m3u8File = new File(rawInput);
                }

                m3u8Content = Files.readString(m3u8File.toPath());
                // Use toURI() to properly convert Windows paths (e.g., D:\path -> file:///D:/path)
                m3u8Url = m3u8File.toURI().toString();
                log.info("Converted local file path to URI: {}", m3u8Url);
                statusUpdateStrategy.update("Reading local m3u8 file...");
            } else if (rawInput.startsWith("curl ")) {
                com.tech.newbie.m3u8downloader.core.common.utils.CurlParser.CurlRequest curlReq = com.tech.newbie.m3u8downloader.core.common.utils.CurlParser
                        .parse(rawInput);
                if (curlReq != null && curlReq.getUrl() != null) {
                    m3u8Url = curlReq.getUrl();
                    headers = curlReq.getHeaders();
                }
            } else {
                m3u8Url = rawInput.replaceAll("\\s+", StringUtils.EMPTY);
            }

            // clear previous output
            resetUIState();

            HttpClient client = HttpClientFactory.createSimpleInsecureHttpClient();

            // Extract base URL for Referer/Origin
            String baseUrl;
            if (m3u8Url.startsWith("http")) {
                java.net.URI uri = java.net.URI.create(m3u8Url);
                baseUrl = uri.getScheme() + "://" + uri.getAuthority();
            } else {
                baseUrl = "https://example.com"; // Fallback for local files
            }

            long parseStart = System.currentTimeMillis();

            // 1- Get m3u8 content (from network or local)
            if (m3u8Content == null) {
                // Download from network
                // 0- build request
                HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(m3u8Url));
                if (headers != null && !headers.isEmpty()) {
                    headers.forEach(builder::header);

                    // Add Referer and Origin if missing (critical for anti-hotlinking)
                    if (!headers.containsKey("Referer") && !headers.containsKey("referer")) {
                        builder.header("Referer", baseUrl + "/");
                        log.info("Added missing Referer: {}", baseUrl + "/");
                    }
                    if (!headers.containsKey("Origin") && !headers.containsKey("origin")) {
                        builder.header("Origin", baseUrl);
                        log.info("Added missing Origin: {}", baseUrl);
                    }
                } else {
                    // Add realistic browser headers if none provided
                    builder.header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                            .header("Accept", "application/vnd.apple.mpegurl, application/x-mpegurl, */*")
                            .header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                            .header("Accept-Encoding", "gzip, deflate, br")
                            .header("Connection", "keep-alive")
                            .header("Referer", baseUrl + "/")
                            .header("Origin", baseUrl)
                            .header("Sec-Fetch-Dest", "empty")
                            .header("Sec-Fetch-Mode", "cors")
                            .header("Sec-Fetch-Site", "same-origin")
                            .header("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                            .header("sec-ch-ua-mobile", "?0")
                            .header("sec-ch-ua-platform", "\"Windows\"");
                }
                HttpRequest request = builder.build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                m3u8Content = response.body();
            }

            // 2- parse all ts urls by response
            // Use baseUrlOverride if provided, otherwise use m3u8Url
            String urlForParsing = baseUrlOverride != null ? baseUrlOverride : m3u8Url;
            log.info("Parsing m3u8 content with base URL: {}", urlForParsing);
            List<String> tsUrls = m3U8ParserService.parseM3U8Content(m3u8Content, urlForParsing);

            // 2.5- Download encryption key if needed
            EncryptionKey encryptionKey = m3U8ParserService.getEncryptionKey();
            if (encryptionKey != null && encryptionKey.isEncrypted()) {
                boolean keyLoaded = false;

                // Try to load key from local file if m3u8 is local
                // Accept file:/, file://, or file:/// formats
                if (m3u8Url.startsWith("file:")) {
                    statusUpdateStrategy.update("Looking for local encryption key...");
                    File m3u8File;
                    try {
                        m3u8File = new File(new URI(m3u8Url));
                    } catch (Exception e) {
                        log.warn("Failed to parse file URI, using fallback: {}", e.getMessage());
                        m3u8File = new File(m3u8Url.substring(7));
                    }
                    File m3u8Dir = m3u8File.getParentFile();

                    // First, try to use the key file name from m3u8 (if it's a relative path or file:// URL)
                    String keyUri = encryptionKey.getUri();
                    if (keyUri != null && !keyUri.startsWith("http")) {
                        // It's a relative path, filename, or file:// URL
                        String keyFileName = keyUri;

                        // Handle file:// URLs (e.g., "file:/D:/path/ts.key" or "file:///D:/path/ts.key")
                        if (keyFileName.startsWith("file:")) {
                            try {
                                // Try to parse as URI
                                File keyFileFromUri = new File(new URI(keyFileName));
                                keyFileName = keyFileFromUri.getName();
                                log.info("Extracted filename from file:// URI: {} -> {}", keyUri, keyFileName);
                            } catch (Exception e) {
                                // Fallback: strip "file:" prefix and extract filename
                                keyFileName = keyFileName.replaceFirst("^file:/*", "");
                                if (keyFileName.contains("/") || keyFileName.contains("\\\\")) {
                                    keyFileName = new File(keyFileName).getName();
                                }
                                log.info("Extracted filename from malformed file URI: {} -> {}", keyUri, keyFileName);
                            }
                        }

                        // Remove query parameters (e.g., "ts.key?" -> "ts.key")
                        int queryIndex = keyFileName.indexOf('?');
                        if (queryIndex > 0) {
                            keyFileName = keyFileName.substring(0, queryIndex);
                            log.info("Removed query parameters from key filename: {} -> {}",
                                    keyUri, keyFileName);
                        }

                        File keyFile = new File(m3u8Dir, keyFileName);
                        log.info("Looking for key file at: {}", keyFile.getAbsolutePath());

                        if (keyFile.exists()) {
                            try {
                                byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
                                encryptionKey.setKeyBytes(keyBytes);
                                log.info("✓ Loaded encryption key from m3u8 specified file: {} ({} bytes)",
                                        keyFile.getName(), keyBytes.length);
                                statusUpdateStrategy.update("Loaded local encryption key: " + keyFile.getName());
                                keyLoaded = true;
                            } catch (Exception e) {
                                log.warn("Failed to read key file {}: {}", keyFile.getName(), e.getMessage());
                            }
                        } else {
                            log.warn("Key file not found: {}", keyFile.getAbsolutePath());
                            log.warn("Directory contents: {}",
                                    m3u8Dir.list() != null ? String.join(", ", m3u8Dir.list()) : "none");
                        }
                    }

                    // If still not loaded, try common key file names
                    if (!keyLoaded) {
                        String[] keyFileNames = {"ts.key", "key.key", "enc.key", "video.key", "encryption.key"};
                        for (String keyFileName : keyFileNames) {
                            File keyFile = new File(m3u8Dir, keyFileName);
                            if (keyFile.exists()) {
                                try {
                                    byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
                                    encryptionKey.setKeyBytes(keyBytes);
                                    log.info("✓ Loaded encryption key from local file: {} ({} bytes)",
                                            keyFile.getName(), keyBytes.length);
                                    statusUpdateStrategy.update("Loaded local encryption key: " + keyFile.getName());
                                    keyLoaded = true;
                                    break;
                                } catch (Exception e) {
                                    log.warn("Failed to read key file {}: {}", keyFile.getName(), e.getMessage());
                                }
                            }
                        }
                    }

                    if (!keyLoaded) {
                        log.warn("⚠ No local key file found. Tried: {}", String.join(", ",
                                new String[]{"ts.key", "key.key", "enc.key", "video.key", "encryption.key"}));
                        log.warn("⚠ Please place key file in same directory as m3u8 file: {}", m3u8Dir.getAbsolutePath());
                    }
                }

                // If key not loaded from local file, try downloading from network
                if (!keyLoaded) {
                    String keyUri = encryptionKey.getUri();
                    // Check if key URI is a valid network URL
                    if (keyUri == null || (!keyUri.startsWith("http://") && !keyUri.startsWith("https://"))) {
                        String errorMsg = String.format("無法載入加密金鑰。本地文件：%s 不存在，且無有效的網絡 URL。",
                                keyUri != null ? keyUri : "null");
                        log.error(errorMsg);
                        throw new RuntimeException(errorMsg);
                    }

                    statusUpdateStrategy.update("Downloading encryption key...");
                    HttpRequest.Builder keyBuilder = HttpRequest.newBuilder().uri(URI.create(keyUri));

                    // Use same headers for key download
                    if (headers != null && !headers.isEmpty()) {
                        headers.forEach(keyBuilder::header);
                        // Add Referer and Origin if missing
                        if (!headers.containsKey("Referer") && !headers.containsKey("referer")) {
                            keyBuilder.header("Referer", baseUrl + "/");
                        }
                        if (!headers.containsKey("Origin") && !headers.containsKey("origin")) {
                            keyBuilder.header("Origin", baseUrl);
                        }
                    } else {
                        keyBuilder.header("User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                                .header("Accept", "*/*")
                                .header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                                .header("Referer", baseUrl + "/")
                                .header("Origin", baseUrl);
                    }

                    HttpRequest keyRequest = keyBuilder.build();
                    HttpResponse<byte[]> keyResponse = client.send(keyRequest, HttpResponse.BodyHandlers.ofByteArray());

                    if (keyResponse.statusCode() == 200) {
                        encryptionKey.setKeyBytes(keyResponse.body());
                        log.info("Encryption key downloaded: {} bytes", keyResponse.body().length);
                    } else {
                        throw new RuntimeException("Failed to download encryption key: HTTP " + keyResponse.statusCode());
                    }
                }
            }

            long parseEnd = System.currentTimeMillis();

            long downloadStart = System.currentTimeMillis();
            // 3- download all ts files (pass baseUrl for Referer/Origin)
            downloadService.downloadTsFiles(tsUrls, path, fileName.get(), headers, encryptionKey, baseUrl);
            long downloadEnd = System.currentTimeMillis();

            long mergeStart = System.currentTimeMillis();
            // 4- merge all ts files
            mergeService.mergeTsToMp4(path, fileName.get(), tsUrls.size());
            long mergeEnd = System.currentTimeMillis();

            long duration = System.currentTimeMillis() - start;

            Map<String, Long> times = new HashMap<>();
            times.put("Parsing", parseEnd - parseStart);
            times.put("Downloading", downloadEnd - downloadStart);
            times.put("Merging", mergeEnd - mergeStart);

            Platform.runLater(() -> phaseTimes.set(times));

            // 5- update UI
            timeLabelUpdateStrategy.update(duration);
            alertUpdateStrategy.update(TimeUtil.formatDuration(duration));
        } catch (Exception e) {
            log.error("error: ", e);
            statusUpdateStrategy.update("error please check......" + e.getMessage());
            alertUpdateStrategy.update("下載失敗: " + e.getMessage());
        }

    }

    private void resetUIState() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        ((ProgressBarUpdateStrategy) progressBarUpdateStrategy).setLastProgress(0.0);
        Platform.runLater(
                () -> {
                    progressBar.set(0.0);
                    timeLabel.set(StringUtils.EMPTY);
                    phaseTimes.set(null);
                    latch.countDown();
                });
        latch.await();
    }

    public Optional<File> getVideoFile() {
        Video video = new Video(fileName.get(), path);
        if (!video.exists()) {
            return Optional.empty();
        }

        return Optional.of(video.getFile());
    }
}
