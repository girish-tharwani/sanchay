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

        UiUtils.applyStylesheet(this);
        setTitle("Import CSV — " + account.getName());
        setHeaderText(null);
        UiUtils.setDialogHeader(this, "⇌", "Import CSV — " + account.getName(),
                "Map CSV columns to transaction fields."
                + (prefilled != null ? "  Pre-filled from saved mapping — please confirm." : ""));
        getDialogPane().setPrefWidth(520);

        getDialogPane().setContent(buildContent());

        ButtonType importBt = new ButtonType("Import", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(importBt, ButtonType.CANCEL);

        // Validation: Date, Description and Amount must be mapped
        Button importBtn = (Button) getDialogPane().lookupButton(importBt);
        importBtn.setDisable(true);
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
        detectedLbl.getStyleClass().add("section-group-label");

        javafx.scene.layout.FlowPane pillBox = new javafx.scene.layout.FlowPane(6, 6);
        for (String h : headers) {
            Label pill = new Label(h);
            pill.getStyleClass().add("chip-teal");
            pillBox.getChildren().add(pill);
        }

        VBox detectedBox = new VBox(6, detectedLbl, pillBox);
        detectedBox.getStyleClass().add("info-box");

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(14, 0, 4, 0));
        ColumnConstraints c1 = new ColumnConstraints(160);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        int row = 0;

        // Date column
        dateCb = colCombo(true);
        addRow(g, row++, "Date column *", dateCb);
        if (prefilled != null) dateCb.setValue(prefilled.getColumnDate());

        // Description column
        descCb = colCombo(true);
        addRow(g, row++, "Description column *", descCb);
        if (prefilled != null) descCb.setValue(prefilled.getColumnDescription());

        // Amount type toggle
        singleRb = new RadioButton("Single amount column");
        splitRb  = new RadioButton("Separate Debit / Credit columns");
        ToggleGroup tg = new ToggleGroup();
        singleRb.setToggleGroup(tg); splitRb.setToggleGroup(tg);
        singleRb.setSelected(prefilled == null || !prefilled.isAmountSplit());
        splitRb.setSelected(prefilled != null && prefilled.isAmountSplit());

        HBox amtTypeRow = new HBox(16, singleRb, splitRb);
        amtTypeRow.setAlignment(Pos.CENTER_LEFT);
        Label amtTypeLbl = new Label("Amount type *");
        amtTypeLbl.getStyleClass().add("form-label");
        g.add(amtTypeLbl, 0, row);
        g.add(amtTypeRow, 1, row++);

        // Single amount row
        amtCb = colCombo(true);
        amtCb.setMaxWidth(Double.MAX_VALUE);
        singleRow = new HBox(amtCb);
        HBox.setHgrow(amtCb, Priority.ALWAYS);
        if (prefilled != null && !prefilled.isAmountSplit() && prefilled.getColumnAmount() != null)
            amtCb.setValue(prefilled.getColumnAmount());
        addRow(g, row++, "Amount column *", singleRow);

        // ── Split amount sub-section ──────────────────────────────────────────
        debitCb = colCombo(false);
        debitCb.setMaxWidth(Double.MAX_VALUE);
        creditCb = colCombo(false);
        creditCb.setMaxWidth(Double.MAX_VALUE);
        if (prefilled != null && prefilled.isAmountSplit() && prefilled.getColumnDebit() != null)
            debitCb.setValue(prefilled.getColumnDebit());
        if (prefilled != null && prefilled.isAmountSplit() && prefilled.getColumnCredit() != null)
            creditCb.setValue(prefilled.getColumnCredit());

        GridPane splitGrid = new GridPane();
        splitGrid.setHgap(12); splitGrid.setVgap(8);
        ColumnConstraints sg1 = new ColumnConstraints(130);
        ColumnConstraints sg2 = new ColumnConstraints(); sg2.setHgrow(Priority.ALWAYS);
        splitGrid.getColumnConstraints().addAll(sg1, sg2);
        addRow(splitGrid, 0, "Debit column", debitCb);
        addRow(splitGrid, 1, "Credit column", creditCb);

        Label splitTitle = new Label("AMOUNT COLUMNS");
        splitTitle.getStyleClass().add("section-group-label");
        debitRow = new HBox();  // reuse field — content irrelevant, used only for visibility toggle
        creditRow = new HBox(); // same
        VBox splitSection = new VBox(8, splitTitle, splitGrid);
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
        fmtCb.setEditable(true);
        fmtCb.getItems().addAll(DATE_FORMATS);
        fmtCb.setMaxWidth(Double.MAX_VALUE);
        if (prefilled != null && prefilled.getDateFormat() != null)
            fmtCb.setValue(prefilled.getDateFormat());
        addRow(g, row++, "Date format *", fmtCb);

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
        fmtHint.getStyleClass().add("text-hint");

        VBox content = new VBox(12, detectedBox, g, fmtHint);
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

    private void addRow(GridPane g, int row, String label, javafx.scene.Node control) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        g.add(lbl, 0, row);
        g.add(control, 1, row);
    }

    private void updateAmountRows(boolean single) {
        singleRow.setVisible(single); singleRow.setManaged(single);
        // debitRow/creditRow visibility is managed inline via splitSection in buildContent()
    }
}
