package com.sanchay.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sanchay.model.PlanParameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Loads and saves the financial planning parameters to {@code plan_params.json}
 * in the app data folder. Returns default {@link PlanParameters} if the file
 * does not yet exist.
 */
public class PlanParamsService {

    private static final String FILE = "plan_params.json";
    private static final Gson   GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataFolder;

    public PlanParamsService(String dataFolderPath) {
        this.dataFolder = Paths.get(dataFolderPath);
    }

    /** Loads parameters from disk. Returns defaults if the file does not exist. */
    public PlanParameters load() {
        Path file = dataFolder.resolve(FILE);
        if (!Files.exists(file)) return new PlanParameters();
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            PlanParameters p = GSON.fromJson(json, PlanParameters.class);
            return p != null ? p : new PlanParameters();
        } catch (IOException e) {
            e.printStackTrace();
            return new PlanParameters();
        }
    }

    /** Persists parameters to disk atomically (write .tmp then rename). */
    public void save(PlanParameters params) {
        Path target = dataFolder.resolve(FILE);
        Path tmp    = dataFolder.resolve(FILE + ".tmp");
        try {
            Files.createDirectories(dataFolder);
            Files.writeString(tmp, GSON.toJson(params), StandardCharsets.UTF_8);
            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException ex) { ex.printStackTrace(); }
        } catch (IOException e) {
            e.printStackTrace();
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }
}
