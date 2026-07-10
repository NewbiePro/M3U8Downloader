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
                String filePath = rawInput.startsWith("file://") ? rawInput.substring(7) : rawInput;
                m3u8Content = java.nio.file.Files.readString(java.nio.file.Path.of(filePath));
                m3u8Url = "file://" + new java.io.File(filePath).getAbsolutePath();
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

            long parseStart = System.currentTimeMillis();

            // 1- Get m3u8 content (from network or local)
            if (m3u8Content == null) {
                // Download from network
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                m3u8Content = response.body();
            }

            // 2- parse all ts urls by response
            List<String> tsUrls = m3U8ParserService.parseM3U8Content(m3u8Content, m3u8Url);

            // 2.5- Download encryption key if needed
            EncryptionKey encryptionKey = m3U8ParserService.getEncryptionKey();
            if (encryptionKey != null && encryptionKey.isEncrypted()) {
                statusUpdateStrategy.update("Downloading encryption key...");
                HttpRequest.Builder keyBuilder = HttpRequest.newBuilder().uri(URI.create(encryptionKey.getUri()));

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
