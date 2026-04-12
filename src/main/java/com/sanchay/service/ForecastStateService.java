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
import java.util.stream.Collectors;

/**
 * Loads and saves user-applied forecast overrides (manual amount corrections and
 * exclusions) to {@code forecast_overrides.json} in the app data folder.
 *
 * Override uniqueness key: (categoryId, subCategoryId, month).
 * Saving an override with the same key replaces the existing one.
 */
public class ForecastStateService {

    private static final String FILE           = "forecast_overrides.json";
    private static final String SELECTION_FILE = "forecast_account_selection.json";
    private static final Gson   GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataFolder;
    private final List<ForecastOverride> overrides = new ArrayList<>();
    /** {@code null} means "all accounts" (default — no file on disk). */
    private Set<String> accountSelection = null;

    public ForecastStateService(String dataFolderPath) {
        this.dataFolder = Paths.get(dataFolderPath);
        load();
        loadAccountSelection();
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

    // ── Account selection ─────────────────────────────────────────────────────

    /**
     * Returns the persisted account-ID selection, or {@code null} when no selection
     * has been saved (meaning "show all accounts").
     */
    public Set<String> getAccountSelection() {
        return accountSelection == null ? null : Collections.unmodifiableSet(accountSelection);
    }

    /**
     * Persists the account selection.  Pass {@code null} or an empty set to reset
     * to "show all".
     */
    public void saveAccountSelection(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            accountSelection = null;
            try { Files.deleteIfExists(dataFolder.resolve(SELECTION_FILE)); } catch (IOException ignored) {}
        } else {
            accountSelection = ids.stream()
                    .limit(10) // hard cap — matches dialog enforcement
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            persistAccountSelection();
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadAccountSelection() {
        Path file = dataFolder.resolve(SELECTION_FILE);
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> loaded = GSON.fromJson(json, listType);
            if (loaded != null && !loaded.isEmpty())
                accountSelection = new LinkedHashSet<>(loaded);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void persistAccountSelection() {
        Path target = dataFolder.resolve(SELECTION_FILE);
        Path tmp    = dataFolder.resolve(SELECTION_FILE + ".tmp");
        try {
            Files.createDirectories(dataFolder);
            Files.writeString(tmp, GSON.toJson(new ArrayList<>(accountSelection)), StandardCharsets.UTF_8);
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
