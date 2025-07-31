package com.tech.newbie.m3u8downloader.service;

import com.tech.newbie.m3u8downloader.service.strategy.StatusUpdateStrategy;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;

import static com.tech.newbie.m3u8downloader.common.constant.Constant.TS_FORMAT;

@Slf4j
public class MergeService {

    private final StatusUpdateStrategy<String> strategy;
    private final StatusUpdateStrategy<String> alert;

    public MergeService(StatusUpdateStrategy<String> strategy, StatusUpdateStrategy<String> alert) {
        this.strategy = strategy;
        this.alert = alert;
    }

    public void mergeTsToMp4(String baseFilePath, String baseFileName, int totalFiles) throws IOException {
        strategy.updateStatus("Start merging.........");
        // create FileList.txt that includes all ts files
        StringBuilder fileListContent = new StringBuilder();
        for (int i = 1; i <= totalFiles; i++) {
            fileListContent.append("file '").append(baseFilePath)
                    .append(File.separator).append(String.format(TS_FORMAT, baseFileName, i))
                    .append("'\n");
        }

        // save fileList.txt
        File fileListTxt = new File(baseFilePath, "fileList.txt");
        writeToFile(fileListTxt, fileListContent.toString());



        // log command
        File outputFile = new File(baseFilePath, baseFileName + ".mp4");
        String command = String.format("ffmpeg -f concat -safe 0 -i %s -c copy %s",
                fileListTxt.getAbsolutePath(),
                outputFile.getAbsolutePath());

        log.info("command: {}", command);
        // execute command
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg","-f","concat","-safe","0",
                "-i", fileListTxt.getAbsolutePath(),
                "-c", "copy",outputFile.getAbsolutePath()

        );

        pb.redirectErrorStream(true); // merge stdError & stdOutput
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info(line);
            }

            //wait for the command to complete
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("merging completed，generated {}.mp4", baseFileName);
                strategy.updateStatus("DONE");
            } else {
                log.info("ffmpeg執行失敗，錯誤代碼: {}", exitCode);
                alert.updateStatus("ffmpeg執行失敗，錯誤代碼: " + exitCode);
            }
        } catch (InterruptedException e) {
            log.error("interrupting, ", e);
        }

        removeFiles(fileListTxt, totalFiles, baseFilePath, baseFileName);
    }

    private void writeToFile(File file, String content){
        try {
            Files.write(file.toPath(), content.getBytes());
        } catch (IOException e) {
            log.error("write error ",e);
        }
    }
    // rm txt files & .ts files
    private void removeFiles(File fileListTxt, int totalTsCount, String baseFilePath, String baseFileName){
        try {
            Files.deleteIfExists(fileListTxt.toPath());
            for (int i = 1; i <= totalTsCount ; i++) {
                File tsFile = new File(baseFilePath, String.format(TS_FORMAT, baseFileName, i));
                Files.deleteIfExists(tsFile.toPath());
            }
        } catch (IOException e){
            log.info("remove files error ", e);
        }
    }

}
