package com.sanchay.ui.dashboard;

import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Dashboard card that launches a global transaction search across all accounts. */
class DashboardTransactionSearchPanel {

    Node build() {
        VBox card = new VBox(0);
        card.getStyleClass().add("table-card");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 20, 14, 20));

        Circle dot = new Circle(4);
        //dot.setStyle("-fx-fill: " + UiUtils.HEX_BRAND_LIGHT + ";");
        dot.setStyle("-fx-fill: -brand-dark;");

        Label titleLbl = new Label("TRANSACTION SEARCH");
        titleLbl.getStyleClass().add("section-group-label");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        header.getChildren().addAll(dot, titleLbl, headerSpacer);

        Region sep = new Region();
        sep.getStyleClass().add("sep-teal");
        sep.setMaxWidth(Double.MAX_VALUE);

        Label fromLbl = new Label("FROM");
        fromLbl.getStyleClass().add("filter-label");

        javafx.scene.control.DatePicker fromPicker = new javafx.scene.control.DatePicker();
        fromPicker.getStyleClass().add("filter-field");
        fromPicker.setPrefWidth(130);
        UiUtils.applySmartDateConverter(fromPicker);
        UiUtils.styleOnShow(fromPicker);

        Label toLbl = new Label("TO");
        toLbl.getStyleClass().add("filter-label");

        javafx.scene.control.DatePicker toPicker = new javafx.scene.control.DatePicker();
        toPicker.getStyleClass().add("filter-field");
        toPicker.setPrefWidth(130);
        UiUtils.applySmartDateConverter(toPicker);
        UiUtils.styleOnShow(toPicker);

        ComboBox<Transaction.Type> typeFilter = new ComboBox<>();
        typeFilter.getItems().add(null);
        typeFilter.getItems().addAll(Transaction.Type.values());
        typeFilter.setPromptText("All Types");
        typeFilter.getStyleClass().add("filter-field");
        typeFilter.setPrefWidth(140);
        typeFilter.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Transaction.Type t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty ? null : (t == null ? "All Types" : UiUtils.badgeText(t)));
            }
        });
        typeFilter.setButtonCell(typeFilter.getCellFactory().call(null));

        TextField searchField = new TextField();
        searchField.setPromptText("Search description or notes…");
        searchField.getStyleClass().add("filter-field");
        searchField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Region filterSep = new Region();
        filterSep.getStyleClass().add("filter-separator");

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("btn-gold");

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().addAll("btn-secondary", "btn-secondary-compact");
        clearBtn.setOnAction(e -> {
            fromPicker.setValue(null);
            toPicker.setValue(null);
            typeFilter.setValue(null);
            searchField.clear();
        });

        HBox filterRow = new HBox(10,
                fromLbl, fromPicker,
                toLbl, toPicker,
                filterSep,
                typeFilter,
                searchField,
                searchBtn,
                clearBtn);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.setPadding(new Insets(12, 16, 14, 16));

        Runnable runSearch = () -> {
            List<Transaction> results = filterTransactions(
                    fromPicker.getValue(),
                    toPicker.getValue(),
                    typeFilter.getValue(),
                    searchField.getText());
            new DashboardTransactionSearchResultsDialog(
                    results,
                    fromPicker.getValue(),
                    toPicker.getValue(),
                    typeFilter.getValue(),
                    searchField.getText()
            ).show();
        };

        searchBtn.setOnAction(e -> runSearch.run());
        searchField.setOnAction(e -> runSearch.run());

        card.getChildren().addAll(header, sep, filterRow);
        return card;
    }

    private List<Transaction> filterTransactions(LocalDate fromDate,
                                                 LocalDate toDate,
                                                 Transaction.Type type,
                                                 String query) {
        String q = query == null ? "" : query.toLowerCase().strip();
        return DataStore.getInstance().getTransactions().stream()
                .filter(t -> fromDate == null || (t.getDate() != null && !t.getDate().isBefore(fromDate)))
                .filter(t -> toDate == null || (t.getDate() != null && !t.getDate().isAfter(toDate)))
                .filter(t -> type == null || t.getType() == type)
                .filter(t -> q.isEmpty()
                        || containsIgnoreCase(t.getDescription(), q)
                        || containsIgnoreCase(t.getNotes(), q))
                .sorted(Comparator.comparing(Transaction::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }
}
