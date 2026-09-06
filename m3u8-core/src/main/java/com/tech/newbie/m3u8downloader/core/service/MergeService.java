package com.tech.newbie.m3u8downloader.core.service;

import com.tech.newbie.m3u8downloader.core.common.callback.UpdateCallback;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.tech.newbie.m3u8downloader.core.common.constant.Constant.INIT_SEGMENT_FORMAT;
import static com.tech.newbie.m3u8downloader.core.common.constant.Constant.SEGMENT_FORMAT;
import static com.tech.newbie.m3u8downloader.core.common.constant.Constant.TS_FORMAT;

@Slf4j
public class MergeService {

    private final UpdateCallback<String> strategy;
    private final UpdateCallback<String> alert;

    public MergeService(UpdateCallback<String> strategy, UpdateCallback<String> alert) {
        this.strategy = strategy;
        this.alert = alert;
    }

    public void mergeTsToMp4(String baseFilePath, String baseFileName, int totalFiles) throws IOException {
        strategy.update("Start merging.........");
        File workingDir = new File(baseFilePath);

        // create fileList.txt with RELATIVE names only. ffmpeg's concat demuxer treats a
        // backslash as an escape character, so Windows absolute paths like
        // C:\dir\name_1.ts break with "Impossible to open". Running ffmpeg with the
        // working directory set to baseFilePath lets us list bare file names instead.
        StringBuilder fileListContent = new StringBuilder();
        for (int i = 1; i <= totalFiles; i++) {
            fileListContent.append("file '")
                    .append(String.format(TS_FORMAT, baseFileName, i))
                    .append("'\n");
        }

        File fileListTxt = new File(baseFilePath, "fileList.txt");
        writeToFile(fileListTxt, fileListContent.toString());

        File outputFile = new File(baseFilePath, baseFileName + ".mp4");

        List<String> command = List.of(
                "ffmpeg", "-f", "concat", "-safe", "0",
                "-i", "fileList.txt",
                "-c", "copy",
                "-bsf:a", "aac_adtstoasc", // Fix AAC bitstream for MP4
                "-y",
                outputFile.getName());

        FfmpegResult result = runFfmpeg(workingDir, command);

        if (result.exitCode == 0) {
            log.info("merging completed，generated {}.mp4", baseFileName);
            strategy.update("DONE");
            Files.deleteIfExists(fileListTxt.toPath());
            removeSegments(baseFilePath, baseFileName, totalFiles, "ts");
        } else if (result.exitCode == Integer.MIN_VALUE) {
            strategy.update("ERROR");
            alert.update("合併中斷或無法啟動 ffmpeg: " + result.output);
        } else {
            log.error("ffmpeg執行失敗，錯誤代碼: {}\n{}", result.exitCode, result.output);
            strategy.update("ERROR: Merge failed");
            alert.update("合併失敗: " + extractErrorMessage(result.output) + "\n錯誤代碼: " + result.exitCode);
        }
    }

    /**
     * Merge fragmented-MP4 (fMP4 / CMAF) output. Each {@code .m4s} fragment only decodes
     * once the {@code #EXT-X-MAP} init segment is prepended, so we binary-concatenate
     * {@code <name>_init.mp4 + <name>_1.m4s + ... + <name>_N.m4s} into one file and let
     * ffmpeg remux it to a clean, non-fragmented MP4.
     */
    public void mergeFmp4ToMp4(String baseFilePath, String baseFileName, int totalFiles) throws IOException {
        strategy.update("Start merging (fMP4).........");
        File workingDir = new File(baseFilePath);

        File initFile = new File(baseFilePath, String.format(INIT_SEGMENT_FORMAT, baseFileName));
        if (!initFile.exists()) {
            strategy.update("ERROR: Merge failed");
            alert.update("合併失敗: 找不到 init segment: " + initFile.getName());
            return;
        }

        File combined = new File(baseFilePath, baseFileName + "_full.mp4");
        try (OutputStream out = Files.newOutputStream(combined.toPath())) {
            Files.copy(initFile.toPath(), out);
            for (int i = 1; i <= totalFiles; i++) {
                File fragment = new File(baseFilePath, String.format(SEGMENT_FORMAT, baseFileName, i, "m4s"));
                if (!fragment.exists()) {
                    throw new IOException("Missing fMP4 fragment: " + fragment.getName());
                }
                Files.copy(fragment.toPath(), out);
            }
        }
        log.info("Concatenated init + {} fragments into {} ({} bytes)",
                totalFiles, combined.getName(), combined.length());

        File outputFile = new File(baseFilePath, baseFileName + ".mp4");
        List<String> command = List.of(
                "ffmpeg",
                "-i", combined.getName(),
                "-c", "copy",
                "-movflags", "+faststart",
                "-y",
                outputFile.getName());

        FfmpegResult result = runFfmpeg(workingDir, command);

        if (result.exitCode == 0) {
            log.info("fMP4 merging completed，generated {}.mp4", baseFileName);
            strategy.update("DONE");
            Files.deleteIfExists(combined.toPath());
            Files.deleteIfExists(initFile.toPath());
            removeSegments(baseFilePath, baseFileName, totalFiles, "m4s");
        } else if (result.exitCode == Integer.MIN_VALUE) {
            strategy.update("ERROR");
            alert.update("合併中斷或無法啟動 ffmpeg: " + result.output);
        } else {
            // The concatenated file is often already playable - keep it as a fallback.
            log.error("ffmpeg remux failed，錯誤代碼: {}\n{}", result.exitCode, result.output);
            strategy.update("ERROR: Merge failed");
            alert.update("fMP4 remux 失敗: " + extractErrorMessage(result.output)
                    + "\n錯誤代碼: " + result.exitCode
                    + "\n已保留合併檔可嘗試直接播放: " + combined.getName());
        }
    }

    private FfmpegResult runFfmpeg(File workingDir, List<String> command) {
        log.info("command: (cwd={}) {}", workingDir, String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(command));
        pb.directory(workingDir);
        pb.redirectErrorStream(true); // merge stdError & stdOutput

        StringBuilder ffmpegOutput = new StringBuilder();
        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("ffmpeg: {}", line);
                    ffmpegOutput.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            return new FfmpegResult(exitCode, ffmpegOutput.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("ffmpeg interrupted", e);
            return new FfmpegResult(Integer.MIN_VALUE, e.getMessage());
        } catch (IOException e) {
            log.error("failed to run ffmpeg (is it installed and on PATH?)", e);
            return new FfmpegResult(Integer.MIN_VALUE, e.getMessage());
        }
    }

    private void writeToFile(File file, String content) {
        try {
            Files.write(file.toPath(), content.getBytes());
        } catch (IOException e) {
            log.error("write error ", e);
        }
    }

    private String extractErrorMessage(String ffmpegOutput) {
        // Extract useful error messages from ffmpeg output
        String[] lines = ffmpegOutput.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].toLowerCase();
            if (line.contains("invalid data") || line.contains("error") ||
                line.contains("failed") || line.contains("could not")) {
                return lines[i].trim();
            }
        }
        return "檢查片段檔是否完整下載";
    }

    private void removeSegments(String baseFilePath, String baseFileName, int totalFiles, String ext) {
        try {
            for (int i = 1; i <= totalFiles; i++) {
                File segment = new File(baseFilePath, String.format(SEGMENT_FORMAT, baseFileName, i, ext));
                Files.deleteIfExists(segment.toPath());
            }
        } catch (IOException e) {
            log.info("remove files error ", e);
        }
    }

    private static final class FfmpegResult {
        // exitCode == Integer.MIN_VALUE means ffmpeg could not be run at all
        final int exitCode;
        final String output;

        FfmpegResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
