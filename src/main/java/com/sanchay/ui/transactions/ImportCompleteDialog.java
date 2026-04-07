package com.sanchay.ui.transactions;

import com.sanchay.service.ImportService;
//import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Read-only summary dialog shown after a CSV import completes.
 */
public class ImportCompleteDialog {

    private final ImportService.ImportResult result;

    public ImportCompleteDialog(ImportService.ImportResult result) {
        this.result = result;
    }

    public void show() {
        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Import Complete", "✓", 420);

        VBox body = new VBox(0);
        body.setPadding(new Insets(16));

        HBox titleRow = new HBox(14);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setPadding(new Insets(0, 0, 14, 0));
        Label iconLbl = new Label("✓");
        // Inline required: gradient circle icon has no CSS class equivalent
        iconLbl.setStyle(
                "-fx-background-color: linear-gradient(135deg, #2a8a7a, #3db89a); "
                + "-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; "
                + "-fx-min-width: 40; -fx-max-width: 40; -fx-min-height: 40; -fx-max-height: 40; "
                + "-fx-background-radius: 20; -fx-alignment: CENTER; -fx-padding: 0;");
        Label titleLbl = new Label("Import Complete");
        titleLbl.getStyleClass().add("text-title-md");
        titleRow.getChildren().addAll(iconLbl, titleLbl);

        VBox lines = new VBox(0);
        lines.getStyleClass().add("info-box");
        lines.getChildren().addAll(
                importLine(result.newCount,                 "new transaction(s) added",                   true),
                importLine(result.reconciledCount,          "reconciled with existing manual entries",     true),
                importLine(result.recurringReconciledCount, "recorded against recurring schedule",         true),
                importLine(result.skippedCount,             "skipped (already imported)",                  false));

        body.getChildren().addAll(titleRow, lines);

        if (!result.ambiguous.isEmpty()) {
            Label warn = new Label("⚠  " + result.ambiguous.size() + " ambiguous match(es) resolved manually");
            warn.getStyleClass().add("text-warning-sm");
            body.getChildren().add(warn);
        }

        dlg.getDialogPane().setContent(body);
        ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().add(ok);
        dlg.showAndWait();
    }

    private static HBox importLine(int count, String desc, boolean successStyle) {
        HBox line = new HBox(10);
        line.setAlignment(Pos.CENTER_LEFT);
        line.setPadding(new Insets(8, 14, 8, 14));
        line.getStyleClass().add("divider-row-bottom");
        String checkBg  = (successStyle && count > 0) ? "#f0fdf4" : "#f8fbfc";
        String checkFg  = (successStyle && count > 0) ? "#16a34a" : "#7aa4b0";
        String checkBdr = (successStyle && count > 0) ? "#bbf7d0" : "rgba(42,138,122,0.15)";
        String symbol   = successStyle ? "✓" : "⊘";
        Label check = new Label(symbol);
        // Inline required: success/failure badge colours are runtime data
        check.setStyle("-fx-background-color: " + checkBg + "; -fx-text-fill: " + checkFg + "; "
                + "-fx-border-color: " + checkBdr + "; "
                + "-fx-border-radius: 10; -fx-background-radius: 10; "
                + "-fx-font-size: 10px; -fx-font-weight: bold; "
                + "-fx-min-width: 20; -fx-max-width: 20; -fx-min-height: 20; -fx-max-height: 20; "
                + "-fx-padding: 0; -fx-alignment: CENTER;");
        Label cnt = new Label(String.valueOf(count));
        // Inline required: count text colour depends on whether count > 0
        cnt.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; "
                + "-fx-text-fill: " + (count > 0 ? "-brand-dark" : "-text-hint") + "; "
                + "-fx-min-width: 28; -fx-alignment: CENTER_RIGHT;");
        Label txt = new Label(desc);
        txt.getStyleClass().add("text-body-muted");
        line.getChildren().addAll(check, cnt, txt);
        return line;
    }
}
