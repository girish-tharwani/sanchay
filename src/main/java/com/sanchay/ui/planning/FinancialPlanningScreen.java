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

    // ── Future earnings breakdown (computed once per buildView) ───────────────
    private PlanningSnapshot planningSnapshot;

    // ── Live-updatable UI handles ─────────────────────────────────────────────
    private Label lastUpdatedLbl;
    private final FutureEarningsSection futureEarningsSection;
    private Label futureEarningsKpiLbl;
    private Label forecastedCorpusKpiLbl;

    // ── Major Events (live-updatable) ─────────────────────────────────────────

    // ── Expenses card (live-updatable expense rows) ───────────────────────────

    // ── Forecasted Corpus card (live-updatable) ───────────────────────────────
    private final ExpensesMajorEventsSection expensesMajorEventsSection;
    private final ForecastedCorpusSection forecastedCorpusSection;
    private long  forecastedCorpusPaise;
    private PostRetirementProjectionPanel postRetirementPanel;

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
        this.postRetirementPanel = new PostRetirementProjectionPanel(planningCalculator, this::formatRupees);
        this.expensesMajorEventsSection = new ExpensesMajorEventsSection(
                planningCalculator,
                majorEventPlanner,
                this::formatRupees,
                () -> paramsService.save(params),
                this::openEditDialog,
                this::refreshForecastCard
        );
        this.futureEarningsSection = new FutureEarningsSection(this::formatRupees);
        this.forecastedCorpusSection = new ForecastedCorpusSection(
                planningCalculator,
                this::formatRupees,
                total -> {
                    forecastedCorpusPaise = total;
                    if (postRetirementPanel != null) {
                        postRetirementPanel.updateInputs(params, selfDob, total);
                    }
                }
        );
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
        planningSnapshot = computePlanningSnapshot();

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

            yearsToRetireLbl.setText(String.format("%.2f", toRetire));
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

        grid.add(kpiCard("Current Corpus", UiUtils.formatCorpusDisplay(planningSnapshot.corpusBreakdown().totalPaise()), "-brand-light", false, false), 0, 0);

        futureEarningsKpiLbl = new Label(UiUtils.formatCorpusDisplay(planningSnapshot.futureEarnings().totalPaise()));
        futureEarningsKpiLbl.getStyleClass().add("card-value");
        grid.add(kpiCardWithLabel("Future Earnings", futureEarningsKpiLbl, "-brand-light"), 1, 0);

        // Initialised with "" — populateCorpusCard() sets the real value immediately after buildView()
        forecastedCorpusKpiLbl = new Label("");
        forecastedCorpusKpiLbl.getStyleClass().addAll("card-value");
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

        VBox card = new VBox(8, lbl, val);
        card.setMaxWidth(Double.MAX_VALUE);
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
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("card-summary");
        // Inline required: stripe colour is data-driven per card
        card.setStyle("-fx-background-color: " + stripeColor + ", white; "
                    + "-fx-background-insets: 0, 0 0 0 3;");
        return card;
    }

    // ── Plan Parameters ───────────────────────────────────────────────────────

    private Node buildParamsCard() {
        Label chevron = new Label("▾");
        chevron.getStyleClass().add("fp-chevron");

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

        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(chevron, title, spacer, recalcBtn);

        Region divider = new Region();
        divider.getStyleClass().add("content-divider");
        divider.setMaxWidth(Double.MAX_VALUE);

        GridPane paramGrid = buildParamGrid();
        // Start collapsed
        paramGrid.setVisible(false);
        paramGrid.setManaged(false);
        divider.setVisible(false);
        divider.setManaged(false);
        recalcBtn.setVisible(false);
        recalcBtn.setManaged(false);
        chevron.setText("▸");

        VBox card = new VBox(12, header, divider, paramGrid);
        card.getStyleClass().add("card");

        chevron.setOnMouseClicked(e -> {
            boolean expanding = !paramGrid.isManaged(); // currently collapsed → expanding
            paramGrid.setVisible(expanding);
            paramGrid.setManaged(expanding);
            divider.setVisible(expanding);
            divider.setManaged(expanding);
            recalcBtn.setVisible(expanding);
            recalcBtn.setManaged(expanding);
            chevron.setText(expanding ? "▾" : "▸");
        });

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

        FinancialPlanningCalculator.CorpusBreakdown corpusBreakdown = planningSnapshot.corpusBreakdown();
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
        Region card = futureEarningsSection.build();
        futureEarningsSection.refresh(planningSnapshot.futureEarnings(), futureEarningsKpiLbl);
        return card;
    }


    private void refreshComputedSections() {
        applyPlanningSnapshot(computePlanningSnapshot());
    }

    // ── Expenses card (Expenses table + Major Events table) ──────────────────

    private Region buildExpensesCard() {
        return expensesMajorEventsSection.build(params, selfDob);
    }


    private void openEditDialog(MajorEvent event) {
        MajorEventDialog dlg = new MajorEventDialog(event);
        MajorEventDialog.Outcome outcome = dlg.show();
        if (outcome == MajorEventDialog.Outcome.SAVED) {
            // event was mutated in place by MajorEventDialog.validate()
            paramsService.save(params);
            refreshForecastCard();
        } else if (outcome == MajorEventDialog.Outcome.DELETED) {
            params.majorEvents.remove(event);
            paramsService.save(params);
            refreshForecastCard();
        }
    }



    // ── Forecasted Corpus card ────────────────────────────────────────────────

    private Region buildForecastedCorpusCard() {
        Region card = forecastedCorpusSection.build();
        refreshForecastCard();
        return card;
    }

    private PlanningSnapshot computePlanningSnapshot() {
        return new PlanningSnapshot(
                planningCalculator.computeCorpusBreakdown(),
                planningCalculator.computeFutureEarnings(params, selfDob)
        );
    }

    private void applyPlanningSnapshot(PlanningSnapshot snapshot) {
        planningSnapshot = snapshot;
        futureEarningsSection.refresh(snapshot.futureEarnings(), futureEarningsKpiLbl);
        forecastedCorpusSection.refresh(
                snapshot.corpusBreakdown(),
                snapshot.futureEarnings(),
                new ForecastedCorpusSection.PlanInputs(params, selfDob),
                forecastedCorpusKpiLbl
        );
        expensesMajorEventsSection.refresh(params, selfDob);
    }

    private void populateCorpusCard() {
        forecastedCorpusSection.refresh(
                planningSnapshot.corpusBreakdown(),
                planningSnapshot.futureEarnings(),
                new ForecastedCorpusSection.PlanInputs(params, selfDob),
                forecastedCorpusKpiLbl
        );
    }

    private void refreshForecastCard() {
        expensesMajorEventsSection.refresh(params, selfDob);
        populateCorpusCard();
    }

    // ── Post-Retirement Projection ────────────────────────────────────────────

    private Node buildPostRetirementCard() {
        postRetirementPanel = new PostRetirementProjectionPanel(planningCalculator, this::formatRupees);
        Region panel = postRetirementPanel.build();
        postRetirementPanel.updateInputs(params, selfDob, forecastedCorpusPaise);
        return panel;
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

    private record PlanningSnapshot(
            FinancialPlanningCalculator.CorpusBreakdown corpusBreakdown,
            FinancialPlanningCalculator.FutureEarningsBreakdown futureEarnings
    ) {}
}
