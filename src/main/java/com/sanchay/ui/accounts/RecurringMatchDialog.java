package com.sanchay.ui.accounts;

import com.sanchay.model.RecurringTransaction;
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
        setHeaderText(null);
        getDialogPane().setPrefWidth(520);
        UiUtils.applyStylesheet(this);

        DataStore ds = DataStore.getInstance();

        // ── Subtitle ─────────────────────────────────────────────────────────
        Label subtitle = new Label(
                "This imported transaction matches a recurring schedule. "
                + "Select the schedule to record against, or add as a new transaction.");
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
        HBox matchesSecLabel = sectionLabel("Matching recurring schedule(s)", "#f0a500");

        ToggleGroup tg = new ToggleGroup();
        VBox candidateBox = new VBox(8);

        for (RecurringTransaction r : candidates) {
            RadioButton rb = new RadioButton();
            rb.setToggleGroup(tg);
            rb.setUserData(r);

            String catName    = ds.getCategoryName(r.getCategoryId());
            String subCatName = ds.getCategoryName(r.getSubCategoryId());
            String catLabel   = catName.isBlank() ? ""
                    : subCatName.isBlank() ? catName : catName + " › " + subCatName;

            String nextDue = r.getNextDueDate() != null ? r.getNextDueDate().toString() : "—";
            String freqText = r.getFrequency().name()
                    .replace('_', ' ')
                    .toLowerCase();
            freqText = Character.toUpperCase(freqText.charAt(0)) + freqText.substring(1);

            // Frequency chip
            Label freqChip = new Label(freqText);
            freqChip.setStyle(
                    "-fx-font-size: 10px; -fx-font-weight: 700; "
                    + "-fx-background-color: rgba(42,138,122,0.12); "
                    + "-fx-text-fill: #2a8a7a; -fx-background-radius: 12; -fx-padding: 2 8;");

            HBox mainRow = new HBox(8);
            mainRow.setAlignment(Pos.CENTER_LEFT);
            Label mainLbl = new Label(r.getDescription() + "   " + r.getAmountInr());
            mainLbl.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 600; -fx-text-fill: #0f3d4a;");
            mainRow.getChildren().addAll(mainLbl, freqChip);

            VBox rCard = new VBox(3);
            rCard.getChildren().add(mainRow);
            label(rCard, "Due: " + nextDue, "-fx-font-size: 11px; -fx-text-fill: #7aa4b0;");
            if (!catLabel.isBlank())
                label(rCard, catLabel, "-fx-font-size: 11px; -fx-text-fill: #7aa4b0;");

            HBox rbRow = new HBox(10, rb, rCard);
            rbRow.setStyle(
                    "-fx-background-color: #f0f8f6; "
                    + "-fx-background-radius: 8; -fx-padding: 10 12; "
                    + "-fx-border-color: rgba(42,138,122,0.22); "
                    + "-fx-border-radius: 8; -fx-border-width: 1;");
            rbRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(rCard, Priority.ALWAYS);

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

        // Auto-select the only candidate when there's exactly one
        if (candidates.size() == 1) {
            tg.getToggles().get(0).setSelected(true);
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
