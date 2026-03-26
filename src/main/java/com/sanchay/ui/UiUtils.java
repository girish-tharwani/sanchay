package com.sanchay.ui;

import com.sanchay.model.Category;
import com.sanchay.model.Transaction;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.skin.DatePickerSkin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        Label detailLbl = new Label(detail);
        detailLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #4a7a88;");
        detailLbl.setWrapText(true);
        return buildStep(number, stepTitle, detailLbl);
    }

    /** Overload accepting a pre-built description Node (e.g. a TextFlow with nav-hint chips). */
    public static HBox buildStep(String number, String stepTitle, Node detailNode) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.TOP_LEFT);

        Label num = new Label(number);
        num.setMinSize(30, 30);
        num.setPrefSize(30, 30);
        num.setMaxSize(30, 30);
        num.setAlignment(Pos.CENTER);
        num.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #2a8a7a, #3db89a); "
                + "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; "
                + "-fx-background-radius: 15; "
                + "-fx-effect: dropshadow(gaussian, rgba(42,138,122,0.35), 8, 0, 0, 2);");

        VBox text = new VBox(5);
        Label titleLbl = new Label(stepTitle);
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #0f3d4a;");
        text.getChildren().addAll(titleLbl, detailNode);
        HBox.setHgrow(text, Priority.ALWAYS);

        row.getChildren().addAll(num, text);
        return row;
    }

    /**
     * Returns a styled navigation hint chip for inline use in step descriptions.
     * E.g. navHint("Profile"), navHint("+ Add Member").
     */
    public static Label navHint(String label) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-background-color: #f0f6f7; "
                + "-fx-border-color: rgba(42,138,122,0.30); "
                + "-fx-border-radius: 5; -fx-background-radius: 5; "
                + "-fx-padding: 1 7 1 7; "
                + "-fx-font-size: 11.5px; -fx-font-weight: 600; "
                + "-fx-text-fill: #2a8a7a;");
        return lbl;
    }

    /**
     * Builds a TextFlow description mixing plain text strings and nav-hint chips.
     * Pass alternating Strings (plain text) and Labels (from navHint()) as parts.
     */
    public static TextFlow stepDescFlow(Object... parts) {
        TextFlow tf = new TextFlow();
        tf.setLineSpacing(3);
        for (Object part : parts) {
            if (part instanceof String s) {
                Text t = new Text(s);
                t.setStyle("-fx-fill: #4a7a88; -fx-font-size: 13px;");
                tf.getChildren().add(t);
            } else if (part instanceof Node n) {
                tf.getChildren().add(n);
            }
        }
        return tf;
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

    /**
     * Sets a consistent branded header on any Dialog: teal icon box + bold title
     * on a #f8fbfc background with a bottom border — matching the design wireframe.
     * Call this immediately after applyStylesheet().
     */
    public static void setDialogHeader(Dialog<?> dlg, String icon, String title) {
        setDialogHeader(dlg, icon, title, null);
    }

    public static void setDialogHeader(Dialog<?> dlg, String icon, String title, String subtitle) {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 20, 14, 20));
        header.setStyle("-fx-background-color: #f8fbfc; "
                + "-fx-border-color: rgba(42,138,122,0.15); -fx-border-width: 0 0 1 0;");

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-background-color: rgba(42,138,122,0.12); "
                + "-fx-border-color: rgba(42,138,122,0.30); "
                + "-fx-border-radius: 8; -fx-background-radius: 8; "
                + "-fx-min-width: 28; -fx-min-height: 28; -fx-max-width: 28; -fx-max-height: 28; "
                + "-fx-alignment: center; -fx-font-size: 13px; -fx-font-weight: bold; "
                + "-fx-text-fill: #2a8a7a;");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #0f3d4a;");

        if (subtitle != null && !subtitle.isBlank()) {
            Label subLbl = new Label(subtitle);
            subLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7aa4b0;");
            subLbl.setWrapText(true);
            VBox titleBlock = new VBox(2, titleLbl, subLbl);
            HBox.setHgrow(titleBlock, Priority.ALWAYS);
            header.getChildren().addAll(iconLbl, titleBlock);
        } else {
            header.getChildren().addAll(iconLbl, titleLbl);
        }

        dlg.getDialogPane().setHeader(header);
    }

    /**
     * Wires filter-as-you-type autocomplete on an editable Category ComboBox.
     * When exactly one match exists and the typed text is a prefix of that match,
     * the editor is completed inline with the suffix selected (so further typing
     * replaces it), giving unambiguous single-match autocomplete.
     */
    public static void wireAutoComplete(ComboBox<Category> combo, List<Category> masterList) {
        combo.setEditable(true);
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(Category c)   { return c == null ? "" : c.getName(); }
            @Override public Category fromString(String s) {
                if (s == null || s.isBlank()) return null;
                return masterList.stream()
                        .filter(c -> c.getName().equalsIgnoreCase(s.trim()))
                        .findFirst().orElse(null);
            }
        });
        boolean[] suppress = {false};
        combo.getEditor().textProperty().addListener((obs, old, text) -> {
            if (suppress[0]) return;
            Category selected = combo.getValue();
            if (selected != null && selected.getName().equals(text)) {
                if (combo.getItems().size() < masterList.size())
                    combo.getItems().setAll(masterList);
                return;
            }
            String lower = text == null ? "" : text.toLowerCase();
            List<Category> filtered = lower.isEmpty()
                    ? new ArrayList<>(masterList)
                    : masterList.stream()
                            .filter(c -> c.getName().toLowerCase().contains(lower))
                            .collect(Collectors.toList());
            combo.getItems().setAll(filtered);
            if (!lower.isEmpty() && filtered.size() == 1
                    && filtered.get(0).getName().toLowerCase().startsWith(lower)) {
                // Unambiguous prefix match — complete inline, select the suffix
                String full = filtered.get(0).getName();
                suppress[0] = true;
                combo.getEditor().setText(full);
                combo.getEditor().selectRange(text.length(), full.length());
                suppress[0] = false;
            } else if (!filtered.isEmpty() && !lower.isEmpty()) {
                combo.show();
            }
        });
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
