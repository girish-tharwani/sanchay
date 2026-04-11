package com.sanchay.ui.planning;

import com.sanchay.service.FinancialPlanningCalculator;
import com.sanchay.ui.UiUtils;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.LongFunction;

/**
 * Owns the future earnings card and its KPI value.
 */
final class FutureEarningsSection {

    private final LongFunction<String> moneyFormatter;

    private PlanningSectionCard card;

    FutureEarningsSection(LongFunction<String> moneyFormatter) {
        this.moneyFormatter = moneyFormatter;
    }

    VBox build() {
        card = new PlanningSectionCard("Future Earnings", "-brand-accent");
        return card.getRoot();
    }

    void refresh(
            FinancialPlanningCalculator.FutureEarningsBreakdown futureEarnings,
            Label futureEarningsKpiLbl
    ) {
        if (card == null || futureEarnings == null) {
            return;
        }

        if (futureEarningsKpiLbl != null) {
            futureEarningsKpiLbl.setText(UiUtils.formatCorpusDisplay(futureEarnings.totalPaise()));
        }

        VBox body = card.getBody();
        body.getChildren().clear();

        addTableSubHeader(body, "Earnings");
        addTableRow(body, "Post-tax Income", moneyFormatter.apply(futureEarnings.postTaxIncomePaise()), false, false);
        addTableRow(body, "PF Contribution", moneyFormatter.apply(futureEarnings.pfContribPaise()), false, false);
        addTableRow(body, "Gratuity", moneyFormatter.apply(futureEarnings.gratuityPaise()), false, false);
        addTableRow(body, "PF Interest", moneyFormatter.apply(futureEarnings.pfInterestPaise()), false, false);
        addTableRow(body, "Subtotal - Earnings", moneyFormatter.apply(futureEarnings.earningsSubtotalPaise()), true, false);

        addTableSubHeader(body, "Realized ROI");
        addTableRow(body, "Bonds Interest", moneyFormatter.apply(futureEarnings.bondsInterestPaise()), false, false);
        addTableRow(body, "FDs Interest", moneyFormatter.apply(futureEarnings.fdInterestPaise()), false, false);
        addTableRow(body, "RDs Interest", moneyFormatter.apply(futureEarnings.rdInterestPaise()), false, false);
        addTableRow(body, "Total Realized ROI", moneyFormatter.apply(futureEarnings.realizedRoiSubtotalPaise()), true, false);

        addTableSubHeader(body, "Unrealized ROI (Appreciation)");
        addTableRow(body, "Equity Appreciation", moneyFormatter.apply(futureEarnings.equityApprecPaise()), false, false);
        addTableRow(body, "MF Appreciation", moneyFormatter.apply(futureEarnings.mfApprecPaise()), false, false);
        addTableRow(body, "Total Unrealized ROI", moneyFormatter.apply(futureEarnings.unrealizedRoiSubtotalPaise()), true, false);
    }

    private void addTableSubHeader(VBox parent, String label) {
        Label lbl = new Label(label.toUpperCase());
        lbl.getStyleClass().add("section-group-label");
        VBox.setMargin(lbl, new javafx.geometry.Insets(10, 0, 2, 0));
        parent.getChildren().add(lbl);
    }

    private void addTableRow(VBox parent, String label, String value, boolean total, boolean negative) {
        HBox row = new HBox();
        row.getStyleClass().add(total ? "fp-table-row-total" : "fp-table-row");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        Label lblNode = new Label(label);
        lblNode.getStyleClass().add(total ? "fp-table-label-total" : "fp-table-label");
        lblNode.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblNode, Priority.ALWAYS);

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
}
