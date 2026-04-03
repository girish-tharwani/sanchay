package com.sanchay.ui.planning;

import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.StageStyle;

/**
 * Styled confirmation dialog for deleting a major event.
 * Modelled after RemoveMemberDialog for visual consistency.
 * Returns true if the user confirmed deletion, false if cancelled.
 */
class DeleteMajorEventDialog {

    private final String eventName;

    DeleteMajorEventDialog(String eventName) {
        this.eventName = eventName;
    }

    boolean showAndWait() {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Delete Major Event");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(420);
        UiUtils.applyStylesheet(dlg);
        dlg.initStyle(StageStyle.UNDECORATED);

        // ── Header ────────────────────────────────────────────────────────────
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dialog-header-bar");

        Label iconLbl = new Label("🗑");
        iconLbl.getStyleClass().addAll("dialog-icon-box", "dialog-icon-box--danger");

        Label titleLbl = new Label("Delete Major Event");
        titleLbl.getStyleClass().add("text-step-title");

        header.getChildren().addAll(iconLbl, titleLbl);
        dlg.getDialogPane().setHeader(header);

        // ── Body ──────────────────────────────────────────────────────────────
        VBox body = new VBox(14);
        body.setPadding(new Insets(20, 22, 4, 22));

        HBox warnRow = new HBox(16);
        warnRow.setAlignment(Pos.TOP_LEFT);

        StackPane warnIcon = new StackPane(new Label("⚠"));
        warnIcon.getStyleClass().addAll("dialog-icon-box-lg", "dialog-icon-box-lg--error");

        VBox warnText = new VBox(4);
        Label headline = new Label("Delete \"" + eventName + "\"?");
        headline.getStyleClass().add("text-step-title");
        Label subtext = new Label("This event and its tracking configuration will be permanently removed.");
        subtext.getStyleClass().add("text-body-muted");
        subtext.setWrapText(true);
        warnText.getChildren().addAll(headline, subtext);
        HBox.setHgrow(warnText, Priority.ALWAYS);

        warnRow.getChildren().addAll(warnIcon, warnText);
        body.getChildren().add(warnRow);
        dlg.getDialogPane().setContent(body);

        // ── Buttons ───────────────────────────────────────────────────────────
        ButtonType deleteBtn = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, deleteBtn);
        dlg.getDialogPane().lookupButton(deleteBtn).getStyleClass().add("btn-danger");

        dlg.setResultConverter(bt -> bt == deleteBtn);
        return dlg.showAndWait().orElse(false);
    }
}
