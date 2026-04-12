package com.sanchay.ui.categories;

import com.sanchay.model.Category;
import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dialog for reviewing and categorizing uncategorized EXPENSE, INCOME, and REFUND transactions.
 * Supports interim saves — categorized rows are removed and the dialog stays open until
 * all transactions are fixed or the user closes it manually.
 */
public class UncategorizedReviewDialog {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final Runnable onSaved;

    public UncategorizedReviewDialog(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void show() {
        DataStore ds = DataStore.getInstance();

        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Uncategorized Transactions", "!", 820);
        dlg.getDialogPane().setPrefHeight(560);

        ObservableList<UncategorizedRow> items =
                FXCollections.observableArrayList(loadRows(ds));

        Label countLbl = new Label();
        countLbl.getStyleClass().add("text-section-title");
        updateCountLabel(countLbl, items.size());

        TableView<UncategorizedRow> table = new TableView<>(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setMinHeight(200);
        table.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);

        StringConverter<Category> catConv = new StringConverter<>() {
            @Override public String toString(Category c)   { return c == null ? "" : c.getName(); }
            @Override public Category fromString(String s) { return null; }
        };

        // DATE
        TableColumn<UncategorizedRow, String> dateCol = new TableColumn<>("DATE");
        dateCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().txn.getDate().format(DATE_FMT)));
        dateCol.setPrefWidth(95);

        // DESCRIPTION
        TableColumn<UncategorizedRow, String> descCol = new TableColumn<>("DESCRIPTION");
        descCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().txn.getDescription()));
        descCol.setPrefWidth(170);

        // TYPE
        TableColumn<UncategorizedRow, Void> typeCol = new TableColumn<>("TYPE");
        typeCol.setPrefWidth(95);
        typeCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                setGraphic(UiUtils.typeBadge(getTableRow().getItem().txn.getType()));
            }
        });

        // ACCOUNT
        TableColumn<UncategorizedRow, String> acctCol = new TableColumn<>("ACCOUNT");
        acctCol.setPrefWidth(115);
        acctCol.setCellValueFactory(c -> {
            Transaction t = c.getValue().txn;
            String id = t.getFromAccountId() != null ? t.getFromAccountId() : t.getToAccountId();
            return new javafx.beans.property.SimpleStringProperty(ds.getAccountName(id));
        });

        // CATEGORY (inline ComboBox)
        TableColumn<UncategorizedRow, Void> catCol = new TableColumn<>("CATEGORY");
        catCol.setPrefWidth(155);
        catCol.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<Category> combo = new ComboBox<>();
            private boolean updating = false;
            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setConverter(catConv);
                combo.setOnAction(e -> {
                    if (updating) return;
                    UncategorizedRow row = rowOf();
                    if (row != null) row.selectedCat.set(combo.getValue());
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                UncategorizedRow row = rowOf();
                if (empty || row == null) { setGraphic(null); return; }
                updating = true;
                combo.setItems(FXCollections.observableArrayList(row.catOptions));
                combo.setValue(row.selectedCat.get());
                updating = false;
                setGraphic(combo);
            }
            private UncategorizedRow rowOf() {
                return (getTableRow() == null || getTableRow().getItem() == null)
                        ? null : getTableRow().getItem();
            }
        });

        // SUB-CATEGORY (inline ComboBox, options driven by selectedCat)
        TableColumn<UncategorizedRow, Void> subCatCol = new TableColumn<>("SUB-CATEGORY");
        subCatCol.setPrefWidth(155);
        subCatCol.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<Category> combo = new ComboBox<>();
            private boolean updating = false;
            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setConverter(catConv);
                combo.setOnAction(e -> {
                    if (updating) return;
                    UncategorizedRow row = rowOf();
                    if (row != null) row.selectedSubCat.set(combo.getValue());
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                UncategorizedRow row = rowOf();
                if (empty || row == null) { setGraphic(null); return; }
                updating = true;
                combo.setItems(row.subCatOptions);
                combo.setValue(row.selectedSubCat.get());
                combo.setDisable(row.selectedCat.get() == null);
                updating = false;
                setGraphic(combo);
            }
            private UncategorizedRow rowOf() {
                return (getTableRow() == null || getTableRow().getItem() == null)
                        ? null : getTableRow().getItem();
            }
        });

        // Wire each row's sub-category options to its selected category
        for (UncategorizedRow row : items) {
            wireSubCatOptions(row, ds, table);
        }

        table.getColumns().addAll(dateCol, descCol, typeCol, acctCol, catCol, subCatCol);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        VBox.setVgrow(content, Priority.ALWAYS);
        content.getChildren().addAll(countLbl, table,
                UiUtils.hintLabel("Set a category for each row, then click Save. You can save in batches."));

        dlg.getDialogPane().setContent(content);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CLOSE);

        // Intercept Save — keep dialog open, only close when list is empty
        Button saveBtn = (Button) dlg.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            int saved = performSave(items, ds);
            if (saved > 0) {
                updateCountLabel(countLbl, items.size());
                if (onSaved != null) onSaved.run();
            }
            if (items.isEmpty()) {
                dlg.setResult(null);
                dlg.close();
            }
        });

        dlg.showAndWait();
    }

    // ── Save logic ────────────────────────────────────────────────────────────

    private int performSave(ObservableList<UncategorizedRow> items, DataStore ds) {
        List<UncategorizedRow> toSave = items.stream()
                .filter(r -> r.selectedCat.get() != null)
                .collect(Collectors.toList());
        if (toSave.isEmpty()) return 0;

        for (UncategorizedRow row : toSave) {
            Transaction t = row.txn;
            Transaction.Classification cl = t.getClassification();
            if (cl == null) {
                cl = new Transaction.Classification();
                t.setClassification(cl);
            }
            cl.setCategoryId(row.selectedCat.get().getId());
            cl.setSubCategoryId(row.selectedSubCat.get() != null
                    ? row.selectedSubCat.get().getId() : null);
        }
        ds.saveTransactionsNow();
        items.removeAll(toSave);
        return toSave.size();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void wireSubCatOptions(UncategorizedRow row, DataStore ds,
                                   TableView<UncategorizedRow> table) {
        row.selectedCat.addListener((obs, oldVal, newVal) -> {
            row.subCatOptions.setAll(newVal == null ? List.of() :
                    ds.getCategories().stream()
                            .filter(c -> newVal.getId().equals(c.getParentId()) && c.isActive())
                            .sorted(Comparator.comparing(Category::getName,
                                    String.CASE_INSENSITIVE_ORDER))
                            .toList());
            row.selectedSubCat.set(null);
            table.refresh();
        });
    }

    private List<UncategorizedRow> loadRows(DataStore ds) {
        return ds.getTransactions().stream()
                .filter(this::isUncategorized)
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .map(t -> new UncategorizedRow(t, ds))
                .collect(Collectors.toList());
    }

    private boolean isUncategorized(Transaction t) {
        return (t.getType() == Transaction.Type.EXPENSE
                || t.getType() == Transaction.Type.INCOME
                || t.getType() == Transaction.Type.REFUND)
                && (t.getClassification() == null
                    || t.getClassification().getCategoryId() == null);
    }

    private void updateCountLabel(Label lbl, int count) {
        lbl.setText(count + " uncategorized transaction"
                + (count == 1 ? "" : "s") + " remaining");
    }

    // ── Row model ─────────────────────────────────────────────────────────────

    private static class UncategorizedRow {
        final Transaction txn;
        final List<Category> catOptions;
        final ObjectProperty<Category> selectedCat    = new SimpleObjectProperty<>();
        final ObjectProperty<Category> selectedSubCat = new SimpleObjectProperty<>();
        final ObservableList<Category> subCatOptions  = FXCollections.observableArrayList();

        UncategorizedRow(Transaction txn, DataStore ds) {
            this.txn = txn;
            Category.CategoryType catType = txn.getType() == Transaction.Type.INCOME
                    ? Category.CategoryType.INCOME
                    : Category.CategoryType.EXPENSE;
            this.catOptions = ds.getCategories().stream()
                    .filter(c -> c.getType() == catType && c.getParentId() == null && c.isActive())
                    .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }
}
