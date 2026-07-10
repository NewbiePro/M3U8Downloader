package com.tech.newbie.m3u8downloader.core.service.strategy.download;

import com.tech.newbie.m3u8downloader.core.config.AppConfig;
import com.tech.newbie.m3u8downloader.core.model.EncryptionKey;
import com.tech.newbie.m3u8downloader.core.model.Statistics;
import com.tech.newbie.m3u8downloader.core.common.callback.UpdateCallback;
import com.tech.newbie.m3u8downloader.core.common.utils.DecryptionUtil;
import com.tech.newbie.m3u8downloader.core.common.utils.HttpClientFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tech.newbie.m3u8downloader.core.common.constant.Constant.TS_FORMAT;

@Slf4j
public class VirtualThreadDownloadService {

    private final UpdateCallback<String> statusUpdateStrategy;
    private final UpdateCallback<Double> progressUpdateStrategy;
    private final AppConfig appConfig;
    private final HttpClient httpClient;
    private final Statistics statistics;

    private final AtomicInteger counter = new AtomicInteger(1);
    private static final Semaphore LIMITER = new Semaphore(32);
    private static final ExecutorService VIRTUAL_THREAD_POOL = Executors.newVirtualThreadPerTaskExecutor();

    public VirtualThreadDownloadService(UpdateCallback<String> statusUpdateStrategy,
            UpdateCallback<Double> progressUpdateStrategy) {
        this.statusUpdateStrategy = statusUpdateStrategy;
        this.progressUpdateStrategy = progressUpdateStrategy;
        this.appConfig = AppConfig.getInstance();
        this.statistics = new Statistics();
        this.httpClient = HttpClientFactory.createInsecureHttpClient();
    }

    public void downloadTsFiles(List<String> tsUrls, String outputDir, String fileName, Map<String, String> headers, EncryptionKey encryptionKey) {
        long startTime = System.currentTimeMillis();
        statistics.setTotalTsFiles(tsUrls.size());
        statistics.setSuccessCount(0);
        statistics.setFailedCount(0);
        statistics.setTotalBytes(0);

        boolean isEncrypted = encryptionKey != null && encryptionKey.isEncrypted();
        log.info("Start using VIRTUAL_THREAD to download [{}] tsFiles (encrypted: {})", tsUrls.size(), isEncrypted);
        statusUpdateStrategy.update(isEncrypted ? "Downloading and decrypting...." : "Downloading....");
        counter.set(1);

        try {
            log.info("Creating download futures with virtual threads...");
            List<CompletableFuture<Void>> futures = tsUrls.stream().map(url -> CompletableFuture.runAsync(
                    () -> {
                        try {
                            downloadTsFile(url, outputDir, fileName, tsUrls.size(), headers, encryptionKey);
                        } catch (Exception e) {
                            log.error("Error downloading with virtual thread: {}", e.getMessage(), e);
                            throw new RuntimeException("Failed to download: " + url, e);
                        }
                    },
                    VIRTUAL_THREAD_POOL)).toList();

            CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allDone.join();

            // Verify all files were downloaded successfully
            int missingFiles = verifyDownloadedFiles(outputDir, fileName, tsUrls.size());
            if (missingFiles > 0) {
                throw new RuntimeException(String.format("%d ts files are missing or incomplete", missingFiles));
            }

            statistics.setDownloadTime(System.currentTimeMillis() - startTime);
            afterDownload();
            cleanup();
        } catch (Exception e) {
            log.error("Download error", e);
            statusUpdateStrategy.update("Error: " + e.getMessage());
            throw new RuntimeException("Download failed", e);
        }
    }

    public void downloadTsFile(String tsUrl, String outputDir, String fileName, int size, Map<String, String> headers, EncryptionKey encryptionKey)
            throws IOException, InterruptedException {
        int index = counter.getAndIncrement();
        File outputFile = new File(outputDir, String.format(TS_FORMAT, fileName, index));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(tsUrl));

        if (headers != null && !headers.isEmpty()) {
            headers.forEach(builder::header);
        } else {
            builder.header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "*/*");
        }

        HttpRequest request = builder.build();

        int maxRetries = appConfig.getMaxRetries();
        int attempt = 0;
        while (true) {
            LIMITER.acquire();
            try {
                // use byte array mode for decryption
                HttpResponse<byte[]> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofByteArray());
                int statusCode = response.statusCode();
                if (statusCode != 200) {
                    log.error("invalid response for ts segment: [{}] status: [{}]", tsUrl, response.statusCode());
                    throw new IOException("invalid response");
                }

                byte[] data = response.body();

                // Decrypt if needed
                if (encryptionKey != null && encryptionKey.isEncrypted()) {
                    data = DecryptionUtil.decryptAES128(data, encryptionKey, index);
                    log.debug("Decrypted ts file {}/{}", index, size);
                }

                // Write to file
                Files.write(outputFile.toPath(), data);
                break;
            } catch (IOException e) {
                attempt++;
                log.warn("Attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt >= maxRetries) {
                    log.error("Max retries reached for ts segment: [{}]", tsUrl);
                    throw new RuntimeException(String.format("Attempt %d failed to fetch ts: %s", attempt, tsUrl));
                }
                Files.deleteIfExists(outputFile.toPath());
            } finally {
                LIMITER.release();
            }
        }

        // update progress bar
        double progress = (double) index / size;
        progressUpdateStrategy.update(progress);
        log.info("Thread:{} Downloaded......{}/{}", Thread.currentThread().getName(), index, size);
    }

    protected void afterDownload() {
        log.info("Finish Downloading, Download Duration: [{}], Total Ts Files: [{}]", statistics.getDownloadTime(),
                statistics.getTotalTsFiles());
        statusUpdateStrategy.update("Download completed");
        if (progressUpdateStrategy != null) {
            progressUpdateStrategy.update(1.0);
        }
    }

    protected void cleanup() {
        log.info("Virtual thread download service cleanup completed");
        // 虚拟线程会自动回收，无需显式关闭
    }

    private int verifyDownloadedFiles(String outputDir, String fileName, int totalFiles) {
        int missingOrInvalid = 0;
        for (int i = 1; i <= totalFiles; i++) {
            File tsFile = new File(outputDir, String.format(TS_FORMAT, fileName, i));
            if (!tsFile.exists()) {
                log.error("Missing ts file: {}", tsFile.getName());
                missingOrInvalid++;
            } else if (tsFile.length() == 0) {
                log.error("Empty ts file: {}", tsFile.getName());
                missingOrInvalid++;
            } else if (tsFile.length() < 100) {
                // Files smaller than 100 bytes are likely error pages
                log.error("Suspicious small ts file ({}bytes): {}", tsFile.length(), tsFile.getName());
                missingOrInvalid++;
            }
        }

        if (missingOrInvalid == 0) {
            log.info("All {} ts files verified successfully", totalFiles);
        } else {
            log.error("Found {} missing or invalid ts files out of {}", missingOrInvalid, totalFiles);
        }

        return missingOrInvalid;
    }
}
