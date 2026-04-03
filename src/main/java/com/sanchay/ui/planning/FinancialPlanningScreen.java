package com.sanchay.ui.planning;

import com.sanchay.model.EarningSource;
import com.sanchay.model.FamilyMember;
import com.sanchay.model.InvestmentAccount;
import com.sanchay.model.MajorEvent;
import com.sanchay.model.MarketValueEntry;
import com.sanchay.model.PlanParameters;
import com.sanchay.model.RecurringTransaction;
import com.sanchay.model.Transaction;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private double            currentAgeDecimal;
    private LocalDate         selfDob;

    // ── Derived (read-only) labels — updated live ─────────────────────────────
    private Label currentAgeLbl;
    private Label yearsToRetireLbl;
    private Label yearsInRetireLbl;
    private Label retirementAgeLbl;    // also reused in KPI strip

    // ── Corpus breakdown (computed once per buildView) ────────────────────────
    private record CorpusBreakdown(
            long bankPaise, long equityPaise, long mfPaise,
            long bondsPaise, long fdPaise, long rdPaise,
            long pfPaise, long totalPaise) {}

    private CorpusBreakdown corpusBreakdown;

    // ── Future earnings breakdown (computed once per buildView) ───────────────
    private record FutureEarningsBreakdown(
            long postTaxIncomePaise,
            long pfContribPaise,
            long gratuityPaise,
            long pfInterestPaise,
            long earningsSubtotalPaise,
            long bondsInterestPaise,
            long fdInterestPaise,
            long rdInterestPaise,
            long realizedRoiSubtotalPaise,
            long equityFvPaise,
            long mfFvPaise,
            long unrealizedRoiSubtotalPaise,
            long totalPaise) {}

    private FutureEarningsBreakdown futureEarnings;

    // ── Live-updatable UI handles ─────────────────────────────────────────────
    private VBox  earningsCard;
    private Label futureEarningsKpiLbl;

    // ── Major Events (live-updatable) ─────────────────────────────────────────
    private Label majorEventsKpiLbl;
    private VBox  majorEventsListBox;
    private Label majorEventsForecastTotalLbl;
    private Label majorEventsActualTotalLbl;

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

        selfDob = self.getDateOfBirth();
        Period agePeriod  = Period.between(selfDob, LocalDate.now());
        currentAgeDecimal = agePeriod.getYears() + agePeriod.getMonths() / 12.0;

        AppConfig.Config cfg = AppConfig.read();
        paramsService = new PlanParamsService(cfg.dataFolderPath);
        params        = paramsService.load();

        initFields();
        updateDerivedLabels();
        corpusBreakdown = computeCorpusBreakdown();
        futureEarnings  = computeFutureEarnings();

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

            yearsToRetireLbl.setText(String.valueOf((int) toRetire));
            yearsInRetireLbl.setText(String.valueOf((int) inRetire));
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

        //Label badge = new Label("⚠  Sample data — connect your profile to compute actuals");
        //badge.getStyleClass().add("fp-sample-badge");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(new VBox(2, title, subtitle), spacer);
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
        // Future Earnings KPI uses a live-updatable label (refreshed on Recalculate)
        futureEarningsKpiLbl = new Label(formatCorpusDisplay(futureEarnings.totalPaise()));
        futureEarningsKpiLbl.getStyleClass().add("card-value");
        grid.add(kpiCardWithLabel("Future Earnings", futureEarningsKpiLbl, "-brand-light"), 1, 0);
        grid.add(kpiCard("Forecasted Retirement Corpus", "₹5.29 Cr",  "-brand-accent", true,  false), 2, 0);
        majorEventsKpiLbl = new Label(formatCorpusDisplay(computeMajorEventsKpi()));
        majorEventsKpiLbl.getStyleClass().add("card-value");
        grid.add(kpiCardWithLabel("Major Events", majorEventsKpiLbl, "-brand-light"), 0, 1);
        grid.add(kpiCard("Corpus Gap",                   "₹89 L",     "#e05555",       false, true),  1, 1);
        // Retirement Age uses the live-computed label
        grid.add(kpiCardWithLabel("Retirement Age", retirementAgeLbl, "-brand-light"), 2, 1);

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
        recalcBtn.setOnAction(e -> { collectAndSave(); refreshEarningsCard(); });

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
        earningsCard = startSectionCard("Future Earnings Until Retirement", "-brand-accent");
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
        addTableRow(card, "Equity Appreciation", formatRupees(futureEarnings.equityFvPaise()), false, false);
        addTableRow(card, "MF Appreciation",     formatRupees(futureEarnings.mfFvPaise()),     false, false);
        addTableRow(card, "Total Unrealized ROI", formatRupees(futureEarnings.unrealizedRoiSubtotalPaise()), true, false);
    }

    private void refreshEarningsCard() {
        futureEarnings = computeFutureEarnings();
        if (futureEarningsKpiLbl != null)
            futureEarningsKpiLbl.setText(formatCorpusDisplay(futureEarnings.totalPaise()));
        if (earningsCard != null) {
            // Keep header+divider (indices 0,1) and replace all content rows
            earningsCard.getChildren().subList(2, earningsCard.getChildren().size()).clear();
            populateEarningsCard(earningsCard);
        }
    }

    // ── Major Events ──────────────────────────────────────────────────────────

    private Region buildMajorEventsCard() {
        if (params.majorEvents == null) params.majorEvents = new ArrayList<>();

        VBox card = startSectionCard("Major Events", "#a78bfa");

        Label hint = new Label("Forecasted cost vs. actuals tracked from your transactions. Double-click an event to edit.");
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
        majorEventsForecastTotalLbl = new Label("₹0");
        majorEventsForecastTotalLbl.getStyleClass().addAll("fp-table-value", "fp-table-value-total");
        majorEventsForecastTotalLbl.setPrefWidth(110);
        majorEventsForecastTotalLbl.setAlignment(Pos.CENTER_RIGHT);
        majorEventsActualTotalLbl = new Label("₹0");
        majorEventsActualTotalLbl.getStyleClass().addAll("fp-table-value", "fp-table-value-total");
        majorEventsActualTotalLbl.setPrefWidth(110);
        majorEventsActualTotalLbl.setAlignment(Pos.CENTER_RIGHT);
        totalRow.getChildren().addAll(totalLbl, majorEventsForecastTotalLbl, majorEventsActualTotalLbl);
        card.getChildren().add(totalRow);

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
                .mapToLong(this::computeEventForecast).sum();
        long actualTotal = params.majorEvents.stream()
                .mapToLong(this::computeEventActual).sum();

        if (majorEventsForecastTotalLbl != null)
            majorEventsForecastTotalLbl.setText(formatRupees(forecastTotal));
        if (majorEventsActualTotalLbl != null)
            majorEventsActualTotalLbl.setText(formatRupees(actualTotal));
        if (majorEventsKpiLbl != null)
            majorEventsKpiLbl.setText(formatCorpusDisplay(computeMajorEventsKpi()));
    }

    private HBox createEventRow(MajorEvent event) {
        long forecast = computeEventForecast(event);
        long actual   = computeEventActual(event);

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
        forecastLbl.getStyleClass().add("fp-event-forecast");
        forecastLbl.setPrefWidth(110);
        forecastLbl.setAlignment(Pos.CENTER_RIGHT);

        Label actualLbl = new Label(formatRupees(actual));
        actualLbl.getStyleClass().add(actual > 0 ? "fp-event-actual" : "fp-event-actual-zero");
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

    // ── Major Event computations ──────────────────────────────────────────────

    /**
     * Total forecasted spend for the event from max(today, startDate) until endDate (or retirement).
     * ONE_TIME events return their stored amountPaise directly.
     * RECURRING events multiply amountPaise by the number of occurrences remaining.
     * If endDate is set on a RECURRING event it is used as-is, even if it extends beyond retirement.
     */
    private long computeEventForecast(MajorEvent event) {
        if (event.getType() == MajorEvent.EventType.ONE_TIME) {
            return event.getAmountPaise();
        }
        // RECURRING — upper bound: endDate if set (even beyond retirement), otherwise retirement date
        LocalDate upperBound = getRetirementDate();
        if (event.getEndDate() != null) {
            try { upperBound = LocalDate.parse(event.getEndDate()); }
            catch (Exception ignored) {}
        }
        LocalDate from = LocalDate.now();
        if (event.getStartDate() != null) {
            try {
                LocalDate sd = LocalDate.parse(event.getStartDate());
                if (sd.isAfter(from)) from = sd;
            } catch (Exception ignored) {}
        }
        if (!from.isBefore(upperBound)) return 0;
        long occurrences = switch (event.getFrequency() != null
                ? event.getFrequency() : MajorEvent.Frequency.MONTHLY) {
            case MONTHLY   -> java.time.temporal.ChronoUnit.MONTHS.between(from, upperBound);
            case QUARTERLY -> java.time.temporal.ChronoUnit.MONTHS.between(from, upperBound) / 3;
            case YEARLY    -> java.time.temporal.ChronoUnit.YEARS.between(from, upperBound);
        };
        return Math.max(0, occurrences) * event.getAmountPaise();
    }

    /**
     * Cumulative actual spend for the event: sum of EXPENSE transactions matching
     * the event's category (and sub-category if set) from the event's start date onwards.
     */
    private long computeEventActual(MajorEvent event) {
        if (event.getCategoryId() == null) return 0;
        LocalDate startDate = null;
        if (event.getStartDate() != null) {
            try { startDate = LocalDate.parse(event.getStartDate()); }
            catch (Exception ignored) {}
        }
        final LocalDate from = startDate;
        return DataStore.getInstance().getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE)
                .filter(t -> t.getClassification() != null)
                .filter(t -> event.getCategoryId().equals(t.getClassification().getCategoryId()))
                .filter(t -> event.getSubCategoryId() == null
                        || event.getSubCategoryId().equals(t.getClassification().getSubCategoryId()))
                .filter(t -> from == null || !t.getDate().isBefore(from))
                .mapToLong(Transaction::getAmountPaise)
                .sum();
    }

    /** Net remaining major-events budget: total forecast minus total actual. */
    private long computeMajorEventsKpi() {
        if (params == null || params.majorEvents == null) return 0;
        long forecast = params.majorEvents.stream().mapToLong(this::computeEventForecast).sum();
        long actual   = params.majorEvents.stream().mapToLong(this::computeEventActual).sum();
        return Math.max(0, forecast - actual);
    }

    /** Retirement date derived from plan parameters; never before today. */
    private LocalDate getRetirementDate() {
        LocalDate retireDate = (params.retirementDate != null)
                ? LocalDate.parse(params.retirementDate)
                : selfDob.plusYears(params.retirementAge);
        return retireDate.isBefore(LocalDate.now()) ? LocalDate.now() : retireDate;
    }

    // ── Expenses + Forecasted Corpus ──────────────────────────────────────────

    private Region buildExpensesAndForecastCard() {
        VBox card = startSectionCard("Expenses Until Retirement", "#f87171");

        LocalDate retireDate = getRetirementDate();
        LocalDate today      = LocalDate.now();

        // ── Expenses ──────────────────────────────────────────────────────────
        double yearsToRetire  = java.time.temporal.ChronoUnit.DAYS.between(today, retireDate) / 365.25;
        long   costOfLiving   = Math.round(params.costOfLivingPaise * yearsToRetire);
        long   loanPayments   = computeLoanPayments(retireDate);
        long   majorEventsNet = computeMajorEventsKpi();
        long   totalExpenses  = costOfLiving + loanPayments + majorEventsNet;

        addTableRow(card, "Loan Payments",  formatRupees(loanPayments),  false, true);
        addTableRow(card, "Cost of Living", formatRupees(costOfLiving),  false, true);
        addTableRow(card, "Major Events",   formatRupees(majorEventsNet), false, true);
        addTableRow(card, "Total Expenses", formatRupees(totalExpenses), true,  true);

        Region spacer = new Region();
        spacer.setPrefHeight(16);
        card.getChildren().add(spacer);

        Label subTitle = new Label("Forecasted Corpus at Retirement");
        subTitle.getStyleClass().add("text-section-title");
        card.getChildren().add(subTitle);

        // ── Corpus buckets ────────────────────────────────────────────────────
        long totalMonths = java.time.temporal.ChronoUnit.MONTHS.between(today, retireDate);

        long pfBalance = corpusBreakdown.pfPaise()
                       + futureEarnings.pfContribPaise()
                       + futureEarnings.pfInterestPaise();

        // equityFvPaise / mfFvPaise contain appreciation only (SIP principal was subtracted
        // during computation); add the SIP principals back here to get the full bucket value.
        long stocksMf  = corpusBreakdown.equityPaise()
                       + corpusBreakdown.mfPaise()
                       + futureEarnings.equityFvPaise()
                       + params.monthlySipEquityPaise * totalMonths
                       + futureEarnings.mfFvPaise()
                       + params.monthlySipMfPaise * totalMonths;

        long cashBondsFds = corpusBreakdown.bankPaise()
                          + corpusBreakdown.bondsPaise()
                          + corpusBreakdown.fdPaise()
                          + corpusBreakdown.rdPaise()
                          + futureEarnings.bondsInterestPaise()
                          + futureEarnings.fdInterestPaise()
                          + futureEarnings.rdInterestPaise()
                          + futureEarnings.postTaxIncomePaise()
                          + futureEarnings.gratuityPaise();

        long grossCorpus     = pfBalance + stocksMf + cashBondsFds;
        long totalForecasted = grossCorpus - totalExpenses;

        addTableRow(card, "PF Balance",            formatRupees(pfBalance),    false, false);
        addTableRow(card, "Stocks & MF",           formatRupees(stocksMf),     false, false);
        addTableRow(card, "Cash, Bonds, FDs etc.", formatRupees(cashBondsFds), false, false);
        addTableRow(card, "Total Forecasted Corpus",
                formatRupees(totalForecasted), true, totalForecasted < 0);

        // ── Surplus / shortfall pill ──────────────────────────────────────────
        boolean shortfall = totalForecasted < 0;
        String pillText = shortfall
                ? "⚠  Corpus shortfall: " + formatCorpusDisplay(Math.abs(totalForecasted))
                : "✓  Corpus surplus: "   + formatCorpusDisplay(totalForecasted);
        Label gapPill = new Label(pillText);
        gapPill.getStyleClass().add(shortfall ? "fp-gap-pill-warn" : "fp-gap-pill-ok");
        HBox pillRow = new HBox(gapPill);
        pillRow.setPadding(new Insets(10, 0, 0, 0));
        card.getChildren().add(pillRow);

        return card;
    }

    /** Sum of all remaining LOAN_PAYMENT recurring instalment amounts until {@code retireDate}. */
    private long computeLoanPayments(LocalDate retireDate) {
        long total = 0;
        for (RecurringTransaction rt : DataStore.getInstance().getRecurring()) {
            if (rt.getTransactionType() != Transaction.Type.LOAN_PAYMENT) continue;
            long occ = countOccurrences(rt, retireDate);
            total += rt.getAmountPaise() * occ;
        }
        return total;
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

    // ── Future earnings computation ───────────────────────────────────────────

    private FutureEarningsBreakdown computeFutureEarnings() {
        DataStore ds         = DataStore.getInstance();
        LocalDate today      = LocalDate.now();
        LocalDate retireDate = (params.retirementDate != null)
                ? LocalDate.parse(params.retirementDate)
                : selfDob.plusYears(params.retirementAge);   // fallback for legacy data
        if (retireDate.isBefore(today)) retireDate = today;
        double yearsToRetire  = ChronoUnit.DAYS.between(today, retireDate) / 365.25;
        long   totalMonths    = ChronoUnit.MONTHS.between(today, retireDate);
        final long ROUND      = 1_000_000L;

        // ── Post-tax Income ───────────────────────────────────────────────────
        Set<String> earningSchedIds = collectEarningScheduleIds(ds);
        long postTaxIncome = 0;
        for (RecurringTransaction rt : ds.getRecurring()) {
            if (rt.getTransactionType() != Transaction.Type.INCOME) continue;
            long occ = countOccurrences(rt, retireDate);
            if (occ <= 0) continue;
            long perOcc = earningSchedIds.contains(rt.getId())
                    ? rt.getAmountPaise()
                    : Math.round(rt.getAmountPaise() * (1.0 - params.preRetireTaxPct / 100.0));
            postTaxIncome += perOcc * occ;
        }

        // ── PF Contributions ──────────────────────────────────────────────────
        long pfContrib        = 0;
        long monthlyPfDeposit = 0;
        for (FamilyMember m : ds.getFamilyMembers()) {
            if (!m.isEarning() || !m.hasEarningsConfigured()) continue;
            for (EarningSource es : m.getEarningSources()) {
                if (es.getType() != FamilyMember.EarningType.SALARY) continue;
                if (es.getPfScheduleId() == null) continue;
                RecurringTransaction pfSched = ds.getRecurring().stream()
                        .filter(r -> es.getPfScheduleId().equals(r.getId()))
                        .findFirst().orElse(null);
                if (pfSched == null || pfSched.getAmountPaise() <= 0) continue;
                long occ = countOccurrences(pfSched, retireDate);
                pfContrib        += pfSched.getAmountPaise() * occ;
                monthlyPfDeposit += pfSched.getAmountPaise(); // PF schedules are monthly
            }
        }

        // ── Gratuity ──────────────────────────────────────────────────────────
        long gratuity = 0;
        if (params.employmentStartDate != null) {
            try {
                LocalDate empStart   = LocalDate.parse(params.employmentStartDate);
                long serviceYears    = ChronoUnit.YEARS.between(empStart, retireDate);
                if (serviceYears > 0) {
                    for (FamilyMember m : ds.getFamilyMembers()) {
                        if (!m.isEarning() || !m.hasEarningsConfigured()) continue;
                        for (EarningSource es : m.getEarningSources()) {
                            if (es.getType() != FamilyMember.EarningType.SALARY) continue;
                            if (!es.isGratuityEnabled()) continue;
                            long monthlyBasic = es.getBasicDaPaise() / 12;
                            long g = Math.round(monthlyBasic * 15.0 * serviceYears / 26.0);
                            gratuity += Math.min(g, 200_000_000L); // capped at ₹20 lakh in paise
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // ── PF Interest (month-by-month simulation) ───────────────────────────
        long pfBalance = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() == InvestmentAccount.InvestmentType.PROVIDENT_FUND)
                pfBalance += ds.getInvestedPaiseAsOf(ia, today);
        }
        long pfStartBalance  = pfBalance;
        double pfMonthlyRate = params.rorPfPct / 100.0 / 12.0;
        for (long mo = 0; mo < totalMonths; mo++) {
            pfBalance += Math.round(pfBalance * pfMonthlyRate); // interest first
            pfBalance += monthlyPfDeposit;                       // then deposit
        }
        long pfInterest = Math.max(0, pfBalance - pfStartBalance - (monthlyPfDeposit * totalMonths));

        // ── Bonds Interest ────────────────────────────────────────────────────
        long bondsInterest = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.DEBT_BONDS) continue;
            // Maturity gain from INVESTMENT transactions
            for (Transaction t : ds.getTransactions()) {
                if (t.getType() != Transaction.Type.INVESTMENT) continue;
                if (!ia.getId().equals(t.getToAccountId())) continue;
                if (t.getInvestmentDetails() == null) continue;
                Transaction.FdDetails fd = t.getInvestmentDetails().getFd();
                if (fd == null || fd.getMaturityAmountPaise() == null) continue;
                if (fd.getMaturityDate() != null && fd.getMaturityDate().isAfter(retireDate)) continue;
                bondsInterest += fd.getMaturityAmountPaise() - t.getAmountPaise();
            }
            // Interest income recurring schedules directed to this account
            for (RecurringTransaction rt : ds.getRecurring()) {
                if (rt.getTransactionType() != Transaction.Type.INCOME) continue;
                if (!ia.getId().equals(rt.getToAccountId())) continue;
                bondsInterest += rt.getAmountPaise() * countOccurrences(rt, retireDate);
            }
        }

        // ── FD Interest ───────────────────────────────────────────────────────
        // Collect FD ref numbers that have already been redeemed
        Set<String> redeemedFdRefs = ds.getTransactions().stream()
                .filter(tx -> tx.getRedeemDetails() != null
                        && tx.getRedeemDetails().getOrgnlFDRef() != null)
                .map(tx -> tx.getRedeemDetails().getOrgnlFDRef())
                .collect(Collectors.toSet());

        long fdInterest = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.FIXED_DEPOSIT) continue;
            // Maturity gain from INVESTMENT transactions
            for (Transaction t : ds.getTransactions()) {
                if (t.getType() != Transaction.Type.INVESTMENT) continue;
                if (!ia.getId().equals(t.getToAccountId())) continue;
                if (t.getInvestmentDetails() == null) continue;
                Transaction.FdDetails fd = t.getInvestmentDetails().getFd();
                if (fd == null || fd.getMaturityAmountPaise() == null) continue;
                // Skip if this FD has already been redeemed
                if (fd.getRef() != null && redeemedFdRefs.contains(fd.getRef())) continue;
                fdInterest += fd.getMaturityAmountPaise() - t.getAmountPaise();
            }
            // Interest income recurring schedules directed to this account
            for (RecurringTransaction rt : ds.getRecurring()) {
                if (rt.getTransactionType() != Transaction.Type.INCOME) continue;
                if (!ia.getId().equals(rt.getToAccountId())) continue;
                fdInterest += rt.getAmountPaise() * countOccurrences(rt, retireDate);
            }
        }
        // Apply pre-retirement tax rate to FD interest (taxable as income)
        fdInterest = Math.round(fdInterest * (1.0 - params.preRetireTaxPct / 100.0));

        // ── RD Interest ───────────────────────────────────────────────────────
        // ── RD Interest ───────────────────────────────────────────────────────
        Set<String> rdAccountIds = ds.getInvestmentAccounts().stream()
                .filter(ia -> ia.getInvestmentType() == InvestmentAccount.InvestmentType.RECURRING_DEPOSIT
                        && ia.getInvestmentStatus() != InvestmentAccount.InvestmentStatus.REDEEMED)
                .map(InvestmentAccount::getId)
                .collect(Collectors.toSet());

        long rdInterest = 0;
        for (RecurringTransaction rt : ds.getRecurring()) {
            if (rt.getTransactionType() != Transaction.Type.INVESTMENT) continue;
            if (!rdAccountIds.contains(rt.getToAccountId())) continue;
            if (rt.getRdMaturityAmountPaise() <= 0) continue;
            long numPayments;
            if (rt.getNumberOfPayments() != null && rt.getNumberOfPayments() > 0) {
                numPayments = rt.getNumberOfPayments();
            } else if (rt.getStartDate() != null && rt.getMaturityDate() != null) {
                numPayments = ChronoUnit.MONTHS.between(rt.getStartDate(), rt.getMaturityDate());
            } else {
                continue; // cannot determine principal without payment count
            }
            long totalPrincipal = numPayments * rt.getAmountPaise();
            rdInterest += Math.max(0, rt.getRdMaturityAmountPaise() - totalPrincipal);
        }
        // Apply pre-retirement tax rate to RD interest (taxable as income)
        rdInterest = Math.round(rdInterest * (1.0 - params.preRetireTaxPct / 100.0));

        // ── Equity Future Value ───────────────────────────────────────────────
        long equityPv = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.EQUITY) continue;
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
            equityPv += mv != null ? mv.getMarketValuePaise() : ds.getInvestedPaiseAsOf(ia, today);
        }
        double equityRate = params.rorEquitiesPct / 100.0;
        long equityFv = Math.round(equityPv * Math.pow(1 + equityRate, yearsToRetire))
                + computeSipFv(params.monthlySipEquityPaise, equityRate, totalMonths)
                - equityPv
                - params.monthlySipEquityPaise * totalMonths;

        // ── MF Future Value ───────────────────────────────────────────────────
        long mfPv = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentType() != InvestmentAccount.InvestmentType.MUTUAL_FUNDS) continue;
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
            mfPv += mv != null ? mv.getMarketValuePaise() : ds.getInvestedPaiseAsOf(ia, today);
        }
        double mfRate = params.rorMfPct / 100.0;
        long mfFv = Math.round(mfPv * Math.pow(1 + mfRate, yearsToRetire))
                + computeSipFv(params.monthlySipMfPaise, mfRate, totalMonths)
                - mfPv
                - params.monthlySipMfPaise * totalMonths;

        // ── Round and assemble ────────────────────────────────────────────────
        long rIncome    = floorRound(postTaxIncome,              ROUND);
        long rPfContrib = floorRound(pfContrib,                  ROUND);
        long rGratuity  = floorRound(gratuity,                   ROUND);
        long rPfInt     = floorRound(pfInterest,                 ROUND);
        long rBondsInt  = floorRound(Math.max(0, bondsInterest), ROUND);
        long rFdInt     = floorRound(Math.max(0, fdInterest),    ROUND);
        long rRdInt     = floorRound(Math.max(0, rdInterest),    ROUND);
        long rEquityFv  = floorRound(Math.max(0, equityFv),      ROUND);
        long rMfFv      = floorRound(Math.max(0, mfFv),          ROUND);

        long earnSub     = rIncome + rPfContrib + rGratuity + rPfInt;
        long realizedSub = rBondsInt + rFdInt + rRdInt;
        long unrlzdSub   = rEquityFv + rMfFv;
        long total       = earnSub + realizedSub + unrlzdSub;

        return new FutureEarningsBreakdown(
                rIncome, rPfContrib, rGratuity, rPfInt, earnSub,
                rBondsInt, rFdInt, rRdInt, realizedSub,
                rEquityFv, rMfFv, unrlzdSub, total);
    }

    private Set<String> collectEarningScheduleIds(DataStore ds) {
        Set<String> ids = new HashSet<>();
        for (FamilyMember m : ds.getFamilyMembers()) {
            if (!m.isEarning() || !m.hasEarningsConfigured()) continue;
            for (EarningSource es : m.getEarningSources()) {
                if (es.getRecurringScheduleId() != null)
                    ids.add(es.getRecurringScheduleId());
            }
        }
        return ids;
    }

    /**
     * Counts future occurrences of a recurring schedule from today until {@code until}.
     * Respects endDate, numberOfPayments, and active status.
     */
    private long countOccurrences(RecurringTransaction rt, LocalDate until) {
        if (rt.getStatus() != RecurringTransaction.Status.ACTIVE) return 0;
        if (rt.getAmountPaise() <= 0) return 0;
        LocalDate from          = LocalDate.now();
        LocalDate effectiveEnd  = (rt.getEndDate() != null && rt.getEndDate().isBefore(until))
                                  ? rt.getEndDate() : until;
        if (from.isAfter(effectiveEnd)) return 0;
        long count = switch (rt.getFrequency()) {
            case MONTHLY        -> ChronoUnit.MONTHS.between(from, effectiveEnd);
            case QUARTERLY      -> ChronoUnit.MONTHS.between(from, effectiveEnd) / 3;
            case ANNUALLY       -> ChronoUnit.YEARS.between(from, effectiveEnd);
            case ALTERNATE_YEAR -> ChronoUnit.YEARS.between(from, effectiveEnd) / 2;
        };
        if (rt.getNumberOfPayments() != null) {
            long remaining = Math.max(0L, rt.getNumberOfPayments() - rt.getPaymentsMade());
            count = Math.min(count, remaining);
        }
        return Math.max(0, count);
    }

    /** Future value of a monthly SIP annuity: FV = PMT × [(1+r)^n − 1] / r */
    private long computeSipFv(long monthlyPaise, double annualRate, long months) {
        if (monthlyPaise <= 0 || months <= 0) return 0;
        double r = annualRate / 12.0;
        if (r == 0) return monthlyPaise * months;
        return Math.round(monthlyPaise * (Math.pow(1 + r, months) - 1) / r);
    }

    private long floorRound(long value, long unit) {
        return Math.floorDiv(value, unit) * unit;
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
