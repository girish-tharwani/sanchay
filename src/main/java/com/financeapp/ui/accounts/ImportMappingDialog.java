package com.financeapp.ui.accounts;

import com.financeapp.model.Account;
import com.financeapp.model.ImportMapping;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

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
     */
    public ImportMappingDialog(Account account, String[] headers, ImportMapping prefilled) {
        this.account   = account;
        this.headers   = headers;
        this.prefilled = prefilled;

        setTitle("Import CSV — " + account.getName());
        setHeaderText("Map CSV columns to transaction fields."
                + (prefilled != null ? "  (Pre-filled from saved mapping — please confirm.)" : ""));
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
        // Detected headers label
        Label detectedLbl = new Label("Detected columns:  "
                + String.join("  ·  ", headers));
        detectedLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #595959;");
        detectedLbl.setWrapText(true);

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
        singleRb.setStyle("-fx-text-fill: #1A1A2E;");
        splitRb  = new RadioButton("Separate Debit / Credit columns");
        splitRb.setStyle("-fx-text-fill: #1A1A2E;");
        ToggleGroup tg = new ToggleGroup();
        singleRb.setToggleGroup(tg); splitRb.setToggleGroup(tg);
        singleRb.setSelected(prefilled == null || !prefilled.isAmountSplit());
        splitRb.setSelected(prefilled != null && prefilled.isAmountSplit());

        HBox amtTypeRow = new HBox(16, singleRb, splitRb);
        amtTypeRow.setAlignment(Pos.CENTER_LEFT);
        Label amtTypeLbl = new Label("Amount type *");
        amtTypeLbl.setStyle("-fx-text-fill: #1A1A2E; -fx-font-weight: bold;");
        g.add(amtTypeLbl, 0, row);
        g.add(amtTypeRow, 1, row++);

        // Single amount row
        amtCb = colCombo(true);
        singleRow = new HBox(amtCb);
        if (prefilled != null && !prefilled.isAmountSplit() && prefilled.getColumnAmount() != null)
            amtCb.setValue(prefilled.getColumnAmount());
        addRow(g, row++, "Amount column *", singleRow);

        // Debit row
        debitCb = colCombo(false);
        debitRow = new HBox(debitCb);
        if (prefilled != null && prefilled.isAmountSplit() && prefilled.getColumnDebit() != null)
            debitCb.setValue(prefilled.getColumnDebit());
        addRow(g, row++, "Debit column", debitRow);

        // Credit row
        creditCb = colCombo(false);
        creditRow = new HBox(creditCb);
        if (prefilled != null && prefilled.isAmountSplit() && prefilled.getColumnCredit() != null)
            creditCb.setValue(prefilled.getColumnCredit());
        addRow(g, row++, "Credit column", creditRow);

        // Toggle visibility
        updateAmountRows(singleRb.isSelected());
        singleRb.selectedProperty().addListener((o, p, n) -> updateAmountRows(n));

        // Date format
        fmtCb = new ComboBox<>();
        fmtCb.setEditable(true);
        fmtCb.getItems().addAll(DATE_FORMATS);
        fmtCb.setMaxWidth(Double.MAX_VALUE);
        if (prefilled != null && prefilled.getDateFormat() != null)
            fmtCb.setValue(prefilled.getDateFormat());
        else
            fmtCb.setValue("dd/MM/yyyy");
        addRow(g, row++, "Date format *", fmtCb);

        Label fmtHint = new Label("Common formats: dd/MM/yyyy · yyyy-MM-dd · dd-MMM-yyyy");
        fmtHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #9E9E9E;");

        VBox content = new VBox(10, detectedLbl, g, fmtHint);
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
        lbl.setStyle("-fx-text-fill: #1A1A2E; -fx-font-weight: bold;");
        g.add(lbl, 0, row);
        g.add(control, 1, row);
    }

    private void updateAmountRows(boolean single) {
        singleRow.setVisible(single);  singleRow.setManaged(single);
        debitRow.setVisible(!single);  debitRow.setManaged(!single);
        creditRow.setVisible(!single); creditRow.setManaged(!single);
    }
}
