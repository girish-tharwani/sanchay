package com.sanchay.ui.categories;

import com.sanchay.model.Category;
import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import com.sanchay.ui.transactions.TransactionDialog;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Read/edit dialog showing all transactions for a category (and its sub-categories).
 * Double-clicking a row (or pressing Enter) opens {@link TransactionDialog} for inline editing.
 */
class CategoryTransactionsDialog {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final Category cat;

    CategoryTransactionsDialog(Category cat) {
        this.cat = cat;
    }

    void show() {
        DataStore ds = DataStore.getInstance();

        // Collect all relevant category IDs (parent + its sub-categories)
        List<String> catIds;
        if (cat.getParentId() == null) {
            List<String> subIds = ds.getCategories().stream()
                    .filter(c -> cat.getId().equals(c.getParentId()))
                    .map(Category::getId)
                    .toList();
            catIds = new ArrayList<>();
            catIds.add(cat.getId());
            catIds.addAll(subIds);
        } else {
            catIds = new ArrayList<>();
            catIds.add(cat.getId());
        }

        List<Transaction> txns = ds.getTransactions().stream()
                .filter(t -> {
                    String tCatId    = t.getClassification() != null ? t.getClassification().getCategoryId() : null;
                    String tSubCatId = t.getClassification() != null ? t.getClassification().getSubCategoryId() : null;
                    return catIds.contains(tCatId) || (tSubCatId != null && catIds.contains(tSubCatId));
                })
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .toList();

        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Transactions — " + cat.getName(), "#", 700);
        dlg.getDialogPane().setPrefHeight(700);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        VBox.setVgrow(content, Priority.ALWAYS);

        Label countLbl = new Label(txns.size() + " transaction" + (txns.size() == 1 ? "" : "s") + " in '" + cat.getName() + "'");
        countLbl.getStyleClass().add("text-section-title");

        if (txns.isEmpty()) {
            Label none = new Label("No transactions found for this category.");
            none.getStyleClass().add("text-empty");
            content.getChildren().addAll(countLbl, none);
        } else {
            TableView<Transaction> table = new TableView<>();
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            table.setMinHeight(200);
            table.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(table, Priority.ALWAYS);

            TableColumn<Transaction, String> dateCol = new TableColumn<>("DATE");
            dateCol.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(c.getValue().getDate().format(DATE_FMT)));
            dateCol.setPrefWidth(100);

            TableColumn<Transaction, String> descCol = new TableColumn<>("DESCRIPTION");
            descCol.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(c.getValue().getDescription()));
            descCol.setPrefWidth(200);
            descCol.setCellFactory(tc -> {
                TableCell<Transaction, String> cell = new TableCell<>() {
                    @Override protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty); setText(empty || item == null ? null : item);
                    }
                };
                cell.getStyleClass().add("cell-desc");
                return cell;
            });

            TableColumn<Transaction, Void> typeCol = new TableColumn<>("TYPE");
            typeCol.setPrefWidth(95);
            typeCol.setCellFactory(tc -> new TableCell<>() {
                @Override protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                    setGraphic(UiUtils.typeBadge(getTableRow().getItem().getType()));
                }
            });

            TableColumn<Transaction, String> accountCol = new TableColumn<>("ACCOUNT");
            accountCol.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            ds.getAccountName(
                                    c.getValue().getFromAccountId() != null
                                            ? c.getValue().getFromAccountId()
                                            : c.getValue().getToAccountId())));
            accountCol.setPrefWidth(130);

            TableColumn<Transaction, String> subCatCol = new TableColumn<>("SUB-CATEGORY");
            subCatCol.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            ds.getCategoryName(c.getValue().getClassification() != null
                                    ? c.getValue().getClassification().getSubCategoryId() : null)));
            subCatCol.setPrefWidth(120);

            TableColumn<Transaction, String> amtCol = new TableColumn<>("AMOUNT");
            amtCol.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            c.getValue().getTypedSignedAmountInr()));
            amtCol.setPrefWidth(100);
            amtCol.setCellFactory(tc -> {
                TableCell<Transaction, String> cell = new TableCell<>() {
                    @Override protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty); setText(empty || item == null ? null : item);
                    }
                };
                cell.getStyleClass().add("cell-amt");
                return cell;
            });

            final List<String> finalCatIds = catIds;
            Runnable refreshTable = () -> {
                List<Transaction> updated = ds.getTransactions().stream()
                        .filter(tx -> {
                            String txCatId    = tx.getClassification() != null ? tx.getClassification().getCategoryId() : null;
                            String txSubCatId = tx.getClassification() != null ? tx.getClassification().getSubCategoryId() : null;
                            return finalCatIds.contains(txCatId) || (txSubCatId != null && finalCatIds.contains(txSubCatId));
                        })
                        .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                        .toList();
                table.getItems().setAll(updated);
            };
            table.setRowFactory(tv -> {
                TableRow<Transaction> row = new TableRow<>();
                row.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2 && !row.isEmpty()) {
                        new TransactionDialog(row.getItem()).showAndWait().ifPresent(saved -> {
                            refreshTable.run();
                            Platform.runLater(() -> restoreSelection(table, saved.getId()));
                        });
                    }
                });
                return row;
            });
            table.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    Transaction sel = table.getSelectionModel().getSelectedItem();
                    if (sel != null) {
                        new TransactionDialog(sel).showAndWait().ifPresent(saved -> {
                            refreshTable.run();
                            Platform.runLater(() -> restoreSelection(table, saved.getId()));
                        });
                    }
                }
            });

            table.getColumns().addAll(dateCol, descCol, typeCol, accountCol, subCatCol, amtCol);
            table.getItems().addAll(txns);

            content.getChildren().addAll(countLbl, table, UiUtils.hintLabel("Double-click a row to edit"));
        }

        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
    }

    private static void restoreSelection(TableView<Transaction> table, String id) {
        for (int i = 0; i < table.getItems().size(); i++) {
            if (table.getItems().get(i).getId().equals(id)) {
                table.getSelectionModel().select(i);
                table.scrollTo(i);
                table.requestFocus();
                return;
            }
        }
    }
}
