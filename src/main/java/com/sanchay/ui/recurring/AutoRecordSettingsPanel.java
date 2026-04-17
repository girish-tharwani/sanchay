package com.sanchay.ui.recurring;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

/**
 * The auto-record toggle row: a checkbox, an overdue-days spinner, and a suffix label.
 * Exposes {@link #getView()} for embedding in the dialog form and {@link #getAutoRecordDays()}
 * for save collection.
 */
class AutoRecordSettingsPanel {

    private final CheckBox        autoRecordCb;
    private final Spinner<Integer> autoRecordDaysSp;
    private final HBox             view;

    AutoRecordSettingsPanel() {
        autoRecordCb     = new CheckBox("Auto-record after");
        autoRecordDaysSp = new Spinner<>(1, 30, 3);
        autoRecordDaysSp.setPrefWidth(70);
        autoRecordDaysSp.setDisable(true);

        Label suffix = new Label("days overdue");
        suffix.getStyleClass().add("text-hint");

        view = new HBox(8, autoRecordCb, autoRecordDaysSp, suffix);
        view.setAlignment(Pos.CENTER_LEFT);

        autoRecordCb.selectedProperty().addListener((obs, o, n) -> autoRecordDaysSp.setDisable(!n));
    }

    Node getView() { return view; }

    void prefill(int autoRecordAfterDays) {
        if (autoRecordAfterDays > 0) {
            autoRecordCb.setSelected(true);
            autoRecordDaysSp.getValueFactory().setValue(autoRecordAfterDays);
        }
    }

    boolean isEnabled() { return autoRecordCb.isSelected(); }

    /** Returns the configured overdue-day threshold, or 0 if auto-record is off. */
    int getAutoRecordDays() {
        return autoRecordCb.isSelected() ? autoRecordDaysSp.getValue() : 0;
    }
}
