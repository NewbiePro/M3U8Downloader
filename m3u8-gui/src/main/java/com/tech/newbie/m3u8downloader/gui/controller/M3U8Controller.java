package com.tech.newbie.m3u8downloader.gui.controller;

import com.tech.newbie.m3u8downloader.core.common.utils.HttpUtil;
import com.tech.newbie.m3u8downloader.gui.strategy.ui.AlertUpdateStrategy;
import com.tech.newbie.m3u8downloader.core.common.callback.UpdateCallback;
import com.tech.newbie.m3u8downloader.gui.viewmodel.M3U8ViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.chart.PieChart;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.chart.PieChart;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

@Slf4j
public class M3U8Controller {
    @FXML
    public TextArea inputArea;
    @FXML
    public TextField fileNameField;
    @FXML
    public Label statusText;
    @FXML
    public ProgressBar progressBar;
    @FXML
    public Label timeLabel;
    @FXML
    public PieChart timePieChart;
    @FXML
    public HBox titleBar;
    @FXML
    public VBox rootBox;

    private final M3U8ViewModel m3U8ViewModel = new M3U8ViewModel();
    private final UpdateCallback<String> alert = new AlertUpdateStrategy();

    // Variables for window dragging offset
    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    public void initialize() {
        // Bind UI components to ViewModel Properties
        statusText.textProperty().bindBidirectional(m3U8ViewModel.getStatusText());
        progressBar.progressProperty().bindBidirectional(m3U8ViewModel.getProgressBar());
        timeLabel.textProperty().bindBidirectional(m3U8ViewModel.getTimeLabel());
        inputArea.textProperty().bindBidirectional(m3U8ViewModel.getInputArea());
        fileNameField.textProperty().bindBidirectional(m3U8ViewModel.getFileName());

        m3U8ViewModel.getPhaseTimes().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
                        new PieChart.Data("Parsing", newValue.getOrDefault("Parsing", 0L)),
                        new PieChart.Data("Downloading", newValue.getOrDefault("Downloading", 0L)),
                        new PieChart.Data("Merging", newValue.getOrDefault("Merging", 0L)));
                timePieChart.setData(pieChartData);
                timePieChart.setVisible(true);
            } else {
                timePieChart.getData().clear();
                timePieChart.setVisible(false);
            }
        });

        // Setup Window Dragging on the Title Bar
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        titleBar.setOnMouseDragged(event -> {
            Stage stage = (Stage) rootBox.getScene().getWindow();
            if (stage != null) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });

        // Setup Drag and Drop for the Input Area
        inputArea.setOnDragOver(event -> {
            if (event.getGestureSource() != inputArea && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                inputArea.getStyleClass().add("drag-over");
            }
            event.consume();
        });

        inputArea.setOnDragExited(event -> {
            inputArea.getStyleClass().remove("drag-over");
            event.consume();
        });

        inputArea.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                inputArea.setText(db.getString());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    @FXML
    public void onMinimizeClick(ActionEvent actionEvent) {
        Stage stage = (Stage) rootBox.getScene().getWindow();
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    @FXML
    public void onCloseClick(ActionEvent actionEvent) {
        Stage stage = (Stage) rootBox.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    public void onSelectPathClick(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        Stage stage = (Stage) inputArea.getScene().getWindow();
        File selectedDir = directoryChooser.showDialog(stage);
        if (selectedDir != null) {
            String path = selectedDir.getAbsolutePath();
            m3U8ViewModel.setPath(path);
            log.info("Path is: {}", path);
        } else {
            alert.update("not chosen any directory, please check");
        }

    }

    @FXML
    public void onDownloadButtonClick(ActionEvent actionEvent) {
        String inputUrl = inputArea.getText();
        String path = m3U8ViewModel.getPath();
        String file = fileNameField.getText();

        if (StringUtils.isBlank(path)) {
            alert.update("Please select a downloading path");
            return;
        }

        if (!HttpUtil.isValidUrl(inputUrl)) {
            alert.update("Please enter a valid url");
            return;
        }

        if (StringUtils.isBlank(file)) {
            alert.update("Please enter a file name");
            return;
        }

        m3U8ViewModel.startDownload();
    }

    @FXML
    public void onPlayButtonClick() {
        Optional<File> fileOpt = m3U8ViewModel.getVideoFile();
        if (fileOpt.isEmpty()) {
            alert.update("video not exists, please download first");
            return;
        }

        playVideo(fileOpt.get());
    }

    @FXML
    public void onChooseVideoClick(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Video File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mkv"));

        Stage stage = (Stage) inputArea.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            playVideo(selectedFile);
        } else {
            alert.update("Please select a video");
        }

    }

    private void playVideo(File videoFile) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerView.fxml"));
            Parent root = loader.load();

            PlayerController controller = loader.getController();

            // call player
            controller.initializePlayer(videoFile.toURI().toString());

            // build up a new scene to play the video
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Video Player");
            stage.show();

        } catch (IOException e) {
            alert.update("Could not play the video: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
