package com.sanchay.ui.wizard;

import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Modal dialog for initial preference selection (currency, year format, date format).
 * Used by FirstRunWizard (page 2) and by SettingsScreen when switching to an empty
 * data folder mid-session.
 *
 * Call show() to display; returns a Result with the three selections, or null if
 * the user dismissed without confirming.
 */
public class PreferencesSetupDialog {

    public record Result(String currency, String yearFormat, String dateFormat) {}

    /**
     * Shows the preferences dialog and blocks until dismissed.
     * Returns the selected values, or null if cancelled.
     */
    public static Result show() {
        Dialog<Result> dlg = new Dialog<>();
        dlg.setTitle("Preferences");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(460);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "⚙", "Set your preferences",
                "These can be changed at any time from Settings.");

        VBox formRows = new VBox(14);
        formRows.setPadding(new Insets(16, 16, 8, 16));

        ComboBox<String> currencyCb = new ComboBox<>();
        currencyCb.getItems().add("INR");
        currencyCb.setValue("INR");
        currencyCb.setMaxWidth(Double.MAX_VALUE);
        formRows.getChildren().add(prefRow("Currency:", currencyCb));

        ComboBox<String> yearFormatCb = new ComboBox<>();
        yearFormatCb.getItems().addAll("Indian Financial Year", "Calendar Year");
        yearFormatCb.setValue("Indian Financial Year");
        yearFormatCb.setMaxWidth(Double.MAX_VALUE);
        formRows.getChildren().add(prefRow("Year Format:", yearFormatCb));

        ComboBox<String> dateFormatCb = new ComboBox<>();
        dateFormatCb.getItems().addAll("DD/MM/YYYY", "YYYY-MM-DD");
        dateFormatCb.setValue("DD/MM/YYYY");
        dateFormatCb.setMaxWidth(Double.MAX_VALUE);
        formRows.getChildren().add(prefRow("Date Format:", dateFormatCb));

        dlg.getDialogPane().setContent(formRows);

        ButtonType continueBtn = new ButtonType("Continue", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(continueBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> bt == continueBtn
                ? new Result(currencyCb.getValue(), yearFormatCb.getValue(), dateFormatCb.getValue())
                : null);

        return dlg.showAndWait().orElse(null);
    }

    private static HBox prefRow(String labelText, ComboBox<String> cb) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        lbl.setMinWidth(150);
        HBox.setHgrow(cb, Priority.ALWAYS);
        row.getChildren().addAll(lbl, cb);
        return row;
    }
}
