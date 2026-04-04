package com.sanchay.ui.common;

import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Generic single-field styled input dialog.
 * Extracted from CategoriesScreen.styledInput().
 */
public class SingleInputDialog {

    /**
     * Shows a styled single-field input dialog.
     *
     * @param title        Dialog title and header text.
     * @param labelText    Label for the text field.
     * @param subtitle     Optional context line shown above the field (e.g. "Parent: Food").
     * @param initialValue Pre-populated field value.
     * @return Trimmed input text, or null if the user cancelled.
     */
    public static String show(String title, String labelText, String subtitle, String initialValue) {
        Dialog<String> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, title, title.startsWith("Edit") || title.startsWith("Rename") ? "✎" : "+", 380);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));

        if (subtitle != null && !subtitle.isBlank()) {
            Label sub = new Label(subtitle);
            sub.getStyleClass().add("text-hint");
            content.getChildren().add(sub);
        }

        GridPane g = UiUtils.buildFormGrid(120);

        TextField tf = new TextField(initialValue == null ? "" : initialValue);
        tf.setMaxWidth(Double.MAX_VALUE);
        UiUtils.addFormRow(g, 0, labelText, tf);

        content.getChildren().add(g);
        dlg.getDialogPane().setContent(content);

        ButtonType saveBtn = UiUtils.addSaveCancel(dlg.getDialogPane());
        dlg.setResultConverter(bt -> bt == saveBtn ? tf.getText().trim() : null);
        return dlg.showAndWait().orElse(null);
    }
}
