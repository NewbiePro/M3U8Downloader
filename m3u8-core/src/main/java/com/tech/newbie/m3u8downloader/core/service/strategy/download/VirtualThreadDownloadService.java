package com.tech.newbie.m3u8downloader.core.service.strategy.download;

import com.tech.newbie.m3u8downloader.core.config.AppConfig;
import com.tech.newbie.m3u8downloader.core.model.Statistics;
import com.tech.newbie.m3u8downloader.core.common.callback.UpdateCallback;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
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
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(appConfig.getTimeout()))
                .sslContext(getInsecureSslContext())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public void downloadTsFiles(List<String> tsUrls, String outputDir, String fileName, Map<String, String> headers) {
        long startTime = System.currentTimeMillis();
        statistics.setTotalTsFiles(tsUrls.size());
        statistics.setSuccessCount(0);
        statistics.setFailedCount(0);
        statistics.setTotalBytes(0);

        log.info("Start using VIRTUAL_THREAD to download [{}] tsFiles", tsUrls.size());
        statusUpdateStrategy.update("Downloading....");
        counter.set(1);

        try {
            log.info("Creating download futures with virtual threads...");
            List<CompletableFuture<Void>> futures = tsUrls.stream().map(url -> CompletableFuture.runAsync(
                    () -> {
                        try {
                            downloadTsFile(url, outputDir, fileName, tsUrls.size(), headers);
                        } catch (Exception e) {
                            log.error("Error downloading with virtual thread", e);
                        }
                    },
                    VIRTUAL_THREAD_POOL)).toList();

            CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allDone.join();

            statistics.setDownloadTime(System.currentTimeMillis() - startTime);
            afterDownload();
            cleanup();
        } catch (Exception e) {
            log.error("Download error", e);
            statusUpdateStrategy.update("Error Downloading");
        }
    }

    public void downloadTsFile(String tsUrl, String outputDir, String fileName, int size, Map<String, String> headers)
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
                // use InputStream mode, which saves memory
                HttpResponse<InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());
                int statusCode = response.statusCode();
                if (statusCode != 200) {
                    if (response.body() != null)
                        response.body().close();
                    log.error("invalid response for ts segment: [{}] status: [{}]", tsUrl, response.statusCode());
                    throw new IOException("invalid response");
                }
                // ts file output to directory
                try (InputStream is = response.body()) {
                    Files.copy(is, outputFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
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

    private SSLContext getInsecureSslContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, trustAllCerts, new SecureRandom());
            return sc;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create insecure SSL context", e);
        }
    }
}
