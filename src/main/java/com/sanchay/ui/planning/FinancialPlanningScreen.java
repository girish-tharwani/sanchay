package com.sanchay.ui.planning;

import com.sanchay.model.FamilyMember;
import com.sanchay.model.MajorEvent;
import com.sanchay.model.PlanParameters;
import com.sanchay.service.AppConfig;
import com.sanchay.service.DataStore;
import com.sanchay.service.FinancialPlanningCalculator;
import com.sanchay.service.MajorEventPlanner;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.service.PlanParamsService;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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

    private enum ProjectionMode {
        MINIMUM,
        ACTUAL
    }

    private ScrollPane view;
    private final Runnable navigateToProfile;

    private PlanParamsService paramsService;
    private final FinancialPlanningCalculator planningCalculator;
    private final MajorEventPlanner majorEventPlanner;
    private PlanParameters    params;
    private double            currentAgeDecimal;
    private LocalDate         selfDob;

    // ── Derived (read-only) labels — updated live ─────────────────────────────
    private Label currentAgeLbl;
    private Label yearsToRetireLbl;
    private Label yearsInRetireLbl;
    private Label retirementAgeLbl;    // also reused in KPI strip

    // ── Corpus breakdown (computed once per buildView) ────────────────────────
    private FinancialPlanningCalculator.CorpusBreakdown corpusBreakdown;

    // ── Future earnings breakdown (computed once per buildView) ───────────────
    private FinancialPlanningCalculator.FutureEarningsBreakdown futureEarnings;

    // ── Live-updatable UI handles ─────────────────────────────────────────────
    private Label lastUpdatedLbl;
    private VBox  earningsCard;
    private Label futureEarningsKpiLbl;
    private Label forecastedCorpusKpiLbl;

    // ── Major Events (live-updatable) ─────────────────────────────────────────
    private Label majorEventsKpiLbl;
    private VBox  majorEventsListBox;
    private Label majorEventsForecastTotalLbl;
    private Label majorEventsActualTotalLbl;

    // ── Expenses card (live-updatable expense rows) ───────────────────────────
    private VBox expenseRowsContainer;

    // ── Forecasted Corpus card (live-updatable) ───────────────────────────────
    private VBox  corpusCard;
    private long  forecastedCorpusPaise;
    private TableView<FinancialPlanningCalculator.PostRetirementRow> postRetirementTable;
    private CheckBox projectionModeSwitch;
    private Label minimumProjectionLbl;
    private Label actualProjectionLbl;
    private ProjectionMode projectionMode = ProjectionMode.MINIMUM;

    // ── Editable fields ───────────────────────────────────────────────────────
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
    private DatePicker employmentStartPicker;
    private TextField  sipMfFld;
    private TextField  sipEquityFld;

    public FinancialPlanningScreen(Runnable navigateToProfile) {
        this.navigateToProfile = navigateToProfile;
        this.majorEventPlanner = new MajorEventPlanner();
        this.planningCalculator = new FinancialPlanningCalculator();
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
            // Defer until the view is placed in the scene graph, then show error and redirect
            Platform.runLater(this::showProfileIncompleteError);
            view = new ScrollPane(new Label(""));
            return;
        }

        selfDob = self.getDateOfBirth();
        Period agePeriod  = Period.between(selfDob, LocalDate.now());
        currentAgeDecimal = agePeriod.getYears() + agePeriod.getMonths() / 12.0;

        AppConfig.Config cfg = AppConfig.read();
        paramsService = new PlanParamsService(cfg.dataFolderPath);
        params        = paramsService.load();

        initFields();
        updateDerivedLabels();
        corpusBreakdown = planningCalculator.computeCorpusBreakdown();
        futureEarnings  = planningCalculator.computeFutureEarnings(params, selfDob);

        VBox content = new VBox(20);
        content.getStyleClass().add("main-panel");
        content.setPadding(new Insets(28));
        content.getChildren().addAll(
                buildHeader(),
                buildKpiGrid(),
                buildParamsCard(),
                buildTwoCol(buildCorpusCard(), buildEarningsCard()),
                buildTwoCol(buildExpensesCard(), buildForecastedCorpusCard()),
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
        currentAgeLbl    = derivedLabel(String.format("%.2f", currentAgeDecimal));
        yearsToRetireLbl = derivedLabel("");
        yearsInRetireLbl = derivedLabel("");
        retirementAgeLbl = derivedLabel("");
        retirementAgeLbl.getStyleClass().add("card-value");

        // Editable text fields
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

        // Date pickers — retirement date and employment start; share the app date format
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
        // Prefer stored retirementDate; fall back to retirementAge years from DOB
        if (params.retirementDate != null) {
            try { retirementDatePicker.setValue(LocalDate.parse(params.retirementDate)); }
            catch (Exception ignored) { retirementDatePicker.setValue(selfDob.plusYears(params.retirementAge)); }
        } else {
            retirementDatePicker.setValue(selfDob.plusYears(params.retirementAge));
        }
        retirementDatePicker.valueProperty().addListener((obs, o, n) -> { updateDerivedLabels(); collectAndSave(); });

        employmentStartPicker = new DatePicker();
        employmentStartPicker.setPrefWidth(130);
        employmentStartPicker.setConverter(dateConverter);
        if (params.employmentStartDate != null) {
            try { employmentStartPicker.setValue(LocalDate.parse(params.employmentStartDate)); }
            catch (Exception ignored) {}
        }
        employmentStartPicker.valueProperty().addListener((obs, o, n) -> collectAndSave());

        // Live-update derived labels when life-expectancy changes
        lifeExpectancyFld.textProperty().addListener((obs, o, n) -> updateDerivedLabels());

        // Auto-save on focus lost for all text fields
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

            //yearsToRetireLbl.setText(String.valueOf((int) toRetire));
            yearsToRetireLbl.setText(String.format("%.2f", toRetire));
            //yearsInRetireLbl.setText(String.valueOf((int) inRetire));
            yearsInRetireLbl.setText(String.format("%.2f", inRetire));
            retirementAgeLbl.setText(String.format("%.2f", retireAge));
        }
    }

    // ── Collect, format, and save ─────────────────────────────────────────────

    private void collectAndSave() {
        if (paramsService == null || params == null) return;

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
        if (employmentStartPicker.getValue() != null) {
            params.employmentStartDate = employmentStartPicker.getValue().toString();
        }

        // Reformat fields for consistent display (auto-append % / ₹ if stripped by user)
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

        Label subtitle = new Label("Retirement & wealth projection");
        subtitle.getStyleClass().add("dialog-subtitle");

        lastUpdatedLbl = new Label(formatLastUpdated(params.lastCalculatedDate));
        lastUpdatedLbl.getStyleClass().add("fp-last-updated");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(new VBox(2, title, subtitle), spacer, lastUpdatedLbl);
        return header;
    }

    private String formatLastUpdated(String isoDate) {
        if (isoDate == null) return "Not yet calculated";
        try {
            LocalDate d = LocalDate.parse(isoDate);
            return "Last updated on: " + d.format(DataStore.getInstance().getDateFormatter());
        } catch (Exception e) {
            return "Last updated on: " + isoDate;
        }
    }

    // ── KPI strip ─────────────────────────────────────────────────────────────

    private Node buildKpiGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);

        ColumnConstraints col = new ColumnConstraints();
        col.setHgrow(Priority.ALWAYS);
        col.setPercentWidth(33.33);
        grid.getColumnConstraints().addAll(col, copy(col), copy(col));

        grid.add(kpiCard("Current Corpus", UiUtils.formatCorpusDisplay(corpusBreakdown.totalPaise()), "-brand-mid", false, false), 0, 0);

        futureEarningsKpiLbl = new Label(UiUtils.formatCorpusDisplay(futureEarnings.totalPaise()));
        futureEarningsKpiLbl.getStyleClass().add("card-value");
        grid.add(kpiCardWithLabel("Future Earnings", futureEarningsKpiLbl, "-brand-light"), 1, 0);

        // Initialised with "" — populateCorpusCard() sets the real value immediately after buildView()
        forecastedCorpusKpiLbl = new Label("");
        forecastedCorpusKpiLbl.getStyleClass().addAll("card-value", "fp-kpi-value-accent");
        grid.add(kpiCardWithLabel("Forecasted Retirement Corpus", forecastedCorpusKpiLbl, "-brand-accent"), 2, 0);

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
        recalcBtn.setOnAction(e -> {
            params.lastCalculatedDate = LocalDate.now().toString();
            collectAndSave();
            refreshComputedSections();
            if (lastUpdatedLbl != null)
                lastUpdatedLbl.setText(formatLastUpdated(params.lastCalculatedDate));
        });

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
        labelCol.setPrefWidth(200);
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
        addParamRow(grid, 1, "Retirement Date",       retirementDatePicker,  0);
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
        VBox card = startSectionCard("Current Corpus", "-brand-mid");

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
        earningsCard = startSectionCard("Future Earnings", "-brand-accent");
        populateEarningsCard(earningsCard);
        return earningsCard;
    }

    private void populateEarningsCard(VBox card) {
        addTableSubHeader(card, "Earnings");
        addTableRow(card, "Post-tax Income",        formatRupees(futureEarnings.postTaxIncomePaise()), false, false);
        addTableRow(card, "PF Contributions",       formatRupees(futureEarnings.pfContribPaise()),     false, false);
        addTableRow(card, "Gratuity at Retirement", formatRupees(futureEarnings.gratuityPaise()),      false, false);
        addTableRow(card, "PF Interest",            formatRupees(futureEarnings.pfInterestPaise()),    false, false);
        addTableRow(card, "Subtotal – Earnings",    formatRupees(futureEarnings.earningsSubtotalPaise()), true, false);

        addTableSubHeader(card, "Realized ROI");
        addTableRow(card, "Bonds Interest", formatRupees(futureEarnings.bondsInterestPaise()), false, false);
        addTableRow(card, "FDs Interest",   formatRupees(futureEarnings.fdInterestPaise()),    false, false);
        addTableRow(card, "RDs Interest",   formatRupees(futureEarnings.rdInterestPaise()),    false, false);
        addTableRow(card, "Total Realized ROI", formatRupees(futureEarnings.realizedRoiSubtotalPaise()), true, false);

        addTableSubHeader(card, "Unrealized ROI (Appreciation)");
        addTableRow(card, "Equity Appreciation", formatRupees(futureEarnings.equityApprecPaise()), false, false);
        addTableRow(card, "MF Appreciation",     formatRupees(futureEarnings.mfApprecPaise()),     false, false);
        addTableRow(card, "Total Unrealized ROI", formatRupees(futureEarnings.unrealizedRoiSubtotalPaise()), true, false);
    }

    private void refreshEarningsCard() {
        futureEarnings = planningCalculator.computeFutureEarnings(params, selfDob);
        if (futureEarningsKpiLbl != null)
            futureEarningsKpiLbl.setText(UiUtils.formatCorpusDisplay(futureEarnings.totalPaise()));
        if (earningsCard != null) {
            // Keep header+divider (indices 0,1) and replace all content rows
            earningsCard.getChildren().subList(2, earningsCard.getChildren().size()).clear();
            populateEarningsCard(earningsCard);
        }
        refreshForecastCard();
    }

    private void refreshComputedSections() {
        corpusBreakdown = planningCalculator.computeCorpusBreakdown();
        refreshEarningsCard();
        refreshPostRetirementTable();
    }

    // ── Expenses card (Expenses table + Major Events table) ──────────────────

    private Region buildExpensesCard() {
        if (params.majorEvents == null) params.majorEvents = new ArrayList<>();

        VBox card = startSectionCard("Expenses Until Retirement", "#f87171");

        // ── Expense rows (refreshable sub-container) ──────────────────────────
        expenseRowsContainer = new VBox(0);
        card.getChildren().add(expenseRowsContainer);
        populateExpenseRows();

        Region divider = new Region();
        divider.getStyleClass().add("content-divider");
        divider.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(divider, new Insets(12, 0, 10, 0));
        card.getChildren().add(divider);

        // ── Major Events section ──────────────────────────────────────────────
        Label eventsTitle = new Label("MAJOR EVENTS");
        eventsTitle.getStyleClass().add("section-group-label");
        card.getChildren().add(eventsTitle);

        Label hint = new Label("Forecasted cost vs. actuals tracked from your transactions.");
        hint.getStyleClass().add("text-hint");
        hint.setWrapText(true);
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

        // Dynamic event rows container
        majorEventsListBox = new VBox();
        card.getChildren().add(majorEventsListBox);

        // Total row — two live labels for forecast and actual totals
        HBox totalRow = new HBox();
        totalRow.getStyleClass().add("fp-events-total-row");
        VBox.setMargin(totalRow, new Insets(8, 0, 0, 0));
        Label totalLbl = new Label("Total");
        totalLbl.getStyleClass().add("fp-table-label-total");
        totalLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(totalLbl, Priority.ALWAYS);
        majorEventsForecastTotalLbl = new Label(MoneyFormatter.symbol() + "0");
        majorEventsForecastTotalLbl.getStyleClass().addAll("fp-table-value", "fp-table-value-total");
        majorEventsForecastTotalLbl.setPrefWidth(110);
        majorEventsForecastTotalLbl.setAlignment(Pos.CENTER_RIGHT);
        majorEventsActualTotalLbl = new Label(MoneyFormatter.symbol() + "0");
        majorEventsActualTotalLbl.getStyleClass().addAll("fp-table-value", "fp-table-value-total");
        majorEventsActualTotalLbl.setPrefWidth(110);
        majorEventsActualTotalLbl.setAlignment(Pos.CENTER_RIGHT);
        totalRow.getChildren().addAll(totalLbl, majorEventsForecastTotalLbl, majorEventsActualTotalLbl);
        card.getChildren().add(totalRow);

        card.getChildren().add(UiUtils.hintLabel("Double-click an event to edit"));

        // "Add Major Event" button
        Button addBtn = new Button("+ Add Major Event");
        addBtn.getStyleClass().add("btn-gold");
        addBtn.setOnAction(e -> {
            MajorEventDialog dlg = new MajorEventDialog(null);
            if (dlg.show() == MajorEventDialog.Outcome.SAVED) {
                params.majorEvents.add(dlg.getResult());
                paramsService.save(params);
                refreshMajorEventsList();
            }
        });
        HBox btnRow = new HBox(addBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(8, 0, 0, 0));
        card.getChildren().add(btnRow);

        refreshMajorEventsList();
        return card;
    }

    private void refreshMajorEventsList() {
        if (majorEventsListBox == null) return;
        if (params.majorEvents == null) params.majorEvents = new ArrayList<>();

        majorEventsListBox.getChildren().clear();

        if (params.majorEvents.isEmpty()) {
            Label emptyLbl = new Label("No major events added yet.");
            emptyLbl.getStyleClass().add("text-hint");
            VBox.setMargin(emptyLbl, new Insets(6, 0, 2, 0));
            majorEventsListBox.getChildren().add(emptyLbl);
        } else {
            for (MajorEvent event : params.majorEvents) {
                HBox row = createEventRow(event);
                row.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) openEditDialog(event);
                });
                majorEventsListBox.getChildren().add(row);
            }
        }

        long forecastTotal = params.majorEvents.stream()
                .mapToLong(event -> majorEventPlanner.computeEventForecast(event,
                        planningCalculator.getRetirementDate(params, selfDob))).sum();
        long actualTotal = params.majorEvents.stream()
                .mapToLong(majorEventPlanner::computeEventActual).sum();

        if (majorEventsForecastTotalLbl != null)
            majorEventsForecastTotalLbl.setText(formatRupees(forecastTotal));
        if (majorEventsActualTotalLbl != null)
            majorEventsActualTotalLbl.setText(formatRupees(actualTotal));
        if (majorEventsKpiLbl != null)
            majorEventsKpiLbl.setText(UiUtils.formatCorpusDisplay(planningCalculator.computeMajorEventsKpi(params, selfDob)));
        // Expense rows include a "Major Events" line driven by computeMajorEventsKpi(); refresh it too
        populateExpenseRows();
        populateCorpusCard();
    }

    private HBox createEventRow(MajorEvent event) {
        long forecast = majorEventPlanner.computeEventForecast(event,
                planningCalculator.getRetirementDate(params, selfDob));
        long actual   = majorEventPlanner.computeEventActual(event);

        HBox row = new HBox();
        row.getStyleClass().add("fp-event-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setCursor(javafx.scene.Cursor.HAND);

        Label nameLbl = new Label(event.getName());
        nameLbl.getStyleClass().add("fp-event-name");
        nameLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        // Fixed column widths must match the column header widths above
        Label forecastLbl = new Label(formatRupees(forecast));
        forecastLbl.getStyleClass().add("fp-table-value");
        forecastLbl.setPrefWidth(110);
        forecastLbl.setAlignment(Pos.CENTER_RIGHT);

        Label actualLbl = new Label(formatRupees(actual));
        actualLbl.getStyleClass().add("fp-table-value");
        actualLbl.setPrefWidth(110);
        actualLbl.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(nameLbl, forecastLbl, actualLbl);
        return row;
    }

    private void openEditDialog(MajorEvent event) {
        MajorEventDialog dlg = new MajorEventDialog(event);
        MajorEventDialog.Outcome outcome = dlg.show();
        if (outcome == MajorEventDialog.Outcome.SAVED) {
            // event was mutated in place by MajorEventDialog.validate()
            paramsService.save(params);
            refreshMajorEventsList();
        } else if (outcome == MajorEventDialog.Outcome.DELETED) {
            params.majorEvents.remove(event);
            paramsService.save(params);
            refreshMajorEventsList();
        }
    }


    // ── Expense rows (refreshable sub-container) ─────────────────────────────

    private void populateExpenseRows() {
        if (expenseRowsContainer == null) return;
        expenseRowsContainer.getChildren().clear();

        FinancialPlanningCalculator.ExpenseSummary expenseSummary =
                planningCalculator.computeExpenseSummary(params, selfDob);
        addTableSubHeader(expenseRowsContainer, "EXPENSES");
        addTableRow(expenseRowsContainer, "Loan Payments",  formatRupees(expenseSummary.loanPaymentsPaise()),  false, false);
        addTableRow(expenseRowsContainer, "Cost of Living", formatRupees(expenseSummary.costOfLivingPaise()),  false, false);
        addTableRow(expenseRowsContainer, "Major Events",   formatRupees(expenseSummary.majorEventsPaise()),   false, false);
        addTableRow(expenseRowsContainer, "Total Expenses", formatRupees(expenseSummary.totalPaise()),         true,  true);
    }

    // ── Forecasted Corpus card ────────────────────────────────────────────────

    private Region buildForecastedCorpusCard() {
        corpusCard = startSectionCard("Forecasted Corpus at Retirement", "#86efac");
        populateCorpusCard();
        return corpusCard;
    }

    private void populateCorpusCard() {
        if (corpusCard == null) return;
        // Keep header+divider (indices 0,1) and replace all content rows
        corpusCard.getChildren().subList(2, corpusCard.getChildren().size()).clear();

        FinancialPlanningCalculator.ForecastedCorpusBreakdown forecastedCorpus =
                planningCalculator.computeForecastedCorpusBreakdown(params, selfDob, corpusBreakdown, futureEarnings);
        long totalForecasted = forecastedCorpus.totalPaise();
        forecastedCorpusPaise = totalForecasted;

        if (forecastedCorpusKpiLbl != null)
            forecastedCorpusKpiLbl.setText(UiUtils.formatCorpusDisplay(totalForecasted));

        addTableRow(corpusCard, "PF Balance",            formatRupees(forecastedCorpus.pfBalancePaise()),    false, false);
        addTableRow(corpusCard, "Stocks & MF",           formatRupees(forecastedCorpus.stocksMfPaise()),     false, false);
        addTableRow(corpusCard, "Cash, Bonds, FDs etc.", formatRupees(forecastedCorpus.cashBondsFdsPaise()), false, false);
        addTableRow(corpusCard, "Total Forecasted Corpus",
                formatRupees(totalForecasted), true, totalForecasted < 0);

        long requiredCorpus = planningCalculator.computeRequiredCorpusPaise(params, selfDob);
        long delta          = totalForecasted - requiredCorpus;
        boolean isShortfall = delta < 0;

        Label kindLbl = new Label(isShortfall
                ? "Projected Shortfall vs Required Corpus"
                : "Projected Surplus vs Required Corpus");
        kindLbl.getStyleClass().add("fp-corpus-pill-kind");

        Label amountLbl = new Label(UiUtils.formatCorpusDisplay(Math.abs(delta)));
        amountLbl.getStyleClass().add("fp-corpus-pill-amount");

        VBox pillBox = new VBox(4, kindLbl, amountLbl);
        pillBox.getStyleClass().addAll("fp-corpus-pill",
                isShortfall ? "fp-corpus-pill-shortfall" : "fp-corpus-pill-excess");
        pillBox.setAlignment(Pos.CENTER);
        pillBox.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(pillBox, new Insets(16, 0, 4, 0));
        corpusCard.getChildren().add(pillBox);
    }

    private void refreshForecastCard() {
        populateExpenseRows();
        populateCorpusCard();
    }

    // ── Post-Retirement Projection ────────────────────────────────────────────

    private Node buildPostRetirementCard() {
        minimumProjectionLbl = new Label("Minimum Corpus Projection");
        minimumProjectionLbl.getStyleClass().addAll("text-section-title", "fp-projection-mode-label");
        minimumProjectionLbl.setPrefWidth(210);
        minimumProjectionLbl.setMinWidth(210);
        minimumProjectionLbl.setAlignment(Pos.CENTER_RIGHT);

        projectionModeSwitch = new CheckBox();
        projectionModeSwitch.getStyleClass().add("fp-projection-switch");
        projectionModeSwitch.setSelected(false);
        projectionModeSwitch.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            projectionMode = isSelected ? ProjectionMode.ACTUAL : ProjectionMode.MINIMUM;
            updateProjectionModeHeader();
            refreshPostRetirementTable();
        });

        actualProjectionLbl = new Label("Actual Corpus Projection");
        actualProjectionLbl.getStyleClass().addAll("text-section-title", "fp-projection-mode-label");
        actualProjectionLbl.setPrefWidth(190);
        actualProjectionLbl.setMinWidth(190);
        actualProjectionLbl.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(10, minimumProjectionLbl, projectionModeSwitch, actualProjectionLbl);
        header.setAlignment(Pos.CENTER_LEFT);
        updateProjectionModeHeader();

        postRetirementTable = buildCashFlowTable();
        refreshPostRetirementTable();

        VBox card = new VBox(12, header, postRetirementTable);
        card.getStyleClass().add("card");
        return card;
    }

    private TableView<FinancialPlanningCalculator.PostRetirementRow> buildCashFlowTable() {
        TableView<FinancialPlanningCalculator.PostRetirementRow> table = new TableView<>();
        table.getStyleClass().add("forecast-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setEditable(false);

        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> yearCol = new TableColumn<>("Year");
        yearCol.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().year())));
        yearCol.setPrefWidth(70);

        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> ageCol = new TableColumn<>("Age");
        ageCol.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().age())));
        ageCol.setPrefWidth(55);

        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> balanceCol = new TableColumn<>("Starting Balance");
        balanceCol.setCellValueFactory(cd -> new SimpleStringProperty(formatRupees(cd.getValue().startingBalancePaise())));
        balanceCol.setPrefWidth(160);
        balanceCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("post-retirement-cf-depleted");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                if (getTableView().getItems().get(getIndex()).startingDepleted())
                    getStyleClass().add("post-retirement-cf-depleted");
            }
        });

        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> roiCol = new TableColumn<>("ROI");
        roiCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().startingDepleted() ? "-" : formatRupees(cd.getValue().roiPaise())));
        roiCol.setPrefWidth(130);

        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> taxCol = new TableColumn<>("Tax");
        taxCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().startingDepleted() ? "-" : formatRupees(cd.getValue().taxPaise())));
        taxCol.setPrefWidth(110);

        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> withdrawalCol = new TableColumn<>("Withdrawal");
        withdrawalCol.setCellValueFactory(cd -> new SimpleStringProperty(formatRupees(cd.getValue().withdrawalPaise())));
        withdrawalCol.setPrefWidth(130);

        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> endingBalanceCol = new TableColumn<>("Ending Balance");
        endingBalanceCol.setCellValueFactory(cd -> new SimpleStringProperty(formatRupees(cd.getValue().endingBalancePaise())));
        endingBalanceCol.setPrefWidth(160);
        endingBalanceCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("post-retirement-cf-depleted");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                if (getTableView().getItems().get(getIndex()).endingNegative())
                    getStyleClass().add("post-retirement-cf-depleted");
            }
        });

        table.getColumns().addAll(yearCol, ageCol, balanceCol, roiCol, taxCol, withdrawalCol, endingBalanceCol);
        return table;
    }

    private List<FinancialPlanningCalculator.PostRetirementRow> computeCashFlowRows() {
        long balance = projectionMode == ProjectionMode.ACTUAL
                ? forecastedCorpusPaise
                : planningCalculator.computeRequiredCorpusPaise(params, selfDob);
        return planningCalculator.computePostRetirementRows(params, selfDob, balance);
    }

    private void refreshPostRetirementTable() {
        if (postRetirementTable == null) return;
        postRetirementTable.getItems().setAll(computeCashFlowRows());
        postRetirementTable.refresh();
    }

    private void updateProjectionModeHeader() {
        if (minimumProjectionLbl == null || actualProjectionLbl == null) return;
        boolean minimumActive = projectionMode == ProjectionMode.MINIMUM;
        minimumProjectionLbl.getStyleClass().remove("fp-projection-mode-label-active");
        actualProjectionLbl.getStyleClass().remove("fp-projection-mode-label-active");
        minimumProjectionLbl.getStyleClass().remove("fp-projection-mode-label-inactive");
        actualProjectionLbl.getStyleClass().remove("fp-projection-mode-label-inactive");
        minimumProjectionLbl.getStyleClass().add(minimumActive
                ? "fp-projection-mode-label-active"
                : "fp-projection-mode-label-inactive");
        actualProjectionLbl.getStyleClass().add(minimumActive
                ? "fp-projection-mode-label-inactive"
                : "fp-projection-mode-label-active");
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

    private String formatPct(double pct) {
        return (pct == Math.floor(pct))
                ? String.format("%.0f%%", pct)
                : String.format("%.1f%%", pct);
    }

    private String formatRupees(long paise) {
        return MoneyFormatter.formatNoDecimal(paise);
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
        return MoneyFormatter.parseAmountSafe(text, fallback);
    }

    /** Sets the field text only if the new value differs, to avoid spurious listener firing. */
    private void setIfDifferent(TextField fld, String newText) {
        if (!newText.equals(fld.getText())) fld.setText(newText);
    }

    /**
     * Shows a styled error dialog when the user's profile lacks a date of birth,
     * then redirects to the Profile screen via the injected callback.
     * Called via Platform.runLater so the view is already in the scene graph.
     */
    private void showProfileIncompleteError() {
        Dialog<ButtonType> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Profile Incomplete", "!", 400);
        Label msg = new Label("Financial planning requires your date of birth.\nPlease set it in your Profile before continuing.");
        msg.getStyleClass().add("text-body-muted");
        msg.setWrapText(true);
        dlg.getDialogPane().setContent(msg);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dlg.showAndWait();
        navigateToProfile.run();
    }
}
