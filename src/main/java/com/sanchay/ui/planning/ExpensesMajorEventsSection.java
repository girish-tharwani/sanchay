package com.sanchay.ui.planning;

import com.sanchay.model.MajorEvent;
import com.sanchay.model.PlanParameters;
import com.sanchay.service.FinancialPlanningCalculator;
import com.sanchay.service.MajorEventPlanner;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * Owns the expenses-until-retirement card and its major-events interactions.
 */
final class ExpensesMajorEventsSection {

    private static final double EVENT_AMOUNT_COL_WIDTH = 110;

    private final FinancialPlanningCalculator planningCalculator;
    private final MajorEventPlanner majorEventPlanner;
    private final LongFunction<String> moneyFormatter;
    private final Runnable saveParams;
    private final Consumer<MajorEvent> editEvent;
    private final Runnable onDerivedDataChanged;

    private PlanParameters params;
    private LocalDate selfDob;

    private VBox root;
    private VBox expenseRowsContainer;
    private VBox majorEventsListBox;
    private Label majorEventsForecastTotalLbl;
    private Label majorEventsActualTotalLbl;

    ExpensesMajorEventsSection(
            FinancialPlanningCalculator planningCalculator,
            MajorEventPlanner majorEventPlanner,
            LongFunction<String> moneyFormatter,
            Runnable saveParams,
            Consumer<MajorEvent> editEvent,
            Runnable onDerivedDataChanged
    ) {
        this.planningCalculator = planningCalculator;
        this.majorEventPlanner = majorEventPlanner;
        this.moneyFormatter = moneyFormatter;
        this.saveParams = saveParams;
        this.editEvent = editEvent;
        this.onDerivedDataChanged = onDerivedDataChanged;
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

        Region divider = new Region();
        divider.getStyleClass().add("content-divider");
        divider.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(divider, new Insets(12, 0, 10, 0));
        body.getChildren().add(divider);

        Label eventsTitle = new Label("MAJOR EVENTS");
        eventsTitle.getStyleClass().add("section-group-label");
        body.getChildren().add(eventsTitle);

        Label hint = new Label("Forecasted cost vs. actuals tracked from your transactions.");
        hint.getStyleClass().add("text-hint");
        hint.setWrapText(true);
        body.getChildren().add(hint);

        body.getChildren().add(buildColumnHeader());

        majorEventsListBox = new VBox();
        body.getChildren().add(majorEventsListBox);

        body.getChildren().add(buildTotalsRow());
        body.getChildren().add(UiUtils.hintLabel("Double-click an event to edit"));
        body.getChildren().add(buildAddButtonRow());

        refresh(params, selfDob);
        return root;
    }

    void refresh(PlanParameters params, LocalDate selfDob) {
        this.params = params;
        this.selfDob = selfDob;
        ensureMajorEvents();
        populateExpenseRows();
        refreshMajorEventsList();
    }

    private void ensureMajorEvents() {
        if (params.majorEvents == null) {
            params.majorEvents = new ArrayList<>();
        }
    }

    private HBox buildColumnHeader() {
        HBox colHeader = new HBox();
        colHeader.getStyleClass().add("fp-event-col-header");
        colHeader.setMaxWidth(Double.MAX_VALUE);

        Label nameHdr = new Label("Event");
        nameHdr.getStyleClass().add("fp-table-header-label");
        nameHdr.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameHdr, Priority.ALWAYS);

        Label forecastHdr = buildAmountHeader("Forecast");
        Label actualHdr = buildAmountHeader("Actual");
        colHeader.getChildren().addAll(nameHdr, forecastHdr, actualHdr);
        return colHeader;
    }

    private HBox buildTotalsRow() {
        HBox totalRow = new HBox();
        totalRow.getStyleClass().add("fp-events-total-row");
        VBox.setMargin(totalRow, new Insets(8, 0, 0, 0));

        Label totalLbl = new Label("Total");
        totalLbl.getStyleClass().add("fp-table-label-total");
        totalLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(totalLbl, Priority.ALWAYS);

        majorEventsForecastTotalLbl = buildAmountValue(MoneyFormatter.symbol() + "0", true);
        majorEventsActualTotalLbl = buildAmountValue(MoneyFormatter.symbol() + "0", true);
        totalRow.getChildren().addAll(totalLbl, majorEventsForecastTotalLbl, majorEventsActualTotalLbl);
        return totalRow;
    }

    private HBox buildAddButtonRow() {
        Button addBtn = new Button("+ Add Major Event");
        addBtn.getStyleClass().add("btn-gold");
        addBtn.setOnAction(e -> {
            MajorEventDialog dlg = new MajorEventDialog(null);
            if (dlg.show() == MajorEventDialog.Outcome.SAVED) {
                params.majorEvents.add(dlg.getResult());
                saveParams.run();
                onDerivedDataChanged.run();
            }
        });

        HBox btnRow = new HBox(addBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(8, 0, 0, 0));
        return btnRow;
    }

    private void populateExpenseRows() {
        if (expenseRowsContainer == null) {
            return;
        }

        expenseRowsContainer.getChildren().clear();
        FinancialPlanningCalculator.ExpenseSummary expenseSummary =
                planningCalculator.computeExpenseSummary(params, selfDob);

        addTableSubHeader(expenseRowsContainer, "EXPENSES");
        addTableRow(expenseRowsContainer, "Loan Payments", moneyFormatter.apply(expenseSummary.loanPaymentsPaise()), false, false);
        addTableRow(expenseRowsContainer, "Cost of Living", moneyFormatter.apply(expenseSummary.costOfLivingPaise()), false, false);
        addTableRow(expenseRowsContainer, "Major Events", moneyFormatter.apply(expenseSummary.majorEventsPaise()), false, false);
        addTableRow(expenseRowsContainer, "Total Expenses", moneyFormatter.apply(expenseSummary.totalPaise()), true, true);
    }

    private void refreshMajorEventsList() {
        if (majorEventsListBox == null) {
            return;
        }

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
                    if (e.getClickCount() == 2) {
                        editEvent.accept(event);
                    }
                });
                majorEventsListBox.getChildren().add(row);
            }
        }

        long forecastTotal = params.majorEvents.stream()
                .mapToLong(event -> majorEventPlanner.computeEventForecast(
                        event, planningCalculator.getRetirementDate(params, selfDob)))
                .sum();
        long actualTotal = params.majorEvents.stream()
                .mapToLong(majorEventPlanner::computeEventActual)
                .sum();

        majorEventsForecastTotalLbl.setText(moneyFormatter.apply(forecastTotal));
        majorEventsActualTotalLbl.setText(moneyFormatter.apply(actualTotal));
    }

    private HBox createEventRow(MajorEvent event) {
        long forecast = majorEventPlanner.computeEventForecast(
                event, planningCalculator.getRetirementDate(params, selfDob));
        long actual = majorEventPlanner.computeEventActual(event);

        HBox row = new HBox();
        row.getStyleClass().add("fp-event-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setCursor(Cursor.HAND);

        Label nameLbl = new Label(event.getName());
        nameLbl.getStyleClass().add("fp-event-name");
        nameLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        Label forecastLbl = buildAmountValue(moneyFormatter.apply(forecast), false);
        Label actualLbl = buildAmountValue(moneyFormatter.apply(actual), false);
        row.getChildren().addAll(nameLbl, forecastLbl, actualLbl);
        return row;
    }

    private Label buildAmountHeader(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("fp-table-header-label");
        lbl.setPrefWidth(EVENT_AMOUNT_COL_WIDTH);
        lbl.setAlignment(Pos.CENTER_RIGHT);
        return lbl;
    }

    private Label buildAmountValue(String text, boolean total) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("fp-table-value");
        if (total) {
            lbl.getStyleClass().add("fp-table-value-total");
        }
        lbl.setPrefWidth(EVENT_AMOUNT_COL_WIDTH);
        lbl.setAlignment(Pos.CENTER_RIGHT);
        return lbl;
    }

    private void addTableSubHeader(VBox parent, String label) {
        Label lbl = new Label(label.toUpperCase());
        lbl.getStyleClass().add("section-group-label");
        VBox.setMargin(lbl, new Insets(10, 0, 2, 0));
        parent.getChildren().add(lbl);
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
