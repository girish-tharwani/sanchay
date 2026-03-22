package com.sanchay.ui.recurring;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.ui.MainWindow;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.skin.DatePickerSkin;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

        Label title = new Label("Recurring Transactions");
        title.getStyleClass().add("screen-title");

        HBox headerRow = new HBox(12);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        Label sub = new Label("Manage repeating schedules — EMIs, SIPs, rent, salary, and more.");
        sub.setStyle("-fx-text-fill: #595959;");
        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);
        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> { openRecurringForm(null); buildView(); });
        headerRow.getChildren().addAll(sub, hSpacer, addBtn);

        content.getChildren().addAll(title, headerRow);

        // ── Pending section ────────────────────────────────────────────────────
        VBox pendingSection = new VBox(8);
        buildPendingSection(pendingSection);
        content.getChildren().add(pendingSection);

        // ── All schedules table ────────────────────────────────────────────────
        Label allTitle = new Label("All Schedules");
        allTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F4E79;");
        content.getChildren().add(allTitle);

        allSchedulesTable = new TableView<>();
        allSchedulesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        allSchedulesTable.setPrefHeight(300);
        allSchedulesTable.getItems().addAll(ds.getRecurring());

        TableColumn<RecurringTransaction, String> descCol = col("Description", 180,
                RecurringTransaction::getDescription);

        TableColumn<RecurringTransaction, Void> typeCol = new TableColumn<>("Type");
        typeCol.setMinWidth(100);
        typeCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                RecurringTransaction r = getTableRow().getItem();
                Label badge = new Label(UiUtils.badgeText(r.getTransactionType()));
                badge.getStyleClass().add(
                        "badge-" + r.getTransactionType().name().toLowerCase().replace("_", "-"));
                setGraphic(badge);
            }
        });

        TableColumn<RecurringTransaction, String> freqCol = col("Frequency", 100,
                r -> formatFrequency(r.getFrequency()));
        TableColumn<RecurringTransaction, String> amtCol  = col("Amount", 90,
                RecurringTransaction::getAmountInr);
        TableColumn<RecurringTransaction, String> nextCol = col("Next Due", 100,
                r -> r.getNextDueDate() != null ? r.getNextDueDate().format(fmt) : "—");
        TableColumn<RecurringTransaction, String> statusCol = col("Status", 80,
                r -> formatStatus(r.getStatus()));
        TableColumn<RecurringTransaction, String> catCol = col("Category", 120,
                r -> ds.getCategoryName(r.getCategoryId()));
        TableColumn<RecurringTransaction, String> subCatCol = col("Sub-category", 120,
                r -> ds.getCategoryName(r.getSubCategoryId()));

        // Actions column: pause/resume + delete
        TableColumn<RecurringTransaction, Void> actionsCol = new TableColumn<>("");
        actionsCol.setMinWidth(96);
        actionsCol.setMaxWidth(96);
        actionsCol.setCellFactory(tc -> new TableCell<>() {
            private final Label pauseBtn  = new Label();
            private final Label deleteBtn = new Label("×");
            {
                pauseBtn.setStyle(
                        "-fx-background-color: #E8F4F8; -fx-text-fill: #1A66CC; "
                        + "-fx-font-size: 10px; -fx-font-weight: bold; "
                        + "-fx-padding: 1 5; -fx-background-radius: 3; -fx-cursor: hand;");
                pauseBtn.setTooltip(new Tooltip("Pause / Resume"));
                deleteBtn.setStyle(
                        "-fx-background-color: #F5DADA; -fx-text-fill: #A93226; "
                        + "-fx-font-size: 10px; -fx-font-weight: bold; "
                        + "-fx-padding: 1 5; -fx-background-radius: 3; -fx-cursor: hand;");
                deleteBtn.setTooltip(new Tooltip("Delete schedule"));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                RecurringTransaction r = getTableRow().getItem();
                pauseBtn.setText(r.getStatus() == RecurringTransaction.Status.ACTIVE ? "‖" : "▶");
                pauseBtn.setOnMouseClicked(e -> {
                    r.setStatus(r.getStatus() == RecurringTransaction.Status.ACTIVE
                            ? RecurringTransaction.Status.PAUSED
                            : RecurringTransaction.Status.ACTIVE);
                    DataStore.getInstance().saveRecurringNow();
                    allSchedulesTable.refresh();
                });
                deleteBtn.setOnMouseClicked(e -> {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Delete Schedule");
                    confirm.setHeaderText("Delete '" + r.getDescription() + "'?");
                    confirm.setContentText("Past recorded transactions will not be affected.");
                    confirm.showAndWait()
                            .filter(b -> b == ButtonType.OK)
                            .ifPresent(b -> {
                                DataStore.getInstance().deleteRecurring(r.getId());
                                buildView();
                            });
                });
                setGraphic(new HBox(2, pauseBtn, deleteBtn));
            }
        });

        allSchedulesTable.setRowFactory(tv -> {
            TableRow<RecurringTransaction> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openRecurringForm(row.getItem());
                    buildView();
                }
            });
            return row;
        });

        allSchedulesTable.getColumns().addAll(
                descCol, typeCol, freqCol, amtCol, nextCol, statusCol, catCol, subCatCol, actionsCol);
        content.getChildren().add(allSchedulesTable);
        content.getChildren().add(UiUtils.hintLabel("Double-click a row to edit"));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #F5F6FA; -fx-background: #F5F6FA;");
        view.getChildren().setAll(scroll);
    }

    // ── Pending section ───────────────────────────────────────────────────────

    private void buildPendingSection(VBox container) {
        container.getChildren().clear();

        Label pendingTitle = new Label("Pending");
        pendingTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F4E79;");
        container.getChildren().add(pendingTitle);

        List<RecurringTransaction> pending = DataStore.getInstance().getPendingRecurring();
        if (pending.isEmpty()) {
            Label none = new Label("No pending transactions.");
            none.setStyle("-fx-text-fill: #9E9E9E;");
            container.getChildren().add(none);
            return;
        }
        for (RecurringTransaction r : pending) {
            container.getChildren().add(buildPendingRow(r, container));
        }
    }

    private HBox buildPendingRow(RecurringTransaction r, VBox container) {
        DataStore ds = DataStore.getInstance();
        DateTimeFormatter fmt = ds.getDateFormatter();

        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setAlignment(Pos.CENTER_LEFT);

        Label typeBadge = new Label(UiUtils.badgeText(r.getTransactionType()));
        typeBadge.setMinWidth(90);
        typeBadge.getStyleClass().add("badge-" + r.getTransactionType().name().toLowerCase().replace("_", "-"));

        Label desc = new Label(r.getDescription());
        desc.setStyle("-fx-font-weight: bold;");
        Label due  = new Label("Due: " + (r.getNextDueDate() != null ? r.getNextDueDate().format(fmt) : "—"));
        due.setStyle("-fx-text-fill: #595959; -fx-font-size: 12px;");
        VBox info = new VBox(2, desc, due);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label amount = new Label(r.getAmountInr());
        amount.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Button recordBtn = new Button("Record");
        recordBtn.getStyleClass().add("btn-primary");
        recordBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 12;");
        recordBtn.setOnAction(e ->
                mainWindow.recordRecurring(r, () -> {
                    buildPendingSection(container);
                    allSchedulesTable.refresh();
                }));

        Button skipBtn = new Button("Skip");
        skipBtn.getStyleClass().add("btn-secondary");
        skipBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 12;");
        skipBtn.setOnAction(e ->
                mainWindow.skipRecurring(r, () -> {
                    buildPendingSection(container);
                    allSchedulesTable.refresh();
                }));

        row.getChildren().addAll(typeBadge, info, sp, amount, recordBtn, skipBtn);
        return row;
    }

    // ── Add / Edit recurring form ─────────────────────────────────────────────

    private void openRecurringForm(RecurringTransaction existing) {
        boolean isNew = (existing == null);
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(isNew ? "New Recurring Schedule" : "Edit Recurring Schedule");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(520);

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(16));
        ColumnConstraints c1 = new ColumnConstraints(150);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        // ── Description ───────────────────────────────────────────────────────
        TextField descFld = new TextField(isNew ? "" : existing.getDescription());
        descFld.setPromptText("e.g. SBI Home Loan EMI");
        descFld.setMaxWidth(Double.MAX_VALUE);

        // ── Transaction type ──────────────────────────────────────────────────
        ComboBox<Transaction.Type> typeCb = new ComboBox<>();
        typeCb.getItems().addAll(
                Transaction.Type.EXPENSE, Transaction.Type.INCOME,
                Transaction.Type.TRANSFER, Transaction.Type.INVESTMENT,
                Transaction.Type.CC_PAYMENT, Transaction.Type.LOAN_PAYMENT);
        typeCb.setConverter(new StringConverter<>() {
            @Override public String toString(Transaction.Type t) {
                return t == null ? "" : UiUtils.badgeText(t);
            }
            @Override public Transaction.Type fromString(String s) { return null; }
        });
        typeCb.setValue(isNew ? Transaction.Type.EXPENSE : existing.getTransactionType());
        typeCb.setMaxWidth(Double.MAX_VALUE);

        // ── Frequency ─────────────────────────────────────────────────────────
        ComboBox<RecurringTransaction.Frequency> freqCb = new ComboBox<>();
        freqCb.getItems().addAll(RecurringTransaction.Frequency.values());
        freqCb.setValue(isNew ? RecurringTransaction.Frequency.MONTHLY : existing.getFrequency());
        freqCb.setMaxWidth(Double.MAX_VALUE);
        freqCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(RecurringTransaction.Frequency item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatFrequency(item));
            }
        });
        freqCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(RecurringTransaction.Frequency item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatFrequency(item));
            }
        });

        // ── Due day ───────────────────────────────────────────────────────────
        Spinner<Integer> daySpinner = new Spinner<>(1, 28, isNew ? 1 : existing.getDueDayOfMonth());
        daySpinner.setMaxWidth(Double.MAX_VALUE);

        // ── Start date ────────────────────────────────────────────────────────
        DatePicker startPicker = new DatePicker(isNew ? LocalDate.now()
                : (existing.getStartDate() != null ? existing.getStartDate() : LocalDate.now()));
        startPicker.setMaxWidth(Double.MAX_VALUE);
        UiUtils.applySmartDateConverter(startPicker);
        startPicker.showingProperty().addListener((obs, wasShowing, nowShowing) -> {
            if (nowShowing) Platform.runLater(() -> stylePickerPopup(startPicker));
        });

        // ── Amount ────────────────────────────────────────────────────────────
        TextField amtFld = new TextField(isNew ? ""
                : (existing.getAmountPaise() > 0
                ? String.format("%.2f", existing.getAmountPaise() / 100.0) : ""));
        amtFld.setPromptText("Leave blank for variable (e.g. CC Payment)");
        amtFld.setMaxWidth(Double.MAX_VALUE);

        // ── From Account (contents vary by type) ──────────────────────────────
        DataStore ds = DataStore.getInstance();
        ComboBox<Account> accountCb = new ComboBox<>();
        accountCb.setMaxWidth(Double.MAX_VALUE);

        // ── To Account controls ───────────────────────────────────────────────
        // Transfer → bank accounts
        ComboBox<Account> transferToCb = new ComboBox<>();
        transferToCb.setPromptText("Select destination account");
        transferToCb.setMaxWidth(Double.MAX_VALUE);
        ds.getBankAccounts().forEach(transferToCb.getItems()::add);

        // Investment → investment accounts
        ComboBox<InvestmentAccount> invDestCb = new ComboBox<>();
        invDestCb.setPromptText("Select investment account");
        invDestCb.setMaxWidth(Double.MAX_VALUE);
        ds.getInvestmentAccounts().forEach(invDestCb.getItems()::add);
        invDestCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(InvestmentAccount item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + "  (" + item.getAccountType() + ")");
            }
        });
        invDestCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(InvestmentAccount item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + "  (" + item.getAccountType() + ")");
            }
        });

        Label invTypeLbl = new Label("—");
        invTypeLbl.setStyle("-fx-text-fill: #595959; -fx-font-style: italic;");

        // CC Payment → credit card accounts
        ComboBox<Account> ccpCardCb = new ComboBox<>();
        ccpCardCb.setPromptText("Select credit card");
        ccpCardCb.setMaxWidth(Double.MAX_VALUE);
        ds.getCreditCardAccounts().forEach(ccpCardCb.getItems()::add);

        // Loan Payment → loan accounts
        ComboBox<Account> loanToCb = new ComboBox<>();
        loanToCb.setPromptText("Select loan account");
        loanToCb.setMaxWidth(Double.MAX_VALUE);
        ds.getActiveLoanAccounts().forEach(loanToCb.getItems()::add);

        // ── Investment type-specific fields ───────────────────────────────────
        TextField invSchemeFld    = new TextField();
        invSchemeFld.setPromptText("e.g. HDFC Flexi Cap Fund, RELIANCE (optional)");
        invSchemeFld.setMaxWidth(Double.MAX_VALUE);

        TextField invUnitsFld     = new TextField();
        invUnitsFld.setPromptText("Units / NAV at investment time (optional)");
        invUnitsFld.setMaxWidth(Double.MAX_VALUE);

        TextField invFdRefFld     = new TextField();
        invFdRefFld.setPromptText("FD reference number (optional)");
        invFdRefFld.setMaxWidth(Double.MAX_VALUE);

        TextField invFdRateFld    = new TextField();
        invFdRateFld.setPromptText("Annual interest rate, e.g. 6.5");
        invFdRateFld.setMaxWidth(Double.MAX_VALUE);

        DatePicker invFdMaturityPicker = new DatePicker();
        invFdMaturityPicker.setPromptText("Maturity date");
        invFdMaturityPicker.setMaxWidth(Double.MAX_VALUE);
        UiUtils.applySmartDateConverter(invFdMaturityPicker);

        TextField invFdMaturityAmtFld = new TextField();
        invFdMaturityAmtFld.setPromptText("Expected maturity amount (optional)");
        invFdMaturityAmtFld.setMaxWidth(Double.MAX_VALUE);

        TextField invRdRefFld     = new TextField();
        invRdRefFld.setPromptText("RD reference number (optional)");
        invRdRefFld.setMaxWidth(Double.MAX_VALUE);

        TextField invRdRateFld    = new TextField();
        invRdRateFld.setPromptText("Annual interest rate, e.g. 7.0");
        invRdRateFld.setMaxWidth(Double.MAX_VALUE);

        DatePicker invRdMaturityPicker = new DatePicker();
        invRdMaturityPicker.setPromptText("Maturity date");
        invRdMaturityPicker.setMaxWidth(Double.MAX_VALUE);
        UiUtils.applySmartDateConverter(invRdMaturityPicker);

        // ── Dynamic containers ────────────────────────────────────────────────
        VBox toAccountSection = new VBox(0);
        toAccountSection.setVisible(false);
        toAccountSection.setManaged(false);

        VBox invDynamicBox = new VBox(0);
        invDynamicBox.setVisible(false);
        invDynamicBox.setManaged(false);

        // ── Refresh logic ─────────────────────────────────────────────────────
        Runnable refreshInvFields = () -> {
            invDynamicBox.getChildren().clear();
            InvestmentAccount sel = invDestCb.getValue();
            if (sel == null) {
                invTypeLbl.setText("—");
                invDynamicBox.setVisible(false);
                invDynamicBox.setManaged(false);
                return;
            }
            invTypeLbl.setText(sel.getAccountType());
            GridPane dg = miniGrid();
            switch (sel.getInvestmentType()) {
                case MUTUAL_FUNDS, EQUITY, DEBT_BONDS -> {
                    formRow(dg, 0, "Scheme / Script", invSchemeFld);
                    formRow(dg, 1, "Units / NAV",     invUnitsFld);
                }
                case FIXED_DEPOSIT -> {
                    formRow(dg, 0, "FD Reference No",   invFdRefFld);
                    formRow(dg, 1, "Interest Rate (%)", invFdRateFld);
                    formRow(dg, 2, "Maturity Date",     invFdMaturityPicker);
                    formRow(dg, 3, "Maturity Amount",   invFdMaturityAmtFld);
                }
                case RECURRING_DEPOSIT -> {
                    formRow(dg, 0, "RD Reference No",   invRdRefFld);
                    formRow(dg, 1, "Interest Rate (%)", invRdRateFld);
                    formRow(dg, 2, "Maturity Date",     invRdMaturityPicker);
                }
                case PROVIDENT_FUND -> { /* no additional fields */ }
            }
            invDynamicBox.getChildren().add(dg);
            invDynamicBox.setVisible(true);
            invDynamicBox.setManaged(true);
        };

        Runnable refreshToAccount = () -> {
            Transaction.Type t = typeCb.getValue();

            // Update From Account contents based on type
            Account prevFrom = accountCb.getValue();
            accountCb.getItems().clear();
            if (t == Transaction.Type.EXPENSE) {
                ds.getBankAccounts().forEach(accountCb.getItems()::add);
                ds.getCreditCardAccounts().forEach(accountCb.getItems()::add);
            } else {
                ds.getBankAccounts().forEach(accountCb.getItems()::add);
            }
            // Preserve selection if still valid, else restore from existing, else pick first
            if (prevFrom != null && accountCb.getItems().contains(prevFrom)) {
                accountCb.setValue(prevFrom);
            } else if (!isNew && existing.getFromAccountId() != null) {
                accountCb.getItems().stream()
                        .filter(a -> a.getId().equals(existing.getFromAccountId()))
                        .findFirst().ifPresent(accountCb::setValue);
            }
            if (accountCb.getValue() == null && !accountCb.getItems().isEmpty())
                accountCb.setValue(accountCb.getItems().get(0));

            // Update To Account section
            boolean showTo = t == Transaction.Type.TRANSFER
                          || t == Transaction.Type.INVESTMENT
                          || t == Transaction.Type.CC_PAYMENT
                          || t == Transaction.Type.LOAN_PAYMENT;
            toAccountSection.getChildren().clear();
            invDynamicBox.getChildren().clear();
            invDynamicBox.setVisible(false);
            invDynamicBox.setManaged(false);
            invTypeLbl.setText("—");
            toAccountSection.setVisible(showTo);
            toAccountSection.setManaged(showTo);
            if (!showTo) return;

            GridPane tg = miniGrid();
            if (t == Transaction.Type.TRANSFER) {
                formRow(tg, 0, "To Account", transferToCb);
            } else if (t == Transaction.Type.INVESTMENT) {
                formRow(tg, 0, "To Account",      invDestCb);
                formRow(tg, 1, "Investment Type", invTypeLbl);
            } else if (t == Transaction.Type.CC_PAYMENT) {
                formRow(tg, 0, "To Account", ccpCardCb);
            } else {
                formRow(tg, 0, "To Account", loanToCb);
            }
            toAccountSection.getChildren().add(tg);
            if (t == Transaction.Type.INVESTMENT) refreshInvFields.run();
        };

        typeCb.setOnAction(e -> refreshToAccount.run());
        invDestCb.setOnAction(e -> refreshInvFields.run());

        // ── Category / Sub-category ───────────────────────────────────────────
        ComboBox<Category> catCb = new ComboBox<>();
        catCb.setMaxWidth(Double.MAX_VALUE);
        catCb.setPromptText("Select category (optional)");
        ds.getExpenseCategories().forEach(catCb.getItems()::add);
        if (!isNew && existing.getCategoryId() != null) {
            ds.getCategories().stream()
                    .filter(c -> c.getId().equals(existing.getCategoryId()))
                    .findFirst().ifPresent(catCb::setValue);
        }

        ComboBox<Category> subCatCb = new ComboBox<>();
        subCatCb.setMaxWidth(Double.MAX_VALUE);
        subCatCb.setPromptText("Select sub-category (optional)");
        subCatCb.setVisible(false);
        subCatCb.setManaged(false);
        subCatCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "  └ " + item.getName());
                setStyle(empty || item == null ? "" : "-fx-text-fill: #1A1A2E;");
            }
        });
        subCatCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "  └ " + item.getName());
            }
        });

        if (catCb.getValue() != null) {
            List<Category> subs = ds.getSubCategories(catCb.getValue().getId());
            if (!subs.isEmpty()) {
                subCatCb.getItems().addAll(subs);
                subCatCb.setVisible(true);
                subCatCb.setManaged(true);
                if (!isNew && existing.getSubCategoryId() != null) {
                    subs.stream().filter(s -> s.getId().equals(existing.getSubCategoryId()))
                            .findFirst().ifPresent(subCatCb::setValue);
                }
            }
        }

        catCb.setOnAction(e -> {
            Category sel = catCb.getValue();
            subCatCb.getItems().clear();
            subCatCb.setValue(null);
            if (sel != null) {
                List<Category> subs = ds.getSubCategories(sel.getId());
                if (!subs.isEmpty()) {
                    subCatCb.getItems().addAll(subs);
                    subCatCb.setVisible(true);
                    subCatCb.setManaged(true);
                } else {
                    subCatCb.setVisible(false);
                    subCatCb.setManaged(false);
                }
            } else {
                subCatCb.setVisible(false);
                subCatCb.setManaged(false);
            }
        });

        // ── Auto-record ───────────────────────────────────────────────────────
        CheckBox autoRecordCb = new CheckBox("Auto-record after");
        autoRecordCb.setStyle("-fx-text-fill: #1A1A2E;");
        Spinner<Integer> autoRecordDaysSp = new Spinner<>(1, 30, 3);
        autoRecordDaysSp.setPrefWidth(70);
        autoRecordDaysSp.setDisable(true);
        Label autoRecordSuffix = new Label("days overdue");
        autoRecordSuffix.setStyle("-fx-text-fill: #595959; -fx-font-size: 12px;");
        HBox autoRecordBox = new HBox(8, autoRecordCb, autoRecordDaysSp, autoRecordSuffix);
        autoRecordBox.setAlignment(Pos.CENTER_LEFT);
        autoRecordCb.selectedProperty().addListener((obs, o, n) -> autoRecordDaysSp.setDisable(!n));

        if (!isNew && existing.getAutoRecordAfterDays() > 0) {
            autoRecordCb.setSelected(true);
            autoRecordDaysSp.getValueFactory().setValue(existing.getAutoRecordAfterDays());
        }

        // ── Pre-select to-account when editing ────────────────────────────────
        if (!isNew && existing.getToAccountId() != null) {
            Transaction.Type t = existing.getTransactionType();
            if (t == Transaction.Type.TRANSFER) {
                ds.getBankAccounts().stream()
                        .filter(a -> a.getId().equals(existing.getToAccountId()))
                        .findFirst().ifPresent(transferToCb::setValue);
            } else if (t == Transaction.Type.INVESTMENT) {
                ds.getInvestmentAccounts().stream()
                        .filter(a -> a.getId().equals(existing.getToAccountId()))
                        .findFirst().ifPresent(invDestCb::setValue);
            } else if (t == Transaction.Type.CC_PAYMENT) {
                ds.getCreditCardAccounts().stream()
                        .filter(a -> a.getId().equals(existing.getToAccountId()))
                        .findFirst().ifPresent(ccpCardCb::setValue);
            } else if (t == Transaction.Type.LOAN_PAYMENT) {
                ds.getActiveLoanAccounts().stream()
                        .filter(a -> a.getId().equals(existing.getToAccountId()))
                        .findFirst().ifPresent(loanToCb::setValue);
            }
        }

        // Trigger initial state (also populates accountCb)
        refreshToAccount.run();

        // ── Layout ────────────────────────────────────────────────────────────
        int row = 0;
        formRow(g, row++, "Description*",    descFld);
        formRow(g, row++, "Type",            typeCb);
        formRow(g, row++, "Frequency",       freqCb);
        formRow(g, row++, "Due Day of Month",daySpinner);
        formRow(g, row++, "Start Date",      startPicker);
        formRow(g, row++, "Amount (₹)",      amtFld);
        formRow(g, row++, "From Account",    accountCb);
        g.add(toAccountSection, 0, row++, 2, 1);
        g.add(invDynamicBox,    0, row++, 2, 1);
        formRow(g, row++, "Category",        catCb);
        formRow(g, row++, "Sub-category",    subCatCb);
        g.add(autoRecordBox,    0, row,   2, 1);

        ScrollPane sp = new ScrollPane(g);
        sp.setFitToWidth(true);
        sp.setPrefHeight(440);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        dlg.getDialogPane().setContent(sp);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String desc = descFld.getText().trim();
            if (desc.isEmpty()) { alert("Validation", "Description is required."); return null; }

            Transaction.Type type               = typeCb.getValue();
            RecurringTransaction.Frequency freq = freqCb.getValue();
            int day   = daySpinner.getValue();
            LocalDate start = startPicker.getValue() != null ? startPicker.getValue() : LocalDate.now();

            // Validate to-account for types that require one
            if (type == Transaction.Type.TRANSFER && transferToCb.getValue() == null) {
                alert("Validation", "Select a destination account for the transfer."); return null;
            }
            if (type == Transaction.Type.INVESTMENT && invDestCb.getValue() == null) {
                alert("Validation", "Select a destination investment account."); return null;
            }
            if (type == Transaction.Type.CC_PAYMENT && ccpCardCb.getValue() == null) {
                alert("Validation", "Select a credit card for the payment."); return null;
            }
            if (type == Transaction.Type.LOAN_PAYMENT && loanToCb.getValue() == null) {
                alert("Validation", "Select a loan account for the payment."); return null;
            }

            long paise = 0;
            String amtRaw = amtFld.getText().trim().replace(",", "").replace("₹", "");
            if (!amtRaw.isEmpty()) {
                try { paise = Math.round(Double.parseDouble(amtRaw) * 100); }
                catch (NumberFormatException e) { alert("Validation", "Invalid amount."); return null; }
            }

            if (autoRecordCb.isSelected() && paise == 0) {
                alert("Validation", "Auto-record requires a fixed amount. Enter an amount or uncheck auto-record.");
                return null;
            }

            // Derive toAccountId
            String toAccountId = null;
            if (type == Transaction.Type.TRANSFER && transferToCb.getValue() != null)
                toAccountId = transferToCb.getValue().getId();
            else if (type == Transaction.Type.INVESTMENT && invDestCb.getValue() != null)
                toAccountId = invDestCb.getValue().getId();
            else if (type == Transaction.Type.CC_PAYMENT && ccpCardCb.getValue() != null)
                toAccountId = ccpCardCb.getValue().getId();
            else if (type == Transaction.Type.LOAN_PAYMENT && loanToCb.getValue() != null)
                toAccountId = loanToCb.getValue().getId();

            // Pack investment-specific fields into notes
            String invNotes = null;
            if (type == Transaction.Type.INVESTMENT && invDestCb.getValue() != null) {
                invNotes = buildInvNotes(invDestCb.getValue(),
                        invSchemeFld, invUnitsFld,
                        invFdRefFld, invFdRateFld, invFdMaturityPicker, invFdMaturityAmtFld,
                        invRdRefFld, invRdRateFld, invRdMaturityPicker);
            }

            String fromAccountId = accountCb.getValue() != null ? accountCb.getValue().getId() : null;
            int autoRecordDays   = autoRecordCb.isSelected() ? autoRecordDaysSp.getValue() : 0;

            if (isNew) {
                RecurringTransaction r = new RecurringTransaction(desc, type, freq, day, start, paise);
                r.setFromAccountId(fromAccountId);
                r.setToAccountId(toAccountId);
                if (catCb.getValue() != null)    r.setCategoryId(catCb.getValue().getId());
                if (subCatCb.getValue() != null) r.setSubCategoryId(subCatCb.getValue().getId());
                r.setNotes(invNotes);
                r.setAutoRecordAfterDays(autoRecordDays);
                r.setStatus(RecurringTransaction.Status.ACTIVE);
                ds.addRecurring(r);
            } else {
                existing.setDescription(desc);
                existing.setTransactionType(type);
                existing.setFrequency(freq);
                existing.setDueDayOfMonth(day);
                existing.setStartDate(start);
                existing.setAmountPaise(paise);
                existing.setFromAccountId(fromAccountId);
                existing.setToAccountId(toAccountId);
                existing.setCategoryId(catCb.getValue() != null ? catCb.getValue().getId() : null);
                existing.setSubCategoryId(subCatCb.getValue() != null ? subCatCb.getValue().getId() : null);
                existing.setNotes(invNotes);
                existing.setAutoRecordAfterDays(autoRecordDays);
                ds.saveRecurringNow();
            }
            return null;
        });

        dlg.showAndWait();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GridPane miniGrid() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setPadding(new Insets(0, 0, 4, 0));
        ColumnConstraints c1 = new ColumnConstraints(150);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);
        return g;
    }

    private String buildInvNotes(InvestmentAccount dest,
            TextField schemeFld, TextField unitsFld,
            TextField fdRefFld, TextField fdRateFld,
            DatePicker fdMaturityPicker, TextField fdMaturityAmtFld,
            TextField rdRefFld, TextField rdRateFld, DatePicker rdMaturityPicker) {
        StringBuilder sb = new StringBuilder();
        switch (dest.getInvestmentType()) {
            case MUTUAL_FUNDS, EQUITY, DEBT_BONDS -> {
                appendNote(sb, "Scheme/Script", schemeFld.getText().trim());
                appendNote(sb, "Units/NAV",     unitsFld.getText().trim());
            }
            case FIXED_DEPOSIT -> {
                appendNote(sb, "FD Ref",          fdRefFld.getText().trim());
                appendNote(sb, "Interest Rate",   fdRateFld.getText().trim());
                appendNote(sb, "Maturity Date",
                        fdMaturityPicker.getValue() != null ? fdMaturityPicker.getValue().toString() : "");
                appendNote(sb, "Maturity Amount", fdMaturityAmtFld.getText().trim());
            }
            case RECURRING_DEPOSIT -> {
                appendNote(sb, "RD Ref",        rdRefFld.getText().trim());
                appendNote(sb, "Interest Rate", rdRateFld.getText().trim());
                appendNote(sb, "Maturity Date",
                        rdMaturityPicker.getValue() != null ? rdMaturityPicker.getValue().toString() : "");
            }
            case PROVIDENT_FUND -> { /* no additional fields */ }
        }
        return sb.length() > 0 ? sb.toString().trim() : null;
    }

    private void appendNote(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank())
            sb.append(key).append(": ").append(value).append("\n");
    }

    private void formRow(GridPane g, int rowIdx, String labelText, Node control) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #1A1A2E; -fx-font-size: 12px;");
        lbl.setMinWidth(145);
        g.add(lbl,     0, rowIdx);
        g.add(control, 1, rowIdx);
        GridPane.setFillWidth(control, true);
    }

    private static String formatFrequency(RecurringTransaction.Frequency f) {
        if (f == null) return "—";
        return switch (f) {
            case MONTHLY        -> "Monthly";
            case QUARTERLY      -> "Quarterly";
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
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setMinWidth(width);
        c.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(extractor.apply(d.getValue())));
        return c;
    }

    /** Styles the DatePicker popup header via the skin since CSS doesn't reliably reach it. */
    private static void stylePickerPopup(DatePicker picker) {
        if (!(picker.getSkin() instanceof DatePickerSkin skin)) return;
        Node content = skin.getPopupContent();
        Node monthPane = content.lookup(".month-year-pane");
        if (monthPane == null) return;
        monthPane.setStyle("-fx-background-color: #E8EEF5;");
        monthPane.lookupAll(".label").forEach(n ->
                n.setStyle("-fx-text-fill: #1F4E79; -fx-font-weight: bold;"));
        monthPane.lookupAll(".left-arrow, .right-arrow").forEach(n ->
                n.setStyle("-fx-background-color: #1F4E79;"));
    }

    private void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
