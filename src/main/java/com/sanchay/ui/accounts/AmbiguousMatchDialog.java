package com.sanchay.ui.accounts;

import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import javafx.geometry.Insets;
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
        setTitle("Ambiguous Match — Manual Selection Required");
        setHeaderText("The imported transaction below matches multiple existing entries.\n"
                + "Select the one to reconcile with, or add as a new transaction.");
        getDialogPane().setPrefWidth(600);

        DataStore ds = DataStore.getInstance();

        // ── Imported transaction card ─────────────────────────────────────────
        VBox importedCard = card("#EFF6FF");
        label(importedCard, "Imported", "-fx-font-weight: bold; -fx-text-fill: #1A66CC;");
        label(importedCard, imported.getDate() + "   " + imported.getAmountInr()
                + "   " + imported.getDescription(), "-fx-text-fill: #1A1A2E;");

        // ── Candidate list ────────────────────────────────────────────────────
        ToggleGroup tg = new ToggleGroup();
        VBox candidateBox = new VBox(8);

        for (Transaction c : candidates) {
            VBox cCard = card("#F9FBF2");
            RadioButton rb = new RadioButton();
            rb.setToggleGroup(tg);
            rb.setUserData(c);

            String catName    = ds.getCategoryName(c.getCategoryId());
            String subCatName = ds.getCategoryName(c.getSubCategoryId());
            String catLabel   = catName.isBlank() ? ""
                    : subCatName.isBlank() ? catName
                    : catName + " > " + subCatName;

            label(cCard, c.getDate() + "   " + c.getAmountInr() + "   " + c.getDescription(),
                    "-fx-text-fill: #1A1A2E;");
            if (!catLabel.isBlank())
                label(cCard, catLabel, "-fx-font-size: 11px; -fx-text-fill: #595959;");

            HBox rbRow = new HBox(8, rb, cCard);
            HBox.setHgrow(cCard, Priority.ALWAYS);
            candidateBox.getChildren().add(rbRow);
        }

        Label importedHdr = new Label("Imported transaction:");
        importedHdr.setStyle("-fx-font-weight: bold; -fx-text-fill: #1A1A2E;");
        Label matchesHdr = new Label("Possible matches (select one):");
        matchesHdr.setStyle("-fx-font-weight: bold; -fx-text-fill: #1A1A2E;");

        VBox content = new VBox(14,
                importedHdr,
                importedCard,
                new Separator(),
                matchesHdr,
                candidateBox);
        content.setPadding(new Insets(8));

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setPrefHeight(360);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
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

    private static VBox card(String bg) {
        VBox box = new VBox(4);
        box.setStyle("-fx-background-color: " + bg + "; "
                + "-fx-background-radius: 6; -fx-padding: 8;");
        return box;
    }

    private static void label(VBox parent, String text, String style) {
        Label lbl = new Label(text);
        if (style != null) lbl.setStyle(style);
        lbl.setWrapText(true);
        parent.getChildren().add(lbl);
    }
}
