package com.tech.newbie.m3u8downloader.gui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

public class PlayerController {

    @FXML
    private MediaView mediaView;
    @FXML
    private Button playPauseButton;
    @FXML
    private Slider progressSlider;
    @FXML
    private Slider volumeSlider;
    @FXML
    private Label timeLabel;

    private MediaPlayer mediaPlayer;

    public void initializePlayer(String mediaPath) {
        Media media = new Media(mediaPath);
        mediaPlayer = new MediaPlayer(media);
        mediaView.setMediaPlayer(mediaPlayer);

        setupMediaControls();

        mediaPlayer.play();
        playPauseButton.setText("⏸");
    }

    private void setupMediaControls() {
        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            Duration total = mediaPlayer.getTotalDuration();
            if (!progressSlider.isValueChanging() && total != null && total.toMillis() > 0) {
                progressSlider.setValue(newTime.toMillis() / total.toMillis() * 100);
            }
            updateTimeLabel(newTime, total);
        });

        progressSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging) {
                Duration total = mediaPlayer.getTotalDuration();
                if (total != null && total.toMillis() > 0) {
                    mediaPlayer.seek(total.multiply(progressSlider.getValue() / 100));
                }
            }
        });

        playPauseButton.setOnAction(e -> {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                playPauseButton.setText("▶");
            } else {
                mediaPlayer.play();
                playPauseButton.setText("⏸");
            }
        });

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            mediaPlayer.setVolume(newVal.doubleValue());
        });

        mediaPlayer.setOnReady(() -> {
            volumeSlider.setValue(mediaPlayer.getVolume());
            updateTimeLabel(mediaPlayer.getCurrentTime(), mediaPlayer.getTotalDuration());
        });
    }

    private void updateTimeLabel(Duration current, Duration total) {
        String currentText = formatTime(current);
        String totalText = total != null ? formatTime(total) : "??:??";
        timeLabel.setText(currentText + " / " + totalText);
    }

    private String formatTime(Duration duration) {
        int minutes = (int) duration.toMinutes();
        int seconds = (int) duration.toSeconds() % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
