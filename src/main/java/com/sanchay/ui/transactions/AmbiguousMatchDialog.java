package com.sanchay.ui.transactions;

import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * Shown when an imported transaction matches more than one existing manual
 * entry for the same account, amount and approximate date.
 *
 * The user either:
 *   – Selects one candidate → dialog returns that Transaction (reconcile it), or
 *   – Clicks "Add as New" → dialog returns null (import without reconciling).
 *
 */
public class AmbiguousMatchDialog extends Dialog<Transaction> {

    /**
     * @param imported   the transaction parsed from the CSV
     * @param candidates two or more manual transactions that could be the same
     */
    public AmbiguousMatchDialog(Transaction imported, List<Transaction> candidates) {
        UiUtils.initDialog(this, "Ambiguous Match", "?", 560);
        getDialogPane().setId("txn-ambiguous-match-dialog-pane");

        DataStore ds = DataStore.getInstance();

        // ── Subtitle ─────────────────────────────────────────────────────────
        Label subtitle = new Label(
                "Multiple existing transactions could match this import. Select one to reconcile.");
        subtitle.setId("txn-ambiguous-match-subtitle");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("text-hint");

        // ── Imported block ────────────────────────────────────────────────────
        HBox importedSecLabel = UiUtils.buildSectionLabel("Imported transaction", UiUtils.HEX_BRAND_LIGHT);

        VBox importedBlock = new VBox(4);
        importedBlock.setId("txn-ambiguous-match-imported-block");
        importedBlock.getStyleClass().add("block-imported");
        Label importedChip = new Label("IMPORTED");
        importedChip.setId("txn-ambiguous-match-imported-chip");
        importedChip.getStyleClass().add("chip-teal");
        Label importedInfo = new Label(
                imported.getDate() + "   " + imported.getAmountInr() + "   " + imported.getDescription());
        importedInfo.setId("txn-ambiguous-match-imported-info");
        importedInfo.getStyleClass().add("text-step-title");
        importedInfo.setWrapText(true);
        importedBlock.getChildren().addAll(importedChip, importedInfo);

        // ── Candidates ────────────────────────────────────────────────────────
        HBox matchesSecLabel = UiUtils.buildSectionLabel("Possible matches — select one", UiUtils.HEX_BRAND_ACCENT);

        ToggleGroup tg = new ToggleGroup();
        VBox candidateBox = new VBox(8);
        candidateBox.setId("txn-ambiguous-match-candidate-box");

        for (int i = 0; i < candidates.size(); i++) {
            Transaction c = candidates.get(i);
            RadioButton rb = new RadioButton();
            rb.setId("txn-ambiguous-match-choice-" + i);
            rb.setToggleGroup(tg);
            rb.setUserData(c);

            String catName    = ds.getCategoryName(c.getClassification() != null ? c.getClassification().getCategoryId() : null);
            String subCatName = ds.getCategoryName(c.getClassification() != null ? c.getClassification().getSubCategoryId() : null);
            String catLabel   = catName.isBlank() ? ""
                    : subCatName.isBlank() ? catName : catName + " › " + subCatName;

            VBox cCard = new VBox(3);
            cCard.setId("txn-ambiguous-match-card-" + i);
            Label cInfo = new Label(c.getDate() + "   " + c.getAmountInr() + "   " + c.getDescription());
            cInfo.setId("txn-ambiguous-match-card-info-" + i);
            cInfo.getStyleClass().add("text-step-title");
            cInfo.setWrapText(true);
            cCard.getChildren().add(cInfo);
            if (!catLabel.isBlank()) {
                Label cCat = new Label(catLabel);
                cCat.setId("txn-ambiguous-match-card-category-" + i);
                cCat.getStyleClass().add("text-hint");
                cCard.getChildren().add(cCat);
            }

            HBox rbRow = new HBox(10, rb, cCard);
            rbRow.setId("txn-ambiguous-match-row-" + i);
            rbRow.getStyleClass().add("match-row");
            rbRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(cCard, Priority.ALWAYS);

            // Highlight selected row via CSS class toggle
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
        content.setId("txn-ambiguous-match-content");
        content.setPadding(new Insets(16, 16, 8, 16));

        ScrollPane sp = new ScrollPane(content);
        sp.setId("txn-ambiguous-match-scroll-pane");
        sp.setFitToWidth(true);
        sp.setPrefHeight(400);
        sp.getStyleClass().add("scroll-transparent");
        getDialogPane().setContent(sp);

        // ── Buttons ───────────────────────────────────────────────────────────
        ButtonType reconcileBt = new ButtonType("Reconcile with Selected",
                ButtonBar.ButtonData.OK_DONE);
        ButtonType newBt       = new ButtonType("Add as New",
                ButtonBar.ButtonData.OTHER);
        getDialogPane().getButtonTypes().addAll(reconcileBt, newBt, ButtonType.CANCEL);

        // Reconcile button enabled only when a radio is selected
        Button reconcileBtn = (Button) getDialogPane().lookupButton(reconcileBt);
        reconcileBtn.setId("txn-ambiguous-match-reconcile-button");
        reconcileBtn.setDisable(true);
        Button newBtn = (Button) getDialogPane().lookupButton(newBt);
        if (newBtn != null) newBtn.setId("txn-ambiguous-match-add-new-button");
        Button cancelBtn = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) cancelBtn.setId("txn-ambiguous-match-cancel-button");
        tg.selectedToggleProperty().addListener(
                (o, p, n) -> reconcileBtn.setDisable(n == null));

        setResultConverter(bt -> {
            if (bt == reconcileBt) {
                Toggle sel = tg.getSelectedToggle();
                return sel == null ? null : (Transaction) sel.getUserData();
            }
            if (bt == newBt) return null;   // null signals "add as new"
            return null;                    // CANCEL — caller checks for empty Optional
        });
    }

}
