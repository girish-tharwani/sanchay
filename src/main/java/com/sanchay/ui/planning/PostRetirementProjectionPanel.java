package com.sanchay.ui.planning;

import com.sanchay.model.PlanParameters;
import com.sanchay.service.FinancialPlanningCalculator;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.function.LongFunction;

class PostRetirementProjectionPanel {

    private enum ProjectionMode {
        MINIMUM,
        ACTUAL
    }

    private final FinancialPlanningCalculator planningCalculator;
    private final LongFunction<String> moneyFormatter;

    private VBox root;
    private TableView<FinancialPlanningCalculator.PostRetirementRow> table;
    private Label minimumProjectionLbl;
    private Label actualProjectionLbl;
    private ProjectionMode projectionMode = ProjectionMode.MINIMUM;

    private PlanParameters params;
    private LocalDate selfDob;
    private long forecastedCorpusPaise;

    PostRetirementProjectionPanel(FinancialPlanningCalculator planningCalculator,
                                  LongFunction<String> moneyFormatter) {
        this.planningCalculator = planningCalculator;
        this.moneyFormatter = moneyFormatter;
    }

    Region build() {
        minimumProjectionLbl = new Label("Minimum Corpus Projection");
        minimumProjectionLbl.getStyleClass().addAll("text-section-title", "fp-projection-mode-label");
        minimumProjectionLbl.setPrefWidth(210);
        minimumProjectionLbl.setMinWidth(210);
        minimumProjectionLbl.setAlignment(Pos.CENTER_RIGHT);

        CheckBox projectionModeSwitch = new CheckBox();
        projectionModeSwitch.getStyleClass().add("fp-projection-switch");
        projectionModeSwitch.setSelected(false);
        projectionModeSwitch.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            projectionMode = isSelected ? ProjectionMode.ACTUAL : ProjectionMode.MINIMUM;
            updateProjectionModeHeader();
            refresh();
        });

        actualProjectionLbl = new Label("Actual Corpus Projection");
        actualProjectionLbl.getStyleClass().addAll("text-section-title", "fp-projection-mode-label");
        actualProjectionLbl.setPrefWidth(190);
        actualProjectionLbl.setMinWidth(190);
        actualProjectionLbl.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(10, minimumProjectionLbl, projectionModeSwitch, actualProjectionLbl);
        header.setAlignment(Pos.CENTER_LEFT);
        updateProjectionModeHeader();

        table = buildCashFlowTable();

        root = new VBox(12, header, table);
        root.getStyleClass().add("card");
        return root;
    }

    void updateInputs(PlanParameters params, LocalDate selfDob, long forecastedCorpusPaise) {
        this.params = params;
        this.selfDob = selfDob;
        this.forecastedCorpusPaise = forecastedCorpusPaise;
        refresh();
    }

    private void refresh() {
        if (table == null || params == null || selfDob == null) return;
        table.getItems().setAll(computeRows());
        table.refresh();
    }

    private List<FinancialPlanningCalculator.PostRetirementRow> computeRows() {
        long balance = projectionMode == ProjectionMode.ACTUAL
                ? forecastedCorpusPaise
                : planningCalculator.computeRequiredCorpusPaise(params, selfDob);
        return planningCalculator.computePostRetirementRows(params, selfDob, balance);
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
        balanceCol.setCellValueFactory(cd -> new SimpleStringProperty(moneyFormatter.apply(cd.getValue().startingBalancePaise())));
        balanceCol.setPrefWidth(160);
        balanceCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("post-retirement-cf-depleted");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item);
                if (getTableView().getItems().get(getIndex()).startingDepleted()) {
                    getStyleClass().add("post-retirement-cf-depleted");
                }
            }
        });

        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> roiCol = new TableColumn<>("ROI");
        roiCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().startingDepleted() ? "-" : moneyFormatter.apply(cd.getValue().roiPaise())));
        roiCol.setPrefWidth(130);

        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> taxCol = new TableColumn<>("Tax");
        taxCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().startingDepleted() ? "-" : moneyFormatter.apply(cd.getValue().taxPaise())));
        taxCol.setPrefWidth(110);

        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> withdrawalCol = new TableColumn<>("Withdrawal");
        withdrawalCol.setCellValueFactory(cd -> new SimpleStringProperty(moneyFormatter.apply(cd.getValue().withdrawalPaise())));
        withdrawalCol.setPrefWidth(130);

        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> endingBalanceCol = new TableColumn<>("Ending Balance");
        endingBalanceCol.setCellValueFactory(cd -> new SimpleStringProperty(moneyFormatter.apply(cd.getValue().endingBalancePaise())));
        endingBalanceCol.setPrefWidth(160);
        endingBalanceCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("post-retirement-cf-depleted");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item);
                if (getTableView().getItems().get(getIndex()).endingNegative()) {
                    getStyleClass().add("post-retirement-cf-depleted");
                }
            }
        });

        table.getColumns().addAll(yearCol, ageCol, balanceCol, roiCol, taxCol, withdrawalCol, endingBalanceCol);
        return table;
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
}
