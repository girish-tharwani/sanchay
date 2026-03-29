package com.sanchay.ui.accounts;

import com.sanchay.model.InvestmentAccount;
import com.sanchay.model.MarketValueEntry;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;

/**
 * Dialog for recording a market value snapshot for an Equity or Mutual Fund account.
 * The invested amount is computed as-of the chosen value date and stored as a snapshot.
 */
public class RecordMarketValueDialog extends Dialog<MarketValueEntry> {

    public RecordMarketValueDialog(InvestmentAccount account) {
        setTitle("Record Market Value");
        setHeaderText(null);
        getDialogPane().setPrefWidth(440);
        getDialogPane().getStyleClass().add("dialog-pane");
        UiUtils.applyStylesheet(this);
        UiUtils.setDialogHeader(this, "📈", "Record Market Value");

        DataStore ds = DataStore.getInstance();

        // ── Fields ────────────────────────────────────────────────────────────
        DatePicker valueDatePicker = new DatePicker(LocalDate.now());
        valueDatePicker.setMaxWidth(Double.MAX_VALUE);
        UiUtils.applySmartDateConverter(valueDatePicker);
        UiUtils.styleOnShow(valueDatePicker);

        TextField marketValueFld = new TextField();
        marketValueFld.setPromptText("e.g. 1,50,000");
        marketValueFld.setMaxWidth(Double.MAX_VALUE);

        // Live invested-as-of label — recomputes when date changes
        Label investedLbl = new Label();
        investedLbl.getStyleClass().add("text-hint");

        Runnable refreshInvested = () -> {
            LocalDate d = valueDatePicker.getValue();
            if (d == null) { investedLbl.setText(""); return; }
            long inv = ds.getInvestedPaiseAsOf(account, d);
            investedLbl.setText(String.format("Invested as of %s: ₹%,.0f",
                    d.format(ds.getDateFormatter()), inv / 100.0));
        };
        valueDatePicker.valueProperty().addListener((obs, o, n) -> refreshInvested.run());
        refreshInvested.run();

        // ── Layout ────────────────────────────────────────────────────────────
        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(12);
        g.setPadding(new Insets(20));
        ColumnConstraints c1 = new ColumnConstraints(140);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        int row = 0;
        addRow(g, row++, "Value Date*",     valueDatePicker);
        addRow(g, row++, "Market Value (₹)*", marketValueFld);
        g.add(investedLbl, 1, row);

        getDialogPane().setContent(g);

        ButtonType saveBtn   = new ButtonType("Save",   ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(saveBtn, cancelBtn);

        // Validate on save
        javafx.application.Platform.runLater(() -> {
            Button btn = (Button) getDialogPane().lookupButton(saveBtn);
            if (btn == null) return;
            btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                if (valueDatePicker.getValue() == null) {
                    showError("Value date is required."); ev.consume(); return;
                }
                if (valueDatePicker.getValue().isAfter(LocalDate.now())) {
                    showError("Value date cannot be in the future."); ev.consume(); return;
                }
                if (parseAmount(marketValueFld.getText()) <= 0) {
                    showError("Enter a valid market value greater than zero."); ev.consume();
                }
            });
        });

        setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            LocalDate date = valueDatePicker.getValue();
            long mv        = parseAmount(marketValueFld.getText());
            long invested  = ds.getInvestedPaiseAsOf(account, date);
            return new MarketValueEntry(account.getId(), date, mv, invested);
        });
    }

    private void addRow(GridPane g, int row, String label, javafx.scene.Node field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        g.add(lbl, 0, row);
        g.add(field, 1, row);
        GridPane.setFillWidth(field, true);
    }

    private long parseAmount(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Math.round(Double.parseDouble(s.replace(",", "").trim()) * 100); }
        catch (NumberFormatException e) { return 0; }
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}
