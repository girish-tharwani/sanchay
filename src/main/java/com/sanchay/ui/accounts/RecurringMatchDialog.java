package com.sanchay.ui.accounts;

import com.sanchay.model.RecurringTransaction;
import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * Shown when an imported transaction matches one or more pending recurring schedules.
 *
 * The user either:
 *   – Selects a recurring candidate → dialog returns that RecurringTransaction, or
 *   – Clicks "Add as New"           → dialog returns ADD_AS_NEW sentinel, or
 *   – Clicks Cancel                 → dialog returns Optional.empty() (skip silently).
 */
public class RecurringMatchDialog extends Dialog<RecurringTransaction> {

    /**
     * Sentinel returned when the user clicks "Add as New".
     * Callers must check identity: {@code chosen == RecurringMatchDialog.ADD_AS_NEW}.
     */
    public static final RecurringTransaction ADD_AS_NEW =
            new RecurringTransaction("__ADD_AS_NEW__", Transaction.Type.EXPENSE,
                    RecurringTransaction.Frequency.MONTHLY, 1, null, 0L);

    /**
     * @param imported    the transaction parsed from the CSV
     * @param candidates  one or more recurring schedules that could cover this import
     */
    public RecurringMatchDialog(Transaction imported, List<RecurringTransaction> candidates) {
        setTitle("Recurring Schedule Match");
        setHeaderText("The imported transaction below matches a pending recurring schedule.\n"
                + "Select the schedule it belongs to, or add it as a new transaction.");
        getDialogPane().setPrefWidth(620);

        DataStore ds = DataStore.getInstance();

        // ── Imported transaction card ─────────────────────────────────────────
        VBox importedCard = card("#EFF6FF");
        label(importedCard, "Imported", "-fx-font-weight: bold; -fx-text-fill: #1A66CC;");
        label(importedCard,
                imported.getDate() + "   " + imported.getAmountInr()
                        + "   " + imported.getDescription(),
                "-fx-text-fill: #1A1A2E;");

        // ── Candidate list ────────────────────────────────────────────────────
        ToggleGroup tg = new ToggleGroup();
        VBox candidateBox = new VBox(8);

        for (RecurringTransaction r : candidates) {
            VBox rCard = card("#F2FBF4");
            RadioButton rb = new RadioButton();
            rb.setToggleGroup(tg);
            rb.setUserData(r);

            String catName    = ds.getCategoryName(r.getCategoryId());
            String subCatName = ds.getCategoryName(r.getSubCategoryId());
            String catLabel   = catName.isBlank() ? ""
                    : subCatName.isBlank() ? catName
                    : catName + " > " + subCatName;

            String nextDue = r.getNextDueDate() != null
                    ? r.getNextDueDate().toString() : "—";

            label(rCard,
                    r.getDescription() + "   " + r.getAmountInr()
                            + "   " + r.getFrequency().name().replace('_', ' '),
                    "-fx-text-fill: #1A1A2E;");
            label(rCard, "Due: " + nextDue,
                    "-fx-font-size: 11px; -fx-text-fill: #595959;");
            if (!catLabel.isBlank())
                label(rCard, catLabel, "-fx-font-size: 11px; -fx-text-fill: #595959;");

            HBox rbRow = new HBox(8, rb, rCard);
            HBox.setHgrow(rCard, Priority.ALWAYS);
            candidateBox.getChildren().add(rbRow);
        }

        // Auto-select the only candidate when there's exactly one
        if (candidates.size() == 1) {
            tg.getToggles().get(0).setSelected(true);
        }

        Label importedHdr = new Label("Imported transaction:");
        importedHdr.setStyle("-fx-font-weight: bold; -fx-text-fill: #1A1A2E;");
        Label matchesHdr = new Label("Matching recurring schedule(s):");
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
        ButtonType reconcileBt = new ButtonType("Record against Schedule",
                ButtonBar.ButtonData.OK_DONE);
        ButtonType newBt       = new ButtonType("Add as New",
                ButtonBar.ButtonData.OTHER);
        getDialogPane().getButtonTypes().addAll(reconcileBt, newBt, ButtonType.CANCEL);

        // "Record against Schedule" enabled only when a radio is selected
        Button reconcileBtn = (Button) getDialogPane().lookupButton(reconcileBt);
        reconcileBtn.setDisable(tg.getSelectedToggle() == null);
        tg.selectedToggleProperty().addListener(
                (o, p, n) -> reconcileBtn.setDisable(n == null));

        setResultConverter(bt -> {
            if (bt == reconcileBt) {
                Toggle sel = tg.getSelectedToggle();
                return sel == null ? null : (RecurringTransaction) sel.getUserData();
            }
            if (bt == newBt) return ADD_AS_NEW;
            return null;  // CANCEL → Optional.empty()
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
