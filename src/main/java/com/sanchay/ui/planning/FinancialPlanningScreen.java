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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class FinancialPlanningScreen {

    private ScrollPane view;
    private final Runnable navigateToProfile;

    private PlanParamsService paramsService;
    private final FinancialPlanningCalculator planningCalculator;
    private final MajorEventPlanner majorEventPlanner;
    private PlanParameters    params;
    private double            currentAgeDecimal;
    private LocalDate         selfDob;

    private Label lastUpdatedLbl;
    private final FutureEarningsSection futureEarningsSection;
    private Label futureEarningsKpiLbl;
    private Label forecastedCorpusKpiLbl;

    private final ForecastedExpensesSection forecastedExpensesSection;
    private final MajorEventsSection majorEventsSection;
    private final ForecastedCorpusSection forecastedCorpusSection;
    private final CorpusDeltaSection corpusDeltaSection;
    private long  forecastedCorpusPaise;
    private PostRetirementProjectionPanel postRetirementPanel;

    private PlanningSnapshot planningSnapshot;

    public FinancialPlanningScreen(Runnable navigateToProfile) {
        this.navigateToProfile = navigateToProfile;
        this.majorEventPlanner = new MajorEventPlanner();
        this.planningCalculator = new FinancialPlanningCalculator();
        this.postRetirementPanel = new PostRetirementProjectionPanel(
                planningCalculator,
                this::formatRupees,
                () -> {
                    paramsService.save(params);
                    refreshComputedSections();
                }
        );
        this.forecastedExpensesSection = new ForecastedExpensesSection(
                planningCalculator,
                this::formatRupees
        );
        this.majorEventsSection = new MajorEventsSection(
                planningCalculator,
                majorEventPlanner,
                this::formatRupees,
                () -> paramsService.save(params),
                this::openEditDialog,
                this::refreshForecastCard
        );
        this.corpusDeltaSection = new CorpusDeltaSection(planningCalculator, this::formatRupees);
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
            Platform.runLater(this::showProfileIncompleteError);
            view = new ScrollPane(new Label(""));
            return;
        }

        selfDob = self.getDateOfBirth();
        currentAgeDecimal = ChronoUnit.DAYS.between(selfDob, LocalDate.now()) / 365.25;

        AppConfig.Config cfg = AppConfig.read();
        paramsService = new PlanParamsService(cfg.dataFolderPath);
        params        = paramsService.load();

        planningSnapshot = computePlanningSnapshot();

        Node paramsCard = new PlanParametersPanel(
                params, selfDob, currentAgeDecimal,
                () -> paramsService.save(params),
                () -> {
                    refreshComputedSections();
                    if (lastUpdatedLbl != null)
                        lastUpdatedLbl.setText(formatLastUpdated(params.lastCalculatedDate));
                }
        ).build();

        VBox content = new VBox(20);
        content.getStyleClass().add("main-panel");
        content.setPadding(new Insets(28));
        content.getChildren().addAll(
                buildHeader(),
                buildKpiGrid(),
                paramsCard,
                buildTwoCol(buildCorpusCard(), buildEarningsCard()),
                buildMajorEventsCard(),
                buildTwoCol(buildExpensesCard(), buildForecastedCorpusCard()),
                buildCorpusDeltaCard(),
                buildPostRetirementCard()
        );

        view = new ScrollPane(content);
        view.setFitToWidth(true);
        view.setFitToHeight(false);
        view.getStyleClass().add("scroll-page-bg");
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private Node buildHeader() {
        Label title = new Label("Financial Plan");
        title.getStyleClass().add("screen-title");

        Label subtitle = new Label("Retirement & wealth projection");
        subtitle.getStyleClass().add("dialog-subtitle");

        lastUpdatedLbl = new Label(formatLastUpdated(params.lastCalculatedDate));
        lastUpdatedLbl.getStyleClass().add("fp-last-updated");

        Label helpHintLbl = new Label("Check Help to understand how these amounts are calculated.");
        helpHintLbl.getStyleClass().add("fp-last-updated");
        helpHintLbl.setAlignment(Pos.CENTER_RIGHT);

        VBox rightBlock = new VBox(2, lastUpdatedLbl, helpHintLbl);
        rightBlock.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(new VBox(2, title, subtitle), spacer, rightBlock);
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

        forecastedCorpusKpiLbl = new Label("");
        forecastedCorpusKpiLbl.getStyleClass().addAll("card-value");
        grid.add(kpiCardWithLabel("Forecasted Retirement Corpus", forecastedCorpusKpiLbl, "-brand-accent"), 2, 0);

        return grid;
    }

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

    // ── Section cards ─────────────────────────────────────────────────────────

    private Region buildCorpusCard() {
        return new CorpusSectionCard().build(planningSnapshot.corpusBreakdown());
    }

    private Region buildEarningsCard() {
        Region card = futureEarningsSection.build();
        futureEarningsSection.refresh(planningSnapshot.futureEarnings(), futureEarningsKpiLbl);
        return card;
    }

    private Region buildExpensesCard() {
        return forecastedExpensesSection.build(params, selfDob);
    }

    private Region buildMajorEventsCard() {
        return majorEventsSection.build(params, selfDob);
    }

    private Region buildForecastedCorpusCard() {
        Region card = forecastedCorpusSection.build();
        refreshForecastCard();
        return card;
    }

    private Region buildCorpusDeltaCard() {
        Region card = corpusDeltaSection.build();
        corpusDeltaSection.refresh(params, selfDob);
        return card;
    }

    private Node buildPostRetirementCard() {
        postRetirementPanel = new PostRetirementProjectionPanel(
                planningCalculator,
                this::formatRupees,
                () -> {
                    paramsService.save(params);
                    refreshComputedSections();
                }
        );
        Region panel = postRetirementPanel.build();
        postRetirementPanel.updateInputs(params, selfDob, forecastedCorpusPaise);
        return panel;
    }

    // ── Refresh orchestration ─────────────────────────────────────────────────

    private void refreshComputedSections() {
        applyPlanningSnapshot(computePlanningSnapshot());
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
        forecastedExpensesSection.refresh(params, selfDob);
        majorEventsSection.refresh(params, selfDob);
        corpusDeltaSection.refresh(params, selfDob);
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
        forecastedExpensesSection.refresh(params, selfDob);
        majorEventsSection.refresh(params, selfDob);
        corpusDeltaSection.refresh(params, selfDob);
        populateCorpusCard();
    }

    private void openEditDialog(MajorEvent event) {
        MajorEventDialog dlg = new MajorEventDialog(event);
        MajorEventDialog.Outcome outcome = dlg.show();
        if (outcome == MajorEventDialog.Outcome.SAVED) {
            paramsService.save(params);
            refreshForecastCard();
        } else if (outcome == MajorEventDialog.Outcome.DELETED) {
            params.majorEvents.remove(event);
            paramsService.save(params);
            refreshForecastCard();
        }
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private HBox buildTwoCol(Region left, Region right) {
        HBox row = new HBox(16, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setMaxWidth(Double.MAX_VALUE);
        right.setMaxWidth(Double.MAX_VALUE);

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

    // ── Utility ───────────────────────────────────────────────────────────────

    private String formatRupees(long paise) {
        return MoneyFormatter.formatNoDecimal(paise);
    }

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
