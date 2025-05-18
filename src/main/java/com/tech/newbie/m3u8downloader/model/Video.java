package com.tech.newbie.m3u8downloader.model;

import java.io.File;

public class Video {
    private final String fileName;
    private final String path;

    public Video(String fileName, String path) {
        this.fileName = fileName;
        this.path = path;
    }

    public File getFile() {
        return new File(path , fileName+ ".mp4");
    }

    public boolean exists(){
        return getFile().exists();
    }

    public String getAbsolutePath(){
        return getFile().getAbsolutePath();
    }

}
