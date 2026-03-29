package com.sanchay.ui.accounts;

import com.sanchay.model.InvestmentAccount;
import com.sanchay.model.MarketValueEntry;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Read-only dialog showing the full market value history for an Equity or Mutual Fund account.
 */
public class MarketValueHistoryDialog extends Dialog<Void> {

    public MarketValueHistoryDialog(InvestmentAccount account) {
        setTitle("Market Value History");
        setHeaderText(null);
        getDialogPane().setPrefWidth(620);
        getDialogPane().getStyleClass().add("dialog-pane");
        UiUtils.applyStylesheet(this);
        UiUtils.setDialogHeader(this, "📊", "Market Value History — " + account.getName());

        DataStore ds = DataStore.getInstance();
        List<MarketValueEntry> entries = ds.getMarketValuesFor(account.getId());
        DateTimeFormatter fmt = ds.getDateFormatter();

        TableView<MarketValueEntry> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(360);
        table.setPlaceholder(new Label("No market value entries recorded yet."));

        TableColumn<MarketValueEntry, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(r ->
                new SimpleStringProperty(r.getValue().getValueDate().format(fmt)));
        dateCol.setPrefWidth(110);

        TableColumn<MarketValueEntry, String> mvCol = new TableColumn<>("Market Value");
        mvCol.setCellValueFactory(r ->
                new SimpleStringProperty(String.format("₹%,.0f", r.getValue().getMarketValuePaise() / 100.0)));
        mvCol.setPrefWidth(130);

        TableColumn<MarketValueEntry, String> invCol = new TableColumn<>("Invested");
        invCol.setCellValueFactory(r ->
                new SimpleStringProperty(String.format("₹%,.0f", r.getValue().getInvestedPaise() / 100.0)));
        invCol.setPrefWidth(130);

        TableColumn<MarketValueEntry, String> glCol = new TableColumn<>("Gain / Loss");
        glCol.setCellValueFactory(r -> {
            long gl = r.getValue().getGainLossPaise();
            return new SimpleStringProperty((gl >= 0 ? "+" : "") + String.format("₹%,.0f", gl / 100.0));
        });
        glCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(null); setStyle(""); return; }
                setText(val);
                // Inline required: colour is runtime data (gain vs loss)
                setStyle(val.startsWith("+") || val.startsWith("₹0")
                        ? "-fx-text-fill: #27AE60; -fx-font-weight: bold;"
                        : "-fx-text-fill: #C62828; -fx-font-weight: bold;");
            }
        });
        glCol.setPrefWidth(120);

        TableColumn<MarketValueEntry, String> glPctCol = new TableColumn<>("Gain %");
        glPctCol.setCellValueFactory(r -> {
            long inv = r.getValue().getInvestedPaise();
            long gl  = r.getValue().getGainLossPaise();
            if (inv <= 0) return new SimpleStringProperty("—");
            double pct = gl * 100.0 / inv;
            return new SimpleStringProperty(String.format("%+.2f%%", pct));
        });
        glPctCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null || val.equals("—")) { setText(val); setStyle(""); return; }
                setText(val);
                // Inline required: colour is runtime data (gain vs loss)
                setStyle(val.startsWith("+") || val.startsWith("0")
                        ? "-fx-text-fill: #27AE60; -fx-font-weight: bold;"
                        : "-fx-text-fill: #C62828; -fx-font-weight: bold;");
            }
        });
        glPctCol.setPrefWidth(90);

        table.getColumns().addAll(dateCol, mvCol, invCol, glCol, glPctCol);
        table.getItems().setAll(entries);

        VBox content = new VBox(table);
        content.setPadding(new Insets(16));

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }
}
