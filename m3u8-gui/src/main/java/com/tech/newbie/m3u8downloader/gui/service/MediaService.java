package com.tech.newbie.m3u8downloader.gui.service;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;

public class MediaService {
    public MediaPlayer createMediaPlayer(File videoFile) {
        Media media = new Media(videoFile.toURI().toString());
        return new MediaPlayer(media);
    }

}
