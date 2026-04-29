package com.sanchay.ui.dashboard;

import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Modal dialog showing dashboard-wide transaction search results. */
class DashboardTransactionSearchResultsDialog extends Dialog<Void> {

    private final List<Transaction> results;
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final Transaction.Type typeFilter;
    private final String query;

    DashboardTransactionSearchResultsDialog(List<Transaction> results,
                                           LocalDate fromDate,
                                           LocalDate toDate,
                                           Transaction.Type typeFilter,
                                           String query) {
        this.results = results;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.typeFilter = typeFilter;
        this.query = query == null ? "" : query.strip();

        UiUtils.initDialog(this, "Matching Transactions", "#", 980,
                results.size() + " result" + (results.size() == 1 ? "" : "s") + " across all accounts");
        buildDialog();
    }

    private void buildDialog() {
        DataStore ds = DataStore.getInstance();
        DateTimeFormatter dateFmt = ds.getDateFormatter();

        VBox body = new VBox(14);
        body.getStyleClass().add("dashboard-search-dialog-body");

        Label criteria = new Label(buildCriteriaSummary());
        criteria.getStyleClass().add("fp-table-row-comment");
        criteria.setWrapText(true);

        TableView<Transaction> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<Transaction, LocalDate> dateCol = new TableColumn<>("DATE");
        dateCol.setPrefWidth(95);
        dateCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getDate()));
        dateCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : d.format(dateFmt));
            }
        });
        dateCol.setSortType(TableColumn.SortType.DESCENDING);

        TableColumn<Transaction, String> descCol = col("Description", 220,
                Transaction::getDescription, "cell-desc");

        TableColumn<Transaction, Void> typeCol = new TableColumn<>("TYPE");
        typeCol.setPrefWidth(96);
        typeCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(UiUtils.typeBadge(getTableRow().getItem().getType()));
            }
        });

        TableColumn<Transaction, String> fromAcctCol = col("From Account", 180,
                t -> accountName(t.getFromAccountId()));
        TableColumn<Transaction, String> toAcctCol = col("To Account", 180,
                t -> accountName(t.getToAccountId()));
        TableColumn<Transaction, String> amtCol = col("Amount", 120,
                t -> MoneyFormatter.format(t.getAmountPaise()), "cell-amt");

        table.getColumns().addAll(dateCol, descCol, typeCol, fromAcctCol, toAcctCol, amtCol);
        table.getItems().setAll(results);
        table.getSortOrder().add(dateCol);

        VBox tableCard = new VBox();
        tableCard.getStyleClass().add("table-card");
        tableCard.setPrefHeight(460);
        table.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);

        HBox footer = new HBox();
        footer.getStyleClass().add("table-footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getChildren().add(UiUtils.hintLabel("Matching description and notes are included in the search."));

        tableCard.getChildren().addAll(table, footer);

        if (results.isEmpty()) {
            Label none = new Label("No transactions matched the selected filters.");
            none.getStyleClass().add("text-empty");
            none.setPadding(new Insets(8, 0, 0, 0));
            body.getChildren().addAll(criteria, none);
        } else {
            body.getChildren().addAll(criteria, tableCard);
        }

        getDialogPane().setContent(body);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.scene.control.Button closeBtn = (javafx.scene.control.Button)
                getDialogPane().lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            ButtonBar.setButtonUniformSize(closeBtn, false);
            closeBtn.getStyleClass().add("btn-secondary");
        }
    }

    private String buildCriteriaSummary() {
        String fromText = fromDate == null ? "From beginning" : "From " + fromDate.format(DataStore.getInstance().getDateFormatter());
        String toText = toDate == null ? "To today" : "To " + toDate.format(DataStore.getInstance().getDateFormatter());
        String typeText = typeFilter == null ? "All types" : UiUtils.badgeText(typeFilter);
        String text = query.isBlank() ? "Any text" : "\"" + query + "\"";
        return fromText + "  ·  " + toText + "  ·  " + typeText + "  ·  " + text;
    }

    private String accountName(String accountId) {
        String name = DataStore.getInstance().getAccountName(accountId);
        return "—".equals(name) ? "" : name;
    }

    private <T> TableColumn<T, String> col(String title,
                                           int prefWidth,
                                           java.util.function.Function<T, String> extractor) {
        TableColumn<T, String> c = new TableColumn<>(title.toUpperCase());
        c.setPrefWidth(prefWidth);
        c.setCellValueFactory(cd -> new SimpleStringProperty(extractor.apply(cd.getValue())));
        return c;
    }

    private <T> TableColumn<T, String> col(String title,
                                           int prefWidth,
                                           java.util.function.Function<T, String> extractor,
                                           String cellStyleClass) {
        TableColumn<T, String> c = col(title, prefWidth, extractor);
        c.setCellFactory(tc -> {
            TableCell<T, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item);
                }
            };
            cell.getStyleClass().add(cellStyleClass);
            return cell;
        });
        return c;
    }
}
