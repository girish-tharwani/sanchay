package com.sanchay.ui.planning;

import com.sanchay.model.PlanParameters;
import com.sanchay.service.FinancialPlanningCalculator;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * Owns the forecasted corpus card and exposes the computed total for downstream consumers.
 */
final class ForecastedCorpusSection {

    private final FinancialPlanningCalculator planningCalculator;
    private final LongFunction<String> moneyFormatter;
    private final Consumer<Long> onTotalChanged;

    private PlanningSectionCard card;

    ForecastedCorpusSection(
            FinancialPlanningCalculator planningCalculator,
            LongFunction<String> moneyFormatter,
            Consumer<Long> onTotalChanged
    ) {
        this.planningCalculator = planningCalculator;
        this.moneyFormatter = moneyFormatter;
        this.onTotalChanged = onTotalChanged;
    }

    VBox build() {
        card = new PlanningSectionCard("Forecasted Corpus at Retirement", "#86efac");
        return card.getRoot();
    }

    void refresh(
            FinancialPlanningCalculator.CorpusBreakdown corpusBreakdown,
            FinancialPlanningCalculator.FutureEarningsBreakdown futureEarnings,
            PlanInputs inputs,
            Label forecastedCorpusKpiLbl
    ) {
        if (card == null) {
            return;
        }

        VBox body = card.getBody();
        body.getChildren().clear();

        FinancialPlanningCalculator.ForecastedCorpusBreakdown forecastedCorpus =
                planningCalculator.computeForecastedCorpusBreakdown(
                        inputs.params(), inputs.selfDob(), corpusBreakdown, futureEarnings);
        long totalForecasted = forecastedCorpus.totalPaise();

        if (forecastedCorpusKpiLbl != null) {
            forecastedCorpusKpiLbl.setText(UiUtils.formatCorpusDisplay(totalForecasted));
        }
        onTotalChanged.accept(totalForecasted);

        addTableRow(body, "PF Balance", moneyFormatter.apply(forecastedCorpus.pfBalancePaise()), false, false);
        addTableRow(body, "Stocks & MF", moneyFormatter.apply(forecastedCorpus.stocksMfPaise()), false, false);
        addTableRow(body, "Cash, Bonds, FDs etc.", moneyFormatter.apply(forecastedCorpus.cashBondsFdsPaise()), false, false);
        addTableRow(body, "Total Forecasted Corpus", moneyFormatter.apply(totalForecasted), true, totalForecasted < 0);

        long requiredCorpus = planningCalculator.computeRequiredCorpusPaise(inputs.params(), inputs.selfDob());
        long delta = totalForecasted - requiredCorpus;
        boolean isShortfall = delta < 0;

        Label kindLbl = new Label(isShortfall
                ? "Projected Shortfall vs Required Corpus"
                : "Projected Surplus vs Required Corpus");
        kindLbl.getStyleClass().add("fp-corpus-pill-kind");

        Label amountLbl = new Label(UiUtils.formatCorpusDisplay(Math.abs(delta)));
        amountLbl.getStyleClass().add("fp-corpus-pill-amount");

        VBox pillBox = new VBox(4, kindLbl, amountLbl);
        pillBox.getStyleClass().addAll(
                "fp-corpus-pill",
                isShortfall ? "fp-corpus-pill-shortfall" : "fp-corpus-pill-excess");
        pillBox.setAlignment(Pos.CENTER);
        pillBox.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(pillBox, new Insets(16, 0, 4, 0));
        body.getChildren().add(pillBox);
    }

    private void addTableRow(VBox parent, String label, String value, boolean total, boolean negative) {
        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox();
        row.getStyleClass().add(total ? "fp-table-row-total" : "fp-table-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        Label lblNode = new Label(label);
        lblNode.getStyleClass().add(total ? "fp-table-label-total" : "fp-table-label");
        lblNode.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.layout.HBox.setHgrow(lblNode, javafx.scene.layout.Priority.ALWAYS);

        Label valNode = new Label(value);
        valNode.getStyleClass().add("fp-table-value");
        if (total) {
            valNode.getStyleClass().add("fp-table-value-total");
        }
        if (negative) {
            valNode.getStyleClass().add("fp-table-value-negative");
        }
        valNode.setMinWidth(Label.USE_PREF_SIZE);

        row.getChildren().addAll(lblNode, valNode);
        parent.getChildren().add(row);
    }

    record PlanInputs(PlanParameters params, LocalDate selfDob) {}
}
