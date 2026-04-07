package com.sanchay.ui.transactions;

import com.sanchay.model.Account;
import com.sanchay.model.ImportMapping;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * Dialog shown before every CSV import — always displayed so the user can
 * confirm column mapping even when a saved mapping exists.
 * Pre-filled from the saved mapping when the header snapshot matches.
 *
 * Returns an {@link ImportMapping} on OK, empty on Cancel.
 *
 */
public class ImportMappingDialog extends Dialog<ImportMapping> {

    private static final String NONE = "— not mapped —";

    private static final List<String> DATE_FORMATS = List.of(
            "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "yyyy-MM-dd",
            "dd/MM/yy", "dd-MMM-yyyy", "dd-MMM-yy", "MM/dd/yyyy");

    private final Account       account;
    private final String[]      headers;
    private final ImportMapping prefilled;  // may be null
    private final String[]      sampleRow;  // first data row, used for date-format auto-detection; may be null

    // Controls
    private ComboBox<String> dateCb;
    private ComboBox<String> descCb;
    private RadioButton      singleRb;
    private RadioButton      splitRb;
    private ComboBox<String> amtCb;
    private ComboBox<String> debitCb;
    private ComboBox<String> creditCb;
    private ComboBox<String> fmtCb;
    private HBox             singleRow;
    private HBox             debitRow;
    private HBox             creditRow;

    /**
     * @param account   the account being imported into
     * @param headers   the first row (header row) of the CSV
     * @param prefilled saved mapping to pre-fill (null = fresh dialog)
     * @param sampleRow the first data row (row index 1), used for date-format auto-detection; may be null
     */
    public ImportMappingDialog(Account account, String[] headers, ImportMapping prefilled, String[] sampleRow) {
        this.account   = account;
        this.headers   = headers;
        this.prefilled = prefilled;
        this.sampleRow = sampleRow;

        UiUtils.initDialog(this, "Import CSV — " + account.getName(), "⇌", 520,
                "Map CSV columns to transaction fields."
                + (prefilled != null ? "  Pre-filled from saved mapping — please confirm." : ""));
        getDialogPane().setId("txn-import-mapping-dialog-pane");

        getDialogPane().setContent(buildContent());

        ButtonType importBt = new ButtonType("Import", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(importBt, ButtonType.CANCEL);

        // Validation: Date, Description and Amount must be mapped
        Button importBtn = (Button) getDialogPane().lookupButton(importBt);
        importBtn.setId("txn-import-mapping-import-button");
        importBtn.setDisable(true);
        Button cancelBtn = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) cancelBtn.setId("txn-import-mapping-cancel-button");
        Runnable validate = () -> {
            boolean dateOk = dateCb.getValue() != null && !NONE.equals(dateCb.getValue());
            boolean descOk = descCb.getValue() != null && !NONE.equals(descCb.getValue());
            boolean amtOk;
            if (singleRb.isSelected()) {
                amtOk = amtCb.getValue() != null && !NONE.equals(amtCb.getValue());
            } else {
                amtOk = (debitCb.getValue() != null && !NONE.equals(debitCb.getValue()))
                     || (creditCb.getValue() != null && !NONE.equals(creditCb.getValue()));
            }
            boolean fmtOk = fmtCb.getValue() != null && !fmtCb.getValue().isBlank();
            importBtn.setDisable(!(dateOk && descOk && amtOk && fmtOk));
        };
        dateCb.valueProperty().addListener((o, p, n) -> validate.run());
        descCb.valueProperty().addListener((o, p, n) -> validate.run());
        amtCb.valueProperty().addListener((o, p, n)  -> validate.run());
        debitCb.valueProperty().addListener((o, p, n) -> validate.run());
        creditCb.valueProperty().addListener((o, p, n) -> validate.run());
        fmtCb.valueProperty().addListener((o, p, n)  -> validate.run());
        validate.run();

        setResultConverter(bt -> {
            if (bt != importBt) return null;
            ImportMapping m = prefilled != null ? prefilled : new ImportMapping();
            m.setAccountId(account.getId());
            m.setColumnDate(dateCb.getValue());
            m.setColumnDescription(descCb.getValue());
            m.setAmountSplit(splitRb.isSelected());
            if (splitRb.isSelected()) {
                m.setColumnDebit(NONE.equals(debitCb.getValue())  ? null : debitCb.getValue());
                m.setColumnCredit(NONE.equals(creditCb.getValue()) ? null : creditCb.getValue());
                m.setColumnAmount(null);
            } else {
                m.setColumnAmount(amtCb.getValue());
                m.setColumnDebit(null);
                m.setColumnCredit(null);
            }
            m.setDateFormat(fmtCb.getValue());
            return m;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildContent() {
        // ── Detected columns as pills ─────────────────────────────────────────
        Label detectedLbl = new Label("DETECTED COLUMNS");
        detectedLbl.setId("txn-import-mapping-detected-columns-label");
        detectedLbl.getStyleClass().add("section-group-label");

        javafx.scene.layout.FlowPane pillBox = new javafx.scene.layout.FlowPane(6, 6);
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i];
            Label pill = new Label(h);
            pill.setId("txn-import-mapping-detected-column-" + i);
            pill.getStyleClass().add("chip-teal");
            pillBox.getChildren().add(pill);
        }

        VBox detectedBox = new VBox(6, detectedLbl, pillBox);
        detectedBox.setId("txn-import-mapping-detected-box");
        detectedBox.getStyleClass().add("info-box");

        GridPane g = UiUtils.buildFormGrid(160);
        g.setPadding(new Insets(14, 0, 4, 0));

        int row = 0;

        // Date column
        dateCb = colCombo(true);
        dateCb.setId("txn-import-mapping-date-column-combo");
        UiUtils.addFormRow(g, row++, "Date column *", dateCb);
        if (prefilled != null) dateCb.setValue(prefilled.getColumnDate());

        // Description column
        descCb = colCombo(true);
        descCb.setId("txn-import-mapping-description-column-combo");
        UiUtils.addFormRow(g, row++, "Description column *", descCb);
        if (prefilled != null) descCb.setValue(prefilled.getColumnDescription());

        // Amount type toggle
        singleRb = new RadioButton("Single amount column");
        splitRb  = new RadioButton("Separate Debit / Credit columns");
        singleRb.setId("txn-import-mapping-single-amount-radio");
        splitRb.setId("txn-import-mapping-split-amount-radio");
        ToggleGroup tg = new ToggleGroup();
        singleRb.setToggleGroup(tg); splitRb.setToggleGroup(tg);
        singleRb.setSelected(prefilled == null || !prefilled.isAmountSplit());
        splitRb.setSelected(prefilled != null && prefilled.isAmountSplit());

        HBox amtTypeRow = new HBox(16, singleRb, splitRb);
        amtTypeRow.setId("txn-import-mapping-amount-type-row");
        amtTypeRow.setAlignment(Pos.CENTER_LEFT);
        Label amtTypeLbl = new Label("Amount type *");
        amtTypeLbl.setId("txn-import-mapping-amount-type-label");
        amtTypeLbl.getStyleClass().add("form-label");
        g.add(amtTypeLbl, 0, row);
        g.add(amtTypeRow, 1, row++);

        // Single amount row
        amtCb = colCombo(true);
        amtCb.setId("txn-import-mapping-amount-column-combo");
        amtCb.setMaxWidth(Double.MAX_VALUE);
        singleRow = new HBox(amtCb);
        singleRow.setId("txn-import-mapping-single-amount-row");
        HBox.setHgrow(amtCb, Priority.ALWAYS);
        if (prefilled != null && !prefilled.isAmountSplit() && prefilled.getColumnAmount() != null)
            amtCb.setValue(prefilled.getColumnAmount());
        UiUtils.addFormRow(g, row++, "Amount column *", singleRow);

        // ── Split amount sub-section ──────────────────────────────────────────
        debitCb = colCombo(false);
        debitCb.setId("txn-import-mapping-debit-column-combo");
        debitCb.setMaxWidth(Double.MAX_VALUE);
        creditCb = colCombo(false);
        creditCb.setId("txn-import-mapping-credit-column-combo");
        creditCb.setMaxWidth(Double.MAX_VALUE);
        if (prefilled != null && prefilled.isAmountSplit() && prefilled.getColumnDebit() != null)
            debitCb.setValue(prefilled.getColumnDebit());
        if (prefilled != null && prefilled.isAmountSplit() && prefilled.getColumnCredit() != null)
            creditCb.setValue(prefilled.getColumnCredit());

        GridPane splitGrid = new GridPane();
        splitGrid.setId("txn-import-mapping-split-grid");
        splitGrid.setHgap(12); splitGrid.setVgap(8);
        ColumnConstraints sg1 = new ColumnConstraints(130);
        ColumnConstraints sg2 = new ColumnConstraints(); sg2.setHgrow(Priority.ALWAYS);
        splitGrid.getColumnConstraints().addAll(sg1, sg2);
        UiUtils.addFormRow(splitGrid, 0, "Debit column", debitCb);
        UiUtils.addFormRow(splitGrid, 1, "Credit column", creditCb);

        Label splitTitle = new Label("AMOUNT COLUMNS");
        splitTitle.setId("txn-import-mapping-split-title");
        splitTitle.getStyleClass().add("section-group-label");
        debitRow = new HBox();  // reuse field — content irrelevant, used only for visibility toggle
        creditRow = new HBox(); // same
        VBox splitSection = new VBox(8, splitTitle, splitGrid);
        splitSection.setId("txn-import-mapping-split-section");
        splitSection.getStyleClass().add("info-box");

        g.add(splitSection, 0, row++, 2, 1);

        // Toggle visibility
        updateAmountRows(singleRb.isSelected());
        singleRb.selectedProperty().addListener((o, p, n) -> {
            singleRow.setVisible(n);  singleRow.setManaged(n);
            splitSection.setVisible(!n); splitSection.setManaged(!n);
            getDialogPane().getScene().getWindow().sizeToScene();
        });
        // Override default updateAmountRows for the split section
        splitSection.setVisible(!singleRb.isSelected());
        splitSection.setManaged(!singleRb.isSelected());

        // Date format
        fmtCb = new ComboBox<>();
        fmtCb.setId("txn-import-mapping-date-format-combo");
        fmtCb.setEditable(true);
        fmtCb.getItems().addAll(DATE_FORMATS);
        fmtCb.setMaxWidth(Double.MAX_VALUE);
        if (prefilled != null && prefilled.getDateFormat() != null)
            fmtCb.setValue(prefilled.getDateFormat());
        UiUtils.addFormRow(g, row++, "Date format *", fmtCb);

        // When the user picks the date column, try to auto-detect the format from the first data row.
        Runnable autoDetectFmt = () -> {
            String col = dateCb.getValue();
            if (col == null || NONE.equals(col) || sampleRow == null) return;
            int idx = Arrays.asList(headers).indexOf(col);
            if (idx < 0 || idx >= sampleRow.length) return;
            String sample = sampleRow[idx].strip();
            if (sample.isBlank()) return;
            for (String fmt : DATE_FORMATS) {
                try {
                    LocalDate.parse(sample, DateTimeFormatter.ofPattern(fmt));
                    fmtCb.setValue(fmt);
                    break;
                } catch (DateTimeParseException ignored) {}
            }
        };
        dateCb.valueProperty().addListener((obs, old, col) -> autoDetectFmt.run());
        // If no format was pre-filled, attempt immediate detection from already-selected date column
        if (fmtCb.getValue() == null || fmtCb.getValue().isBlank()) autoDetectFmt.run();

        Label fmtHint = new Label("Common formats: dd/MM/yyyy · yyyy-MM-dd · dd-MMM-yyyy");
        fmtHint.setId("txn-import-mapping-date-format-hint");
        fmtHint.getStyleClass().add("text-hint");

        VBox content = new VBox(12, detectedBox, g, fmtHint);
        content.setId("txn-import-mapping-content");
        content.setPadding(new Insets(4, 8, 4, 8));
        return content;
    }

    private ComboBox<String> colCombo(boolean required) {
        ComboBox<String> cb = new ComboBox<>();
        if (!required) cb.getItems().add(NONE);
        cb.getItems().addAll(Arrays.asList(headers));
        cb.setMaxWidth(Double.MAX_VALUE);
        return cb;
    }

    private void updateAmountRows(boolean single) {
        singleRow.setVisible(single); singleRow.setManaged(single);
        // debitRow/creditRow visibility is managed inline via splitSection in buildContent()
    }
}
