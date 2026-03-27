package com.sanchay.ui.recurring;

import com.sanchay.model.RecurringTransaction;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;

/**
 * Dialog for skipping a single occurrence of a recurring transaction.
 * Extracted from MainWindow.skipRecurring().
 */
public class SkipRecurringDialog {

    private final RecurringTransaction r;

    public SkipRecurringDialog(RecurringTransaction r) {
        this.r = r;
    }

    public void show(Runnable onComplete, Runnable postRefresh) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Skip Occurrence");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(400);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "↷", "Skip Occurrence");

        VBox body = new VBox(14);
        body.setPadding(new Insets(16));

        HBox iconRow = new HBox(14);
        iconRow.setAlignment(Pos.CENTER_LEFT);
        Label iconLbl = new Label("↷");
        iconLbl.getStyleClass().addAll("dialog-icon-box-lg", "dialog-icon-box-lg--gold");
        VBox textBlock = new VBox(4);
        Label headline = new Label("Skip \u2018" + r.getDescription() + "\u2019?");
        headline.getStyleClass().add("text-step-title");
        headline.setWrapText(true);
        Label subLbl = new Label(
                "Use this when the transaction was already recorded separately. "
                + "The schedule will advance to the next due date.");
        subLbl.getStyleClass().add("dialog-subtitle");
        subLbl.setWrapText(true);
        subLbl.setMaxWidth(300);
        textBlock.getChildren().addAll(headline, subLbl);
        iconRow.getChildren().addAll(iconLbl, textBlock);
        body.getChildren().add(iconRow);

        dlg.getDialogPane().setContent(body);
        ButtonType skipBtn = new ButtonType("Skip", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, skipBtn);

        dlg.showAndWait().filter(b -> b == skipBtn).ifPresent(b -> {
            r.markRecorded(LocalDate.now());
            DataStore.getInstance().saveRecurringNow();
            if (onComplete != null) onComplete.run();
            if (postRefresh != null) postRefresh.run();
        });
    }
}
