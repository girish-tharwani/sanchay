package com.sanchay.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sanchay.model.ForecastOverride;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Loads and saves user-applied forecast overrides (manual amount corrections and
 * exclusions) to {@code forecast_overrides.json} in the app data folder.
 *
 * Override uniqueness key: (categoryId, subCategoryId, month).
 * Saving an override with the same key replaces the existing one.
 */
public class ForecastStateService {

    private static final String FILE = "forecast_overrides.json";
    private static final Gson   GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataFolder;
    private final List<ForecastOverride> overrides = new ArrayList<>();

    public ForecastStateService(String dataFolderPath) {
        this.dataFolder = Paths.get(dataFolderPath);
        load();
    }

    public List<ForecastOverride> getOverrides() {
        return Collections.unmodifiableList(overrides);
    }

    /**
     * Saves an override, replacing any existing entry with the same key.
     *
     * Special case — "include all months" ({@code !excluded}, no amount, allMonths):
     * removes every override for this sub-category entirely, restoring the default
     * computed forecast across all months.
     */
    public void saveOverride(ForecastOverride override) {
        String catId    = override.getCategoryId();
        String subCatId = override.getSubCategoryId();

        if (!override.isExcluded()
                && override.getOverrideAmountPaise() == null
                && override.isAllMonths()) {
            // "Include all future months" — wipe every override for this sub-category
            overrides.removeIf(o -> Objects.equals(o.getCategoryId(),    catId)
                                 && Objects.equals(o.getSubCategoryId(), subCatId));
        } else {
            // Replace matching key, then add
            overrides.removeIf(o -> o.sameKey(override));
            overrides.add(override);
        }
        persist();
    }

    /** Discards all overrides (called when the user regenerates projections from scratch). */
    public void clearAllOverrides() {
        overrides.clear();
        persist();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        Path file = dataFolder.resolve(FILE);
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<ForecastOverride>>() {}.getType();
            List<ForecastOverride> loaded = GSON.fromJson(json, listType);
            if (loaded != null) overrides.addAll(loaded);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void persist() {
        Path target = dataFolder.resolve(FILE);
        Path tmp    = dataFolder.resolve(FILE + ".tmp");
        try {
            Files.createDirectories(dataFolder);
            Files.writeString(tmp, GSON.toJson(overrides), StandardCharsets.UTF_8);
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
