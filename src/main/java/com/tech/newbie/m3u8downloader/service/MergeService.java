package com.tech.newbie.m3u8downloader.service;

import com.tech.newbie.m3u8downloader.service.strategy.StatusUpdateStrategy;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;

import static com.tech.newbie.m3u8downloader.common.Constant.TS_FORMAT;

public class MergeService {

    private final StatusUpdateStrategy<String> strategy;

    public MergeService(StatusUpdateStrategy<String> strategy) {
        this.strategy = strategy;
    }

    public void mergeTsToMp4(String baseFilePath, String baseFileName, int totalFiles) throws IOException {
        strategy.updateStatus("Start merging.........");
        // create FileList.txt that includes all ts files
        StringBuilder fileListContent = new StringBuilder();
        for (int i = 1; i < totalFiles; i++) {
            fileListContent.append("file '").append(baseFilePath)
                    .append(File.separator).append(String.format(TS_FORMAT, baseFileName, i))
                    .append(" '\n");
        }

        // save fileList.txt
        File fileList = new File(baseFilePath, "fileList.txt");
        Files.write(fileList.toPath(), fileListContent.toString().getBytes());

        // call ffmpeg
        String command = String.format("ffmpeg -f concat -safe 0 -i %s -c copy %s",
                fileList.getAbsolutePath(),
                new File(baseFilePath, baseFileName + ".mp4").getAbsolutePath());
        System.out.println("command: "+ command);
        // execute command
        ProcessBuilder pb = new ProcessBuilder(command.split(" "));
        pb.redirectErrorStream(true); // merge stdError & stdOutput
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            //等待命令執行完畢
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("合併完成，生成 " + baseFileName + ".mp4");
            } else {
                System.out.println("ffmpeg執行失敗，錯誤代碼: " + exitCode);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

}
