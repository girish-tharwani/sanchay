package com.sanchay.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Persists report UI preferences (hidden category sets) to
 * {@code report_prefs.json} in the user's data folder.
 */
public class ReportPrefsService {

    private static final String FILE = "report_prefs.json";
    private static final Gson   GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;
    private Prefs prefs;

    /** DTO stored in report_prefs.json */
    private static class Prefs {
        Set<String> expenseReportHidden = new HashSet<>();
        Set<String> expenseTrendHidden  = new HashSet<>();
    }

    public ReportPrefsService(String dataFolderPath) {
        this.filePath = Paths.get(dataFolderPath, FILE);
        load();
    }

    // ── Expense Report ────────────────────────────────────────────────────────

    public Set<String> getExpenseReportHidden() {
        return new HashSet<>(prefs.expenseReportHidden);
    }

    public void saveExpenseReportHidden(Set<String> hidden) {
        prefs.expenseReportHidden = new HashSet<>(hidden);
        save();
    }

    // ── Expense Trend ─────────────────────────────────────────────────────────

    public Set<String> getExpenseTrendHidden() {
        return new HashSet<>(prefs.expenseTrendHidden);
    }

    public void saveExpenseTrendHidden(Set<String> hidden) {
        prefs.expenseTrendHidden = new HashSet<>(hidden);
        save();
    }

    // ── Load / Save ───────────────────────────────────────────────────────────

    private void load() {
        if (Files.exists(filePath)) {
            try {
                String json = Files.readString(filePath, StandardCharsets.UTF_8);
                Prefs loaded = GSON.fromJson(json, Prefs.class);
                prefs = (loaded != null) ? loaded : new Prefs();
                if (prefs.expenseReportHidden == null) prefs.expenseReportHidden = new HashSet<>();
                if (prefs.expenseTrendHidden  == null) prefs.expenseTrendHidden  = new HashSet<>();
            } catch (IOException e) {
                prefs = new Prefs();
            }
        } else {
            prefs = new Prefs();
        }
    }

    private void save() {
        try {
            Files.writeString(filePath, GSON.toJson(prefs), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            // Non-fatal — prefs will reset on next restart
        }
    }
}
