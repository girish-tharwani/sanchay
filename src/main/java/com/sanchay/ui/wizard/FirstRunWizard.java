package com.sanchay.ui.wizard;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.nio.file.*;

/**
 * First-Run Wizard (spec §3.0).
 *
 * A full-window splash screen shown when:
 *   - app-config.json does not exist (genuine first run), OR
 *   - the stored data folder no longer exists (folder moved/deleted).
 *
 * Page 1 — data folder selection. For fresh setups, PreferencesSetupDialog
 * is shown as a modal dialog after folder selection before the wizard closes.
 *
 * Returns the selected data folder path via showAndWait(), or null if
 * the user closed the window without selecting.
 * Preferences are exposed via getCurrency(), getYearFormat(), getDateFormat()
 * and are non-null only when a fresh setup was completed.
 */
public class FirstRunWizard {

    private final Stage stage;
    private final String missingPreviousPath;

    private String selectedPath   = null;
    private String selectedCurrency   = null;
    private String selectedYearFormat = null;
    private String selectedDateFormat = null;

    public FirstRunWizard(Stage owner, String missingPreviousPath) {
        this.missingPreviousPath = missingPreviousPath;
        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle("Sanchay — Setup");
        stage.setMinWidth(620);
        stage.setMinHeight(510);
        stage.setResizable(false);
        if (owner != null) stage.initOwner(owner);
    }

    /**
     * Shows the wizard and blocks until the user completes or closes it.
     * Returns the chosen data folder path, or null if cancelled.
     */
    public String showAndWait() {
        stage.setScene(buildFolderScene());
        stage.showAndWait();
        return selectedPath;
    }

    public String getCurrency()   { return selectedCurrency; }
    public String getYearFormat() { return selectedYearFormat; }
    public String getDateFormat() { return selectedDateFormat; }

    // ── Page 1: Data folder selection ─────────────────────────────────────────

    private Scene buildFolderScene() {
        VBox root = new VBox(0);
        root.getStyleClass().add("wizard-root");

        root.getChildren().addAll(buildHeader(), buildFolderBody(root));

        return applyStylesheet(new Scene(root, 620, 510));
    }

    private VBox buildFolderBody(VBox root) {
        VBox body = new VBox(20);
        body.getStyleClass().add("wizard-body");
        body.setPadding(new Insets(36, 48, 36, 48));
        VBox.setVgrow(body, Priority.ALWAYS);

        if (missingPreviousPath != null) {
            Label warn = new Label(
                    "⚠  Your previous data folder could not be found:\n" + missingPreviousPath
                    + "\n\nPlease select the new location of your data folder, "
                    + "or choose a different folder to start fresh.");
            warn.setWrapText(true);
            warn.getStyleClass().add("alert-warning");
            body.getChildren().add(warn);
        } else {
            Label welcome = new Label("Welcome! Let's get you set up.");
            welcome.getStyleClass().add("text-heading-lg");
            body.getChildren().add(welcome);
        }

        Label explanation = new Label(
                "Your financial data is saved as files in a folder you choose. \n"
                + "You can store this folder on your computer, an external drive, "
                + "or a synced folder (e.g. OneDrive, Google Drive).\n"
                + "If you already have a data folder from a previous installation, "
                + "select that folder and your data will be loaded automatically.");
        explanation.setWrapText(true);
        explanation.setMinHeight(Region.USE_PREF_SIZE);
        explanation.getStyleClass().add("text-body-muted");

        Label folderLabel = new Label("Data folder:");
        folderLabel.getStyleClass().add("form-label");

        TextField pathField = new TextField();
        pathField.setPromptText("No folder selected…");
        pathField.setEditable(false);
        pathField.setPrefWidth(340);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button browseBtn = new Button("Browse…");
        browseBtn.getStyleClass().add("btn-gold");

        HBox pickerRow = new HBox(10, pathField, browseBtn);
        pickerRow.setAlignment(Pos.CENTER_LEFT);

        Label statusLbl = new Label();
        statusLbl.getStyleClass().add("text-success");

        Button startBtn = new Button("Get Started");
        startBtn.getStyleClass().add("btn-gold");
        startBtn.setDisable(true);

        Label footerNote = new Label("You can change this location at any time from Settings.");
        footerNote.getStyleClass().add("text-hint");

        body.getChildren().addAll(explanation, folderLabel, pickerRow, statusLbl, startBtn, footerNote);

        boolean[] hasData = {false};

        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Data Folder");
            File chosen = dc.showDialog(stage);
            if (chosen == null) return;

            String path = chosen.getAbsolutePath();
            pathField.setText(path);
            startBtn.setDisable(false);

            hasData[0] = Files.exists(Paths.get(path, "accounts.json"))
                    || Files.exists(Paths.get(path, "transactions.json"))
                    || Files.exists(Paths.get(path, "categories.json"));

            if (hasData[0]) {
                statusLbl.setText("✅  Existing data found — it will be loaded automatically.");
                startBtn.setText("Open Existing Data");
            } else {
                statusLbl.setText("📁  New folder — a fresh data set will be created here.");
                startBtn.setText("Get Started →");
            }
        });

        startBtn.setOnAction(e -> {
            selectedPath = pathField.getText();
            if (!hasData[0]) {
                // Fresh setup — collect preferences via dialog before closing
                PreferencesSetupDialog.Result result = PreferencesSetupDialog.show();
                if (result != null) {
                    selectedCurrency   = result.currency();
                    selectedYearFormat = result.yearFormat();
                    selectedDateFormat = result.dateFormat();
                }
            }
            stage.close();
        });

        return body;
    }

    // ── Shared header band ────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox header = new VBox(8);
        header.setPadding(new Insets(40, 48, 32, 48));
        header.setAlignment(Pos.CENTER_LEFT);

        Label appName = new Label("💰 Sanchay");
        appName.getStyleClass().add("wizard-app-name");

        Label tagline = new Label("Your household finances, organised.");
        tagline.getStyleClass().add("wizard-tagline");

        header.getChildren().addAll(appName, tagline);
        return header;
    }

    private Scene applyStylesheet(Scene scene) {
        try {
            String css = getClass().getResource("/com/sanchay/css/app.css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception ignored) {}
        return scene;
    }
}
