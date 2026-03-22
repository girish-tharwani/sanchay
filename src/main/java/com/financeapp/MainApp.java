package com.financeapp;

import com.financeapp.service.AppConfig;
import com.financeapp.service.DataStore;
import com.financeapp.service.PersistenceService;
import com.financeapp.ui.MainWindow;
import com.financeapp.ui.SplashScreen;
import com.financeapp.ui.wizard.FirstRunWizard;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Application entry point.
 *
 * Startup decision tree:
 *
 *   1. Read %APPDATA%\sanchay\app-config.json
 *   2. Config missing → First-Run Wizard
 *      Config present but folder missing → First-Run Wizard (recovery mode)
 *      Config present and folder valid → proceed
 *   3. Load data files from folder into DataStore
 *   4. Show main application window
 *
 * seedMockData() is NOT called — all data comes from files.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        String dataFolderPath = resolveDataFolder(primaryStage);

        if (dataFolderPath == null) {
            // User closed the wizard without selecting a folder
            javafx.application.Platform.exit();
            return;
        }

        SplashScreen splash = new SplashScreen();
        splash.show();

        PersistenceService persistence = new PersistenceService(dataFolderPath);
        DataStore store = DataStore.getInstance();
        store.setPersistence(persistence);
        store.setDataFolderPath(dataFolderPath);

        // Check before loadAll — seeding creates files so checking after always returns false
        boolean isFirstRun = !persistence.hasExistingData();
        persistence.loadAll(store);

        splash.closeAndThen(() -> new MainWindow(isFirstRun).show(primaryStage));
    }

    /**
     * Reads app-config.json and decides whether to show the wizard.
     * Returns the confirmed data folder path, or null if the user cancelled.
     */
    private String resolveDataFolder(Stage primaryStage) {
        AppConfig.Config cfg = AppConfig.read();

        if (AppConfig.isValid(cfg)) {
            // Happy path — folder exists, proceed
            return cfg.dataFolderPath;
        }

        // Show wizard — either first run or folder missing
        String missingPath = (cfg != null && cfg.dataFolderPath != null)
                ? cfg.dataFolderPath : null;

        FirstRunWizard wizard = new FirstRunWizard(primaryStage, missingPath);
        String chosen = wizard.showAndWait();

        if (chosen == null) return null;

        // Save the chosen path to app-config.json
        try {
            AppConfig.write(chosen);
        } catch (Exception e) {
            // Non-fatal: warn but continue — data will still load correctly this session
            e.printStackTrace();
        }

        return chosen;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
