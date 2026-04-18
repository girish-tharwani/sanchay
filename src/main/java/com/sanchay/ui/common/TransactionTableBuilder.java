package com.sanchay.ui.common;

import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.util.ArrayList;
import java.util.List;

public final class TransactionTableBuilder {
    private TransactionTableBuilder() {}

    /**
     * Builds the standard set of transaction table columns.
     * Always includes: DATE, DESCRIPTION, TYPE, AMOUNT.
     * Optional: ACCOUNT (showAccount), SUB-CATEGORY (showSubCategory).
     */
    public static List<TableColumn<Transaction, ?>> buildStandardColumns(
            DataStore ds, boolean showAccount, boolean showSubCategory) {

        List<TableColumn<Transaction, ?>> cols = new ArrayList<>();

        TableColumn<Transaction, String> dateCol = new TableColumn<>("DATE");
        dateCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDate().format(ds.getDateFormatter())));
        dateCol.setPrefWidth(100);
        cols.add(dateCol);

        TableColumn<Transaction, String> descCol = new TableColumn<>("DESCRIPTION");
        descCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDescription()));
        descCol.setPrefWidth(200);
        descCol.setCellFactory(tc -> {
            TableCell<Transaction, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item);
                }
            };
            cell.getStyleClass().add("cell-desc");
            return cell;
        });
        cols.add(descCol);

        TableColumn<Transaction, Void> typeCol = new TableColumn<>("TYPE");
        typeCol.setPrefWidth(95);
        typeCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                setGraphic(UiUtils.typeBadge(getTableRow().getItem().getType()));
            }
        });
        cols.add(typeCol);

        if (showAccount) {
            TableColumn<Transaction, String> accountCol = new TableColumn<>("ACCOUNT");
            accountCol.setCellValueFactory(c ->
                    new SimpleStringProperty(ds.getAccountName(
                            c.getValue().getFromAccountId() != null
                                    ? c.getValue().getFromAccountId()
                                    : c.getValue().getToAccountId())));
            accountCol.setPrefWidth(130);
            cols.add(accountCol);
        }

        if (showSubCategory) {
            TableColumn<Transaction, String> subCatCol = new TableColumn<>("SUB-CATEGORY");
            subCatCol.setCellValueFactory(c ->
                    new SimpleStringProperty(ds.getCategoryName(
                            c.getValue().getClassification() != null
                                    ? c.getValue().getClassification().getSubCategoryId() : null)));
            subCatCol.setPrefWidth(120);
            cols.add(subCatCol);
        }

        TableColumn<Transaction, String> amtCol = new TableColumn<>("AMOUNT");
        amtCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTypedSignedAmountInr()));
        amtCol.setPrefWidth(100);
        amtCol.setCellFactory(tc -> {
            TableCell<Transaction, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item);
                }
            };
            cell.getStyleClass().add("cell-amt");
            return cell;
        });
        cols.add(amtCol);

        return cols;
    }
}
