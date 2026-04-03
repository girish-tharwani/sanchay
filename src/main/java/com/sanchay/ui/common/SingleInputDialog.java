package com.sanchay.ui.common;

import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

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
        dlg.setTitle(title);
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(380);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, title.startsWith("Edit") || title.startsWith("Rename") ? "✎" : "+", title);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));

        if (subtitle != null && !subtitle.isBlank()) {
            Label sub = new Label(subtitle);
            sub.getStyleClass().add("text-hint");
            content.getChildren().add(sub);
        }

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        ColumnConstraints c1 = new ColumnConstraints(120);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        TextField tf = new TextField(initialValue == null ? "" : initialValue);
        tf.setMaxWidth(Double.MAX_VALUE);
        g.add(lbl, 0, 0);
        g.add(tf, 1, 0);

        content.getChildren().add(g);
        dlg.getDialogPane().setContent(content);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> bt == saveBtn ? tf.getText().trim() : null);
        return dlg.showAndWait().orElse(null);
    }
}
