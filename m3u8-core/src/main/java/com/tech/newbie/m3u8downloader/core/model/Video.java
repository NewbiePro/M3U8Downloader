package com.tech.newbie.m3u8downloader.core.model;

import java.io.File;

public class Video {
    private final String fileName;
    private final String path;
    private File file;
    private long durationMillis;
    private long currentMillis;

    public Video(String fileName, String path) {
        this.fileName = fileName;
        this.path = path;
        file = new File(path , fileName+ ".mp4");
    }

    public File getFile() {
        if(file == null){
            return new File(path , fileName+ ".mp4");
        }
        return file;
    }

    public boolean exists(){
        return getFile().exists();
    }

    public String getAbsolutePath(){
        return getFile().getAbsolutePath();
    }

}
