package com.sanchay.ui.transactions;

import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Shown when the user right-clicks an I or ? badge and chooses "Merge with existing".
 *
 * Presents MANUAL transactions within ±10 days of the imported transaction as candidates.
 * When the user selects one and confirms:
 *   – The manual entry keeps its category/sub-category/member.
 *   – The import's date, amount and hash replace the manual entry's values.
 *   – The manual entry becomes RECONCILED.
 *   – The imported transaction is deleted from the store.
 *
 * Returns the selected manual {@link Transaction}, or {@code null} if cancelled.
 */
public class MergeWithManualDialog extends Dialog<Transaction> {

    public MergeWithManualDialog(Transaction imported, List<Transaction> candidates,
                                  DateTimeFormatter fmt) {
        UiUtils.initDialog(this, "Merge with Existing", "⇄", 560);
        getDialogPane().setId("txn-merge-dialog-pane");

        DataStore ds = DataStore.getInstance();

        Label subtitle = new Label(
                "Select a manually-entered transaction to merge this import into. "
                + "The manual entry keeps its category; the import date and amount will be used.");
        subtitle.setId("txn-merge-subtitle");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("text-hint");

        // ── Imported block ─────────────────────────────────────────────────────
        HBox importedSecLabel = UiUtils.buildSectionLabel("Imported transaction", UiUtils.HEX_BRAND_LIGHT);

        VBox importedBlock = new VBox(4);
        importedBlock.setId("txn-merge-imported-block");
        importedBlock.getStyleClass().add("block-imported");
        Label importedChip = new Label(
                imported.getSourceIndicator() == Transaction.SourceIndicator.AUTO_CATEGORIZED
                        ? "AUTO-CATEGORIZED" : "IMPORTED");
        importedChip.setId("txn-merge-imported-chip");
        importedChip.getStyleClass().add("chip-teal");
        Label importedInfo = new Label(
                imported.getDate().format(fmt)
                + "   " + imported.getAmountInr()
                + "   " + imported.getDescription());
        importedInfo.setId("txn-merge-imported-info");
        importedInfo.getStyleClass().add("text-step-title");
        importedInfo.setWrapText(true);
        importedBlock.getChildren().addAll(importedChip, importedInfo);

        // ── Candidates ─────────────────────────────────────────────────────────
        HBox matchesSecLabel = UiUtils.buildSectionLabel(
                "Manual transactions within ±10 days — select one", UiUtils.HEX_BRAND_ACCENT);

        ToggleGroup tg = new ToggleGroup();
        VBox candidateBox = new VBox(8);
        candidateBox.setId("txn-merge-candidate-box");

        for (int i = 0; i < candidates.size(); i++) {
            Transaction c = candidates.get(i);
            RadioButton rb = new RadioButton();
            rb.setId("txn-merge-choice-" + i);
            rb.setToggleGroup(tg);
            rb.setUserData(c);

            String catName    = ds.getCategoryName(
                    c.getClassification() != null ? c.getClassification().getCategoryId() : null);
            String subCatName = ds.getCategoryName(
                    c.getClassification() != null ? c.getClassification().getSubCategoryId() : null);
            String catLabel   = catName.isBlank() ? ""
                    : subCatName.isBlank() ? catName : catName + " › " + subCatName;

            VBox cCard = new VBox(3);
            cCard.setId("txn-merge-card-" + i);
            Label cInfo = new Label(
                    c.getDate().format(fmt)
                    + "   " + c.getAmountInr()
                    + "   " + c.getDescription());
            cInfo.setId("txn-merge-card-info-" + i);
            cInfo.getStyleClass().add("text-step-title");
            cInfo.setWrapText(true);
            cCard.getChildren().add(cInfo);
            if (!catLabel.isBlank()) {
                Label cCat = new Label(catLabel);
                cCat.setId("txn-merge-card-category-" + i);
                cCat.getStyleClass().add("text-hint");
                cCard.getChildren().add(cCat);
            }

            HBox rbRow = new HBox(10, rb, cCard);
            rbRow.setId("txn-merge-row-" + i);
            rbRow.getStyleClass().add("match-row");
            rbRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(cCard, Priority.ALWAYS);

            rb.selectedProperty().addListener((obs, o, n) -> {
                rbRow.getStyleClass().removeAll("match-row", "match-row-selected");
                rbRow.getStyleClass().add(n ? "match-row-selected" : "match-row");
            });

            candidateBox.getChildren().add(rbRow);
        }

        VBox content = new VBox(14,
                subtitle,
                importedSecLabel, importedBlock,
                matchesSecLabel, candidateBox);
        content.setId("txn-merge-content");
        content.setPadding(new Insets(16, 16, 8, 16));

        ScrollPane sp = new ScrollPane(content);
        sp.setId("txn-merge-scroll-pane");
        sp.setFitToWidth(true);
        sp.setPrefHeight(400);
        sp.getStyleClass().add("scroll-transparent");
        getDialogPane().setContent(sp);

        // ── Buttons ────────────────────────────────────────────────────────────
        ButtonType mergeBt = new ButtonType("Merge", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(mergeBt, ButtonType.CANCEL);

        Button mergeBtn = (Button) getDialogPane().lookupButton(mergeBt);
        mergeBtn.setId("txn-merge-confirm-button");
        mergeBtn.setDisable(true);
        Button cancelBtn = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) cancelBtn.setId("txn-merge-cancel-button");
        tg.selectedToggleProperty().addListener((o, p, n) -> mergeBtn.setDisable(n == null));

        setResultConverter(bt -> {
            if (bt == mergeBt) {
                Toggle sel = tg.getSelectedToggle();
                return sel == null ? null : (Transaction) sel.getUserData();
            }
            return null;
        });
    }
}
