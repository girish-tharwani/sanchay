package com.sanchay.ui.planning;

import com.sanchay.model.FamilyMember;
import com.sanchay.model.InvestmentAccount;
import com.sanchay.model.MarketValueEntry;
import com.sanchay.model.PlanParameters;
import com.sanchay.service.AppConfig;
import com.sanchay.service.DataStore;
import com.sanchay.service.PlanParamsService;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

/**
 * Financial Planning screen — long-range retirement and wealth projection.
 *
 * Plan parameters are loaded from / saved to {@code plan_params.json}.
 * Current Age is derived from the "Self" family member's date of birth.
 * Computed fields (Years to Retirement etc.) update live as the user
 * edits Retirement Age or Life Expectancy. All editable fields auto-save
 * when focus leaves them.
 *
 * KPI cards and corpus/earnings breakdowns are illustrative placeholders;
 * the computation layer will be wired in a future iteration.
 */
public class FinancialPlanningScreen {

    private ScrollPane view;
    private final Runnable navigateToProfile;

    private PlanParamsService paramsService;
    private PlanParameters    params;
    private int               currentAge;

    // ── Derived (read-only) labels — updated live ─────────────────────────────
    private Label currentAgeLbl;
    private Label yearsToRetireLbl;
    private Label yearsInRetireLbl;
    private Label retirementYearLbl;   // also reused in KPI strip

    // ── Corpus breakdown (computed once per buildView) ────────────────────────
    private record CorpusBreakdown(
            long bankPaise, long equityPaise, long mfPaise,
            long bondsPaise, long fdPaise, long rdPaise,
            long pfPaise, long totalPaise) {}

    private CorpusBreakdown corpusBreakdown;

    // ── Editable fields ───────────────────────────────────────────────────────
    private TextField  retirementAgeFld;
    private TextField  lifeExpectancyFld;
    private TextField  preRetireTaxFld;
    private TextField  postRetireTaxFld;
    private TextField  rorEquityFld;
    private TextField  rorMfFld;
    private TextField  rorPfFld;
    private TextField  rorPostRetireFld;
    private TextField  inflationFld;
    private TextField  costOfLivingFld;
    private DatePicker employmentStartPicker;
    private TextField  sipMfFld;
    private TextField  sipEquityFld;

    public FinancialPlanningScreen(Runnable navigateToProfile) {
        this.navigateToProfile = navigateToProfile;
        buildView();
    }

    public Node getView() { return view; }

    public void refresh() { buildView(); }

    // ── Root layout ───────────────────────────────────────────────────────────

    private void buildView() {
        FamilyMember self = DataStore.getInstance().getFamilyMembers().stream()
                .filter(m -> m.getRelationship() == FamilyMember.Relationship.SELF)
                .findFirst().orElse(null);

        if (self == null || self.getDateOfBirth() == null) {
            // Show error on next pulse (after the view is placed in the scene graph)
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Profile Incomplete");
                alert.setHeaderText("Date of birth not set");
                alert.setContentText(
                        "Financial planning requires your date of birth.\n"
                        + "Please set it in your Profile before continuing.");
                alert.showAndWait();
                navigateToProfile.run();
            });
            view = new ScrollPane(new Label(""));
            return;
        }

        currentAge = Period.between(self.getDateOfBirth(), LocalDate.now()).getYears();

        AppConfig.Config cfg = AppConfig.read();
        paramsService = new PlanParamsService(cfg.dataFolderPath);
        params        = paramsService.load();

        initFields();
        updateDerivedLabels();
        corpusBreakdown = computeCorpusBreakdown();

        VBox content = new VBox(20);
        content.getStyleClass().add("main-panel");
        content.setPadding(new Insets(28));
        content.getChildren().addAll(
                buildHeader(),
                buildKpiGrid(),
                buildParamsCard(),
                buildTwoCol(buildCorpusCard(), buildEarningsCard()),
                buildTwoCol(buildMajorEventsCard(), buildExpensesAndForecastCard()),
                buildPostRetirementCard()
        );

        view = new ScrollPane(content);
        view.setFitToWidth(true);
        view.setFitToHeight(false);
        view.getStyleClass().add("scroll-page-bg");
    }

    // ── Field initialisation ──────────────────────────────────────────────────

    /** Creates all field instances, populates from loaded params, and wires listeners. */
    private void initFields() {
        // Derived labels
        currentAgeLbl     = derivedLabel(String.valueOf(currentAge));
        yearsToRetireLbl  = derivedLabel("");
        yearsInRetireLbl  = derivedLabel("");
        retirementYearLbl = derivedLabel("");
        retirementYearLbl.getStyleClass().add("card-value");

        // Editable text fields
        retirementAgeFld  = intField(params.retirementAge);
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

        // Date picker for employment start — uses the app's date format from settings
        DateTimeFormatter dtFmt = DataStore.getInstance().getDateFormatter();
        employmentStartPicker = new DatePicker();
        employmentStartPicker.setPrefWidth(130);
        employmentStartPicker.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate d) {
                return d == null ? "" : dtFmt.format(d);
            }
            @Override public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) return null;
                try { return LocalDate.parse(s, dtFmt); } catch (Exception e) { return null; }
            }
        });
        if (params.employmentStartDate != null) {
            try { employmentStartPicker.setValue(LocalDate.parse(params.employmentStartDate)); }
            catch (Exception ignored) {}
        }
        employmentStartPicker.valueProperty().addListener((obs, o, n) -> collectAndSave());

        // Live-update derived labels whenever the age/life-expectancy fields change
        retirementAgeFld.textProperty().addListener((obs, o, n)  -> updateDerivedLabels());
        lifeExpectancyFld.textProperty().addListener((obs, o, n) -> updateDerivedLabels());

        // Auto-save on focus lost for all text fields
        for (TextField fld : new TextField[] {
                retirementAgeFld, lifeExpectancyFld,
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
        int retAge   = parseIntSafe(retirementAgeFld.getText(), params.retirementAge);
        int lifeExp  = parseIntSafe(lifeExpectancyFld.getText(), params.lifeExpectancy);
        int toRetire = Math.max(0, retAge - currentAge);
        int inRetire = Math.max(0, lifeExp - retAge);
        int retYear  = LocalDate.now().getYear() + toRetire;

        yearsToRetireLbl.setText(String.valueOf(toRetire));
        yearsInRetireLbl.setText(String.valueOf(inRetire));
        retirementYearLbl.setText(String.valueOf(retYear));
    }

    // ── Collect, format, and save ─────────────────────────────────────────────

    private void collectAndSave() {
        if (paramsService == null || params == null) return;

        params.retirementAge         = parseIntSafe(retirementAgeFld.getText(), params.retirementAge);
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

        if (employmentStartPicker.getValue() != null) {
            params.employmentStartDate = employmentStartPicker.getValue().toString();
        }

        // Reformat fields for consistent display (auto-append % / ₹ if stripped by user)
        setIfDifferent(retirementAgeFld,  String.valueOf(params.retirementAge));
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
        paramsService.save(params);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private Node buildHeader() {
        Label title = new Label("Financial Plan");
        title.getStyleClass().add("screen-title");

        Label subtitle = new Label("Retirement & wealth projection · illustrative data");
        subtitle.getStyleClass().add("dialog-subtitle");

        Label badge = new Label("⚠  Sample data — connect your profile to compute actuals");
        badge.getStyleClass().add("fp-sample-badge");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(new VBox(2, title, subtitle), spacer, badge);
        return header;
    }

    // ── KPI strip ─────────────────────────────────────────────────────────────

    private Node buildKpiGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);

        ColumnConstraints col = new ColumnConstraints();
        col.setHgrow(Priority.ALWAYS);
        col.setPercentWidth(33.33);
        grid.getColumnConstraints().addAll(col, copy(col), copy(col));

        grid.add(kpiCard("Current Corpus",               formatCorpusDisplay(corpusBreakdown.totalPaise()),  "-brand-mid",    false, false), 0, 0);
        grid.add(kpiCard("Future Earnings",              "₹5.38 Cr",  "-brand-light",  false, false), 1, 0);
        grid.add(kpiCard("Forecasted Retirement Corpus", "₹5.29 Cr",  "-brand-accent", true,  false), 2, 0);
        grid.add(kpiCard("Major Events (Forecast)",      "₹3.00 Cr",  "-brand-light",  false, false), 0, 1);
        grid.add(kpiCard("Corpus Gap",                   "₹89 L",     "#e05555",       false, true),  1, 1);
        // Retirement Year uses the live-computed label
        grid.add(kpiCardWithLabel("Retirement Year", retirementYearLbl, "-brand-light"), 2, 1);

        return grid;
    }

    /**
     * Stat card matching the DashboardScreen.summaryCard pattern.
     * stripeColor is data-driven per card, so inline is justified.
     */
    private VBox kpiCard(String label, String value,
                          String stripeColor, boolean accent, boolean negative) {
        Label lbl = new Label(label.toUpperCase());
        lbl.getStyleClass().add("card-title");

        Label val = new Label(value);
        val.getStyleClass().add("card-value");
        if (accent)   val.getStyleClass().add("fp-kpi-value-accent");
        if (negative) val.getStyleClass().add("fp-kpi-value-negative");

        VBox card = new VBox(6, lbl, val);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.getStyleClass().add("card-summary");
        // Inline required: stripe colour is data-driven per card
        card.setStyle("-fx-background-color: " + stripeColor + ", white; "
                    + "-fx-background-insets: 0, 0 0 0 3;");
        return card;
    }

    /** KPI card using an externally managed Label (for live-updated values). */
    private VBox kpiCardWithLabel(String labelText, Label valueLabel, String stripeColor) {
        Label lbl = new Label(labelText.toUpperCase());
        lbl.getStyleClass().add("card-title");

        VBox card = new VBox(6, lbl, valueLabel);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.getStyleClass().add("card-summary");
        // Inline required: stripe colour is data-driven per card
        card.setStyle("-fx-background-color: " + stripeColor + ", white; "
                    + "-fx-background-insets: 0, 0 0 0 3;");
        return card;
    }

    // ── Plan Parameters ───────────────────────────────────────────────────────

    private Node buildParamsCard() {
        Label title = new Label("Plan Parameters");
        title.getStyleClass().add("text-section-title");

        Button recalcBtn = new Button("Recalculate");
        recalcBtn.getStyleClass().add("btn-gold");
        recalcBtn.setOnAction(e -> collectAndSave());

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, recalcBtn);

        Region divider = new Region();
        divider.getStyleClass().add("content-divider");
        divider.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(12, header, divider, buildParamGrid());
        card.getStyleClass().add("card");
        return card;
    }

    private GridPane buildParamGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setPrefWidth(190);
        labelCol.setMinWidth(120);

        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);

        ColumnConstraints gapCol = new ColumnConstraints();
        gapCol.setMinWidth(24);
        gapCol.setPrefWidth(24);

        // 5-column layout: [label, field, gap, label, field]
        grid.getColumnConstraints().addAll(labelCol, fieldCol, gapCol, labelCol, fieldCol);

        // ── Left column ───────────────────────────────────────────────────────
        addParamRow(grid, 0, "Current Age",           currentAgeLbl,     0);
        addParamRow(grid, 1, "Retirement Age",        retirementAgeFld,  0);
        addParamRow(grid, 2, "Life Expectancy",       lifeExpectancyFld, 0);
        addParamRow(grid, 3, "Years to Retirement",   yearsToRetireLbl,  0);
        addParamRow(grid, 4, "Years in Retirement",   yearsInRetireLbl,  0);
        addParamRow(grid, 5, "Pre-Retire Tax Rate",   preRetireTaxFld,   0);
        addParamRow(grid, 6, "Post-Retire Tax Rate",  postRetireTaxFld,  0);
        addParamRow(grid, 7, "Inflation Rate",        inflationFld,      0);

        // ── Right column ──────────────────────────────────────────────────────
        addParamRow(grid, 0, "RoR – Equities (Pre-retire)",    rorEquityFld,          3);
        addParamRow(grid, 1, "RoR – MF (Pre-retire)",          rorMfFld,              3);
        addParamRow(grid, 2, "RoR – PF (Pre-retire)",          rorPfFld,              3);
        addParamRow(grid, 3, "RoR – Post-Retirement",          rorPostRetireFld,      3);
        addParamRow(grid, 4, "Cost of Living / Year",          costOfLivingFld,       3);
        addParamRow(grid, 5, "Current Employment Start Date",  employmentStartPicker, 3);
        addParamRow(grid, 6, "Monthly MF SIP",                 sipMfFld,              3);
        addParamRow(grid, 7, "Monthly Equity SIP",             sipEquityFld,          3);

        return grid;
    }

    /**
     * Adds one label + value row to the param grid.
     * startCol is 0 for the left column pair, 3 for the right column pair.
     */
    private void addParamRow(GridPane grid, int row, String labelText,
                              Node valueNode, int startCol) {
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

    // ── Current Corpus Breakdown ──────────────────────────────────────────────

    private Region buildCorpusCard() {
        VBox card = startSectionCard("Current Corpus Breakdown", "-brand-mid");

        addTableRow(card, "Bank Accounts",      formatRupees(corpusBreakdown.bankPaise()),   false, false);
        addTableRow(card, "Equities",           formatRupees(corpusBreakdown.equityPaise()), false, false);
        addTableRow(card, "Mutual Funds",       formatRupees(corpusBreakdown.mfPaise()),     false, false);
        addTableRow(card, "Bonds",              formatRupees(corpusBreakdown.bondsPaise()),  false, false);
        addTableRow(card, "Fixed Deposits",     formatRupees(corpusBreakdown.fdPaise()),     false, false);
        addTableRow(card, "Recurring Deposits", formatRupees(corpusBreakdown.rdPaise()),     false, false);
        addTableRow(card, "Provident Fund",     formatRupees(corpusBreakdown.pfPaise()),     false, false);
        addTableRow(card, "Total Corpus",       formatRupees(corpusBreakdown.totalPaise()),  true,  false);

        String[][] comment_rows = {
                { "* Bank accounts amount excluding credit card balances" },
                { "* Equities and Mutual Funds valued at 90% of last recorded market value" },
                { "* Bonds, FDs and RDs valued as per their invested amount" },
                { "* Provident Fund as per account balance" },
        };
        for (String[] r : comment_rows) addTableCommentRow(card, r[0]);

        return card;
    }

    // ── Future Earnings ───────────────────────────────────────────────────────

    private Region buildEarningsCard() {
        VBox card = startSectionCard("Future Earnings Until Retirement", "-brand-accent");

        addTableSubHeader(card, "Earnings");
        String[][] earnings = {
            { "Post-tax Income",         "₹3,78,00,000" },
            { "PF Contributions",        "₹60,40,000"   },
            { "Gratuity at Retirement",  "₹20,00,000"   },
            { "PF Interest",             "₹80,00,000"   },
        };
        for (String[] r : earnings) addTableRow(card, r[0], r[1], false, false);
        addTableRow(card, "Subtotal – Earnings", "₹5,38,40,000", true, false);

        addTableSubHeader(card, "Realized ROI");
        String[][] roi = {
            { "Bonds Interest", "₹4,60,000" },
            { "FDs Interest",   "₹2,60,000" },
            { "RDs Interest",   "₹1,00,000" },
        };
        for (String[] r : roi) addTableRow(card, r[0], r[1], false, false);
        addTableRow(card, "Total Realized ROI", "₹8,20,000", true, false);

        addTableSubHeader(card, "Unrealized ROI (Appreciation)");
        String[][] unrealized = {
            { "Equity Appreciation + Future SIP", "₹3,00,000"  },
            { "MF Appreciation + Future SIP",     "₹46,00,000" },
        };
        for (String[] r : unrealized) addTableRow(card, r[0], r[1], false, false);
        addTableRow(card, "Total Unrealized ROI", "₹49,00,000", true, false);

        return card;
    }

    // ── Major Events ──────────────────────────────────────────────────────────

    private Region buildMajorEventsCard() {
        VBox card = startSectionCard("Major Events", "#a78bfa");

        Label hint = new Label("Forecasted cost vs. actuals tracked from your transactions");
        hint.getStyleClass().add("text-hint");
        card.getChildren().add(hint);

        // Column headers — widths must match data row column widths exactly
        HBox colHeader = new HBox();
        colHeader.getStyleClass().add("fp-event-col-header");
        colHeader.setMaxWidth(Double.MAX_VALUE);
        Label nameHdr = new Label("Event");
        nameHdr.getStyleClass().add("fp-table-header-label");
        nameHdr.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameHdr, Priority.ALWAYS);
        Label forecastHdr = new Label("Forecast");
        forecastHdr.getStyleClass().add("fp-table-header-label");
        forecastHdr.setPrefWidth(110);
        forecastHdr.setAlignment(Pos.CENTER_RIGHT);
        Label actualHdr = new Label("Actual");
        actualHdr.getStyleClass().add("fp-table-header-label");
        actualHdr.setPrefWidth(110);
        actualHdr.setAlignment(Pos.CENTER_RIGHT);
        colHeader.getChildren().addAll(nameHdr, forecastHdr, actualHdr);
        card.getChildren().add(colHeader);

        Object[][] events = {
            { "Education",         "₹50,00,000",   "₹11,78,325", true  },
            { "Travel / Vacation", "₹1,00,00,000", "₹10,69,571", true  },
            { "Emergency Fund",    "₹50,00,000",   "₹0",         false },
            { "Home Renovation",   "₹20,00,000",   "₹0",         false },
            { "Wedding Fund",      "₹50,00,000",   "₹0",         false },
        };
        for (Object[] ev : events) {
            addEventRow(card, (String) ev[0], (String) ev[1],
                        (String) ev[2], (boolean) ev[3]);
        }

        // Total row
        HBox totalRow = new HBox();
        totalRow.getStyleClass().add("fp-events-total-row");
        VBox.setMargin(totalRow, new Insets(8, 0, 0, 0));
        Label totalLbl = new Label("Total Forecast");
        totalLbl.getStyleClass().add("fp-table-label-total");
        totalLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(totalLbl, Priority.ALWAYS);
        Label totalVal = new Label("₹2,70,00,000");
        totalVal.getStyleClass().addAll("fp-table-value", "fp-table-value-total");
        totalRow.getChildren().addAll(totalLbl, totalVal);
        card.getChildren().add(totalRow);

        // "Add Event" button
        Button addBtn = new Button("+ Add Major Event");
        addBtn.getStyleClass().add("btn-gold");
        addBtn.setOnAction(e -> { /* TODO: open add-event dialog */ });
        HBox btnRow = new HBox(addBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(8, 0, 0, 0));
        card.getChildren().add(btnRow);

        return card;
    }

    private void addEventRow(VBox parent, String name,
                              String forecast, String actual, boolean hasActual) {
        HBox row = new HBox();
        row.getStyleClass().add("fp-event-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        Label nameLbl = new Label(name);
        nameLbl.getStyleClass().add("fp-event-name");
        nameLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        // Fixed column widths must match the column header widths above
        Label forecastLbl = new Label(forecast);
        forecastLbl.getStyleClass().add("fp-event-forecast");
        forecastLbl.setPrefWidth(110);
        forecastLbl.setAlignment(Pos.CENTER_RIGHT);

        Label actualLbl = new Label(actual);
        actualLbl.getStyleClass().add(hasActual ? "fp-event-actual" : "fp-event-actual-zero");
        actualLbl.setPrefWidth(110);
        actualLbl.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(nameLbl, forecastLbl, actualLbl);
        parent.getChildren().add(row);
    }

    // ── Expenses + Forecasted Corpus ──────────────────────────────────────────

    private Region buildExpensesAndForecastCard() {
        VBox card = startSectionCard("Expenses Until Retirement", "#f87171");

        String[][] expenses = {
            { "Loan Payments",  "₹26,80,000"   },
            { "Cost of Living", "₹1,16,00,000" },
        };
        for (String[] r : expenses) addTableRow(card, r[0], r[1], false, true);
        addTableRow(card, "Total Expenses", "₹1,42,80,000", true, true);

        Region gap = new Region();
        gap.setPrefHeight(16);
        card.getChildren().add(gap);

        Label subTitle = new Label("Forecasted Corpus at Retirement");
        subTitle.getStyleClass().add("text-section-title");
        card.getChildren().add(subTitle);

        String[][] corpus = {
            { "PF Balance",            "₹2,44,00,000" },
            { "Stocks & MF",           "₹2,03,00,000" },
            { "Cash (incl. Gratuity)", "₹82,00,000"   },
        };
        for (String[] r : corpus) addTableRow(card, r[0], r[1], false, false);
        addTableRow(card, "Gross Corpus", "₹5,29,00,000", true, false);

        // Gap pill
        Label gapPill = new Label("⚠  Corpus shortfall: ₹89 L");
        gapPill.getStyleClass().add("fp-gap-pill-warn");
        HBox pillRow = new HBox(gapPill);
        pillRow.setPadding(new Insets(10, 0, 0, 0));
        card.getChildren().add(pillRow);

        return card;
    }

    // ── Post-Retirement Projection ────────────────────────────────────────────

    private Node buildPostRetirementCard() {
        Label title = new Label("Post-Retirement Corpus Projection");
        title.getStyleClass().add("text-section-title");

        // Legend — dot colours are data-driven (not derivable from a CSS class alone)
        HBox legend = new HBox(16,
                buildLegendDot("-brand-mid",    "Healthy balance"),
                buildLegendDot("-brand-accent", "Below ₹2 Cr"),
                buildLegendDot("-color-error",  "Depleted"));
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setPadding(new Insets(6, 0, 8, 0));

        double maxBalance = 5.73;
        Object[][] yearData = {
            { "2032", 5.29 }, { "2033", 5.38 }, { "2034", 5.46 }, { "2035", 5.53 },
            { "2036", 5.59 }, { "2037", 5.65 }, { "2038", 5.69 }, { "2039", 5.71 },
            { "2040", 5.73 }, { "2041", 5.73 }, { "2042", 5.71 }, { "2043", 5.66 },
            { "2044", 5.60 }, { "2045", 5.52 }, { "2046", 5.40 }, { "2047", 5.26 },
            { "2048", 5.08 }, { "2049", 4.87 }, { "2050", 4.62 }, { "2051", 4.32 },
            { "2052", 3.98 }, { "2053", 3.59 }, { "2054", 3.14 }, { "2055", 2.63 },
            { "2056", 2.06 }, { "2057", 1.41 }, { "2058", 0.69 },
            { "2059", -0.12 }, { "2060", -1.01 }, { "2064", -5.05 },
        };

        VBox bars = new VBox(4);
        for (Object[] row : yearData) {
            bars.getChildren().add(buildBarRow((String) row[0], (Double) row[1], maxBalance));
        }

        Label warning = new Label(
                "⚠  At current withdrawal rates, corpus depletes around 2058–2059 (Age 85). "
                + "Life expectancy target is 80. Consider increasing corpus or reducing "
                + "post-retirement withdrawal rate.");
        warning.getStyleClass().add("fp-depletion-warning");
        warning.setWrapText(true);
        warning.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(12, title, legend, bars, warning);
        card.getStyleClass().add("card");
        return card;
    }

    private HBox buildBarRow(String year, double balanceCr, double maxCr) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Label yearLbl = new Label(year);
        yearLbl.getStyleClass().add("fp-bar-year");

        StackPane track = new StackPane();
        track.getStyleClass().add("fp-bar-track");
        HBox.setHgrow(track, Priority.ALWAYS);

        if (balanceCr > 0) {
            Region fill = new Region();
            double pct = Math.min(balanceCr / maxCr, 1.0);
            fill.getStyleClass().add(balanceCr < 2.0 ? "fp-bar-fill-low" : "fp-bar-fill-healthy");
            fill.prefWidthProperty().bind(Bindings.multiply(track.widthProperty(), pct));
            fill.setMaxWidth(Double.MAX_VALUE);
            StackPane.setAlignment(fill, Pos.CENTER_LEFT);
            track.getChildren().add(fill);
        } else {
            // Full red bar for depleted years
            Region fill = new Region();
            fill.getStyleClass().add("fp-bar-fill-depleted");
            fill.setMaxWidth(Double.MAX_VALUE);
            track.getChildren().add(fill);
        }

        String displayVal = balanceCr >= 0
                ? String.format("₹%.2f Cr", balanceCr)
                : String.format("−₹%.2f Cr", Math.abs(balanceCr));
        Label valLbl = new Label(displayVal);
        valLbl.getStyleClass().add("fp-bar-value");
        if (balanceCr < 0) valLbl.getStyleClass().add("fp-bar-value-negative");

        row.getChildren().addAll(yearLbl, track, valLbl);
        return row;
    }

    /**
     * Legend entry: coloured dot + label text.
     * dotColor is a CSS token (e.g. "-brand-mid") or hex — it drives the suffix used in
     * the fp-legend-dot-* classes defined in app.css. The suffix is formed by stripping
     * hyphens from the token name.
     */
    private HBox buildLegendDot(String colorToken, String label) {
        Region dot = new Region();
        String suffix = colorToken.replace("-", "").replace("fx", "");
        dot.getStyleClass().addAll("fp-legend-dot", "fp-legend-dot-" + suffix);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("fp-legend-label");

        HBox entry = new HBox(6, dot, lbl);
        entry.setAlignment(Pos.CENTER_LEFT);
        return entry;
    }

    // ── Shared table-building helpers ─────────────────────────────────────────

    /**
     * Creates a section card with a title and a coloured dot indicator.
     * dotColor is a CSS token or hex literal — used as-is in an inline fill on the Circle.
     * Inline required: Shape.fill cannot be driven by a CSS class.
     */
    private VBox startSectionCard(String title, String dotColor) {
        Circle dot = new Circle(4);
        // Inline required: Shape.fill cannot be set via CSS class; colour is data-driven
        dot.setStyle("-fx-fill: " + dotColor + ";");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("text-section-title");

        HBox header = new HBox(8, dot, titleLbl);
        header.setAlignment(Pos.CENTER_LEFT);

        Region divider = new Region();
        divider.getStyleClass().add("content-divider");
        divider.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(0, header, divider);
        card.getStyleClass().add("card");
        VBox.setMargin(header,  new Insets(0, 0, 8,  0));
        VBox.setMargin(divider, new Insets(0, 0, 10, 0));
        return card;
    }

    private void addTableSubHeader(VBox parent, String label) {
        Label lbl = new Label(label.toUpperCase());
        lbl.getStyleClass().add("section-group-label");
        VBox.setMargin(lbl, new Insets(10, 0, 2, 0));
        parent.getChildren().add(lbl);
    }

    private void addTableRow(VBox parent, String label, String value,
                              boolean total, boolean negative) {
        HBox row = new HBox();
        row.getStyleClass().add(total ? "fp-table-row-total" : "fp-table-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        Label lblNode = new Label(label);
        lblNode.getStyleClass().add(total ? "fp-table-label-total" : "fp-table-label");
        lblNode.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblNode, Priority.ALWAYS);

        Label valNode = new Label(value);
        valNode.getStyleClass().add("fp-table-value");
        if (total)    valNode.getStyleClass().add("fp-table-value-total");
        if (negative) valNode.getStyleClass().add("fp-table-value-negative");
        valNode.setMinWidth(Label.USE_PREF_SIZE);

        row.getChildren().addAll(lblNode, valNode);
        parent.getChildren().add(row);
    }

    private void addTableCommentRow(VBox parent, String label) {
        HBox row = new HBox();
        row.getStyleClass().add("fp-table-row-comment");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        Label lblNode = new Label(label);
        lblNode.getStyleClass().add("fp-table-label-comment");
        lblNode.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblNode, Priority.ALWAYS);

        row.getChildren().addAll(lblNode);
        parent.getChildren().add(row);
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    /*private HBox buildTwoCol(Region left, Region right) {
        HBox row = new HBox(16, left, right);
        HBox.setHgrow(left,  Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setMaxWidth(Double.MAX_VALUE);
        right.setMaxWidth(Double.MAX_VALUE);
        return row;
    }*/

    private HBox buildTwoCol(Region left, Region right) {
        HBox row = new HBox(16, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setMaxWidth(Double.MAX_VALUE);
        right.setMaxWidth(Double.MAX_VALUE);

        // Bind widths to half of the row width
        row.widthProperty().addListener((obs, oldVal, newVal) -> {
            double half = newVal.doubleValue() / 2.0;
            left.setPrefWidth(half);
            right.setPrefWidth(half);
        });

        return row;
    }

    private ColumnConstraints copy(ColumnConstraints src) {
        ColumnConstraints c = new ColumnConstraints();
        c.setHgrow(src.getHgrow());
        c.setPercentWidth(src.getPercentWidth());
        return c;
    }

    // ── Field factory helpers ─────────────────────────────────────────────────

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

    // ── Corpus computation ────────────────────────────────────────────────────

    private CorpusBreakdown computeCorpusBreakdown() {
        DataStore ds = DataStore.getInstance();
        LocalDate today = LocalDate.now();
        final long ROUND = 1_000_000L; // 10,000 rupees in paise

        long bank = Math.floorDiv(
                ds.getTotalBankBalancePaise() - ds.getTotalCreditCardOutstandingPaise(), ROUND) * ROUND;

        long equity = 0, mf = 0, bonds = 0, fd = 0, rd = 0, pf = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            long value;
            switch (ia.getInvestmentType()) {
                case EQUITY -> {
                    MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
                    value = mv != null ? (long) (mv.getMarketValuePaise() * 0.9)
                                       : ds.getInvestedPaiseAsOf(ia, today);
                    equity += Math.floorDiv(value, ROUND) * ROUND;
                }
                case MUTUAL_FUNDS -> {
                    MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
                    value = mv != null ? (long) (mv.getMarketValuePaise() * 0.9)
                                       : ds.getInvestedPaiseAsOf(ia, today);
                    mf += Math.floorDiv(value, ROUND) * ROUND;
                }
                case DEBT_BONDS        -> bonds += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND) * ROUND;
                case FIXED_DEPOSIT     -> fd    += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND) * ROUND;
                case RECURRING_DEPOSIT -> rd    += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND) * ROUND;
                case PROVIDENT_FUND    -> pf    += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND) * ROUND;
            }
        }

        return new CorpusBreakdown(bank, equity, mf, bonds, fd, rd, pf,
                bank + equity + mf + bonds + fd + rd + pf);
    }

    /** Formats a paise value as a human-readable corpus amount: ₹3.40 Cr, ₹50 Lakh, etc. */
    private String formatCorpusDisplay(long paise) {
        long rupees = paise / 100;
        if (rupees >= 1_00_00_000L) {
            return String.format("₹%.2f Cr", rupees / 1_00_00_000.00);
        } else if (rupees >= 1_00_000L) {
            return "₹" + Math.round(rupees / 1_00_000.00) + " Lakh";
        } else {
            return String.format("₹%,.0f", (double) rupees);
        }
    }

    // ── Format / parse helpers ────────────────────────────────────────────────

    private String formatPct(double pct) {
        return (pct == Math.floor(pct))
                ? String.format("%.0f%%", pct)
                : String.format("%.1f%%", pct);
    }

    private String formatRupees(long paise) {
        return String.format("₹%,.0f", paise / 100.0);
    }

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
        if (text == null || text.isBlank()) return fallback;
        try {
            double rupees = Double.parseDouble(
                    text.replace("₹", "").replace(",", "").trim());
            return Math.round(rupees * 100);
        } catch (Exception e) { return fallback; }
    }

    /** Sets the field text only if the new value differs, to avoid spurious listener firing. */
    private void setIfDifferent(TextField fld, String newText) {
        if (!newText.equals(fld.getText())) fld.setText(newText);
    }
}
