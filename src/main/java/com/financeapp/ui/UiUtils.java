package com.financeapp.ui;

import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class UiUtils {
    private UiUtils() {}

    /** Small italic hint label for placement below editable tables. */
    public static Label hintLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888; -fx-font-style: italic;");
        return lbl;
    }

    /**
     * Applies a smart date converter to the given DatePicker.
     *
     * Handles 2-digit year input: "2/2/26" → 2026-02-02 instead of 0026-02-02.
     * After any successful parse, if the year is < 100 it is shifted to 20xx.
     * Accepts common separators (/ and -) and yyyy or yy year widths.
     * Display format is d/M/yyyy (e.g. "2/2/2026").
     */
    public static void applySmartDateConverter(DatePicker dp) {
        dp.setConverter(new StringConverter<>() {
            private static final DateTimeFormatter DISPLAY =
                    DateTimeFormatter.ofPattern("d/M/yyyy");
            private static final List<DateTimeFormatter> PARSE_FMTS = List.of(
                    DateTimeFormatter.ofPattern("d/M/yyyy"),
                    DateTimeFormatter.ofPattern("d-M-yyyy"),
                    DateTimeFormatter.ofPattern("yyyy-M-d"),
                    DateTimeFormatter.ofPattern("d/M/yy"),
                    DateTimeFormatter.ofPattern("d-M-yy")
            );

            @Override public String toString(LocalDate d) {
                return d == null ? "" : DISPLAY.format(d);
            }

            @Override public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) return null;
                for (DateTimeFormatter fmt : PARSE_FMTS) {
                    try {
                        LocalDate d = LocalDate.parse(text.strip(), fmt);
                        if (d.getYear() < 100) d = d.withYear(d.getYear() + 2000);
                        return d;
                    } catch (DateTimeParseException ignored) {}
                }
                return null;
            }
        });
        // Force dark text on the editor regardless of which scene the picker is in.
        // Dialogs have a separate scene that doesn't inherit the main scene's CSS,
        // so this inline style is the only reliable way to ensure visibility.
        dp.getEditor().setStyle("-fx-text-fill: #1A1A2E;");
    }
}
