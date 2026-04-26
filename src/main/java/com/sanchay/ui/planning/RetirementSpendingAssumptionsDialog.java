package com.sanchay.ui.planning;

import com.sanchay.model.PlanParameters;
import com.sanchay.service.FinancialPlanningCalculator;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Dialog for configuring the three-phase retirement spending assumptions.
 */
class RetirementSpendingAssumptionsDialog {

    private static final double FIELD_WIDTH = 70;

    private final PlanParameters params;
    private final FinancialPlanningCalculator planningCalculator;
    private final Dialog<Boolean> dlg;
    private final TextField slowGoAgeFld;
    private final TextField slowGoAdjustmentFld;
    private final TextField healthcareAgeFld;
    private final TextField healthcareAdjustmentFld;

    RetirementSpendingAssumptionsDialog(PlanParameters params,
                                        FinancialPlanningCalculator planningCalculator) {
        this.params = params;
        this.planningCalculator = planningCalculator;

        dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Retirement Spending Assumptions", "✎", 560,
                "Adjust the age bands and inflation changes used in the three-phase retirement spending model.");

        slowGoAgeFld = compactNumberField(String.valueOf(planningCalculator.getSpendingSlowGoAge(params)));
        slowGoAdjustmentFld = compactNumberField(formatPct(
                planningCalculator.getSpendingSlowGoInflationAdjustmentRate(params) * 100.0));
        healthcareAgeFld = compactNumberField(String.valueOf(planningCalculator.getSpendingNoGoAge(params)));
        healthcareAdjustmentFld = compactNumberField(formatPct(
                planningCalculator.getSpendingHealthcareInflationAdjustmentRate(params) * 100.0));

        VBox content = new VBox(12,
                phaseRow("Slow-go phase starts at age", slowGoAgeFld,
                        "with inflation adjustment", slowGoAdjustmentFld),
                phaseRow("Healthcare phase starts at age", healthcareAgeFld,
                        "with inflation adjustment", healthcareAdjustmentFld),
                hint()
        );
        content.setPadding(new Insets(16));
        dlg.getDialogPane().setContent(content);

        ButtonType saveBtn = UiUtils.addSaveCancel(dlg.getDialogPane());
        Platform.runLater(() -> {
            Button saveButton = (Button) dlg.getDialogPane().lookupButton(saveBtn);
            if (saveButton == null) return;
            saveButton.getStyleClass().add("btn-gold");
            saveButton.addEventFilter(ActionEvent.ACTION, event -> {
                if (!validateAndApply()) {
                    event.consume();
                }
            });
        });

        dlg.setResultConverter(bt -> bt == saveBtn);
    }

    boolean showAndWait() {
        return dlg.showAndWait().orElse(false);
    }

    private HBox phaseRow(String firstLabel, TextField ageField, String secondLabel, TextField adjustmentField) {
        Label first = formLabel(firstLabel);
        Label second = formLabel(secondLabel);
        Label percent = formLabel("%");

        HBox row = new HBox(8, first, ageField, second, adjustmentField, percent);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label hint() {
        Label hint = UiUtils.hintLabel(
                "Inflation adjustment is added to normal inflation for that phase. "
                        + "Use negative values to reduce inflation and positive values to increase it; "
                        + "blank or 0 means no adjustment.");
        hint.setWrapText(true);
        return hint;
    }

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private TextField compactNumberField(String value) {
        TextField field = new TextField(value);
        field.setPrefWidth(FIELD_WIDTH);
        field.setMaxWidth(FIELD_WIDTH);
        HBox.setHgrow(field, Priority.NEVER);
        return field;
    }

    private boolean validateAndApply() {
        try {
            int slowGoAge = parsePositiveInt(slowGoAgeFld.getText(), "Slow-go phase starts at age");
            int healthcareAge = parsePositiveInt(healthcareAgeFld.getText(), "Healthcare phase starts at age");
            double slowGoAdjustmentPct = parseAdjustmentPct(
                    slowGoAdjustmentFld.getText(), "Slow-go inflation adjustment");
            double healthcareAdjustmentPct = parseAdjustmentPct(
                    healthcareAdjustmentFld.getText(), "Healthcare inflation adjustment");

            if (healthcareAge <= slowGoAge) {
                alert("Validation Error", "Healthcare age must be greater than slow-go age.");
                return false;
            }

            params.spendingSlowGoAge = slowGoAge;
            params.spendingNoGoAge = healthcareAge;
            params.spendingSlowGoInflationAdjustmentPct = slowGoAdjustmentPct;
            params.spendingHealthcareInflationAdjustmentPct = healthcareAdjustmentPct;
            params.spendingSlowGoReductionPct = Math.max(0.0, -slowGoAdjustmentPct);
            return true;
        } catch (IllegalArgumentException ex) {
            alert("Validation Error", ex.getMessage());
            return false;
        }
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

    private double parseAdjustmentPct(String text, String fieldName) {
        if (text == null || text.trim().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(text.trim().replace("%", ""));
        } catch (Exception e) {
            throw new IllegalArgumentException("Enter a valid number for " + fieldName + ".");
        }
    }

    private String formatPct(double value) {
        return value == Math.floor(value)
                ? String.format("%.0f", value)
                : String.format("%.1f", value);
    }

    private void alert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        UiUtils.applyStylesheet(alert);
        alert.showAndWait();
    }
}
