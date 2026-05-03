package com.sanchay.ui.planning;

import com.sanchay.model.PlanParameters;
import com.sanchay.service.FinancialPlanningCalculator;
import com.sanchay.service.MoneyFormatter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.function.LongFunction;

/**
 * Owns the expenses-until-retirement card.
 */
final class ForecastedExpensesSection {

    private final FinancialPlanningCalculator planningCalculator;
    private final LongFunction<String> moneyFormatter;

    private PlanParameters params;
    private LocalDate selfDob;

    private VBox root;
    private VBox expenseRowsContainer;

    ForecastedExpensesSection(
            FinancialPlanningCalculator planningCalculator,
            LongFunction<String> moneyFormatter
    ) {
        this.planningCalculator = planningCalculator;
        this.moneyFormatter = moneyFormatter;
    }

    Region build(PlanParameters params, LocalDate selfDob) {
        this.params = params;
        this.selfDob = selfDob;
        ensureMajorEvents();

        PlanningSectionCard card = new PlanningSectionCard("Expenses Until Retirement", "#f87171");
        root = card.getRoot();
        VBox body = card.getBody();

        expenseRowsContainer = new VBox(0);
        body.getChildren().add(expenseRowsContainer);
        populateExpenseRows();

        refresh(params, selfDob);
        return root;
    }

    void refresh(PlanParameters params, LocalDate selfDob) {
        this.params = params;
        this.selfDob = selfDob;
        ensureMajorEvents();
        populateExpenseRows();
    }

    private void ensureMajorEvents() {
        if (params.majorEvents == null) {
            params.majorEvents = new ArrayList<>();
        }
    }

    private void populateExpenseRows() {
        if (expenseRowsContainer == null) {
            return;
        }

        expenseRowsContainer.getChildren().clear();
        FinancialPlanningCalculator.ExpenseSummary expenseSummary =
                planningCalculator.computeExpenseSummary(params, selfDob);

        addTableRow(expenseRowsContainer, "Loan Payments", moneyFormatter.apply(expenseSummary.loanPaymentsPaise()), false, false);
        addTableRow(expenseRowsContainer, "Cost of Living", moneyFormatter.apply(expenseSummary.costOfLivingPaise()), false, false);
        addTableRow(expenseRowsContainer, "Major Events", moneyFormatter.apply(expenseSummary.majorEventsPaise()), false, false);
        addTableRow(expenseRowsContainer, "Total Expenses", moneyFormatter.apply(expenseSummary.totalPaise()), true, true);
    }

    private Label buildAmountValue(String text, double width, boolean total) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("fp-table-value");
        lbl.setMinWidth(width);
        lbl.setPrefWidth(width);
        lbl.setMaxWidth(width);
        if (total) {
            lbl.getStyleClass().add("fp-table-value-total");
        }
        lbl.setAlignment(Pos.CENTER_RIGHT);
        return lbl;
    }

    private void addTableRow(VBox parent, String label, String value, boolean total, boolean negative) {
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
