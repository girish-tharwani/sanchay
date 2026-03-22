package com.sanchay.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Manages the two-tier configuration model (spec §2.2, §2.3).
 *
 * Tier 1 — app-config.json at:
 *   %APPDATA%\sanchay\app-config.json
 *
 * This file stores only the pointer to the user's chosen data folder.
 * All financial data lives in that data folder (Tier 2), managed by
 * PersistenceService.
 */
public class AppConfig {

    private static final String APP_DIR_NAME  = "sanchay";
    private static final String CONFIG_FILE   = "app-config.json";

    /** Loaded once from version.properties (Maven-filtered from pom.xml). */
    private static final String APP_VERSION = loadVersion();

    private static String loadVersion() {
        try (InputStream in = AppConfig.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(in);
                String v = props.getProperty("app.version");
                if (v != null && !v.isBlank()) return v;
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    /** Returns the application version read from pom.xml via version.properties. */
    public static String getAppVersion() { return APP_VERSION; }

    /** In-memory model of app-config.json */
    public static class Config {
        public String dataFolderPath;
        public String appVersion = APP_VERSION;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Resolve paths ─────────────────────────────────────────────────────────

    /** Returns path to %APPDATA%\sanchay\ */
    public static Path getAppConfigDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            // Fallback for non-Windows or missing env var
            appData = System.getProperty("user.home");
        }
        return Paths.get(appData, APP_DIR_NAME);
    }

    /** Returns path to %APPDATA%\sanchay\app-config.json */
    public static Path getConfigFilePath() {
        return getAppConfigDir().resolve(CONFIG_FILE);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Reads app-config.json.
     * Returns null if the file does not exist.
     * Returns a Config with dataFolderPath == null if the file is malformed.
     */
    public static Config read() {
        Path path = getConfigFilePath();
        if (!Files.exists(path)) return null;
        try (Reader r = new InputStreamReader(
                new FileInputStream(path.toFile()), StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, Config.class);
        } catch (Exception e) {
            // Malformed config — treat as missing
            return new Config();
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Writes app-config.json with the given data folder path.
     * Creates the %APPDATA%\sanchay\ directory if needed.
     */
    public static void write(String dataFolderPath) throws IOException {
        Path dir = getAppConfigDir();
        Files.createDirectories(dir);

        Config cfg = new Config();
        cfg.dataFolderPath = dataFolderPath;
        cfg.appVersion     = APP_VERSION;

        Path tmpFile = dir.resolve(CONFIG_FILE + ".tmp");
        Path target  = dir.resolve(CONFIG_FILE);

        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(tmpFile.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(cfg, w);
        }
        Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    // ── Validate ──────────────────────────────────────────────────────────────

    /**
     * Returns true if the config exists AND its dataFolderPath points to
     * a directory that currently exists on disk.
     */
    public static boolean isValid(Config cfg) {
        if (cfg == null) return false;
        if (cfg.dataFolderPath == null || cfg.dataFolderPath.isBlank()) return false;
        return Files.isDirectory(Paths.get(cfg.dataFolderPath));
    }
}
