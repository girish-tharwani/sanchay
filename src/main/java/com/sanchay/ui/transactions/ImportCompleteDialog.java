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
        dlg.getDialogPane().setId("txn-import-complete-dialog-pane");

        VBox body = new VBox(0);
        body.setId("txn-import-complete-body");
        body.setPadding(new Insets(16));

        HBox titleRow = new HBox(14);
        titleRow.setId("txn-import-complete-title-row");
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setPadding(new Insets(0, 0, 14, 0));
        Label iconLbl = new Label("✓");
        iconLbl.setId("txn-import-complete-icon");
        iconLbl.getStyleClass().add("import-success-header");
        Label titleLbl = new Label("Import Complete");
        titleLbl.setId("txn-import-complete-title");
        titleLbl.getStyleClass().add("text-title-md");
        titleRow.getChildren().addAll(iconLbl, titleLbl);

        VBox lines = new VBox(0);
        lines.setId("txn-import-complete-summary-lines");
        lines.getStyleClass().add("info-box");
        lines.getChildren().addAll(
                importLine(result.newCount,                 "new transaction(s) added",                   true),
                importLine(result.reconciledCount,          "reconciled with existing manual entries",     true),
                importLine(result.recurringReconciledCount, "recorded against recurring schedule",         true),
                importLine(result.skippedCount,             "skipped (already imported)",                  false));

        body.getChildren().addAll(titleRow, lines);

        if (!result.ambiguous.isEmpty()) {
            Label warn = new Label("⚠  " + result.ambiguous.size() + " ambiguous match(es) resolved manually");
            warn.setId("txn-import-complete-warning");
            warn.getStyleClass().add("text-warning-sm");
            body.getChildren().add(warn);
        }

        dlg.getDialogPane().setContent(body);
        ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().add(ok);
        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ok);
        if (okBtn != null) okBtn.setId("txn-import-complete-ok-button");
        dlg.showAndWait();
    }

    private static HBox importLine(int count, String desc, boolean successStyle) {
        HBox line = new HBox(10);
        line.setId("txn-import-complete-line-" + desc.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""));
        line.setAlignment(Pos.CENTER_LEFT);
        line.setPadding(new Insets(8, 14, 8, 14));
        line.getStyleClass().add("divider-row-bottom");
        String symbol = successStyle ? "✓" : "⊘";
        Label check = new Label(symbol);
        check.setId(line.getId() + "-icon");
        check.getStyleClass().addAll("import-check-badge",
                (successStyle && count > 0) ? "import-check-badge-success" : "import-check-badge-neutral");
        Label cnt = new Label(String.valueOf(count));
        cnt.setId(line.getId() + "-count");
        cnt.getStyleClass().addAll("import-stat-count",
                count > 0 ? "import-stat-count-active" : "import-stat-count-zero");
        Label txt = new Label(desc);
        txt.setId(line.getId() + "-text");
        txt.getStyleClass().add("text-body-muted");
        line.getChildren().addAll(check, cnt, txt);
        return line;
    }
}
