package com.sanchay.ui.accounts;

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
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #7aa4b0;");

        // ── Imported block ────────────────────────────────────────────────────
        HBox importedSecLabel = sectionLabel("Imported transaction", "#3db89a");

        VBox importedBlock = new VBox(4);
        importedBlock.setStyle(
                "-fx-background-color: rgba(42,138,122,0.12), #f0f8f6; "
                + "-fx-background-insets: 0, 0 0 0 4; "
                + "-fx-background-radius: 8, 6; "
                + "-fx-padding: 10 12 10 14;");
        label(importedBlock, "IMPORTED",
                "-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: #2a8a7a; -fx-letter-spacing: 0.5;");
        label(importedBlock,
                imported.getDate() + "   " + imported.getAmountInr() + "   " + imported.getDescription(),
                "-fx-font-size: 12.5px; -fx-font-weight: 600; -fx-text-fill: #0f3d4a;");

        // ── Candidates ────────────────────────────────────────────────────────
        HBox matchesSecLabel = sectionLabel("Possible matches — select one", "#f0a500");

        ToggleGroup tg = new ToggleGroup();
        VBox candidateBox = new VBox(8);

        for (Transaction c : candidates) {
            RadioButton rb = new RadioButton();
            rb.setToggleGroup(tg);
            rb.setUserData(c);

            String catName    = ds.getCategoryName(c.getCategoryId());
            String subCatName = ds.getCategoryName(c.getSubCategoryId());
            String catLabel   = catName.isBlank() ? ""
                    : subCatName.isBlank() ? catName : catName + " › " + subCatName;

            VBox cCard = new VBox(3);
            label(cCard, c.getDate() + "   " + c.getAmountInr() + "   " + c.getDescription(),
                    "-fx-font-size: 12.5px; -fx-font-weight: 600; -fx-text-fill: #0f3d4a;");
            if (!catLabel.isBlank())
                label(cCard, catLabel, "-fx-font-size: 11px; -fx-text-fill: #7aa4b0;");

            HBox rbRow = new HBox(10, rb, cCard);
            rbRow.setStyle(
                    "-fx-background-color: #f0f8f6; "
                    + "-fx-background-radius: 8; -fx-padding: 10 12; "
                    + "-fx-border-color: rgba(42,138,122,0.22); "
                    + "-fx-border-radius: 8; -fx-border-width: 1;");
            rbRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(cCard, Priority.ALWAYS);

            // Highlight selected row
            rb.selectedProperty().addListener((obs, o, n) ->
                    rbRow.setStyle(n
                            ? "-fx-background-color: rgba(42,138,122,0.10), #f0f8f6; "
                              + "-fx-background-radius: 8; -fx-padding: 10 12; "
                              + "-fx-border-color: #2a8a7a; -fx-border-radius: 8; -fx-border-width: 1.5;"
                            : "-fx-background-color: #f0f8f6; "
                              + "-fx-background-radius: 8; -fx-padding: 10 12; "
                              + "-fx-border-color: rgba(42,138,122,0.22); "
                              + "-fx-border-radius: 8; -fx-border-width: 1;"));

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

    private static HBox sectionLabel(String text, String dotColor) {
        Circle dot = new Circle(4, Color.web(dotColor));
        Label lbl = new Label(text.toUpperCase());
        lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #7aa4b0;");
        HBox h = new HBox(8, dot, lbl);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static void label(VBox parent, String text, String style) {
        Label lbl = new Label(text);
        if (style != null) lbl.setStyle(style);
        lbl.setWrapText(true);
        parent.getChildren().add(lbl);
    }
}
