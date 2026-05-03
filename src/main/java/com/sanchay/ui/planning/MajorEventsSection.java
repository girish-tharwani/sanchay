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
 * Owns the separate major events card with its forecast/actual details.
 */
final class MajorEventsSection {

    private static final double EVENT_AMOUNT_COL_WIDTH = 130;
    private static final double EVENT_AMOUNT_COL_WIDTH_SHORT = 80;

    private final FinancialPlanningCalculator planningCalculator;
    private final MajorEventPlanner majorEventPlanner;
    private final LongFunction<String> moneyFormatter;
    private final Runnable saveParams;
    private final Consumer<MajorEvent> editEvent;
    private final Runnable onDerivedDataChanged;

    private PlanParameters params;
    private LocalDate selfDob;

    private VBox root;
    private VBox majorEventsListBox;
    private Label majorEventsForecastUntilRetirementTotalLbl;
    private Label majorEventsForecastAfterRetirementTotalLbl;
    private Label majorEventsActualTotalLbl;

    MajorEventsSection(
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

        PlanningSectionCard card = new PlanningSectionCard("Major Events", "#801f67");
        root = card.getRoot();
        VBox body = card.getBody();

        Label hint = new Label("Forecasted cost vs. actuals tracked from your transactions.");
        hint.getStyleClass().add("fp-table-label-comment");
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

        Label forecastUntilRetirementHdr = buildAmountHeader("Forecast pre-retire", EVENT_AMOUNT_COL_WIDTH);
        Label forecastAfterRetirementHdr = buildAmountHeader("Forecast post-retire", EVENT_AMOUNT_COL_WIDTH);
        Label actualHdr = buildAmountHeader("Actual", EVENT_AMOUNT_COL_WIDTH_SHORT);
        colHeader.getChildren().addAll(nameHdr, forecastUntilRetirementHdr, forecastAfterRetirementHdr, actualHdr);
        return colHeader;
    }

    private HBox buildTotalsRow() {
        HBox totalRow = new HBox();
        totalRow.getStyleClass().add("fp-events-total-row");
        VBox.setMargin(totalRow, new Insets(8, 0, 0, 0));

        Label totalLbl = new Label("Total Major Events");
        totalLbl.getStyleClass().add("fp-table-label-total");
        totalLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(totalLbl, Priority.ALWAYS);

        majorEventsForecastUntilRetirementTotalLbl = buildAmountValue(MoneyFormatter.symbol() + "0", EVENT_AMOUNT_COL_WIDTH, true);
        majorEventsForecastAfterRetirementTotalLbl = buildAmountValue(MoneyFormatter.symbol() + "0", EVENT_AMOUNT_COL_WIDTH, true);
        majorEventsActualTotalLbl = buildAmountValue(MoneyFormatter.symbol() + "0", EVENT_AMOUNT_COL_WIDTH_SHORT, true);
        totalRow.getChildren().addAll(totalLbl, majorEventsForecastUntilRetirementTotalLbl,
                majorEventsForecastAfterRetirementTotalLbl, majorEventsActualTotalLbl);
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

        LocalDate retirementDate = planningCalculator.getRetirementDate(params, selfDob);
        LocalDate projectionEndDate = postRetirementProjectionEndDate(retirementDate);
        long forecastUntilRetirementTotal = params.majorEvents.stream()
                .mapToLong(event -> majorEventPlanner.computeEventForecast(event, retirementDate))
                .sum();
        long forecastAfterRetirementTotal = params.majorEvents.stream()
                .mapToLong(event -> majorEventPlanner.computeEventPostRetirementForecast(
                        event, retirementDate, projectionEndDate))
                .sum();
        long actualTotal = params.majorEvents.stream()
                .mapToLong(event -> majorEventPlanner.computeEventActual(event, retirementDate))
                .sum();

        majorEventsForecastUntilRetirementTotalLbl.setText(moneyFormatter.apply(forecastUntilRetirementTotal));
        majorEventsForecastAfterRetirementTotalLbl.setText(moneyFormatter.apply(forecastAfterRetirementTotal));
        majorEventsActualTotalLbl.setText(moneyFormatter.apply(actualTotal));
    }

    private HBox createEventRow(MajorEvent event) {
        LocalDate retirementDate = planningCalculator.getRetirementDate(params, selfDob);
        LocalDate projectionEndDate = postRetirementProjectionEndDate(retirementDate);
        long forecastUntilRetirement = majorEventPlanner.computeEventForecast(event, retirementDate);
        long forecastAfterRetirement = majorEventPlanner.computeEventPostRetirementForecast(
                event, retirementDate, projectionEndDate);
        long actual = majorEventPlanner.computeEventActual(event, retirementDate);

        HBox row = new HBox();
        row.getStyleClass().add("fp-event-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setCursor(Cursor.HAND);

        Label nameLbl = new Label(event.getName());
        nameLbl.getStyleClass().add("fp-event-name");
        nameLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        Label forecastUntilRetirementLbl = buildAmountValue(moneyFormatter.apply(forecastUntilRetirement),EVENT_AMOUNT_COL_WIDTH, false);
        Label forecastAfterRetirementLbl = buildAmountValue(moneyFormatter.apply(forecastAfterRetirement),EVENT_AMOUNT_COL_WIDTH, false);
        Label actualLbl = buildAmountValue(moneyFormatter.apply(actual),EVENT_AMOUNT_COL_WIDTH_SHORT, false);
        row.getChildren().addAll(nameLbl, forecastUntilRetirementLbl, forecastAfterRetirementLbl, actualLbl);
        return row;
    }

    private LocalDate postRetirementProjectionEndDate(LocalDate retirementDate) {
        int retirementAge = planningCalculator.getRetirementAgeYears(params, selfDob);
        int postRetirementYears = Math.max(0, params.lifeExpectancy - retirementAge);
        return retirementDate.plusYears(postRetirementYears);
    }

    private Label buildAmountHeader(String text, double width) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("fp-table-header-label");
        lbl.setMinWidth(width);
        lbl.setPrefWidth(width);
        lbl.setMaxWidth(width);
        lbl.setWrapText(true);
        lbl.setAlignment(Pos.CENTER_RIGHT);
        return lbl;
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
}
