package com.sanchay.ui;

import com.sanchay.model.Transaction;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.skin.DatePickerSkin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class UiUtils {
    private UiUtils() {}

    private static final String CSS_PATH = "/com/sanchay/css/app.css";

    /**
     * Applies the app stylesheet to a dialog's DialogPane.
     * Must be called on every Dialog because JavaFX dialogs open in a separate
     * scene that does not inherit the main window's stylesheets.
     */
    public static void applyStylesheet(Dialog<?> dialog) {
        applyStylesheet(dialog.getDialogPane());
    }

    public static void applyStylesheet(DialogPane pane) {
        String css = UiUtils.class.getResource(CSS_PATH).toExternalForm();
        pane.getStylesheets().add(css);
    }

    /**
     * Builds a numbered step row used in setup guides.
     * Shared by DashboardScreen (welcome banner) and HelpDialog (get started section).
     */
    public static HBox buildStep(String number, String stepTitle, String detail) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.TOP_LEFT);

        Label num = new Label(number);
        num.setMinSize(28, 28);
        num.setPrefSize(28, 28);
        num.setAlignment(Pos.CENTER);
        num.setStyle("-fx-background-color: #0f3d4a; -fx-text-fill: white; "
                + "-fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 14;");

        VBox text = new VBox(3);
        Label titleLbl = new Label(stepTitle);
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0f3d4a;");
        Label detailLbl = new Label(detail);
        detailLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #595959;");
        detailLbl.setWrapText(true);
        text.getChildren().addAll(titleLbl, detailLbl);
        HBox.setHgrow(text, Priority.ALWAYS);

        row.getChildren().addAll(num, text);
        return row;
    }

    /** Returns the CSS style class for a transaction type badge. */
    public static String badgeStyle(Transaction.Type type) {
        return switch (type) {
            case EXPENSE      -> "badge-expense";
            case INCOME       -> "badge-income";
            case TRANSFER     -> "badge-transfer";
            case INVESTMENT   -> "badge-investment";
            case CC_PAYMENT   -> "badge-cc-payment";
            case REFUND       -> "badge-refund";
            case REDEEM       -> "badge-redeem";
            case LOAN_PAYMENT -> "badge-loan-payment";
            case GAIN         -> "badge-gain";
            case LOSE         -> "badge-lose";
        };
    }

    /** Returns the display text for a transaction type badge. */
    public static String badgeText(Transaction.Type type) {
        return switch (type) {
            case EXPENSE      -> "Expense";
            case INCOME       -> "Income";
            case TRANSFER     -> "Transfer";
            case INVESTMENT   -> "Investment";
            case CC_PAYMENT   -> "CC Payment";
            case REFUND       -> "Refund";
            case REDEEM       -> "Redeem";
            case LOAN_PAYMENT -> "Loan Payment";
            case GAIN         -> "Gain";
            case LOSE         -> "Loss";
        };
    }

    /** Small italic hint label for placement below editable tables. */
    public static Label hintLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888; -fx-font-style: italic;");
        return lbl;
    }

    /**
     * Attaches a listener that styles the DatePicker's calendar popup header each time
     * it opens. Uses the DatePickerSkin to reach the popup's separate scene, since CSS
     * from the main/dialog scene does not reliably propagate to popup scenes.
     * Safe to call on any DatePicker in any context (dialog or main scene).
     */
    public static void styleOnShow(DatePicker dp) {
        dp.showingProperty().addListener((obs, wasShowing, nowShowing) -> {
            if (nowShowing) Platform.runLater(() -> applyPopupHeaderStyle(dp));
        });
    }

    private static void applyPopupHeaderStyle(DatePicker dp) {
        if (!(dp.getSkin() instanceof DatePickerSkin skin)) return;
        Node content = skin.getPopupContent();
        Node monthPane = content.lookup(".month-year-pane");
        if (monthPane == null) return;
        monthPane.setStyle("-fx-background-color: #d8f0ec;");
        monthPane.lookupAll(".label").forEach(n ->
                n.setStyle("-fx-text-fill: #0f3d4a; -fx-font-weight: bold;"));
        monthPane.lookupAll(".left-arrow, .right-arrow").forEach(n ->
                n.setStyle("-fx-background-color: #0f3d4a;"));
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
        dp.getEditor().setStyle("-fx-text-fill: #0f3d4a;");
    }
}
