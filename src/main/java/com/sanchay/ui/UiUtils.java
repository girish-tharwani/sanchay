package com.sanchay.ui;

import java.util.Map;
import com.sanchay.model.Category;
import com.sanchay.model.Transaction;
import com.sanchay.service.MoneyFormatter;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.DatePickerSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;

import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class UiUtils {
    private UiUtils() {}

    private static final String[] CSS_FILES = {
        "/com/sanchay/css/theme.css",
        "/com/sanchay/css/components.css",
        "/com/sanchay/css/layout.css",
        "/com/sanchay/css/reports.css",
        "/com/sanchay/css/planning.css",
        "/com/sanchay/css/screens/help.css"
    };

    /** Brand palette hex literals — centralised so a palette change is a single edit. */
    public static final String HEX_BRAND_LIGHT  = "#3db89a";
    public static final String HEX_BRAND_MID    = "#2a8a7a";
    public static final String HEX_BRAND_ACCENT = "#f0a500";

    /** Shared chart palette — used by ReportsScreen bar/pie charts (category breakdown). */
    public static final String[] CHART_PALETTE = {
            "#E74C3C","#E67E22","#F39C12","#8E44AD","#2E75B6",
            "#1ABC9C","#27AE60","#2980B9","#C0392B","#16A085"};

    /**
     * Series colours for account cash-flow forecast chart (CashFlowForecastTab).
     * Rules:
     *   [0] gold is EXCLUSIVE to the Total series — never reuse on an account line.
     *   Only one green-family colour (teal at [1]) to avoid the "too many greens" problem.
     *   12 entries cover the maximum expected account count without wrapping/recycling.
     * Runtime-assigned to legend swatch rectangles — cannot use CSS tokens here.
     */
    public static final String[] FORECAST_SERIES_COLORS = {
        "#f0a500",  //  0 — Total    (gold — exclusive, never used for accounts)
        "#2a8a7a",  //  1 — teal     (single green-family entry)
        "#e05555",  //  2 — red
        "#7c3aed",  //  3 — purple
        "#1d70b5",  //  4 — blue
        "#e8892b",  //  5 — orange
        "#c026d3",  //  6 — fuchsia
        "#0891b2",  //  7 — cyan
        "#475569",  //  8 — slate
        "#b45309",  //  9 — brown
        "#ec4899",  // 10 — pink
        "#6366f1",  // 11 — indigo
    };


    /**
     * Applies the app stylesheet to a dialog's DialogPane.
     * Must be called on every Dialog because JavaFX dialogs open in a separate
     * scene that does not inherit the main window's stylesheets.
     */
    public static void applyStylesheet(Dialog<?> dialog) {
        applyStylesheet(dialog.getDialogPane());
    }

    public static void applyStylesheet(DialogPane pane) {
        for (String f : CSS_FILES)
            pane.getStylesheets().add(UiUtils.class.getResource(f).toExternalForm());
    }

    /**
     * Builds a numbered step row used in setup guides.
     * Shared by DashboardScreen (welcome banner) and HelpDialog (get started section).
     */
    public static HBox buildStep(String number, String stepTitle, String detail) {
        Label detailLbl = new Label(detail);
        detailLbl.getStyleClass().add("text-body-muted");
        detailLbl.setWrapText(true);
        return buildStep(number, stepTitle, detailLbl);
    }

    /** Overload accepting a pre-built description Node (e.g. a TextFlow with nav-hint chips). */
    public static HBox buildStep(String number, String stepTitle, Node detailNode) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.TOP_LEFT);

        Label num = new Label(number);
        num.getStyleClass().add("step-badge");

        VBox text = new VBox(5);
        Label titleLbl = new Label(stepTitle);
        titleLbl.getStyleClass().add("text-step-title");
        text.getChildren().addAll(titleLbl, detailNode);
        HBox.setHgrow(text, Priority.ALWAYS);

        row.getChildren().addAll(num, text);
        return row;
    }

    /**
     * Coloured dot + uppercase section label row.
     * Previously duplicated as private sectionLabel() in RecurringScreen,
     * AmbiguousMatchDialog, and RecurringMatchDialog.
     *
     * dotColor stays as a String because it is data-driven (callers pass different
     * brand colours). Shape.fill cannot be set via CSS class — legitimate exception.
     */
    public static HBox buildSectionLabel(String text, String dotColor) {
        Circle dot = new Circle(4, Color.web(dotColor));
        Label lbl = new Label(text.toUpperCase());
        lbl.getStyleClass().add("section-group-label");
        HBox box = new HBox(8, dot, lbl);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Arrow text node used between nav-hint chips in step descriptions.
     * Shared by buildGetStartedSteps() — previously duplicated in HelpDialog and DashboardScreen.
     */
    public static Text navArrow() {
        Text t = new Text(" → ");
        // Text nodes don't support style classes for -fx-fill; inline style required.
        t.setStyle("-fx-fill: -text-muted; -fx-font-size: 11px;");
        return t;
    }

    /**
     * Builds the three-step "Get Started" setup guide rows, using the full step descriptions.
     * Shared by HelpDialog and DashboardScreen welcome banner.
     * Returns a VBox containing three HBox step rows with 16px vertical padding each.
     * Callers that need separators between steps (e.g. HelpDialog) should interleave them after
     * extracting the children, or wrap the result as needed.
     */
    public static VBox buildGetStartedSteps() {
        VBox steps = new VBox(0);
        steps.getChildren().addAll(
                stepRowPadded("1", "Add family members",
                        stepDescFlow(
                                "Go to ", navHint("Profile"), navArrow(),
                                navHint("+ Add Member"),
                                ". Add everyone in your household. Don't mark anyone as an "
                                + "earning member yet — you'll do that in step 3.")),
                stepRowPadded("2", "Add your bank accounts",
                        stepDescFlow(
                                "Go to ", navHint("Accounts"), navArrow(),
                                navHint("Bank Accounts"), navArrow(),
                                navHint("+ Add"),
                                ". Add the accounts where salaries and income are deposited. "
                                + "You can add credit cards and loans later.")),
                stepRowPadded("3", "Complete earnings details",
                        stepDescFlow(
                                "Return to ", navHint("Profile"),
                                " and click the ", navHint(MoneyFormatter.symbol()),
                                " button next to each earning member. Mark them as earning "
                                + "and fill in their salary and income details."))
        );
        return steps;
    }

    private static HBox stepRowPadded(String number, String title, TextFlow desc) {
        HBox row = buildStep(number, title, desc);
        row.setPadding(new javafx.geometry.Insets(16, 0, 16, 12));
        return row;
    }

    /**
     * Returns a styled navigation hint chip for inline use in step descriptions.
     * E.g. navHint("Profile"), navHint("+ Add Member").
     */
    public static Label navHint(String label) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("nav-hint");
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
                // Text nodes don't support style classes for -fx-fill; inline style required.
                // Using CSS looked-up token so the colour stays in sync with the design system.
                t.setStyle("-fx-fill: -text-secondary; -fx-font-size: 13px;");
                tf.getChildren().add(t);
            } else if (part instanceof Node n) {
                tf.getChildren().add(n);
            }
        }
        return tf;
    }

    /** Builds a styled transaction type badge label (type colour class + badge-sm size). */
    public static Label typeBadge(Transaction.Type type) {
        Label lbl = new Label(badgeText(type));
        lbl.getStyleClass().addAll("badge", badgeStyle(type), "badge-sm");
        return lbl;
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
        // Remove the native OS title bar — the custom branded header is the only chrome needed
        dlg.initStyle(StageStyle.UNDECORATED);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dialog-header-bar");

        Label iconLbl = new Label(icon);
        iconLbl.getStyleClass().add("dialog-icon-box");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("text-step-title");

        if (subtitle != null && !subtitle.isBlank()) {
            Label subLbl = new Label(subtitle);
            subLbl.getStyleClass().add("dialog-subtitle");
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
        // When Tab is pressed, commit the matching category as the combo's value so
        // that valueProperty listeners (e.g. sub-category visibility) fire correctly.
        combo.getEditor().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.TAB) {
                String text = combo.getEditor().getText();
                if (text != null && !text.isBlank()) {
                    masterList.stream()
                            .filter(c -> c.getName().equalsIgnoreCase(text.trim()))
                            .findFirst()
                            .ifPresent(combo::setValue);
                }
            }
        });
    }

    /**
     * Wires soft dropdown autocomplete on a plain TextField.
     * As the user types, a dropdown shows all starts-with matches in order.
     * Tab accepts the first match; clicking any item accepts it.
     * The field text is never modified while the user is typing.
     */
    public static void wireDescriptionAutocomplete(TextField field, List<String> suggestions) {
        ContextMenu popup = new ContextMenu();
        popup.setAutoHide(true);
        boolean[] suppress = {false};

        field.textProperty().addListener((obs, old, text) -> {
            if (suppress[0]) return;
            if (text == null || text.isEmpty()) { popup.hide(); return; }
            String lower = text.toLowerCase();
            List<String> matches = suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(lower))
                    .collect(Collectors.toList());
            if (matches.isEmpty()) { popup.hide(); return; }
            popup.getItems().clear();
            for (String match : matches) {
                MenuItem item = new MenuItem(match);
                item.setOnAction(e -> {
                    suppress[0] = true;
                    field.setText(match);
                    field.positionCaret(match.length());
                    suppress[0] = false;
                    popup.hide();
                });
                popup.getItems().add(item);
            }
            if (!popup.isShowing() && field.getScene() != null) popup.show(field, Side.BOTTOM, 0, 0);
        });

        // Tab accepts the top match without moving focus
        field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB && popup.isShowing() && !popup.getItems().isEmpty()) {
                String match = popup.getItems().get(0).getText();
                suppress[0] = true;
                field.setText(match);
                field.positionCaret(match.length());
                suppress[0] = false;
                popup.hide();
                e.consume();
            }
        });
    }

    /**
     * Makes an integer Spinner directly editable.
     * On focus loss the typed value is parsed, clamped to [min, max], and committed.
     * Invalid text is reverted to the current value.
     */
    public static void makeSpinnerEditable(Spinner<Integer> spinner, int min, int max) {
        spinner.setEditable(true);
        spinner.getEditor().focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) {
                try {
                    int parsed = Integer.parseInt(spinner.getEditor().getText().trim());
                    spinner.getValueFactory().setValue(Math.max(min, Math.min(max, parsed)));
                } catch (NumberFormatException e) {
                    spinner.getEditor().setText(String.valueOf(spinner.getValue()));
                }
            }
        });
    }

    /** Small italic hint label for placement below editable tables. */
    public static Label hintLabel(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("text-hint");
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
        // Inline required: DatePicker calendar popup is a separate JavaFX skin node tree
        // not accessible via the main stylesheet; setStyle() via lookup is the only way.
        monthPane.setStyle("-fx-background-color: -surface-datepicker-header;");
        monthPane.lookupAll(".label").forEach(n ->
                n.setStyle("-fx-text-fill: -brand-dark; -fx-font-weight: bold;"));
        monthPane.lookupAll(".left-arrow, .right-arrow").forEach(n ->
                n.setStyle("-fx-background-color: -brand-dark;"));
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
        dp.getEditor().setStyle("-fx-text-fill: -brand-dark;");
    }

    /**
     * Compact rupee display used on KPI tiles across screens.
     * ≥ ₹1 Cr  → "₹1.25 Cr"
     * ≥ ₹1 L   → "₹45 Lakh"
     * otherwise → "₹12,500"
     */
    public static String formatCorpusDisplay(long paise) {
        return MoneyFormatter.formatCompact(paise);
    }

    // ── Dialog / form boilerplate helpers ────────────────────────────────────

    /**
     * Configures a freshly created Dialog with the standard app chrome:
     * title, no native header, preferred width, stylesheet, and branded header bar.
     * Call immediately after {@code new Dialog<>()}, before adding content.
     *
     * <pre>
     *   Dialog&lt;MyType&gt; dlg = new Dialog&lt;&gt;();
     *   UiUtils.initDialog(dlg, "Edit Account", "✎", 500);
     * </pre>
     */
    public static void initDialog(Dialog<?> dlg, String title, String icon, double prefWidth) {
        dlg.setTitle(title);
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(prefWidth);
        applyStylesheet(dlg);
        setDialogHeader(dlg, icon, title);
    }

    /** Overload for dialogs that need a subtitle beneath the title in the header. */
    public static void initDialog(Dialog<?> dlg, String title, String icon, double prefWidth, String subtitle) {
        dlg.setTitle(title);
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(prefWidth);
        applyStylesheet(dlg);
        setDialogHeader(dlg, icon, title, subtitle);
    }

    /**
     * Adds a "Save" OK button and a Cancel button to {@code pane}, and returns
     * the Save {@link ButtonType} so the caller can wire {@code setResultConverter}.
     *
     * <pre>
     *   ButtonType saveBtn = UiUtils.addSaveCancel(dlg.getDialogPane());
     *   dlg.setResultConverter(bt -> bt == saveBtn ? result : null);
     * </pre>
     */
    public static ButtonType addSaveCancel(DialogPane pane) {
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        return saveBtn;
    }

    /**
     * Creates a styled DatePicker with the smart date converter and calendar popup
     * header styling applied. Sets maxWidth to Double.MAX_VALUE so it fills its
     * grid column.
     *
     * Use for any date picker where the user types or picks a date. Do NOT use for
     * display-only or programmatically-set date pickers.
     */
    public static DatePicker createDatePicker(LocalDate initial) {
        DatePicker dp = new DatePicker(initial);
        dp.setMaxWidth(Double.MAX_VALUE);
        applySmartDateConverter(dp);
        styleOnShow(dp);
        return dp;
    }

    /**
     * Creates the standard 2-column form GridPane used in all dialog forms.
     * hgap=12, vgap=10; column 0 fixed at {@code labelColWidth} px; column 1 expands.
     * Callers set their own padding (varies per dialog).
     */
    public static GridPane buildFormGrid(int labelColWidth) {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        ColumnConstraints c1 = new ColumnConstraints(labelColWidth);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);
        return g;
    }

    /**
     * Adds a label+control row to a form GridPane.
     * The label gets the "form-label" style class; the control fills column 1.
     */
    public static void addFormRow(GridPane g, int row, String label, Node control) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        g.add(lbl, 0, row);
        g.add(control, 1, row);
        GridPane.setFillWidth(control, true);
    }
}
