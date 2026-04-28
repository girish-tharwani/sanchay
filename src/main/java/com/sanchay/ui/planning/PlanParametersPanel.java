package com.sanchay.ui.planning;

import com.sanchay.model.PlanParameters;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Collapsible Plan Parameters card: form fields, live-derived labels, auto-save, and Recalculate button. */
class PlanParametersPanel {

    private final PlanParameters params;
    private final LocalDate      selfDob;
    private final double         currentAgeDecimal;
    private final Runnable       onSave;
    private final Runnable       onRecalculate;

    // Derived (read-only) labels
    private Label currentAgeLbl;
    private Label yearsToRetireLbl;
    private Label yearsInRetireLbl;
    private Label retirementAgeLbl;

    // Editable fields
    private DatePicker retirementDatePicker;
    private TextField  lifeExpectancyFld;
    private TextField  preRetireTaxFld;
    private TextField  postRetireTaxFld;
    private TextField  rorEquityFld;
    private TextField  rorMfFld;
    private TextField  rorPfFld;
    private TextField  rorPostRetireFld;
    private TextField  inflationFld;
    private TextField  costOfLivingFld;
    private TextField  sipMfFld;
    private TextField  sipEquityFld;

    PlanParametersPanel(PlanParameters params, LocalDate selfDob, double currentAgeDecimal,
                        Runnable onSave, Runnable onRecalculate) {
        this.params            = params;
        this.selfDob           = selfDob;
        this.currentAgeDecimal = currentAgeDecimal;
        this.onSave            = onSave;
        this.onRecalculate     = onRecalculate;
    }

    Node build() {
        initFields();
        updateDerivedLabels();
        return buildCard();
    }

    // ── Card ──────────────────────────────────────────────────────────────────

    private Node buildCard() {
        Label chevron = new Label("▾");
        chevron.getStyleClass().add("fp-chevron");

        Label title = new Label("Plan Parameters");
        title.getStyleClass().add("text-section-title");

        Button recalcBtn = new Button("Recalculate");
        recalcBtn.getStyleClass().add("btn-gold");
        recalcBtn.setOnAction(e -> {
            params.lastCalculatedDate = LocalDate.now().toString();
            collectAndSave();
            onRecalculate.run();
        });

        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(chevron, title, spacer, recalcBtn);

        Region divider = new Region();
        divider.getStyleClass().add("content-divider");
        divider.setMaxWidth(Double.MAX_VALUE);

        GridPane paramGrid = buildParamGrid();
        paramGrid.setVisible(false);
        paramGrid.setManaged(false);
        divider.setVisible(false);
        divider.setManaged(false);
        recalcBtn.setVisible(false);
        recalcBtn.setManaged(false);
        chevron.setText("▸");

        VBox card = new VBox(12, header, divider, paramGrid);
        card.getStyleClass().add("card");

        Runnable toggleExpanded = () -> {
            boolean expanding = !paramGrid.isManaged();
            paramGrid.setVisible(expanding);
            paramGrid.setManaged(expanding);
            divider.setVisible(expanding);
            divider.setManaged(expanding);
            recalcBtn.setVisible(expanding);
            recalcBtn.setManaged(expanding);
            chevron.setText(expanding ? "▾" : "▸");
        };
        chevron.setCursor(Cursor.HAND);
        title.setCursor(Cursor.HAND);
        chevron.setOnMouseClicked(e -> toggleExpanded.run());
        title.setOnMouseClicked(e -> toggleExpanded.run());

        return card;
    }

    // ── Field initialisation ──────────────────────────────────────────────────

    private void initFields() {
        currentAgeLbl    = derivedLabel(String.format("%.2f", currentAgeDecimal));
        yearsToRetireLbl = derivedLabel("");
        yearsInRetireLbl = derivedLabel("");
        retirementAgeLbl = derivedLabel("");
        retirementAgeLbl.getStyleClass().add("card-value");

        lifeExpectancyFld = intField(params.lifeExpectancy);
        preRetireTaxFld   = pctField(params.preRetireTaxPct);
        postRetireTaxFld  = pctField(params.postRetireTaxPct);
        inflationFld      = pctField(params.inflationPct);
        rorEquityFld      = pctField(params.rorEquitiesPct);
        rorMfFld          = pctField(params.rorMfPct);
        rorPfFld          = pctField(params.rorPfPct);
        rorPostRetireFld  = pctField(params.rorPostRetirePct);
        costOfLivingFld   = rupeesField(params.costOfLivingPaise);
        sipMfFld          = rupeesField(params.monthlySipMfPaise);
        sipEquityFld      = rupeesField(params.monthlySipEquityPaise);

        DateTimeFormatter dtFmt = DataStore.getInstance().getDateFormatter();
        StringConverter<LocalDate> dateConverter = new StringConverter<>() {
            @Override public String toString(LocalDate d)   { return d == null ? "" : dtFmt.format(d); }
            @Override public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) return null;
                try { return LocalDate.parse(s, dtFmt); } catch (Exception e) { return null; }
            }
        };

        retirementDatePicker = new DatePicker();
        retirementDatePicker.setPrefWidth(130);
        retirementDatePicker.setConverter(dateConverter);
        if (params.retirementDate != null) {
            try { retirementDatePicker.setValue(LocalDate.parse(params.retirementDate)); }
            catch (Exception ignored) { retirementDatePicker.setValue(selfDob.plusYears(params.retirementAge)); }
        } else {
            retirementDatePicker.setValue(selfDob.plusYears(params.retirementAge));
        }
        retirementDatePicker.valueProperty().addListener((obs, o, n) -> { updateDerivedLabels(); collectAndSave(); });

        lifeExpectancyFld.textProperty().addListener((obs, o, n) -> updateDerivedLabels());

        for (TextField fld : new TextField[] {
                lifeExpectancyFld,
                preRetireTaxFld, postRetireTaxFld, inflationFld,
                rorEquityFld, rorMfFld, rorPfFld, rorPostRetireFld,
                costOfLivingFld, sipMfFld, sipEquityFld }) {
            fld.focusedProperty().addListener((obs, was, focused) -> {
                if (!focused) collectAndSave();
            });
        }
    }

    // ── Derived-label computation ─────────────────────────────────────────────

    private void updateDerivedLabels() {
        LocalDate retDate = retirementDatePicker != null ? retirementDatePicker.getValue() : null;
        int lifeExp = parseIntSafe(lifeExpectancyFld != null ? lifeExpectancyFld.getText() : "",
                                   params.lifeExpectancy);

        if (retDate != null && selfDob != null) {
            double retireAge = ChronoUnit.DAYS.between(selfDob, retDate) / 365.25;
            double toRetire  = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), retDate) / 365.25);
            double inRetire  = Math.max(0, lifeExp - retireAge);

            yearsToRetireLbl.setText(String.format("%.2f", toRetire));
            yearsInRetireLbl.setText(String.format("%.2f", inRetire));
            retirementAgeLbl.setText(String.format("%.2f", retireAge));
        }
    }

    // ── Collect and save ──────────────────────────────────────────────────────

    private void collectAndSave() {
        params.lifeExpectancy        = parseIntSafe(lifeExpectancyFld.getText(), params.lifeExpectancy);
        params.preRetireTaxPct       = parsePctSafe(preRetireTaxFld.getText(), params.preRetireTaxPct);
        params.postRetireTaxPct      = parsePctSafe(postRetireTaxFld.getText(), params.postRetireTaxPct);
        params.inflationPct          = parsePctSafe(inflationFld.getText(), params.inflationPct);
        params.rorEquitiesPct        = parsePctSafe(rorEquityFld.getText(), params.rorEquitiesPct);
        params.rorMfPct              = parsePctSafe(rorMfFld.getText(), params.rorMfPct);
        params.rorPfPct              = parsePctSafe(rorPfFld.getText(), params.rorPfPct);
        params.rorPostRetirePct      = parsePctSafe(rorPostRetireFld.getText(), params.rorPostRetirePct);
        params.costOfLivingPaise     = parseRupeesSafe(costOfLivingFld.getText(), params.costOfLivingPaise);
        params.monthlySipMfPaise     = parseRupeesSafe(sipMfFld.getText(), params.monthlySipMfPaise);
        params.monthlySipEquityPaise = parseRupeesSafe(sipEquityFld.getText(), params.monthlySipEquityPaise);

        if (retirementDatePicker.getValue() != null) {
            params.retirementDate = retirementDatePicker.getValue().toString();
            // Keep retirementAge in sync (rounded) for backward compatibility with other consumers
            params.retirementAge  = (int) Math.round(
                    ChronoUnit.DAYS.between(selfDob, retirementDatePicker.getValue()) / 365.25);
        }

        setIfDifferent(lifeExpectancyFld, String.valueOf(params.lifeExpectancy));
        setIfDifferent(preRetireTaxFld,   formatPct(params.preRetireTaxPct));
        setIfDifferent(postRetireTaxFld,  formatPct(params.postRetireTaxPct));
        setIfDifferent(inflationFld,      formatPct(params.inflationPct));
        setIfDifferent(rorEquityFld,      formatPct(params.rorEquitiesPct));
        setIfDifferent(rorMfFld,          formatPct(params.rorMfPct));
        setIfDifferent(rorPfFld,          formatPct(params.rorPfPct));
        setIfDifferent(rorPostRetireFld,  formatPct(params.rorPostRetirePct));
        setIfDifferent(costOfLivingFld,   formatRupees(params.costOfLivingPaise));
        setIfDifferent(sipMfFld,          formatRupees(params.monthlySipMfPaise));
        setIfDifferent(sipEquityFld,      formatRupees(params.monthlySipEquityPaise));

        updateDerivedLabels();
        onSave.run();
    }

    // ── Param grid ────────────────────────────────────────────────────────────

    private GridPane buildParamGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setPrefWidth(280);
        labelCol.setMinWidth(120);

        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);

        ColumnConstraints gapCol = new ColumnConstraints();
        gapCol.setMinWidth(24);
        gapCol.setPrefWidth(24);

        // 5-column layout: [label, field, gap, label, field]
        grid.getColumnConstraints().addAll(labelCol, fieldCol, gapCol, labelCol, fieldCol);

        // ── Left column ───────────────────────────────────────────────────────
        addParamRow(grid, 0, "Current Age",            currentAgeLbl,         0);
        addParamRow(grid, 1, "Retirement Date",        retirementDatePicker,  0);
        addParamRow(grid, 2, "Retirement Age",         retirementAgeLbl,      0);
        addParamRow(grid, 3, "Years to Retirement",    yearsToRetireLbl,      0);
        addParamRow(grid, 4, "Life Expectancy",        lifeExpectancyFld,     0);
        addParamRow(grid, 5, "Years in Retirement",    yearsInRetireLbl,      0);
        addParamRow(grid, 6, "Cost of Living / Year",   costOfLivingFld,       0);
        addParamRow(grid, 7, "Inflation Rate",         inflationFld,          0);
        

        // ── Right column ──────────────────────────────────────────────────────
        addParamRow(grid, 0, "Tax Rate – Blended (Pre-retirement)",    preRetireTaxFld,       3);
        addParamRow(grid, 1, "Tax Rate – Blended (Post-retirement)",   postRetireTaxFld,      3);
        addParamRow(grid, 2, "RoR – Equities (Pre-retirement)",     rorEquityFld,          3);
        addParamRow(grid, 3, "RoR – Mutual Funds (Pre-retirement)",           rorMfFld,              3);
        addParamRow(grid, 4, "RoR – Provident Fund (Pre-retirement)",           rorPfFld,              3);
        addParamRow(grid, 5, "RoR – Blended (Post-retirement)",     rorPostRetireFld,      3);
        addParamRow(grid, 6, "Monthly Mutual Fund SIP",                      sipMfFld,              3);
        addParamRow(grid, 7, "Monthly Equity SIP",                  sipEquityFld,          3);

        return grid;
    }

    private void addParamRow(GridPane grid, int row, String labelText, Node valueNode, int startCol) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("fp-param-key");
        lbl.setMaxWidth(Double.MAX_VALUE);

        HBox labelCell = new HBox(lbl);
        labelCell.getStyleClass().add("fp-param-row");
        labelCell.setAlignment(Pos.CENTER_LEFT);

        HBox valueCell = new HBox(valueNode);
        valueCell.getStyleClass().add("fp-param-row");
        valueCell.setAlignment(Pos.CENTER_RIGHT);

        grid.add(labelCell, startCol,     row);
        grid.add(valueCell, startCol + 1, row);
    }

    // ── Field factories ───────────────────────────────────────────────────────

    private Label derivedLabel(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("fp-param-value");
        return lbl;
    }

    private TextField intField(int value) {
        TextField fld = new TextField(String.valueOf(value));
        fld.setPrefWidth(130);
        fld.setMaxWidth(160);
        return fld;
    }

    private TextField pctField(double value) {
        TextField fld = new TextField(formatPct(value));
        fld.setPrefWidth(130);
        fld.setMaxWidth(160);
        return fld;
    }

    private TextField rupeesField(long paise) {
        TextField fld = new TextField(formatRupees(paise));
        fld.setPrefWidth(130);
        fld.setMaxWidth(160);
        return fld;
    }

    // ── Formatters ────────────────────────────────────────────────────────────

    private String formatPct(double pct) {
        return (pct == Math.floor(pct))
                ? String.format("%.0f%%", pct)
                : String.format("%.1f%%", pct);
    }

    private static String formatRupees(long paise) {
        return MoneyFormatter.formatNoDecimal(paise);
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private int parseIntSafe(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); }
        catch (Exception e) { return fallback; }
    }

    private double parsePctSafe(String text, double fallback) {
        if (text == null || text.isBlank()) return fallback;
        try { return Double.parseDouble(text.replace("%", "").trim()); }
        catch (Exception e) { return fallback; }
    }

    private long parseRupeesSafe(String text, long fallback) {
        return MoneyFormatter.parseAmountSafe(text, fallback);
    }

    private void setIfDifferent(TextField fld, String newText) {
        if (!newText.equals(fld.getText())) fld.setText(newText);
    }
}
