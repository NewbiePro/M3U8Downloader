package com.tech.newbie.m3u8downloader.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private final Properties props = new Properties();
    private static final AppConfig INSTANCE = new AppConfig();

    public AppConfig(){
        try(InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            props .load(input);
        } catch (IOException e) {
            throw new RuntimeException("failed to load application.properties", e);
        }
    }

    public static AppConfig getInstance() {
        return INSTANCE;
    }

    public int getMaxThreads() {
        return Integer.parseInt(props.getProperty("download.max-threads", "10"));
    }

    public int getMaxRetries() {
        return Integer.parseInt(props.getProperty("download.max-retries", "3"));
    }

    public int getTimeout() {
        return Integer.parseInt(props.getProperty("download.timeout", "50000"));
    }
}