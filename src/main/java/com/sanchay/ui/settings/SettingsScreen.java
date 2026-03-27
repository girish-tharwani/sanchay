package com.sanchay.ui.settings;

import com.sanchay.service.AppConfig;
import com.sanchay.service.DataStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.*;

/** Settings screen. */
public class SettingsScreen {

    private ScrollPane view;

    public SettingsScreen() { buildView(); }

    public Node getView() { return view; }

    public void refresh() { buildView(); }

    private void buildView() {
        VBox content = new VBox(24);
        content.getStyleClass().add("main-panel");
        content.setPadding(new Insets(24));

        Label title = new Label("Settings");
        title.getStyleClass().add("screen-title");

        content.getChildren().addAll(
                title,
                buildSection("📁  Data",       buildDataSection()),
                buildSection("🎨  Appearance", buildAppearanceSection()),
                buildSection("💾  Backup",     buildBackupSection()),
                buildSection("ℹ️  About",     buildAboutSection())
        );

        view = new ScrollPane(content);
        view.setFitToWidth(true);
        view.getStyleClass().add("scroll-page-bg");
    }

    // ── Section wrapper ───────────────────────────────────────────────────────

    private VBox buildSection(String heading, Node body) {
        VBox section = new VBox(8);
        HBox labelRow = new HBox(8);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        // Shape.fill cannot be set via style class; brand accent stays inline
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4,
                javafx.scene.paint.Color.web("#3db89a"));
        Label h = new Label(heading.replaceAll("^[^\\w]*", "").toUpperCase());
        h.getStyleClass().add("section-group-label");
        labelRow.getChildren().addAll(dot, h);
        VBox card = new VBox(12);
        card.getStyleClass().add("table-card");
        card.setPadding(new Insets(16));
        card.getChildren().add(body);
        section.getChildren().addAll(labelRow, card);
        return section;
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private VBox buildDataSection() {
        VBox box = new VBox(10);

        HBox pathRow = new HBox(10);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        Label pathLbl = new Label("Data Folder:");
        pathLbl.getStyleClass().add("text-form-value");
        pathLbl.setMinWidth(130);

        String currentPath = DataStore.getInstance().getDataFolderPath();
        TextField pathField = new TextField(currentPath);
        pathField.setEditable(false);
        pathField.setPrefWidth(360);
        Button browseBtn = new Button("Change…");
        browseBtn.getStyleClass().add("btn-gold");
        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Data Folder");
            File chosen = dc.showDialog(null);
            if (chosen != null) {
                pathField.setText(chosen.getAbsolutePath());
                showInfo("Data Folder", "Restart the application to apply the new data folder location.");
            }
        });
        pathRow.getChildren().addAll(pathLbl, pathField, browseBtn);

        Label hint = new Label("You can move your data folder to any location — cloud drive, external drive, etc.");
        hint.getStyleClass().add("text-hint");
        hint.setWrapText(true);
        box.getChildren().addAll(pathRow, hint);
        return box;
    }

    // ── Appearance ────────────────────────────────────────────────────────────

    private VBox buildAppearanceSection() {
        VBox box = new VBox(10);

        HBox dateRow = new HBox(10);
        dateRow.setAlignment(Pos.CENTER_LEFT);
        Label dateLbl = new Label("Date Format:");
        dateLbl.getStyleClass().add("text-form-value");
        dateLbl.setMinWidth(180);

        ComboBox<String> dateCb = new ComboBox<>();
        dateCb.getItems().addAll("DD/MM/YYYY", "YYYY-MM-DD");
        dateCb.setValue(DataStore.getInstance().getDateFormat());

        dateCb.setOnAction(e -> DataStore.getInstance().setDateFormat(dateCb.getValue()));

        Label hint = new Label("Takes effect immediately on all date displays throughout the app.");
        hint.getStyleClass().add("text-hint");

        dateRow.getChildren().addAll(dateLbl, dateCb);
        box.getChildren().addAll(dateRow, hint);
        return box;
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    private HBox buildBackupSection() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label("Create a timestamped ZIP archive of your entire data folder.");
        lbl.getStyleClass().add("text-body-muted");
        lbl.setWrapText(true);
        HBox.setHgrow(lbl, Priority.ALWAYS);
        Button backupBtn = new Button("Backup Now");
        backupBtn.getStyleClass().add("btn-gold");
        backupBtn.setOnAction(e -> doBackup());
        row.getChildren().addAll(lbl, backupBtn);
        return row;
    }

    private void doBackup() {
        DataStore ds = DataStore.getInstance();
        if (ds.getPersistence() == null) {
            showInfo("Backup", "No data folder configured.");
            return;
        }
        String dataPath = ds.getDataFolderPath();
        if (dataPath == null) {
            showInfo("Backup", "No data folder configured.");
            return;
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Save Backup As");
        fc.setInitialFileName("PersonalFinance_backup_" + timestamp + ".zip");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("ZIP Archives", "*.zip"));
        File zipFile = fc.showSaveDialog(null);
        if (zipFile == null) return;
        try {
            zipFolder(Paths.get(dataPath), zipFile.toPath());
            showInfo("Backup Complete", "Saved to:\n" + zipFile.getAbsolutePath());
        } catch (IOException ex) {
            showInfo("Backup Failed", "Error: " + ex.getMessage());
        }
    }

    private void zipFolder(Path sourceFolder, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(
                new FileOutputStream(zipPath.toFile()))) {
            try (var stream = Files.walk(sourceFolder)) {
                for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                    ZipEntry entry = new ZipEntry(
                            sourceFolder.relativize(file).toString().replace("\\", "/"));
                    zos.putNextEntry(entry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    // ── About ─────────────────────────────────────────────────────────────────

    private VBox buildAboutSection() {
        VBox box = new VBox(6);
        addInfo(box, "Application",    "Personal Finance Manager");
        addInfo(box, "Version",        AppConfig.getAppVersion());
        addInfo(box, "Data Schema",    "v4");
        addInfo(box, "Platform",       "Windows 11+ / JavaFX");
        return box;
    }

    private void addInfo(VBox box, String label, String value) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label + ":");
        lbl.setMinWidth(130);
        lbl.getStyleClass().add("text-form-value");
        Label val = new Label(value);
        val.getStyleClass().add("text-body-muted");
        row.getChildren().addAll(lbl, val);
        box.getChildren().add(row);
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}