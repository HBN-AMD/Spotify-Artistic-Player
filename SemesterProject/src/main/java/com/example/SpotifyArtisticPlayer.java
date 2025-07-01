package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.sql.*;
import java.util.*;

public class SpotifyArtisticPlayer extends Application {

    // Database connection
    private static final String DB_URL = "jdbc:mysql://localhost:3306/spotify_player";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "password";

    private List<Song> songs = new ArrayList<>();
    private FlowPane songPane = new FlowPane();
    private VBox nowPlayingBar = new VBox();
    private MediaPlayer mediaPlayer;
    private Song currentSong = null;

    @Override
    public void start(Stage primaryStage) {
        // Top bar: add song button
        Button addBtn = new Button("+");
        addBtn.setPrefSize(50, 50);
        addBtn.setOnAction(e -> showAddSongDialog(primaryStage));

        Label trending = new Label("Songs Palyer");

        HBox topBar = new HBox(addBtn, trending);
        topBar.setSpacing(20);
        topBar.setPadding(new Insets(10, 0, 0, 30));

        // Song cards area
        songPane.setPadding(new Insets(30, 10, 10, 30));
        songPane.setHgap(40);
        songPane.setVgap(30);

        ScrollPane scrollPane = new ScrollPane(songPane);
        scrollPane.setFitToWidth(true);

        // Now Playing Bar
        nowPlayingBar.setVisible(false); // hidden until a song is played

        VBox root = new VBox(topBar, scrollPane, nowPlayingBar);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Scene scene = new Scene(root, 1100, 700);
        primaryStage.setTitle("Spotify Artistic FX");
        primaryStage.setScene(scene);
        primaryStage.show();

        loadSongsFromDatabase();
        refreshUI(primaryStage);
    }

    private void refreshUI(Stage primaryStage) {
        Platform.runLater(() -> {
            songPane.getChildren().clear();
            for (Song song : songs) {
                VBox card = createSongCard(song, primaryStage);
                songPane.getChildren().add(card);
            }
        });
    }

    private VBox createSongCard(Song song, Stage primaryStage) {
        ImageView coverView = new ImageView();
        try {
            coverView.setImage(new Image("file:" + song.coverPath, 180, 180, true, true));
        } catch (Exception e) { }
        coverView.setFitWidth(180);
        coverView.setFitHeight(180);

        Label titleLbl = new Label(song.title);
        Label artistLbl = new Label(song.artist);

        VBox card = new VBox(coverView, titleLbl, artistLbl);
        card.setAlignment(Pos.TOP_CENTER);
        card.setSpacing(9);
        card.setPadding(new Insets(14, 10, 14, 10));
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) openNowPlayingBar(song);
        });
        return card;
    }

    private void openNowPlayingBar(Song song) {
        if (mediaPlayer != null) mediaPlayer.stop();
        currentSong = song;
        Media media = new Media(new File(song.path).toURI().toString());
        mediaPlayer = new MediaPlayer(media);

        // UI controls
        ImageView cover = new ImageView(new Image("file:" + song.coverPath, 60, 60, true, true));
        Label title = new Label(song.title); title.setStyle("font-size: 16; text-fill: white; font-weight:bold;");
        Label artist = new Label(song.artist); artist.setStyle("text-fill: #b3b3b3;");
        VBox info = new VBox(title, artist); info.setSpacing(1);

        Button prev = iconBtn("⏮", "Previous");
        Button playPause = iconBtn("⏸", "Pause");
        Button next = iconBtn("⏭", "Next");
        Button repeat = iconBtn("🔁", "Repeat");

        Slider progress = new Slider(0, 1, 0);
        Label timeNow = new Label("0:00"); timeNow.setStyle("text-fill:white;");
        Label timeTotal = new Label("0:00"); timeTotal.setStyle("text-fill:white;");

        HBox controls = new HBox(15, prev, playPause, next, repeat);
        controls.setAlignment(Pos.CENTER);

        HBox progressBar = new HBox(8, timeNow, progress, timeTotal);
        progressBar.setAlignment(Pos.CENTER);

        HBox mainBar = new HBox(15, cover, info, controls);
        mainBar.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(mainBar, progressBar);
        bar.setSpacing(8);

        nowPlayingBar.getChildren().setAll(bar);
        nowPlayingBar.setVisible(true);

        // MediaPlayer event bindings
        mediaPlayer.setOnReady(() -> {
            progress.setMax(mediaPlayer.getTotalDuration().toSeconds());
            timeTotal.setText(formatTime(mediaPlayer.getTotalDuration()));
        });
        mediaPlayer.currentTimeProperty().addListener((obs, old, cur) -> {
            progress.setValue(cur.toSeconds());
            timeNow.setText(formatTime(cur));
        });
        progress.valueChangingProperty().addListener((obs, was, is) -> {
            if (!is && mediaPlayer != null) mediaPlayer.seek(Duration.seconds(progress.getValue()));
        });
        playPause.setOnAction(e -> {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                playPause.setText("▶");
                playPause.setTooltip(new Tooltip("Play"));
            } else {
                mediaPlayer.play();
                playPause.setText("⏸");
                playPause.setTooltip(new Tooltip("Pause"));
            }
        });
        prev.setOnAction(e -> playPrev());
        next.setOnAction(e -> playNext());
        repeat.setOnAction(e -> mediaPlayer.seek(Duration.ZERO));
        mediaPlayer.play();
    }

    private Button iconBtn(String icon, String tooltip) {
        Button btn = new Button(icon);
        btn.setTooltip(new Tooltip(tooltip));
        return btn;
    }

    private void playPrev() {
        if (currentSong == null || songs.isEmpty()) return;
        int idx = songs.indexOf(currentSong);
        if (idx > 0) openNowPlayingBar(songs.get(idx - 1));
    }
    private void playNext() {
        if (currentSong == null || songs.isEmpty()) return;
        int idx = songs.indexOf(currentSong);
        if (idx < songs.size() - 1) openNowPlayingBar(songs.get(idx + 1));
    }

    private String formatTime(Duration d) {
        int min = (int) d.toMinutes(), sec = (int) (d.toSeconds() % 60);
        return String.format("%d:%02d", min, sec);
    }

    // Song upload dialog, improved
    private void showAddSongDialog(Stage owner) {
        Dialog<Song> dialog = new Dialog<>();
        dialog.setTitle("Add Song");
        dialog.getDialogPane().setStyle("background-color: #222;");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField titleField = new TextField();
        TextField artistField = new TextField();
        Button chooseMp3 = new Button("Choose MP3");
        Button chooseImg = new Button("Choose Image");
        Label mp3Label = new Label("");
        Label imgLabel = new Label("");
        final String[] mp3Path = {null};
        final String[] imgPath = {null};

        chooseMp3.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP3", "*.mp3"));
            File f = fc.showOpenDialog(owner);
            if (f != null) {
                mp3Path[0] = f.getAbsolutePath();
                String baseName = f.getName();
                if (baseName.endsWith(".mp3")) baseName = baseName.substring(0, baseName.length()-4);
                mp3Label.setText(f.getName());
            }
        });
        chooseImg.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image", "*.png", "*.jpg", "*.jpeg"));
            File f = fc.showOpenDialog(owner);
            if (f != null) { imgPath[0] = f.getAbsolutePath(); imgLabel.setText(f.getName()); }
        });

        GridPane grid = new GridPane();
        grid.setVgap(12); grid.setHgap(10); grid.setPadding(new Insets(24));
        grid.add(new Label("Song name:"), 0, 0); grid.add(titleField, 1, 0);
        grid.add(new Label("Artist:"), 0, 1); grid.add(artistField, 1, 1);
        grid.add(chooseMp3, 0, 2); grid.add(mp3Label, 1, 2);
        grid.add(chooseImg, 0, 3); grid.add(imgLabel, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK && titleField.getText().trim().length() > 0 &&
                    mp3Path[0] != null && imgPath[0] != null) {
                return new Song(-1, titleField.getText().trim(), artistField.getText().trim(),
                        mp3Path[0], imgPath[0]);
            }
            return null;
        });

        Optional<Song> result = dialog.showAndWait();
        result.ifPresent(song -> {
            int songId = insertSongToDatabase(song.title, song.artist, song.path, song.coverPath);
            song.id = songId;
            songs.add(song);
            refreshUI(owner);
        });
    }

    // DATABASE PART: load and insert songs
    private void loadSongsFromDatabase() {
        songs.clear();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM songs")) {
            while (rs.next()) {
                songs.add(new Song(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getString("path"),
                        rs.getString("cover")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int insertSongToDatabase(String title, String artist, String path, String coverPath) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO songs (title, artist, path, cover) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, title);
            ps.setString(2, artist);
            ps.setString(3, path);
            ps.setString(4, coverPath);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static void main(String[] args) {
        launch(args);
    }
}