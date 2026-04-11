package com.sanchay.ui.recurring;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.ui.MainWindow;
import com.sanchay.ui.UiUtils;
//import javafx.application.Platform;
//import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

//import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** Recurring Transactions screen. */
public class RecurringScreen {

    private final MainWindow  mainWindow;
    private final StackPane   view = new StackPane();
    private TableView<RecurringTransaction> allSchedulesTable;

    public RecurringScreen(MainWindow mainWindow) {
        if (mainWindow == null)
            throw new IllegalArgumentException("RecurringScreen requires a non-null MainWindow reference.");
        this.mainWindow = mainWindow;
        buildView();
    }

    public Node getView() { return view; }

    public void refresh() { buildView(); }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void buildView() {
        DataStore ds = DataStore.getInstance();
        DateTimeFormatter fmt = ds.getDateFormatter();

        VBox content = new VBox(20);
        content.getStyleClass().add("main-panel");
        content.setPadding(new Insets(24));

        // ── Page header ────────────────────────────────────────────────────────
        HBox pageHeader = new HBox();
        pageHeader.setAlignment(Pos.TOP_LEFT);
        VBox titleBlock = new VBox(3);
        Label title = new Label("Recurring Transactions");
        title.getStyleClass().add("screen-title");
        Label sub = new Label("Manage repeating schedules — EMIs, SIPs, rent, salary, and more.");
        sub.getStyleClass().add("text-hint");
        titleBlock.getChildren().addAll(title, sub);
        pageHeader.getChildren().add(titleBlock);

        content.getChildren().add(pageHeader);

        // ── Pending section ────────────────────────────────────────────────────
        VBox pendingSection = new VBox(8);
        buildPendingSection(pendingSection);
        content.getChildren().add(pendingSection);

        // ── All schedules table ────────────────────────────────────────────────
        HBox allHeader = UiUtils.buildSectionLabel("All Schedules", "#3db89a");
        Region allHeaderSpacer = new Region();
        HBox.setHgrow(allHeaderSpacer, Priority.ALWAYS);
        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("btn-gold");
        addBtn.setOnAction(e -> { AddEditRecurringDialog.show(null); buildView(); });
        allHeader.getChildren().addAll(allHeaderSpacer, addBtn);
        VBox.setMargin(allHeader, new Insets(6, 0, 0, 0));
        content.getChildren().add(allHeader);

        // ── Filter bar ────────────────────────────────────────────────────────
        ComboBox<Transaction.Type> typeFilter = new ComboBox<>();
        typeFilter.getItems().add(null);
        typeFilter.getItems().addAll(
                Transaction.Type.EXPENSE, Transaction.Type.INCOME, Transaction.Type.TRANSFER,
                Transaction.Type.CC_PAYMENT, Transaction.Type.LOAN_PAYMENT, Transaction.Type.INVESTMENT);
        typeFilter.setPromptText("All Types");
        typeFilter.getStyleClass().add("filter-field");
        typeFilter.setPrefWidth(130);
        typeFilter.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Transaction.Type t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty ? null : (t == null ? "All Types" : UiUtils.badgeText(t)));
            }
        });
        typeFilter.setButtonCell(typeFilter.getCellFactory().call(null));

        TextField searchField = new TextField();
        searchField.setPromptText("Search description…");
        searchField.getStyleClass().add("filter-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setMaxWidth(Double.MAX_VALUE);

        HBox filterRow = new HBox(10, typeFilter, searchField);
        filterRow.getStyleClass().add("filter-bar");
        filterRow.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(filterRow);

        allSchedulesTable = new TableView<>();
        allSchedulesTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<RecurringTransaction, String> descCol = col("Description", 180,
                RecurringTransaction::getDescription, "cell-desc");

        TableColumn<RecurringTransaction, Void> typeCol = new TableColumn<>("TYPE");
        typeCol.setPrefWidth(100);
        typeCol.setMinWidth(80);
        typeCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                RecurringTransaction r = empty ? null : getTableRow().getItem();
                if (r == null) { setGraphic(null); return; }
                Label badge = new Label(UiUtils.badgeText(r.getTransactionType()));
                badge.getStyleClass().add(
                        "badge-" + r.getTransactionType().name().toLowerCase().replace("_", "-"));
                setGraphic(badge);
            }
        });

        TableColumn<RecurringTransaction, String> freqCol = col("Frequency", 100,
                r -> formatFrequency(r.getFrequency()));
        TableColumn<RecurringTransaction, String> amtCol  = col("Amount", 90,
                RecurringTransaction::getAmountInr, "cell-amt");
        TableColumn<RecurringTransaction, java.time.LocalDate> nextCol = new TableColumn<>("NEXT DUE");
        nextCol.setPrefWidth(100);
        nextCol.setMinWidth(60);
        nextCol.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(d.getValue().getNextDueDate()));
        nextCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(java.time.LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty) { setText(null); return; }
                setText(date == null ? "—" : date.format(fmt));
            }
        });
        nextCol.setComparator(java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
        TableColumn<RecurringTransaction, String> statusCol = col("Status", 80,
                r -> formatStatus(r.getStatus()));
        TableColumn<RecurringTransaction, String> paymentsCol = col("Payments", 80,
                r -> r.getNumberOfPayments() != null
                        ? r.getPaymentsMade() + " / " + r.getNumberOfPayments()
                        : "—");

        // Actions column: record + pause/resume + delete
        TableColumn<RecurringTransaction, Void> actionsCol = new TableColumn<>("");
        actionsCol.setMinWidth(128);
        actionsCol.setMaxWidth(128);
        actionsCol.setCellFactory(tc -> new TableCell<>() {
            private final Button recordBtn = new Button("✓");
            private final Button pauseBtn  = new Button();
            private final Button deleteBtn = new Button("x");
            {
                recordBtn.getStyleClass().add("btn-action-sm");
                recordBtn.setTooltip(new Tooltip("Record occurrence"));
                pauseBtn.getStyleClass().add("btn-icon");
                pauseBtn.setTooltip(new Tooltip("Pause / Resume"));
                deleteBtn.getStyleClass().add("btn-danger-sm");
                deleteBtn.setTooltip(new Tooltip("Delete schedule"));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                RecurringTransaction r = getTableRow().getItem();
                recordBtn.setOnAction(e -> mainWindow.recordRecurring(r, () -> {
                    buildPendingSection(pendingSection);
                    allSchedulesTable.refresh();
                }));
                pauseBtn.setText(r.getStatus() == RecurringTransaction.Status.ACTIVE ? "‖" : "▶");
                pauseBtn.setOnAction(e -> {
                    r.setStatus(r.getStatus() == RecurringTransaction.Status.ACTIVE
                            ? RecurringTransaction.Status.PAUSED
                            : RecurringTransaction.Status.ACTIVE);
                    DataStore.getInstance().saveRecurringNow();
                    allSchedulesTable.refresh();
                });
                deleteBtn.setOnAction(e -> {
                    if (showDeleteScheduleConfirm(r)) {
                        DataStore.getInstance().deleteRecurring(r.getId());
                        buildView();
                    }
                });
                setGraphic(new HBox(2, recordBtn, pauseBtn, deleteBtn));
            }
        });

        allSchedulesTable.setRowFactory(tv -> {
            TableRow<RecurringTransaction> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    AddEditRecurringDialog.show(row.getItem());
                    buildView();
                }
            });
            return row;
        });

        allSchedulesTable.getColumns().addAll(
                descCol, typeCol, freqCol, amtCol, nextCol, statusCol, paymentsCol, actionsCol);

        Runnable applyFilter = () -> {
            String q = searchField.getText().toLowerCase().strip();
            Transaction.Type selType = typeFilter.getValue();
            List<RecurringTransaction> filtered = ds.getRecurring().stream()
                    .filter(r -> selType == null || r.getTransactionType() == selType)
                    .filter(r -> q.isEmpty() || r.getDescription().toLowerCase().contains(q))
                    .collect(Collectors.toList());
            allSchedulesTable.getItems().setAll(filtered);
        };
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        searchField.textProperty().addListener((obs, o, n) -> applyFilter.run());
        applyFilter.run();

        HBox tableFooter = new HBox();
        tableFooter.getStyleClass().add("table-footer");
        tableFooter.getChildren().add(UiUtils.hintLabel("Double-click a row to edit"));

        VBox tableCard = new VBox();
        tableCard.getStyleClass().add("table-card");
        allSchedulesTable.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(allSchedulesTable, Priority.ALWAYS);
        tableCard.getChildren().addAll(allSchedulesTable, tableFooter);
        VBox.setVgrow(tableCard, Priority.ALWAYS);
        content.getChildren().add(tableCard);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        scroll.getStyleClass().add("scroll-page-bg");
        view.getChildren().setAll(scroll);
    }

    // ── Pending section ───────────────────────────────────────────────────────

    private void buildPendingSection(VBox container) {
        container.getChildren().clear();

        container.getChildren().add(UiUtils.buildSectionLabel("Pending", "#f0a500"));

        List<RecurringTransaction> pending = DataStore.getInstance().getPendingRecurring();
        if (pending.isEmpty()) {
            Label none = new Label("No pending transactions.");
            none.getStyleClass().add("text-empty");
            container.getChildren().add(none);
            return;
        }

        // All pending rows in a single white card
        VBox pendingCard = new VBox(0);
        pendingCard.getStyleClass().add("table-card");
        for (int i = 0; i < pending.size(); i++) {
            HBox row = buildPendingRow(pending.get(i), container);
            // Last item: remove the bottom border added by .pending-item
            if (i == pending.size() - 1) {
                row.setStyle("-fx-border-color: transparent; -fx-border-width: 0;");
            }
            pendingCard.getChildren().add(row);
        }
        container.getChildren().add(pendingCard);
    }

    private HBox buildPendingRow(RecurringTransaction r, VBox container) {
        DataStore ds = DataStore.getInstance();
        DateTimeFormatter fmt = ds.getDateFormatter();

        HBox row = new HBox(12);
        row.getStyleClass().add("pending-item");
        row.setAlignment(Pos.CENTER_LEFT);

        Label typeBadge = new Label(UiUtils.badgeText(r.getTransactionType()));
        typeBadge.setMinWidth(100);
        typeBadge.getStyleClass().add("badge-" + r.getTransactionType().name().toLowerCase().replace("_", "-"));

        Label desc = new Label(r.getDescription());
        desc.getStyleClass().add("text-step-title");
        Label due  = new Label("Due: " + (r.getNextDueDate() != null ? r.getNextDueDate().format(fmt) : "—"));
        due.getStyleClass().add("text-hint");
        VBox info = new VBox(2, desc, due);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label amount = new Label(r.getAmountInr());
        amount.getStyleClass().add("text-step-title");
        amount.setMinWidth(110);

        Button recordBtn = new Button("✓");
        recordBtn.getStyleClass().add("btn-action-sm");
        recordBtn.setTooltip(new Tooltip("Record"));
        recordBtn.setOnAction(e ->
                mainWindow.recordRecurring(r, () -> {
                    buildPendingSection(container);
                    allSchedulesTable.refresh();
                }));

        Button skipBtn = new Button("≫");
        skipBtn.getStyleClass().add("btn-action-sm");
        skipBtn.setTooltip(new Tooltip("Skip"));
        skipBtn.setOnAction(e ->
                mainWindow.skipRecurring(r, () -> {
                    buildPendingSection(container);
                    allSchedulesTable.refresh();
                }));

        HBox.setMargin(skipBtn, new Insets(0, 16, 0, 0));
        row.getChildren().addAll(typeBadge, info, amount, recordBtn, skipBtn);
        return row;
    }

    private static String formatFrequency(RecurringTransaction.Frequency f) {
        if (f == null) return "—";
        return switch (f) {
            case MONTHLY        -> "Monthly";
            case QUARTERLY      -> "Quarterly";
            case HALF_YEARLY    -> "Half Yearly";
            case ANNUALLY       -> "Annually";
            case ALTERNATE_YEAR -> "Alternate Year";
        };
    }

    private static String formatStatus(RecurringTransaction.Status s) {
        if (s == null) return "—";
        return switch (s) {
            case ACTIVE    -> "Active";
            case PAUSED    -> "Paused";
            case COMPLETED -> "Completed";
        };
    }

    private <T> TableColumn<T, String> col(String title, double width,
                                           java.util.function.Function<T, String> extractor) {
        TableColumn<T, String> c = new TableColumn<>(title.toUpperCase());
        c.setPrefWidth(width);
        c.setMinWidth(Math.min(width, 60));
        c.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(extractor.apply(d.getValue())));
        return c;
    }

    private <T> TableColumn<T, String> col(String title, double width,
                                           java.util.function.Function<T, String> extractor,
                                           String cellStyleClass) {
        TableColumn<T, String> c = col(title, width, extractor);
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

    private boolean showDeleteScheduleConfirm(RecurringTransaction r) {
        Dialog<ButtonType> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Delete Schedule", "⚠", 420);

        VBox body = new VBox(14);
        body.setPadding(new Insets(16));

        HBox warnRow = new HBox(12);
        warnRow.setAlignment(Pos.CENTER_LEFT);
        Label warnIcon = new Label("⚠");
        // Inline required: colour computed from role (danger), no CSS token for icon-only size
        warnIcon.getStyleClass().add("icon-danger");
        VBox warnText = new VBox(4);
        Label headline = new Label("Delete '" + r.getDescription() + "'?");
        headline.getStyleClass().add("text-section-title");
        Label subLbl = new Label("Past recorded transactions will not be affected.");
        subLbl.getStyleClass().add("text-hint");
        subLbl.setWrapText(true);
        warnText.getChildren().addAll(headline, subLbl);
        warnRow.getChildren().addAll(warnIcon, warnText);

        VBox detailBlock = new VBox(5);
        detailBlock.getStyleClass().add("dialog-danger-block");
        Label descLbl = new Label(r.getDescription());
        // Inline required: colour computed from role (danger)
        descLbl.getStyleClass().add("text-body-strong");
        descLbl.setWrapText(true);
        HBox meta = new HBox(8);
        meta.setAlignment(Pos.CENTER_LEFT);
        Label amtLbl = new Label(r.getAmountInr());
        amtLbl.getStyleClass().add("text-amount-error");
        Label sep = new Label("·");
        sep.getStyleClass().add("text-hint");
        Label freqLbl = new Label(formatFrequency(r.getFrequency()));
        freqLbl.getStyleClass().add("text-hint");
        meta.getChildren().addAll(amtLbl, sep, freqLbl);
        detailBlock.getChildren().addAll(descLbl, meta);

        body.getChildren().addAll(warnRow, detailBlock);
        dlg.getDialogPane().setContent(body);

        ButtonType deleteBtn = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, deleteBtn);
        Button deleteButton = (Button) dlg.getDialogPane().lookupButton(deleteBtn);
        if (deleteButton != null) {
            ButtonBar.setButtonUniformSize(deleteButton, false);
            deleteButton.getStyleClass().add("btn-danger");
        }

        return dlg.showAndWait().filter(b -> b == deleteBtn).isPresent();
    }

}
