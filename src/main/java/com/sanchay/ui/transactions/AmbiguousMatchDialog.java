package com.sanchay.ui.transactions;

import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

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
        setTitle("Ambiguous Match");
        setHeaderText(null);
        getDialogPane().setPrefWidth(560);
        UiUtils.applyStylesheet(this);
        UiUtils.setDialogHeader(this, "?", "Ambiguous Match");

        DataStore ds = DataStore.getInstance();

        // ── Subtitle ─────────────────────────────────────────────────────────
        Label subtitle = new Label(
                "Multiple existing transactions could match this import. Select one to reconcile.");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("text-hint");

        // ── Imported block ────────────────────────────────────────────────────
        HBox importedSecLabel = sectionLabel("Imported transaction", "#3db89a");

        VBox importedBlock = new VBox(4);
        importedBlock.getStyleClass().add("block-imported");
        Label importedChip = new Label("IMPORTED");
        importedChip.getStyleClass().add("chip-teal");
        Label importedInfo = new Label(
                imported.getDate() + "   " + imported.getAmountInr() + "   " + imported.getDescription());
        importedInfo.getStyleClass().add("text-step-title");
        importedInfo.setWrapText(true);
        importedBlock.getChildren().addAll(importedChip, importedInfo);

        // ── Candidates ────────────────────────────────────────────────────────
        HBox matchesSecLabel = sectionLabel("Possible matches — select one", "#f0a500");

        ToggleGroup tg = new ToggleGroup();
        VBox candidateBox = new VBox(8);

        for (Transaction c : candidates) {
            RadioButton rb = new RadioButton();
            rb.setToggleGroup(tg);
            rb.setUserData(c);

            String catName    = ds.getCategoryName(c.getClassification() != null ? c.getClassification().getCategoryId() : null);
            String subCatName = ds.getCategoryName(c.getClassification() != null ? c.getClassification().getSubCategoryId() : null);
            String catLabel   = catName.isBlank() ? ""
                    : subCatName.isBlank() ? catName : catName + " › " + subCatName;

            VBox cCard = new VBox(3);
            Label cInfo = new Label(c.getDate() + "   " + c.getAmountInr() + "   " + c.getDescription());
            cInfo.getStyleClass().add("text-step-title");
            cInfo.setWrapText(true);
            cCard.getChildren().add(cInfo);
            if (!catLabel.isBlank()) {
                Label cCat = new Label(catLabel);
                cCat.getStyleClass().add("text-hint");
                cCard.getChildren().add(cCat);
            }

            HBox rbRow = new HBox(10, rb, cCard);
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
        content.setPadding(new Insets(16, 16, 8, 16));

        ScrollPane sp = new ScrollPane(content);
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
        reconcileBtn.setDisable(true);
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static HBox sectionLabel(String text, String dotColor) {
        // Shape.fill cannot be set via style class; data-driven colour stays inline
        Circle dot = new Circle(4, Color.web(dotColor));
        Label lbl = new Label(text.toUpperCase());
        lbl.getStyleClass().add("section-group-label");
        HBox h = new HBox(8, dot, lbl);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }
}
