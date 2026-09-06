package com.tech.newbie.m3u8downloader.core.service;

import com.tech.newbie.m3u8downloader.core.common.callback.UpdateCallback;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;

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

        // save fileList.txt
        File fileListTxt = new File(baseFilePath, "fileList.txt");
        writeToFile(fileListTxt, fileListContent.toString());

        File outputFile = new File(baseFilePath, baseFileName + ".mp4");
        String command = String.format("(cwd=%s) ffmpeg -f concat -safe 0 -i fileList.txt -c copy -bsf:a aac_adtstoasc -y %s",
                baseFilePath,
                outputFile.getName());

        log.info("command: {}", command);
        // execute command
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-f", "concat", "-safe", "0",
                "-i", "fileList.txt",
                "-c", "copy",
                "-bsf:a", "aac_adtstoasc",  // Fix AAC bitstream for MP4
                "-y",  // Overwrite output file if exists
                outputFile.getName()

        );
        pb.directory(workingDir);

        pb.redirectErrorStream(true); // merge stdError & stdOutput
        Process process = pb.start();

        // 讀取 ffmpeg 的輸出（包含錯誤資訊）
        StringBuilder ffmpegOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("ffmpeg: {}", line);
                ffmpegOutput.append(line).append("\n");
            }
        }

        try {
            // wait for the command to complete
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("merging completed，generated {}.mp4", baseFileName);
                strategy.update("DONE");
                removeFiles(fileListTxt, totalFiles, baseFilePath, baseFileName);
            } else {
                log.error("ffmpeg執行失敗，錯誤代碼: {}", exitCode);
                log.error("ffmpeg output:\n{}", ffmpegOutput);

                String errorMsg = extractErrorMessage(ffmpegOutput.toString());
                strategy.update("ERROR: Merge failed");
                alert.update("合併失敗: " + errorMsg + "\n錯誤代碼: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("interrupting, ", e);
            strategy.update("ERROR");
            alert.update("合併中斷: " + e.getMessage());
        } catch (Exception e) {
            log.error("error ", e);
            strategy.update("ERROR");
            alert.update("合併錯誤: " + e.getMessage());
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
        return "檢查ts文件是否完整下載";
    }

    // rm txt files & .ts files
    private void removeFiles(File fileListTxt, int totalTsCount, String baseFilePath, String baseFileName) {
        try {
            Files.deleteIfExists(fileListTxt.toPath());
            for (int i = 1; i <= totalTsCount; i++) {
                File tsFile = new File(baseFilePath, String.format(TS_FORMAT, baseFileName, i));
                Files.deleteIfExists(tsFile.toPath());
            }
        } catch (IOException e) {
            log.info("remove files error ", e);
        }
    }

}
