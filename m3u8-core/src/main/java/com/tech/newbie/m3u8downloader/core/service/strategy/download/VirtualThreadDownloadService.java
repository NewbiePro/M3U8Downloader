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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static com.tech.newbie.m3u8downloader.core.common.constant.Constant.INIT_SEGMENT_FORMAT;
import static com.tech.newbie.m3u8downloader.core.common.constant.Constant.SEGMENT_FORMAT;

@Slf4j
public class VirtualThreadDownloadService {

    private final UpdateCallback<String> statusUpdateStrategy;
    private final UpdateCallback<Double> progressUpdateStrategy;
    private final AppConfig appConfig;
    private final HttpClient httpClient;
    private final Statistics statistics;

    // Reduced from 32 to 8 to avoid rate limiting
    private static final Semaphore LIMITER = new Semaphore(8);
    private static final ExecutorService VIRTUAL_THREAD_POOL = Executors.newVirtualThreadPerTaskExecutor();

    public VirtualThreadDownloadService(UpdateCallback<String> statusUpdateStrategy,
            UpdateCallback<Double> progressUpdateStrategy) {
        this.statusUpdateStrategy = statusUpdateStrategy;
        this.progressUpdateStrategy = progressUpdateStrategy;
        this.appConfig = AppConfig.getInstance();
        this.statistics = new Statistics();
        this.httpClient = HttpClientFactory.createInsecureHttpClient();
    }

    /**
     * @param initSegmentUrl when non-null the stream is fragmented MP4 (fMP4/CMAF): each
     *                       media segment is an {@code .m4s} fragment that only plays once
     *                       the init segment (from {@code #EXT-X-MAP}) is prepended.
     */
    public void downloadTsFiles(List<String> tsUrls, String outputDir, String fileName, Map<String, String> headers,
            EncryptionKey encryptionKey, String baseUrl, String initSegmentUrl) {
        long startTime = System.currentTimeMillis();
        statistics.setTotalTsFiles(tsUrls.size());
        statistics.setSuccessCount(0);
        statistics.setFailedCount(0);
        statistics.setTotalBytes(0);

        boolean fmp4 = initSegmentUrl != null;
        String segmentExt = fmp4 ? "m4s" : "ts";
        boolean isEncrypted = encryptionKey != null && encryptionKey.isEncrypted();
        log.info("Start using VIRTUAL_THREAD to download [{}] segments (fmp4: {}, encrypted: {})",
                tsUrls.size(), fmp4, isEncrypted);
        statusUpdateStrategy.update(isEncrypted ? "Downloading and decrypting...." : "Downloading....");

        try {
            if (fmp4) {
                log.info("Downloading fMP4 init segment: {}", initSegmentUrl);
                statusUpdateStrategy.update("Downloading init segment...");
                byte[] initData = fetchSegment(initSegmentUrl, headers, baseUrl);
                File initFile = new File(outputDir, String.format(INIT_SEGMENT_FORMAT, fileName));
                Files.write(initFile.toPath(), initData);
                if (!looksLikeMp4(initFile)) {
                    throw new RuntimeException("Init segment is not a valid MP4 (likely an error page): " + initSegmentUrl);
                }
            }

            log.info("Creating download futures with virtual threads...");
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < tsUrls.size(); i++) {
                // 1-based position of this segment in the playlist. Fixed per URL so the
                // output file index and the AES IV/sequence are deterministic regardless
                // of the order virtual threads happen to run in.
                final int position = i + 1;
                final String url = tsUrls.get(i);
                futures.add(CompletableFuture.runAsync(
                        () -> {
                            try {
                                downloadTsFile(url, outputDir, fileName, position, tsUrls.size(), headers,
                                        encryptionKey, baseUrl, segmentExt);
                            } catch (Exception e) {
                                log.error("Error downloading with virtual thread: {}", e.getMessage(), e);
                                throw new RuntimeException("Failed to download: " + url, e);
                            }
                        },
                        VIRTUAL_THREAD_POOL));
            }

            CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allDone.join();

            // Verify all files were downloaded successfully
            int missingFiles = verifyDownloadedFiles(outputDir, fileName, tsUrls.size(), segmentExt, fmp4);
            if (missingFiles > 0) {
                throw new RuntimeException(String.format("%d segments are missing or incomplete", missingFiles));
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

    public void downloadTsFile(String tsUrl, String outputDir, String fileName, int index, int size,
            Map<String, String> headers, EncryptionKey encryptionKey, String baseUrl, String segmentExt)
            throws IOException, InterruptedException {
        File outputFile = new File(outputDir, String.format(SEGMENT_FORMAT, fileName, index, segmentExt));

        HttpRequest request = buildRequest(tsUrl, headers, baseUrl);

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
                    log.error("invalid response for segment: [{}] status: [{}]", tsUrl, response.statusCode());
                    throw new IOException("invalid response");
                }

                byte[] data = response.body();

                // Decrypt if needed. When the playlist supplies no explicit IV, HLS uses the
                // segment's media sequence number, which is 0-based (first segment -> 0).
                if (encryptionKey != null && encryptionKey.isEncrypted()) {
                    data = DecryptionUtil.decryptAES128(data, encryptionKey, index - 1);
                    log.debug("Decrypted segment {}/{}", index, size);
                }

                // Write to file
                Files.write(outputFile.toPath(), data);
                break;
            } catch (IOException e) {
                attempt++;
                log.warn("Attempt {}/{} failed for segment {}/{}: {}", attempt, maxRetries, index, size, e.getMessage());
                if (attempt >= maxRetries) {
                    log.error("Max retries reached for segment: [{}]", tsUrl);
                    throw new RuntimeException(String.format("Attempt %d failed to fetch segment: %s", attempt, tsUrl));
                }
                Files.deleteIfExists(outputFile.toPath());

                // Exponential backoff with jitter to avoid rate limiting
                // Wait time: baseDelay * (2 ^ attempt) + random jitter
                int baseDelayMs = 1000; // 1 second
                int exponentialDelay = baseDelayMs * (int) Math.pow(2, attempt - 1);
                int jitter = (int) (Math.random() * baseDelayMs);
                int totalDelay = exponentialDelay + jitter;

                log.info("Waiting {}ms before retry {} for segment {}/{}", totalDelay, attempt + 1, index, size);
                try {
                    Thread.sleep(totalDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Download interrupted", ie);
                }
            } finally {
                LIMITER.release();
            }
        }

        // update progress bar
        double progress = (double) index / size;
        progressUpdateStrategy.update(progress);
        log.info("Thread:{} Downloaded......{}/{}", Thread.currentThread().getName(), index, size);
    }

    /** One-shot fetch with retry, used for the fMP4 init segment. */
    private byte[] fetchSegment(String url, Map<String, String> headers, String baseUrl)
            throws IOException, InterruptedException {
        HttpRequest request = buildRequest(url, headers, baseUrl);
        int maxRetries = Math.max(1, appConfig.getMaxRetries());
        for (int attempt = 1; ; attempt++) {
            try {
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                return response.body();
            } catch (IOException e) {
                if (attempt >= maxRetries) {
                    throw e;
                }
                log.warn("Attempt {}/{} failed for {}: {}", attempt, maxRetries, url, e.getMessage());
                Thread.sleep(1000L * attempt);
            }
        }
    }

    private HttpRequest buildRequest(String url, Map<String, String> headers, String baseUrl) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(builder::header);
            // Add Referer and Origin if missing (critical for anti-hotlinking)
            if (!headers.containsKey("Referer") && !headers.containsKey("referer")) {
                builder.header("Referer", baseUrl + "/");
            }
            if (!headers.containsKey("Origin") && !headers.containsKey("origin")) {
                builder.header("Origin", baseUrl);
            }
        } else {
            // Add more realistic browser headers to bypass anti-bot protection
            builder.header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Referer", baseUrl + "/")
                    .header("Origin", baseUrl)
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"Windows\"");
        }
        return builder.build();
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

    private int verifyDownloadedFiles(String outputDir, String fileName, int totalFiles, String segmentExt, boolean fmp4) {
        int missingOrInvalid = 0;
        for (int i = 1; i <= totalFiles; i++) {
            File segment = new File(outputDir, String.format(SEGMENT_FORMAT, fileName, i, segmentExt));
            if (!segment.exists()) {
                log.error("Missing segment: {}", segment.getName());
                missingOrInvalid++;
            } else if (segment.length() == 0) {
                log.error("Empty segment: {}", segment.getName());
                missingOrInvalid++;
            } else if (segment.length() < 100) {
                // Files smaller than 100 bytes are likely error pages
                log.error("Suspicious small segment ({}bytes): {}", segment.length(), segment.getName());
                missingOrInvalid++;
            } else if (fmp4 ? !looksLikeMp4Fragment(segment) : !looksLikeMpegTs(segment)) {
                // Not valid media: usually an HTML/JSON error page (rate limit / anti-hotlink)
                // or a failed AES decryption (wrong key or IV).
                log.error("Segment is not valid {} data: {} - likely an error page or failed decryption",
                        fmp4 ? "fMP4" : "MPEG-TS", segment.getName());
                missingOrInvalid++;
            }
        }

        if (missingOrInvalid == 0) {
            log.info("All {} segments verified successfully", totalFiles);
        } else {
            log.error("Found {} missing or invalid segments out of {}", missingOrInvalid, totalFiles);
        }

        return missingOrInvalid;
    }

    /**
     * A raw MPEG-TS stream is a sequence of 188-byte packets, each starting with the
     * sync byte 0x47. Some streams carry a small ID3/tag prefix, so scan the first few
     * KB for a plausible run of sync bytes rather than only checking byte 0.
     */
    private boolean looksLikeMpegTs(File tsFile) {
        byte[] head = readHead(tsFile, 8192);
        if (head == null) {
            return true; // don't block the merge on an inspection failure
        }
        for (int offset = 0; offset < head.length - 188 * 3; offset++) {
            if ((head[offset] & 0xFF) == 0x47
                    && (head[offset + 188] & 0xFF) == 0x47
                    && (head[offset + 188 * 2] & 0xFF) == 0x47) {
                return true;
            }
        }
        return false;
    }

    /** ISO-BMFF init segment: an {@code ftyp} box near the start. */
    private boolean looksLikeMp4(File file) {
        return containsBox(file, "ftyp") || containsBox(file, "styp") || containsBox(file, "moov");
    }

    /** CMAF media fragment: {@code styp}/{@code moof}/{@code mdat} boxes. */
    private boolean looksLikeMp4Fragment(File file) {
        return containsBox(file, "moof") || containsBox(file, "styp") || containsBox(file, "mdat");
    }

    private boolean containsBox(File file, String boxType) {
        byte[] head = readHead(file, 4096);
        if (head == null) {
            return true;
        }
        byte[] needle = boxType.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= head.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (head[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private byte[] readHead(File file, int maxBytes) {
        try {
            byte[] head = new byte[Math.min((int) file.length(), maxBytes)];
            try (InputStream in = Files.newInputStream(file.toPath())) {
                int read = 0;
                while (read < head.length) {
                    int n = in.read(head, read, head.length - read);
                    if (n < 0) break;
                    read += n;
                }
            }
            return head;
        } catch (IOException e) {
            log.warn("Could not inspect file {}: {}", file.getName(), e.getMessage());
            return null;
        }
    }
}
