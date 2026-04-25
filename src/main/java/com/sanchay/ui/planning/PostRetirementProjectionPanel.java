package com.sanchay.ui.planning;

import com.sanchay.model.PlanParameters;
import com.sanchay.service.FinancialPlanningCalculator;
import com.sanchay.ui.UiUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

class PostRetirementProjectionPanel {
    private enum ProjectionMode { MINIMUM, ACTUAL }

    private final FinancialPlanningCalculator planningCalculator;
    private final LongFunction<String> moneyFormatter;
    private final Runnable onAssumptionsChanged;

    private VBox root;
    private VBox tableComments;
    private TableView<FinancialPlanningCalculator.PostRetirementRow> table;
    private Label minimumProjectionLbl;
    private Label actualProjectionLbl;
    private ProjectionMode projectionMode = ProjectionMode.MINIMUM;

    private PlanParameters params;
    private LocalDate selfDob;
    private long forecastedCorpusPaise;

    PostRetirementProjectionPanel(FinancialPlanningCalculator planningCalculator,
                                  LongFunction<String> moneyFormatter,
                                  Runnable onAssumptionsChanged) {
        this.planningCalculator = planningCalculator;
        this.moneyFormatter = moneyFormatter;
        this.onAssumptionsChanged = onAssumptionsChanged;
    }

    Region build() {
        Label chevron = new Label("▾");
        chevron.getStyleClass().add("fp-chevron");

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

        HBox header = new HBox(10, chevron, minimumProjectionLbl, projectionModeSwitch, actualProjectionLbl);
        header.setAlignment(Pos.CENTER_LEFT);
        updateProjectionModeHeader();

        tableComments = buildTableComments();
        table = buildCashFlowTable();
        tableComments.setVisible(false);
        tableComments.setManaged(false);
        table.setVisible(false);
        table.setManaged(false);
        chevron.setText("▸");

        Runnable toggleExpanded = () -> {
            boolean expanding = !table.isManaged();
            tableComments.setVisible(expanding);
            tableComments.setManaged(expanding);
            table.setVisible(expanding);
            table.setManaged(expanding);
            chevron.setText(expanding ? "▾" : "▸");
        };
        chevron.setCursor(Cursor.HAND);
        minimumProjectionLbl.setCursor(Cursor.HAND);
        actualProjectionLbl.setCursor(Cursor.HAND);
        chevron.setOnMouseClicked(e -> toggleExpanded.run());
        minimumProjectionLbl.setOnMouseClicked(e -> toggleExpanded.run());
        actualProjectionLbl.setOnMouseClicked(e -> toggleExpanded.run());

        root = new VBox(12, header, tableComments, table);
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
        refreshTableComments();
        table.getItems().setAll(buildTableItems());
        table.refresh();
    }

    private List<FinancialPlanningCalculator.PostRetirementRow> buildTableItems() {
        long balance = projectionMode == ProjectionMode.ACTUAL
                ? forecastedCorpusPaise
                : planningCalculator.computeRequiredCorpusPaise(params, selfDob);
        boolean includeScheduledInflows = projectionMode == ProjectionMode.ACTUAL;
        return new ArrayList<>(planningCalculator.computePostRetirementRows(
                params, selfDob, balance, includeScheduledInflows));
    }

    private VBox buildTableComments() {
        VBox comments = new VBox(0);
        refreshTableComments(comments);
        return comments;
    }

    private void refreshTableComments() {
        refreshTableComments(tableComments);
    }

    private void refreshTableComments(VBox comments) {
        if (comments == null) return;
        comments.getChildren().clear();

        int slowGoAge = planningCalculator.getSpendingSlowGoAge(params);
        int noGoAge = planningCalculator.getSpendingNoGoAge(params);
        double slowGoReductionPct = params != null
                ? params.spendingSlowGoReductionPct
                : FinancialPlanningCalculator.SPENDING_SLOW_GO_REDUCTION * 100.0;

        HBox headingRow = new HBox();
        headingRow.getStyleClass().add("fp-table-row-comment");
        headingRow.setAlignment(Pos.CENTER_LEFT);
        headingRow.setMaxWidth(Double.MAX_VALUE);

        Hyperlink assumptionsLink = new Hyperlink("Three-phase retirement spending model");
        assumptionsLink.getStyleClass().addAll("link-teal", "text-link-button");
        assumptionsLink.setOnAction(e -> openSpendingAssumptionsDialog());
        headingRow.getChildren().add(assumptionsLink);
        comments.getChildren().add(headingRow);

        addTableCommentRow(comments, String.format(
                "Phase 1  Active retirement, up to %d years of age: full inflation",
                slowGoAge - 1));
        addTableCommentRow(comments, String.format(
                "Phase 2  Slow-go retirement, up to %d years of age: inflation-%.1f%%",
                noGoAge - 1, slowGoReductionPct));
        addTableCommentRow(comments, "Phase 3  Healthcare retirement: full inflation");
        addTableCommentRow(comments, "Actual corpus projection also includes scheduled post-retirement bond, FD, and RD yields.");
    }

    private void addTableCommentRow(VBox parent, String label) {
        HBox row = new HBox();
        row.getStyleClass().add("fp-table-row-comment");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        Label lblNode = new Label(label);
        lblNode.getStyleClass().add("fp-table-label-comment");
        lblNode.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblNode, javafx.scene.layout.Priority.ALWAYS);

        row.getChildren().add(lblNode);
        parent.getChildren().add(row);
    }

    private void openSpendingAssumptionsDialog() {
        if (params == null) return;

        Dialog<Boolean> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Retirement Spending Assumptions", "✎", 460,
                "Adjust the age bands and inflation reduction used in the three-phase retirement spending model.");

        GridPane form = UiUtils.buildFormGrid(185);
        form.setPadding(new Insets(16));

        TextField slowGoAgeFld = numericField(String.valueOf(planningCalculator.getSpendingSlowGoAge(params)));
        TextField noGoAgeFld = numericField(String.valueOf(planningCalculator.getSpendingNoGoAge(params)));
        TextField reductionFld = numericField(formatPct(params.spendingSlowGoReductionPct));

        UiUtils.addFormRow(form, 0, "Slow-go starts at age", slowGoAgeFld);
        UiUtils.addFormRow(form, 1, "Healthcare starts at age", noGoAgeFld);
        UiUtils.addFormRow(form, 2, "Phase 2 inflation reduction", reductionFld);

        Label hint = UiUtils.hintLabel("Enter the reduction as a percent, for example 1.5 for 1.5%.");
        hint.setWrapText(true);

        VBox content = new VBox(12, form, hint);
        dlg.getDialogPane().setContent(content);

        ButtonType saveBtn = UiUtils.addSaveCancel(dlg.getDialogPane());
        Button saveButton = (Button) dlg.getDialogPane().lookupButton(saveBtn);
        saveButton.getStyleClass().add("btn-gold");

        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return Boolean.FALSE;
            try {
                int slowGoAge = parsePositiveInt(slowGoAgeFld.getText(), "Slow-go starts at age");
                int noGoAge = parsePositiveInt(noGoAgeFld.getText(), "Healthcare starts at age");
                double reductionPct = parseNonNegativeDouble(reductionFld.getText(), "Phase 2 inflation reduction");

                if (noGoAge <= slowGoAge) {
                    showValidationError("Healthcare age must be greater than slow-go age.");
                    return Boolean.FALSE;
                }

                params.spendingSlowGoAge = slowGoAge;
                params.spendingNoGoAge = noGoAge;
                params.spendingSlowGoReductionPct = reductionPct;
                return Boolean.TRUE;
            } catch (IllegalArgumentException ex) {
                showValidationError(ex.getMessage());
                return Boolean.FALSE;
            }
        });

        if (dlg.showAndWait().orElse(Boolean.FALSE)) {
            if (onAssumptionsChanged != null) {
                onAssumptionsChanged.run();
            } else {
                refresh();
            }
        }
    }

    private TextField numericField(String value) {
        TextField field = new TextField(value);
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        return field;
    }

    private int parsePositiveInt(String text, String fieldName) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Enter a valid positive number for " + fieldName + ".");
        }
    }

    private double parseNonNegativeDouble(String text, String fieldName) {
        try {
            double value = Double.parseDouble(text.trim().replace("%", ""));
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Enter a valid non-negative number for " + fieldName + ".");
        }
    }

    private String formatPct(double value) {
        return value == Math.floor(value)
                ? String.format("%.0f", value)
                : String.format("%.1f", value);
    }

    private void showValidationError(String message) {
        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Validation Error", "⚠", 380);

        Label messageLbl = new Label(message);
        messageLbl.getStyleClass().add("form-label");
        messageLbl.setWrapText(true);

        dlg.getDialogPane().setContent(messageLbl);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dlg.showAndWait();
    }

    private TableView<FinancialPlanningCalculator.PostRetirementRow> buildCashFlowTable() {
        TableView<FinancialPlanningCalculator.PostRetirementRow> tv = new TableView<>();
        tv.getStyleClass().add("forecast-table");
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setEditable(false);
        tv.setRowFactory(t -> new javafx.scene.control.TableRow<>() {
            @Override protected void updateItem(FinancialPlanningCalculator.PostRetirementRow row, boolean empty) {
                super.updateItem(row, empty);
                if (!empty && row != null) {
                    int slowGoAge = planningCalculator.getSpendingSlowGoAge(params);
                    int noGoAge = planningCalculator.getSpendingNoGoAge(params);
                    boolean outerPhase = row.age() < slowGoAge || row.age() >= noGoAge;
                    setStyle(outerPhase ? "-fx-background-color: -surface-teal-faint;" : "");
                } else {
                    setStyle("");
                }
            }
        });
        tv.getColumns().addAll(
                yearCol(), ageCol(), balanceCol(), roiCol(), taxCol(), withdrawalCol(),
                scheduledInflowsCol(), endingBalanceCol()
        );
        return tv;
    }

    private TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> yearCol() {
        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> col = new TableColumn<>("Year");
        col.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().year())));
        col.setPrefWidth(70);
        return col;
    }

    private TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> ageCol() {
        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> col = new TableColumn<>("Age");
        col.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().age())));
        col.setPrefWidth(55);
        return col;
    }

    private TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> balanceCol() {
        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> col = new TableColumn<>("Starting Balance");
        col.setCellValueFactory(cd -> new SimpleStringProperty(moneyFormatter.apply(cd.getValue().startingBalancePaise())));
        col.setPrefWidth(160);
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("post-retirement-cf-depleted");
                if (empty || item == null || item.isEmpty()) { setText(null); return; }
                setText(item);
                FinancialPlanningCalculator.PostRetirementRow row = getTableView().getItems().get(getIndex());
                if (row.startingDepleted())
                    getStyleClass().add("post-retirement-cf-depleted");
            }
        });
        return col;
    }

    private TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> roiCol() {
        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> col = new TableColumn<>("ROI");
        col.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().startingDepleted() ? "-" : moneyFormatter.apply(cd.getValue().roiPaise())));
        col.setPrefWidth(130);
        return col;
    }

    private TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> taxCol() {
        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> col = new TableColumn<>("Tax");
        col.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().startingDepleted() ? "-" : moneyFormatter.apply(cd.getValue().taxPaise())));
        col.setPrefWidth(110);
        return col;
    }

    private TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> withdrawalCol() {
        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> col = new TableColumn<>("Withdrawal");
        col.setCellValueFactory(cd -> new SimpleStringProperty(moneyFormatter.apply(cd.getValue().withdrawalPaise())));
        col.setPrefWidth(130);
        return col;
    }

    private TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> endingBalanceCol() {
        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> col = new TableColumn<>("Ending Balance");
        col.setCellValueFactory(cd -> new SimpleStringProperty(moneyFormatter.apply(cd.getValue().endingBalancePaise())));
        col.setPrefWidth(160);
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("post-retirement-cf-depleted");
                if (empty || item == null || item.isEmpty()) { setText(null); return; }
                setText(item);
                FinancialPlanningCalculator.PostRetirementRow row = getTableView().getItems().get(getIndex());
                if (row.endingNegative())
                    getStyleClass().add("post-retirement-cf-depleted");
            }
        });
        return col;
    }

    private TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> scheduledInflowsCol() {
        TableColumn<FinancialPlanningCalculator.PostRetirementRow, String> col = new TableColumn<>("Scheduled Inflows");
        col.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().scheduledInflowsPaise() <= 0 ? "-" : moneyFormatter.apply(cd.getValue().scheduledInflowsPaise())));
        col.setPrefWidth(150);
        return col;
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
