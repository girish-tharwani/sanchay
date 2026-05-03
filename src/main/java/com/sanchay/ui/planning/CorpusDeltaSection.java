package com.sanchay.ui.planning;

import com.sanchay.model.PlanParameters;
import com.sanchay.service.FinancialPlanningCalculator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.function.LongFunction;

final class CorpusDeltaSection {

    private final FinancialPlanningCalculator planningCalculator;
    private final LongFunction<String> moneyFormatter;

    private VBox root;

    CorpusDeltaSection(FinancialPlanningCalculator planningCalculator, LongFunction<String> moneyFormatter) {
        this.planningCalculator = planningCalculator;
        this.moneyFormatter = moneyFormatter;
    }

    VBox build() {
        root = new VBox();
        root.getStyleClass().add("card");
        return root;
    }

    void refresh(PlanParameters params, LocalDate selfDob) {
        if (root == null) {
            return;
        }

        VBox body = root;
        body.getChildren().clear();

        long totalForecasted = planningCalculator.computeForecastedCorpusBreakdown(
                params, selfDob, planningCalculator.computeCorpusBreakdown(), 
                planningCalculator.computeFutureEarnings(params, selfDob)
        ).totalPaise();

        long requiredCorpus = planningCalculator.computeRequiredCorpusPaise(params, selfDob);
        long delta = totalForecasted - requiredCorpus;
        boolean isShortfall = delta < 0;

        Label kindLbl = new Label(isShortfall
                ? "Projected Shortfall vs Required Corpus"
                : "Projected Surplus vs Required Corpus");
        kindLbl.getStyleClass().add("fp-corpus-pill-kind");

        Label amountLbl = new Label(moneyFormatter.apply(Math.abs(delta)));
        amountLbl.getStyleClass().add("fp-corpus-pill-amount");

        VBox pillBox = new VBox(4, kindLbl, amountLbl);
        pillBox.getStyleClass().addAll(
                "fp-corpus-pill",
                isShortfall ? "fp-corpus-pill-shortfall" : "fp-corpus-pill-excess");
        pillBox.setAlignment(Pos.CENTER);
        pillBox.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(pillBox, new Insets(0, 0, 0, 0));
        body.getChildren().add(pillBox);
    }
}
