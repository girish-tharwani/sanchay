package com.sanchay.ui.accounts;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.ImportService;
import com.sanchay.ui.MainWindow;
import com.sanchay.ui.UiUtils;
import com.sanchay.ui.transactions.TransactionDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

/** Accounts module. */
public class AccountsScreen {

    private static final PseudoClass PC_IMPORTED   = PseudoClass.getPseudoClass("row-imported");
    private static final PseudoClass PC_RECONCILED = PseudoClass.getPseudoClass("row-reconciled");


    private final MainWindow mainWindow;

    // view is initialised ONCE; never reassigned so that the reference held by MainWindow stays valid
    private final StackPane view;

    // "Show Closed" checkbox per group — state persists across buildList() rebuilds
    private final CheckBox showClosedBank       = new CheckBox("Show Closed");
    private final CheckBox showClosedCC         = new CheckBox("Show Closed");
    private final CheckBox showClosedLoan       = new CheckBox("Show Closed");
    private final CheckBox showClosedInvestment = new CheckBox("Show Closed");

    public AccountsScreen(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.view = new StackPane();
        buildList();
    }

    public Node getView() { return view; }

    private DateTimeFormatter dateFmt() {
        return DataStore.getInstance().getDateFormatter();
    }

    // ── Account list ──────────────────────────────────────────────────────────

    private void buildList() {
        mainWindow.setPostTransactionCallback(null);
        VBox content = new VBox(24);
        content.getStyleClass().add("main-panel");
        content.setPadding(new Insets(24));

        Label title = new Label("Accounts");
        title.getStyleClass().add("screen-title");

        DataStore ds = DataStore.getInstance();
        content.getChildren().addAll(
                title,
                buildGroup("Bank Accounts",   "#3db89a",
                        showClosedBank.isSelected() ? ds.getAllBankAccounts()       : ds.getBankAccounts(),
                        "bank", showClosedBank),
                buildGroup("Credit Cards",    "#a78bfa",
                        showClosedCC.isSelected()   ? ds.getAllCreditCardAccounts() : ds.getCreditCardAccounts(),
                        "cc", showClosedCC),
                buildGroup("Loan Accounts",   "#f87171",
                        showClosedLoan.isSelected()  ? ds.getAllLoanAccounts()       : ds.getActiveLoanAccounts(),
                        "loan", showClosedLoan),
                buildGroup("Investments",     "#f0a500",
                        showClosedInvestment.isSelected() ? ds.getAllInvestmentAccounts() : ds.getInvestmentAccounts(),
                        "investment", showClosedInvestment)
        );

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #eef4f5; -fx-background: #eef4f5;");

        view.getChildren().setAll(scroll);
    }

    private <T extends Account> VBox buildGroup(String heading, String dotColor, List<T> accounts, String type, CheckBox showClosedCb) {
        VBox group = new VBox(10);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        // Colored dot
        Circle dot = new Circle(4);
        dot.setFill(Color.web(dotColor));

        Label h = new Label(heading.toUpperCase());
        h.getStyleClass().add("filter-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        showClosedCb.setStyle("-fx-font-size: 11px; -fx-text-fill: #7aa4b0;");
        showClosedCb.setOnAction(e -> buildList());

        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("btn-gold");
        addBtn.setOnAction(e -> { openAddAccountDialog(type); buildList(); });
        header.getChildren().addAll(dot, h, spacer, showClosedCb, addBtn);
        group.getChildren().add(header);

        if (accounts.isEmpty()) {
            Label none = new Label("No accounts added yet.");
            none.setStyle("-fx-text-fill: #9E9E9E;");
            group.getChildren().add(none);
        } else {
            for (T acc : accounts) {
                group.getChildren().add(buildAccountCard(acc));
            }
        }
        return group;
    }

    private HBox buildAccountCard(Account acc) {
        HBox card = new HBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setAlignment(Pos.CENTER_LEFT);

        boolean active = acc.isActive();

        VBox info = new VBox(3);
        info.setMinWidth(200);
        info.setMaxWidth(200);
        Label name = new Label(acc.getName());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;"
                + (active ? " -fx-text-fill: #0f3d4a;" : " -fx-text-fill: #9E9E9E;"));
        name.setMaxWidth(200);
        Label sub  = new Label(acc.getAccountType());
        sub.setStyle("-fx-text-fill: #9E9E9E; -fx-font-size: 11px;");
        info.getChildren().addAll(name, sub);

        Label descLbl = new Label(
                (acc.getDescription() != null && !acc.getDescription().isBlank())
                        ? acc.getDescription() : "");
        descLbl.setStyle("-fx-text-fill: #9E9E9E; -fx-font-size: 11px; -fx-font-style: italic;");
        descLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(descLbl, Priority.ALWAYS);

        VBox balanceBox;
        if (active) {
            balanceBox = buildBalanceBox(acc);
        } else {
            Label statusBadge = new Label(formatAccountStatus(acc));
            statusBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 10;"
                    + " -fx-background-color: #F5F5F5; -fx-text-fill: #9E9E9E;");
            balanceBox = new VBox(statusBadge);
        }
        balanceBox.setAlignment(Pos.CENTER_RIGHT);

        Button detailsBtn = new Button("ℹ");
        detailsBtn.getStyleClass().add("btn-icon");
        detailsBtn.setTooltip(new Tooltip("Details"));
        detailsBtn.setOnAction(e -> showAccountDetails(acc));

        Button txnBtn = new Button("≡");
        txnBtn.getStyleClass().add("btn-icon");
        txnBtn.setTooltip(new Tooltip("Transactions"));
        txnBtn.setOnAction(e -> showAccountTransactions(acc));

        card.getChildren().addAll(info, descLbl, balanceBox, detailsBtn, txnBtn);
        return card;
    }

    private VBox buildBalanceBox(Account acc) {
        DataStore ds = DataStore.getInstance();
        String label;
        String value;
        String valueColour;

        if (acc instanceof BankAccount ba) {
            long bal = computeRunningBalance(ba);
            label = "Balance";
            value = String.format("₹%,.2f", bal / 100.0);
            valueColour = bal >= 0 ? "#0f3d4a" : "#c0392b";
        } else if (acc instanceof CreditCardAccount cc) {
            long out = ds.getCreditCardOutstandingPaise(cc.getId());
            long avail = cc.getCreditLimitPaise() - out;
            label = "Outstanding / Available";
            value = String.format("₹%,.0f  /  ₹%,.0f", out / 100.0, avail / 100.0);
            valueColour = out > 0 ? "#c0392b" : "#0f3d4a";
        } else if (acc instanceof LoanAccount la) {
            long paid = ds.getTransactions().stream()
                    .filter(t -> (t.getType() == Transaction.Type.TRANSFER
                              || t.getType() == Transaction.Type.LOAN_PAYMENT)
                              && la.getId().equals(t.getToAccountId()))
                    .mapToLong(Transaction::getAmountPaise).sum();
            long outstanding = Math.max(0, la.getOutstandingPrincipalPaise() - paid);
            label = "Outstanding";
            value = String.format("₹%,.2f", outstanding / 100.0);
            valueColour = outstanding > 0 ? "#c0392b" : "#0f3d4a";
        } else if (acc instanceof InvestmentAccount ia) {
            label = "Invested";
            long invested = ia.getInvestedAmountPaise()
                    + ds.getTransactions().stream()
                        .filter(t -> t.getType() == Transaction.Type.INVESTMENT
                                  && ia.getId().equals(t.getToAccountId()))
                        .mapToLong(Transaction::getAmountPaise).sum()
                    - ds.getTransactions().stream()
                        .filter(t -> t.getType() == Transaction.Type.TRANSFER
                                  && ia.getId().equals(t.getFromAccountId()))
                        .mapToLong(Transaction::getAmountPaise).sum();
            value = String.format("₹%,.2f", Math.max(0, invested) / 100.0);
            valueColour = "#2a8a7a";
        } else {
            return new VBox();
        }

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: 600; -fx-text-fill: #7aa4b0;");
        Label val = new Label(value);
        val.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: " + valueColour + ";");
        VBox box = new VBox(2, lbl, val);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    // ── Account Details view ──────────────────────────────────────────────────

    private void showAccountDetails(Account acc) {
        VBox panel = new VBox(16);
        panel.getStyleClass().add("main-panel");
        panel.setPadding(new Insets(24));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Button back = new Button("⬅");
        back.getStyleClass().add("btn-secondary");
        back.setTooltip(new Tooltip("Back"));
        back.setOnAction(e -> buildList());
        Label title = new Label(acc.getName() + " — Details");
        title.getStyleClass().add("screen-title");
        header.getChildren().addAll(back, title);

        VBox fields = new VBox(8);
        addField(fields, "Account Type", acc.getAccountType());
        if (acc.getDescription() != null && !acc.getDescription().isBlank())
            addField(fields, "Description", acc.getDescription());
        addField(fields, "Currency",     acc.getCurrency());
        addField(fields, "Status",       formatAccountStatus(acc));
        if (acc.getOpeningDate() != null)
            addField(fields, "Opening Date", acc.getOpeningDate().format(dateFmt()));
        if (acc.isJointAccount())
            addField(fields, "Joint Holder", acc.getSecondHolderName());

        if (acc instanceof BankAccount ba) {
            addField(fields, "Account Number", ba.getMaskedAccountNumber());
            addField(fields, "Bank",
                    ba.getBankName());
            addField(fields, "Holder",         ba.getAccountHolder());
            addField(fields, "Current Balance",
                    String.format("₹%,.2f", computeRunningBalance(ba) / 100.0));
        } else if (acc instanceof CreditCardAccount cc) {
            addField(fields, "Card Number",  cc.getMaskedCardNumber());
            addField(fields, "Issuer",       cc.getBankIssuer());
            addField(fields, "Credit Limit", String.format("₹%,.2f", cc.getCreditLimitPaise() / 100.0));
            long out = DataStore.getInstance().getCreditCardOutstandingPaise(cc.getId());
            addField(fields, "Outstanding",  String.format("₹%,.2f", out / 100.0));
            addField(fields, "Billing Date", cc.getBillingCycleDate() + " of month");
            addField(fields, "Payment Due",  cc.getPaymentDueDays() + " days after billing");
            if (cc.isAddOnCard())
                addField(fields, "Add-on Card Holder", cc.getAddOnCardHolderName());
        } else if (acc instanceof LoanAccount la) {
            addField(fields, "Loan A/C Number", la.getLoanAccountNumber());
            addField(fields, "Lender",          la.getLenderName());
            addField(fields, "Loan Amount",     String.format("₹%,.2f", la.getLoanAmountPaise() / 100.0));
            addField(fields, "Interest Rate",   la.getInterestRate() + "% p.a.");
            addField(fields, "Tenure",          la.getTenureMonths() + " months");
            addField(fields, "EMI",             String.format("₹%,.2f", la.getEmiAmountPaise() / 100.0));
            addField(fields, "EMI Due Day",     la.getEmiDueDay() + " of month");
            long laPaid = DataStore.getInstance().getTransactions().stream()
                    .filter(t -> (t.getType() == Transaction.Type.TRANSFER
                              || t.getType() == Transaction.Type.LOAN_PAYMENT)
                              && la.getId().equals(t.getToAccountId()))
                    .mapToLong(Transaction::getAmountPaise).sum();
            addField(fields, "Current Outstanding Amount", String.format("₹%,.2f",
                    Math.max(0, la.getOutstandingPrincipalPaise() - laPaid) / 100.0));
            if (la.isJointAccount() && la.getCoApplicantName() != null && !la.getCoApplicantName().isBlank())
                addField(fields, "Co-applicant", la.getCoApplicantName());
        } else if (acc instanceof InvestmentAccount ia) {
            long invested = ia.getInvestedAmountPaise()
                    + DataStore.getInstance().getTransactions().stream()
                        .filter(t -> t.getType() == Transaction.Type.INVESTMENT
                                  && ia.getId().equals(t.getToAccountId()))
                        .mapToLong(Transaction::getAmountPaise).sum()
                    - DataStore.getInstance().getTransactions().stream()
                        .filter(t -> t.getType() == Transaction.Type.TRANSFER
                                  && ia.getId().equals(t.getFromAccountId()))
                        .mapToLong(Transaction::getAmountPaise).sum()
                    - DataStore.getInstance().getTransactions().stream()
                        .filter(t -> t.getType() == Transaction.Type.REDEEM
                                  && ia.getId().equals(t.getFromAccountId()))
                        .mapToLong(t -> t.getPrincipalPaise() > 0 ? t.getPrincipalPaise() : t.getAmountPaise()).sum();
            addField(fields, "Investment Type",         ia.getAccountType());
            addField(fields, "Account Number",           ia.getFolioAccountNumber());
            addField(fields, "Current Invested Amount", String.format("₹%,.2f", Math.max(0, invested) / 100.0));
        }

        if (acc.getNotes() != null && !acc.getNotes().isBlank())
            addField(fields, "Notes", acc.getNotes());

        Button editBtn = new Button("✏️  Edit Account Details");
        editBtn.getStyleClass().add("btn-primary");
        editBtn.setOnAction(e -> { openEditAccountDialog(acc); showAccountDetails(acc); });

        panel.getChildren().addAll(header, fields, editBtn);

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #eef4f5; -fx-background: #eef4f5;");

        view.getChildren().setAll(scroll);
    }

    // ── Account Transactions view ─────────────────────────────────────────────

    private void showAccountTransactions(Account acc) {
        VBox panel = new VBox(14);
        panel.getStyleClass().add("main-panel");
        panel.setPadding(new Insets(24));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Button back = new Button("⬅");
        back.getStyleClass().add("btn-secondary");
        back.setTooltip(new Tooltip("Back"));
        back.setOnAction(e -> buildList());
        Label title = new Label(acc.getName() + " — Transactions");
        title.getStyleClass().add("screen-title");
        header.getChildren().addAll(back, title);

        if (acc instanceof CreditCardAccount cc) {
            long outstanding = DataStore.getInstance().getCreditCardOutstandingPaise(cc.getId());
            long available   = Math.min(cc.getCreditLimitPaise(), cc.getCreditLimitPaise() - outstanding);
            HBox ccSummary = new HBox(24);
            ccSummary.getStyleClass().add("card");
            ccSummary.setPadding(new Insets(12, 16, 12, 16));
            ccSummary.setAlignment(Pos.CENTER_LEFT);
            ccSummary.getChildren().addAll(
                    ccStat("Credit Limit",  "₹" + String.format("%,.0f", cc.getCreditLimitPaise() / 100.0), "#595959"),
                    ccStat("Outstanding",   "₹" + String.format("%,.0f", outstanding / 100.0), "#E74C3C"),
                    ccStat("Available",     "₹" + String.format("%,.0f", available / 100.0), "#27AE60"),
                    ccStat("Billing Date",  cc.getBillingCycleDate() + " of month", "#595959"),
                    ccStat("Payment Due",   cc.getPaymentDueDays() + " days after billing", "#595959")
            );
            panel.getChildren().addAll(header, ccSummary);
        } else {
            panel.getChildren().add(header);
        }

        // ── Filters ────────────────────────────────────────────────────────────
        TextField search = new TextField();
        search.setPromptText("Search description or notes…");
        search.getStyleClass().add("filter-field");
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);

        DataStore ds = DataStore.getInstance();
        DatePicker fromPicker = new DatePicker(ds.getActiveFYStart());
        DatePicker toPicker   = new DatePicker(ds.getActiveFYEnd());
        fromPicker.setPrefWidth(130);
        toPicker.setPrefWidth(130);
        fromPicker.getStyleClass().add("filter-field");
        toPicker.getStyleClass().add("filter-field");
        UiUtils.applySmartDateConverter(fromPicker);
        UiUtils.applySmartDateConverter(toPicker);
        UiUtils.styleOnShow(fromPicker);
        UiUtils.styleOnShow(toPicker);

        Label fromLbl = new Label("FROM");
        fromLbl.getStyleClass().add("filter-label");
        Label toLbl = new Label("TO");
        toLbl.getStyleClass().add("filter-label");

        CheckBox pendingOnly = new CheckBox("Show pending review only");
        pendingOnly.setStyle("-fx-text-fill: #7aa4b0; -fx-font-size: 12px;");

        Region filterSep = new Region();
        filterSep.setStyle("-fx-background-color: rgba(42,138,122,0.18); -fx-pref-width: 1; -fx-min-width: 1; -fx-max-width: 1; -fx-pref-height: 22;");

        HBox filterRow = new HBox(10);
        filterRow.getStyleClass().add("filter-bar");
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.getChildren().addAll(
                fromLbl, fromPicker,
                toLbl, toPicker,
                filterSep,
                search,
                pendingOnly
        );

        // ── Transaction table ──────────────────────────────────────────────────
        TableView<Transaction> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(400);

        TableColumn<Transaction, LocalDate> dateCol = new TableColumn<>("DATE");
        dateCol.setPrefWidth(90);
        dateCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getDate()));
        dateCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : d.format(dateFmt()));
            }
        });
        dateCol.setSortType(TableColumn.SortType.DESCENDING);
        TableColumn<Transaction, String> descCol = col("Description", 180,
                Transaction::getDescription, "cell-desc");
        TableColumn<Transaction, Void> typeCol = new TableColumn<>("TYPE");
        typeCol.setPrefWidth(88);
        typeCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                setGraphic(typeBadge(getTableRow().getItem().getType()));
            }
        });
        TableColumn<Transaction, String> acctCol = col("To / From Account", 140, t -> {
            String secondId = acc.getId().equals(t.getFromAccountId())
                    ? t.getToAccountId() : t.getFromAccountId();
            String name = ds.getAccountName(secondId);
            return "—".equals(name) ? "" : name;
        });
        TableColumn<Transaction, String> catCol  = col("Category", 100,
                t -> ds.getCategoryName(t.getCategoryId()));
        TableColumn<Transaction, String> subCatCol = col("Sub-category", 100,
                t -> ds.getCategoryName(t.getSubCategoryId()));
        TableColumn<Transaction, Long> amtCol = new TableColumn<>("AMOUNT");
        amtCol.setPrefWidth(90);
        amtCol.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleObjectProperty<>(
                cd.getValue().getSignedAmountPaise(acc.getId())));
        amtCol.setCellFactory(tc -> new TableCell<>() {
            { getStyleClass().add("cell-amt"); }
            @Override protected void updateItem(Long paise, boolean empty) {
                super.updateItem(paise, empty);
                if (empty || paise == null) { setText(null); return; }
                setText(paise < 0
                    ? "(" + String.format("₹%,.2f", -paise / 100.0) + ")"
                    :        String.format("₹%,.2f",  paise / 100.0));
            }
        });

        // Define applyFilter first so the Actions column can reference it
        Runnable applyFilter = () -> {
            String q = search.getText().toLowerCase();
            LocalDate from = fromPicker.getValue();
            LocalDate to   = toPicker.getValue();
            List<Transaction> filtered = ds.getTransactions().stream()
                    .filter(t -> isForAccount(t, acc))
                    .filter(t -> from == null || !t.getDate().isBefore(from))
                    .filter(t -> to   == null || !t.getDate().isAfter(to))
                    .filter(t -> q.isEmpty()
                            || t.getDescription().toLowerCase().contains(q)
                            || (t.getNotes() != null && t.getNotes().toLowerCase().contains(q)))
                    .filter(t -> !pendingOnly.isSelected()
                            || t.getSourceIndicator() == Transaction.SourceIndicator.AUTO_CATEGORIZED)
                    .collect(Collectors.toList());

            if (table.getSortOrder().contains(dateCol)) {
                // Sort by actual LocalDate with an insertion-order tiebreaker so that
                // within same-date groups the newest transaction sits at the top
                // when sorted descending (and at the bottom when ascending).
                boolean desc = dateCol.getSortType() == TableColumn.SortType.DESCENDING;
                List<Transaction> all = ds.getTransactions();
                Map<String, Integer> insertionIdx = new java.util.HashMap<>();
                for (int i = 0; i < all.size(); i++) insertionIdx.put(all.get(i).getId(), i);
                Comparator<Transaction> cmp = Comparator.comparing(Transaction::getDate);
                if (desc) {
                    cmp = cmp.reversed()
                            .thenComparingInt(t -> -insertionIdx.getOrDefault(t.getId(), 0));
                } else {
                    cmp = cmp.thenComparingInt(t -> insertionIdx.getOrDefault(t.getId(), 0));
                }
                filtered.sort(cmp);
                table.getItems().setAll(filtered);
            } else {
                table.getItems().setAll(filtered);
                table.sort();
            }
        };

        search.textProperty().addListener((obs, o, n) -> applyFilter.run());
        fromPicker.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        toPicker.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        table.comparatorProperty().addListener((obs, o, n) -> applyFilter.run());
        pendingOnly.selectedProperty().addListener((obs, o, n) -> applyFilter.run());
        applyFilter.run();
        mainWindow.setPostTransactionCallback(applyFilter);

        // Double-click or Enter to edit / re-classify
        table.setRowFactory(tv -> {
            TableRow<Transaction> row = new TableRow<>() {
                @Override
                protected void updateItem(Transaction t, boolean empty) {
                    super.updateItem(t, empty);
                    pseudoClassStateChanged(PC_IMPORTED,   false);
                    pseudoClassStateChanged(PC_RECONCILED, false);
                    if (t == null || empty) return;
                    switch (t.getSourceIndicator()) {
                        case IMPORTED, AUTO_CATEGORIZED -> pseudoClassStateChanged(PC_IMPORTED,   true);
                        case RECONCILED                 -> pseudoClassStateChanged(PC_RECONCILED, true);
                        default -> {}
                    }
                }
            };
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    new TransactionDialog(row.getItem()).showAndWait().ifPresent(saved -> {
                        applyFilter.run();
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
                        applyFilter.run();
                        Platform.runLater(() -> restoreSelection(table, saved.getId()));
                    });
                }
            }
        });

        TableColumn<Transaction, Void> actionsCol = new TableColumn<>("");
        actionsCol.setMinWidth(36);
        actionsCol.setMaxWidth(36);
        actionsCol.setCellFactory(tc -> new TableCell<>() {
            private final Label deleteBtn = new Label("×");
            {
                deleteBtn.setStyle(
                        "-fx-background-color: #F5DADA; -fx-text-fill: #A93226; "
                        + "-fx-font-size: 10px; -fx-font-weight: bold; "
                        + "-fx-padding: 1 5; -fx-background-radius: 3; -fx-cursor: hand;");
                deleteBtn.setTooltip(new Tooltip("Delete transaction"));
                deleteBtn.setOnMouseClicked(e -> {
                    Transaction t = getTableRow().getItem();
                    if (t == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Delete Transaction");
                    boolean isGrouped = t.getGroupTransactionId() != null;
                    confirm.setHeaderText(isGrouped
                            ? "Delete linked redemption group?"
                            : "Delete this transaction?");
                    confirm.setContentText(
                            t.getDescription() + "\n"
                            + t.getAmountInr() + "  ·  "
                            + t.getDate().format(dateFmt())
                            + (isGrouped ? "\n\nThis will also delete the related principal and gain/loss entries." : ""));
                    confirm.showAndWait()
                            .filter(b -> b == ButtonType.OK)
                            .ifPresent(b -> {
                                DataStore.getInstance().deleteTransaction(t.getId());
                                applyFilter.run();
                            });
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                setGraphic(deleteBtn);
            }
        });

        TableColumn<Transaction, Void> srcCol = new TableColumn<>("");
        srcCol.setMinWidth(32); srcCol.setMaxWidth(32);
        srcCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                Transaction t = getTableRow().getItem();
                Label badge = new Label();
                switch (t.getSourceIndicator()) {
                    case IMPORTED -> {
                        badge.setText("I");
                        badge.setStyle("-fx-background-color: #DDEEFF; -fx-text-fill: #1A66CC; "
                                + "-fx-font-weight: bold; -fx-font-size: 10px; "
                                + "-fx-padding: 1 5; -fx-background-radius: 3;");
                        badge.setTooltip(new Tooltip("Imported from file"));
                    }
                    case AUTO_CATEGORIZED -> {
                        badge.setText("?");
                        badge.setStyle("-fx-background-color: #FFF3CD; -fx-text-fill: #856404; "
                                + "-fx-font-weight: bold; -fx-font-size: 10px; "
                                + "-fx-padding: 1 5; -fx-background-radius: 3; -fx-cursor: hand;");
                        // Build tooltip showing what was auto-suggested
                        StringBuilder tip = new StringBuilder();
                        boolean isTypeSuggested = t.getType() != Transaction.Type.EXPENSE
                                               && t.getType() != Transaction.Type.INCOME;
                        if (isTypeSuggested) {
                            tip.append("Type auto-filled: ").append(t.getType().toString());
                            String secondId = acc.getId().equals(t.getFromAccountId())
                                    ? t.getToAccountId() : t.getFromAccountId();
                            String acctName = ds.getAccountName(secondId);
                            if (!"—".equals(acctName)) tip.append(" → ").append(acctName);
                        } else {
                            tip.append("Category auto-filled");
                            if (t.getCategoryId() != null)
                                tip.append(": ").append(ds.getCategoryName(t.getCategoryId()));
                            if (t.getSubCategoryId() != null)
                                tip.append(" / ").append(ds.getCategoryName(t.getSubCategoryId()));
                        }
                        tip.append("\nClick to accept, or double-click to edit");
                        badge.setTooltip(new Tooltip(tip.toString()));
                        badge.setOnMouseClicked(e -> {
                            t.setSourceIndicator(Transaction.SourceIndicator.RECONCILED);
                            DataStore.getInstance().saveTransactionsNow();
                            applyFilter.run();
                            e.consume();
                        });
                    }
                    case RECONCILED -> {
                        badge.setText("R");
                        badge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #166534; "
                                + "-fx-font-weight: bold; -fx-font-size: 10px; "
                                + "-fx-padding: 1 5; -fx-background-radius: 3;");
                        badge.setTooltip(new Tooltip("Reconciled with import"));
                    }
                    default -> {
                        badge.setText("M");
                        badge.setStyle("-fx-background-color: #EEEEEE; -fx-text-fill: #555555; "
                                + "-fx-font-weight: bold; -fx-font-size: 10px; "
                                + "-fx-padding: 1 5; -fx-background-radius: 3;");
                        badge.setTooltip(new Tooltip("Manually entered"));
                    }
                }
                setGraphic(badge);
            }
        });

        table.getColumns().addAll(dateCol, descCol, typeCol, acctCol, catCol, subCatCol, amtCol,
                srcCol, actionsCol);
        table.getSortOrder().add(dateCol);

        Button exportBtn = new Button("Export CSV");
        exportBtn.getStyleClass().add("btn-gold");
        exportBtn.setOnAction(e -> exportCsv(acc, table.getItems()));

        HBox footerRow = new HBox(8);
        footerRow.getStyleClass().add("table-footer");
        footerRow.setAlignment(Pos.CENTER_LEFT);
        Label hintLbl = UiUtils.hintLabel("Double-click a row to edit");
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        footerRow.getChildren().addAll(hintLbl, footerSpacer);
        if (acc instanceof BankAccount || acc instanceof CreditCardAccount) {
            Button importBtn = new Button("Import CSV");
            importBtn.getStyleClass().add("btn-gold");
            importBtn.setOnAction(e -> doImportCsv(acc, applyFilter));
            footerRow.getChildren().add(importBtn);
        }
        footerRow.getChildren().add(exportBtn);

        VBox tableCard = new VBox();
        tableCard.getStyleClass().add("table-card");
        tableCard.getChildren().addAll(table, footerRow);

        panel.getChildren().addAll(filterRow, tableCard);

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #eef4f5; -fx-background: #eef4f5;");

        view.getChildren().setAll(scroll);
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

    private boolean isForAccount(Transaction t, Account acc) {
        String id = acc.getId();
        return id.equals(t.getFromAccountId()) || id.equals(t.getToAccountId());
    }

    private long computeRunningBalance(BankAccount ba) {
        long balance = ba.getOpeningBalancePaise();
        for (Transaction t : DataStore.getInstance().getTransactions()) {
            if (ba.getId().equals(t.getFromAccountId())) balance -= t.getAmountPaise();
            if (ba.getId().equals(t.getToAccountId()))   balance += t.getAmountPaise();
        }
        return balance;
    }

    private Map<String, Long> buildRunningBalanceMap(BankAccount ba) {
        Map<String, Long> map = new LinkedHashMap<>();
        long balance = ba.getOpeningBalancePaise();
        List<Transaction> sorted = DataStore.getInstance().getTransactions().stream()
                .filter(t -> ba.getId().equals(t.getFromAccountId())
                        || ba.getId().equals(t.getToAccountId()))
                .sorted(Comparator.comparing(Transaction::getDate))
                .collect(Collectors.toList());
        for (Transaction t : sorted) {
            if (ba.getId().equals(t.getFromAccountId())) balance -= t.getAmountPaise();
            if (ba.getId().equals(t.getToAccountId()))   balance += t.getAmountPaise();
            map.put(t.getId(), balance);
        }
        return map;
    }

    private VBox ccStat(String label, String value, String colour) {
        VBox b = new VBox(2);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: 600; -fx-text-fill: #7aa4b0;");
        Label val = new Label(value);
        val.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + colour + ";");
        b.getChildren().addAll(lbl, val);
        return b;
    }

    // ── Import CSV ────────────────────────────────────────────────────────────

    private void doImportCsv(Account acc, Runnable refreshTable) {
        DataStore ds = DataStore.getInstance();

        FileChooser fc = new FileChooser();
        fc.setTitle("Import Bank / CC Statement");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        ImportMapping prior = ImportService.findMapping(acc.getId(), ds.getImportMappings());
        if (prior != null && prior.getLastImportPath() != null) {
            File lastDir = new File(prior.getLastImportPath());
            if (lastDir.isDirectory()) fc.setInitialDirectory(lastDir);
        }
        File file = fc.showOpenDialog(null);
        if (file == null) return;

        // Parse
        List<String[]> rows;
        try { rows = ImportService.parseCsv(file); }
        catch (IOException ex) { info("Import Failed", "Could not read file:\n" + ex.getMessage()); return; }

        if (rows.isEmpty()) { info("Import Failed", "The file is empty."); return; }

        // Validate header
        if (!ImportService.isLikelyHeader(rows.get(0))) {
            info("Import Rejected",
                    "The first row does not look like a header row.\n"
                    + "Please ensure the CSV has column headers in row 1.");
            return;
        }

        ImportMapping saved = ImportService.findMapping(acc.getId(), ds.getImportMappings());
        String importDir    = file.getParentFile() != null ? file.getParentFile().getAbsolutePath() : null;
        String snapshot     = String.join(",", rows.get(0));
        boolean snapshotOk  = saved != null && snapshot.equals(saved.getHeaderSnapshot());

        // Always show mapping dialog — pre-filled when snapshot matches
        ImportMappingDialog dlg = new ImportMappingDialog(acc, rows.get(0),
                snapshotOk ? saved : null);
        Optional<ImportMapping> mappingOpt = dlg.showAndWait();
        if (mappingOpt.isEmpty() || mappingOpt.get() == null) return;

        ImportMapping mapping = mappingOpt.get();
        mapping.setHeaderSnapshot(snapshot);
        if (importDir != null) mapping.setLastImportPath(importDir);
        ds.saveOrUpdateImportMapping(mapping);

        // Execute import
        ImportService.ImportResult result = ImportService.executeImport(rows, mapping, acc, ds);

        // Resolve ambiguous matches.
        // After reconciling one CSV row, its chosen manual becomes RECONCILED.
        // For subsequent ambiguous rows, filter out already-reconciled candidates:
        //   – 0 still-MANUAL candidates → all taken by earlier rows → add as new automatically
        //   – 1+ still-MANUAL candidates → show dialog with only the available ones
        for (ImportService.AmbiguousMatch am : result.ambiguous) {
            List<Transaction> available = am.candidates.stream()
                    .filter(c -> c.getSourceIndicator() == Transaction.SourceIndicator.MANUAL)
                    .collect(java.util.stream.Collectors.toList());

            if (available.isEmpty()) {
                // All candidates were claimed by earlier CSV rows — add as new automatically
                boolean categorized = ds.suggestCategoryForDescription(
                        am.imported.getDescription(), am.imported.getType())
                        .map(rule -> {
                            am.imported.setCategoryId(rule.getCategoryId());
                            am.imported.setSubCategoryId(rule.getSubCategoryId());
                            return true;
                        }).orElse(false);
                am.imported.setSourceIndicator(categorized
                        ? Transaction.SourceIndicator.AUTO_CATEGORIZED
                        : Transaction.SourceIndicator.IMPORTED);
                ds.addTransactionInternal(am.imported);
                result.newCount++;
                continue;
            }

            AmbiguousMatchDialog amd = new AmbiguousMatchDialog(am.imported, available);
            Optional<Transaction> choice = amd.showAndWait();
            if (choice.isPresent()) {
                Transaction chosen = choice.get();
                if (chosen != null) {
                    ImportService.reconcile(am.imported, chosen, ds);
                    result.reconciledCount++;
                } else {
                    // "Add as New" was clicked
                    boolean categorized = ds.suggestCategoryForDescription(
                            am.imported.getDescription(), am.imported.getType())
                            .map(rule -> {
                                am.imported.setCategoryId(rule.getCategoryId());
                                am.imported.setSubCategoryId(rule.getSubCategoryId());
                                return true;
                            }).orElse(false);
                    am.imported.setSourceIndicator(categorized
                            ? Transaction.SourceIndicator.AUTO_CATEGORIZED
                            : Transaction.SourceIndicator.IMPORTED);
                    ds.addTransactionInternal(am.imported);
                    result.newCount++;
                }
            }
            // CANCEL on ambiguous dialog → skip this entry silently
        }
        if (!result.ambiguous.isEmpty()) ds.saveTransactionsNow();

        // Resolve recurring matches — show RecurringMatchDialog for each.
        for (ImportService.RecurringMatch rm : result.recurringMatches) {
            RecurringMatchDialog rmd = new RecurringMatchDialog(rm.imported, rm.candidates);
            java.util.Optional<com.sanchay.model.RecurringTransaction> choice = rmd.showAndWait();
            if (choice.isEmpty()) continue;  // cancelled — skip silently

            com.sanchay.model.RecurringTransaction chosen = choice.get();
            if (chosen == RecurringMatchDialog.ADD_AS_NEW) {
                boolean categorized = ds.suggestCategoryForDescription(
                        rm.imported.getDescription(), rm.imported.getType())
                        .map(rule -> {
                            rm.imported.setCategoryId(rule.getCategoryId());
                            rm.imported.setSubCategoryId(rule.getSubCategoryId());
                            return true;
                        }).orElse(false);
                rm.imported.setSourceIndicator(categorized
                        ? Transaction.SourceIndicator.AUTO_CATEGORIZED
                        : Transaction.SourceIndicator.IMPORTED);
                ds.addTransactionInternal(rm.imported);
                ds.saveTransactionsNow();
                result.newCount++;
            } else {
                ImportService.reconcileWithRecurring(rm.imported, chosen, ds);
                result.recurringReconciledCount++;
            }
        }

        refreshTable.run();

        info("Import Complete",
                "✓ " + result.newCount                 + " new transaction(s) added\n"
              + "✓ " + result.reconciledCount           + " reconciled with existing manual entries\n"
              + (result.recurringReconciledCount > 0
                  ? "✓ " + result.recurringReconciledCount + " recorded against recurring schedule\n"
                  : "")
              + "⊘ " + result.skippedCount              + " skipped (already imported)"
              + (result.ambiguous.isEmpty() ? "" :
                  "\n⚠ " + result.ambiguous.size() + " ambiguous match(es) resolved manually"));
    }

    private void exportCsv(Account acc, List<Transaction> txs) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Transactions as CSV");
        fc.setInitialFileName(acc.getName().replaceAll("\\s+", "_") + "_transactions.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(null);
        if (file == null) return;
        try {
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println("Date,Description,Type,Category,Sub-category,Amount");
                DataStore ds = DataStore.getInstance();
                for (Transaction t : txs) {
                    // Export signed amounts so re-importing preserves EXPENSE vs INCOME.
                    // INCOME / transfer arriving here → positive; everything else → negative.
                    long signedPaise;
                    if (t.getType() == Transaction.Type.INCOME) {
                        signedPaise = t.getAmountPaise();
                    } else if (t.getType() == Transaction.Type.TRANSFER
                            || t.getType() == Transaction.Type.LOAN_PAYMENT) {
                        signedPaise = acc.getId().equals(t.getFromAccountId())
                                ? -t.getAmountPaise() : t.getAmountPaise();
                    } else {
                        // EXPENSE, INVESTMENT, CC_PAYMENT
                        signedPaise = -t.getAmountPaise();
                    }
                    pw.println(String.join(",",
                            t.getDate().format(dateFmt()),
                            "\"" + t.getDescription().replace("\"", "\"\"") + "\"",
                            t.getType().name(),
                            "\"" + ds.getCategoryName(t.getCategoryId()) + "\"",
                            "\"" + ds.getCategoryName(t.getSubCategoryId()) + "\"",
                            String.format("%.2f", signedPaise / 100.0)
                    ));
                }
            }
            info("Export Complete", "Saved " + txs.size() + " transaction(s) to:\n" + file.getAbsolutePath());
        } catch (IOException ex) {
            info("Export Failed", "Could not write file: " + ex.getMessage());
        }
    }

    // ── Add Account Dialogs ───────────────────────────────────────────────────

    private void openAddAccountDialog(String type) {
        switch (type) {
            case "bank"       -> openBankAccountDialog(null);
            case "cc"         -> openCreditCardDialog(null);
            case "loan"       -> openLoanDialog(null);
            case "investment" -> openInvestmentDialog(null);
        }
    }

    private void openEditAccountDialog(Account acc) {
        if (acc instanceof BankAccount ba)            openBankAccountDialog(ba);
        else if (acc instanceof CreditCardAccount cc) openCreditCardDialog(cc);
        else if (acc instanceof LoanAccount la)       openLoanDialog(la);
        else if (acc instanceof InvestmentAccount ia) openInvestmentDialog(ia);
    }

    // ── Bank Account Dialog ───────────────────────────────────────────────────

    private void openBankAccountDialog(BankAccount existing) {
        boolean isNew = (existing == null);
        Dialog<Void> dlg = dialog(isNew ? "Add Bank Account" : "Edit Bank Account");
        GridPane g = formGrid();

        TextField nameFld   = tf(isNew ? "" : existing.getName(),        "e.g. HDFC Primary Savings");
        TextField descFld   = tf(isNew ? "" : nvl(existing.getDescription()), "Short description (optional)");
        TextField bankFld   = tf(isNew ? "" : nvl(existing.getBankName()),  "e.g. HDFC Bank");
        ComboBox<String> holderCb = memberCombo(isNew ? null : existing.getAccountHolder());
        holderCb.setPromptText("Account holder name");
        TextField acctNoFld = tf(isNew ? "" : nvl(existing.getAccountNumber()), "Account number");
        ComboBox<String> subtypeCb = new ComboBox<>();
        subtypeCb.getItems().addAll("Savings", "Current");
        subtypeCb.setValue(isNew ? "Savings"
                : existing.getSubType() == BankAccount.SubType.SAVINGS ? "Savings" : "Current");
        subtypeCb.setMaxWidth(Double.MAX_VALUE);
        TextField currencyFld = new TextField("INR");
        currencyFld.setEditable(false);
        currencyFld.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> statusCb = new ComboBox<>();
        statusCb.getItems().addAll("Active", "Closed");
        statusCb.setValue(isNew ? "Active" : (existing.isActive() ? "Active" : "Closed"));
        statusCb.setMaxWidth(Double.MAX_VALUE);
        DatePicker openDate = new DatePicker(isNew ? LocalDate.now() : existing.getOpeningDate());
        openDate.setMaxWidth(Double.MAX_VALUE);
        UiUtils.applySmartDateConverter(openDate);
        UiUtils.styleOnShow(openDate);
        TextField openBalFld = tf(isNew ? "0.00"
                : String.format("%.2f", existing.getOpeningBalancePaise() / 100.0), "Opening balance");

        CheckBox jointCb = new CheckBox("Joint Account");
        jointCb.setSelected(!isNew && existing.isJointAccount());
        ComboBox<String> secondHolderCb = memberCombo(
                isNew ? null : existing.getSecondHolderName());
        secondHolderCb.setPromptText("Second holder name");
        secondHolderCb.setVisible(jointCb.isSelected());
        secondHolderCb.setManaged(jointCb.isSelected());
        jointCb.selectedProperty().addListener((obs, o, n) -> {
            secondHolderCb.setVisible(n);
            secondHolderCb.setManaged(n);
        });

        addRow(g,  0, "Name*",           nameFld);
        addRow(g,  1, "Description",     descFld);
        addRow(g,  2, "Bank*",           bankFld);
        addRow(g,  3, "Account Holder*", holderCb);
        addRow(g,  4, "Account Number",  acctNoFld);
        addRow(g,  5, "Sub-Type",        subtypeCb);
        addRow(g,  6, "Currency",        currencyFld);
        addRow(g,  7, "Status",          statusCb);
        addRow(g,  8, "Opening Date",    openDate);
        addRow(g,  9, "Opening Balance", openBalFld);
        addRow(g, 10, "Joint Account",   jointCb);
        addRow(g, 11, "Second Holder",   secondHolderCb);

        dlg.getDialogPane().setContent(scrolled(g));
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { info("Validation", "Name is required."); return null; }
            BankAccount acc = isNew
                    ? new BankAccount(name, BankAccount.SubType.valueOf(subtypeCb.getValue().toUpperCase()))
                    : existing;
            if (!isNew) acc.setName(name);
            acc.setDescription(descFld.getText().trim());
            acc.setBankName(bankFld.getText().trim());
            acc.setAccountHolder(holderCb.getEditor().getText().trim());
            acc.setAccountNumber(acctNoFld.getText().trim());
            acc.setOpeningDate(openDate.getValue());
            try { acc.setOpeningBalancePaise(Math.round(
                    Double.parseDouble(openBalFld.getText().replace(",", "")) * 100));
            } catch (NumberFormatException ignore) {}
            acc.setJointAccount(jointCb.isSelected());
            acc.setSecondHolderName(jointCb.isSelected() ? secondHolderCb.getEditor().getText().trim() : null);
            acc.setStatus("Active".equals(statusCb.getValue()) ? Account.Status.ACTIVE : Account.Status.CLOSED);
            if (isNew) DataStore.getInstance().addAccount(acc);
            else DataStore.getInstance().getPersistence().saveAccounts(DataStore.getInstance());
            Platform.runLater(dlg::close);
            return null;
        });
        dlg.showAndWait();
    }

    // ── Credit Card Dialog ────────────────────────────────────────────────────

    private void openCreditCardDialog(CreditCardAccount existing) {
        boolean isNew = (existing == null);
        Dialog<Void> dlg = dialog(isNew ? "Add Credit Card" : "Edit Credit Card");
        GridPane g = formGrid();

        TextField nameFld   = tf(isNew ? "" : existing.getName(),              "e.g. HDFC Regalia");
        TextField descFld   = tf(isNew ? "" : nvl(existing.getDescription()), "Short description (optional)");
        TextField issuerFld = tf(isNew ? "" : nvl(existing.getBankIssuer()),        "e.g. HDFC Bank");
        ComboBox<String> holderCb = memberCombo(isNew ? null : existing.getCardHolderName());
        holderCb.setPromptText("Card holder name");
        TextField cardNoFld = tf(isNew ? "" : "", "16-digit card number");
        TextField limitFld  = tf(isNew ? "0"
                : String.format("%.0f", existing.getCreditLimitPaise() / 100.0), "Credit limit");
        Spinner<Integer> billDay = new Spinner<>(1, 28, isNew ? 1 : existing.getBillingCycleDate());
        billDay.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> dueDays = new Spinner<>(1, 60, isNew ? 20 : existing.getPaymentDueDays());
        dueDays.setMaxWidth(Double.MAX_VALUE);

        CheckBox addOnCb = new CheckBox("Has Add-on Card");
        addOnCb.setSelected(!isNew && existing.isAddOnCard());
        ComboBox<String> addOnHolderCb = memberCombo(
                isNew ? null : existing.getAddOnCardHolderName());
        addOnHolderCb.setPromptText("Add-on card holder name");
        addOnHolderCb.setVisible(addOnCb.isSelected());
        addOnHolderCb.setManaged(addOnCb.isSelected());
        addOnCb.selectedProperty().addListener((obs, o, n) -> {
            addOnHolderCb.setVisible(n);
            addOnHolderCb.setManaged(n);
        });

        TextField currencyFld = new TextField("INR");
        currencyFld.setEditable(false);
        currencyFld.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> statusCb = new ComboBox<>();
        statusCb.getItems().addAll("Active", "Blocked", "Cancelled");
        statusCb.setValue(isNew ? "Active" : formatCardStatus(existing.getCardStatus()));
        statusCb.setMaxWidth(Double.MAX_VALUE);

        addRow(g,  0, "Name*",              nameFld);
        addRow(g,  1, "Description",        descFld);
        addRow(g,  2, "Issuer*",            issuerFld);
        addRow(g,  3, "Card Holder*",       holderCb);
        addRow(g,  4, "Card Number",        cardNoFld);
        addRow(g,  5, "Credit Limit*",      limitFld);
        addRow(g,  6, "Currency",           currencyFld);
        addRow(g,  7, "Status",             statusCb);
        addRow(g,  8, "Billing Date",       billDay);
        addRow(g,  9, "Payment Due (days)", dueDays);
        addRow(g, 10, "Add-on Card",        addOnCb);
        addRow(g, 11, "Add-on Card Holder", addOnHolderCb);

        dlg.getDialogPane().setContent(scrolled(g));
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { info("Validation", "Name is required."); return null; }
            CreditCardAccount acc = isNew ? new CreditCardAccount(name) : existing;
            if (!isNew) acc.setName(name);
            acc.setDescription(descFld.getText().trim());
            acc.setBankIssuer(issuerFld.getText().trim());
            acc.setCardHolderName(holderCb.getEditor().getText().trim());
            if (!cardNoFld.getText().isBlank()) acc.setCardNumber(cardNoFld.getText().trim());
            try { acc.setCreditLimitPaise(Math.round(
                    Double.parseDouble(limitFld.getText().replace(",", "")) * 100));
            } catch (NumberFormatException ignore) {}
            acc.setBillingCycleDate(billDay.getValue());
            acc.setPaymentDueDays(dueDays.getValue());
            acc.setAddOnCard(addOnCb.isSelected());
            acc.setAddOnCardHolderName(addOnCb.isSelected() ?
                    addOnHolderCb.getEditor().getText().trim() : null);
            CreditCardAccount.CardStatus cs = parseCardStatus(statusCb.getValue());
            acc.setCardStatus(cs);
            acc.setStatus(cs == CreditCardAccount.CardStatus.ACTIVE ? Account.Status.ACTIVE : Account.Status.CLOSED);
            if (isNew) DataStore.getInstance().addAccount(acc);
            else DataStore.getInstance().getPersistence().saveAccounts(DataStore.getInstance());
            Platform.runLater(dlg::close);
            return null;
        });
        dlg.showAndWait();
    }

    // ── Loan Dialog ───────────────────────────────────────────────────────────

    private void openLoanDialog(LoanAccount existing) {
        boolean isNew = (existing == null);
        Dialog<Void> dlg = dialog(isNew ? "Add Loan Account" : "Edit Loan Account");
        GridPane g = formGrid();

        TextField nameFld    = tf(isNew ? "" : existing.getName(),              "e.g. ICICI Home Loan");
        TextField descFld    = tf(isNew ? "" : nvl(existing.getDescription()), "Short description (optional)");
        ComboBox<LoanAccount.LoanType> typeCb = new ComboBox<>();
        typeCb.getItems().addAll(LoanAccount.LoanType.values());
        typeCb.setValue(isNew ? LoanAccount.LoanType.HOME : existing.getLoanType());
        typeCb.setMaxWidth(Double.MAX_VALUE);
        typeCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(LoanAccount.LoanType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatLoanType(item));
            }
        });
        typeCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(LoanAccount.LoanType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatLoanType(item));
            }
        });
        TextField lenderFld  = tf(isNew ? "" : nvl(existing.getLenderName()),        "Lender name");
        TextField acctNoFld  = tf(isNew ? "" : nvl(existing.getLoanAccountNumber()), "Loan account number");
        TextField loanAmtFld = tf(isNew ? "0" : String.format("%.0f", existing.getLoanAmountPaise() / 100.0), "Loan amount");
        TextField rateFld    = tf(isNew ? "0" : String.valueOf(existing.getInterestRate()), "Interest rate %");
        TextField tenureFld  = tf(isNew ? "0" : String.valueOf(existing.getTenureMonths()), "Tenure in months");
        TextField emiFld     = tf(isNew ? "0" : String.format("%.0f", existing.getEmiAmountPaise() / 100.0), "EMI amount");
        Spinner<Integer> emiDay = new Spinner<>(1, 28, isNew ? 1 : existing.getEmiDueDay());
        emiDay.setMaxWidth(Double.MAX_VALUE);
        TextField outFld     = tf(isNew ? "0" : String.format("%.0f", existing.getOutstandingPrincipalPaise() / 100.0), "Outstanding principal");

        CheckBox jointCb = new CheckBox("Joint Account");
        jointCb.setSelected(!isNew && existing.isJointAccount());
        ComboBox<String> coApplicantCb = memberCombo(
                isNew ? null : existing.getCoApplicantName());
        coApplicantCb.setPromptText("Co-applicant name");
        coApplicantCb.setVisible(jointCb.isSelected());
        coApplicantCb.setManaged(jointCb.isSelected());
        jointCb.selectedProperty().addListener((obs, o, n) -> {
            coApplicantCb.setVisible(n);
            coApplicantCb.setManaged(n);
        });

        TextField currencyFld = new TextField("INR");
        currencyFld.setEditable(false);
        currencyFld.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> statusCb = new ComboBox<>();
        statusCb.getItems().addAll("Active", "Closed", "Settled");
        statusCb.setValue(isNew ? "Active" : formatLoanStatus(existing.getLoanStatus()));
        statusCb.setMaxWidth(Double.MAX_VALUE);

        addRow(g,  0, "Name*",                      nameFld);
        addRow(g,  1, "Description",                descFld);
        addRow(g,  2, "Loan Type",                  typeCb);
        addRow(g,  3, "Currency",                   currencyFld);
        addRow(g,  4, "Status",                     statusCb);
        addRow(g,  5, "Lender",                     lenderFld);
        addRow(g,  6, "Account No.",                acctNoFld);
        addRow(g,  7, "Loan Amount*",               loanAmtFld);
        addRow(g,  8, "Interest Rate",              rateFld);
        addRow(g,  9, "Tenure (months)",            tenureFld);
        addRow(g, 10, "EMI Amount",                 emiFld);
        addRow(g, 11, "EMI Due Day",                emiDay);
        addRow(g, 12, "Opening Outstanding Amount", outFld);
        addRow(g, 13, "Joint Account",              jointCb);
        addRow(g, 14, "Co-applicant",               coApplicantCb);

        dlg.getDialogPane().setContent(scrolled(g));
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { info("Validation", "Name is required."); return null; }
            LoanAccount.LoanType ltype = typeCb.getValue();
            LoanAccount acc = isNew ? new LoanAccount(name, ltype) : existing;
            if (!isNew) acc.setName(name);
            acc.setDescription(descFld.getText().trim());
            acc.setLenderName(lenderFld.getText().trim());
            acc.setLoanAccountNumber(acctNoFld.getText().trim());
            try {
                acc.setLoanAmountPaise(Math.round(Double.parseDouble(loanAmtFld.getText().replace(",", "")) * 100));
                acc.setInterestRate(Double.parseDouble(rateFld.getText().replace(",", "")));
                acc.setTenureMonths(Integer.parseInt(tenureFld.getText().trim()));
                acc.setEmiAmountPaise(Math.round(Double.parseDouble(emiFld.getText().replace(",", "")) * 100));
                acc.setEmiDueDay(emiDay.getValue());
                acc.setOutstandingPrincipalPaise(Math.round(Double.parseDouble(outFld.getText().replace(",", "")) * 100));
            } catch (NumberFormatException ignore) {}
            acc.setJointAccount(jointCb.isSelected());
            acc.setCoApplicantName(jointCb.isSelected() ? coApplicantCb.getEditor().getText().trim() : null);
            LoanAccount.LoanStatus ls = parseLoanStatus(statusCb.getValue());
            acc.setLoanStatus(ls);
            acc.setStatus(ls == LoanAccount.LoanStatus.ACTIVE ? Account.Status.ACTIVE : Account.Status.CLOSED);
            if (isNew) DataStore.getInstance().addAccount(acc);
            else DataStore.getInstance().getPersistence().saveAccounts(DataStore.getInstance());
            Platform.runLater(dlg::close);
            return null;
        });
        dlg.showAndWait();
    }

    // ── Investment Dialog ─────────────────────────────────────────────────────

    private void openInvestmentDialog(InvestmentAccount existing) {
        boolean isNew = (existing == null);
        Dialog<Void> dlg = dialog(isNew ? "Add Investments" : "Edit Investments");
        GridPane g = formGrid();

        TextField nameFld = tf(isNew ? "" : existing.getName(),              "e.g. HDFC Flexi Cap Fund");
        TextField descFld = tf(isNew ? "" : nvl(existing.getDescription()), "Short description (optional)");

        ComboBox<String> typeCb = new ComboBox<>();
        for (InvestmentAccount.InvestmentType t : InvestmentAccount.InvestmentType.values())
            typeCb.getItems().add(formatInvestmentType(t));
        typeCb.setValue(isNew ? formatInvestmentType(InvestmentAccount.InvestmentType.MUTUAL_FUNDS)
                : formatInvestmentType(existing.getInvestmentType()));
        typeCb.setMaxWidth(Double.MAX_VALUE);

        TextField folioFld  = tf(isNew ? "" : nvl(existing.getFolioAccountNumber()), "Folio / account number");
        TextField invAmtFld = tf(isNew ? "0"
                : String.format("%.0f", existing.getInvestedAmountPaise() / 100.0), "Total invested amount");
        TextField currencyFld = new TextField("INR");
        currencyFld.setEditable(false);
        currencyFld.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> statusCb = new ComboBox<>();
        statusCb.getItems().addAll("Active", "Closed", "Redeemed");
        statusCb.setValue(isNew ? "Active" : formatInvestmentStatus(existing.getInvestmentStatus()));
        statusCb.setMaxWidth(Double.MAX_VALUE);

        addRow(g, 0, "Name*",                    nameFld);
        addRow(g, 1, "Description",              descFld);
        addRow(g, 2, "Investment Type",          typeCb);
        addRow(g, 3, "Currency",                 currencyFld);
        addRow(g, 4, "Status",                   statusCb);
        addRow(g, 5, "Account Number",           folioFld);
        addRow(g, 6, "Opening Invested Amount*", invAmtFld);

        dlg.getDialogPane().setContent(scrolled(g));
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { info("Validation", "Name is required."); return null; }
            InvestmentAccount.InvestmentType itype = parseInvestmentType(typeCb.getValue());
            InvestmentAccount acc = isNew ? new InvestmentAccount(name, itype) : existing;
            if (!isNew) acc.setName(name);
            acc.setDescription(descFld.getText().trim());
            acc.setFolioAccountNumber(folioFld.getText().trim());
            try { acc.setInvestedAmountPaise(Math.round(
                    Double.parseDouble(invAmtFld.getText().replace(",", "")) * 100));
            } catch (NumberFormatException ignore) {}
            InvestmentAccount.InvestmentStatus is = parseInvestmentStatus(statusCb.getValue());
            acc.setInvestmentStatus(is);
            acc.setStatus(is == InvestmentAccount.InvestmentStatus.ACTIVE ? Account.Status.ACTIVE : Account.Status.CLOSED);
            if (isNew) DataStore.getInstance().addAccount(acc);
            else DataStore.getInstance().getPersistence().saveAccounts(DataStore.getInstance());
            Platform.runLater(dlg::close);
            return null;
        });
        dlg.showAndWait();
    }

    // ── Investment type formatting helpers ────────────────────────────────────

    private String formatInvestmentType(InvestmentAccount.InvestmentType t) {
        return switch (t) {
            case MUTUAL_FUNDS        -> "Mutual Funds";
            case EQUITY              -> "Equities";
            case DEBT_BONDS          -> "Debt Bonds";
            case FIXED_DEPOSIT       -> "Fixed Deposits";
            case RECURRING_DEPOSIT   -> "Recurring Deposits";
            case PROVIDENT_FUND      -> "Provident Fund";
        };
    }

    private InvestmentAccount.InvestmentType parseInvestmentType(String display) {
        for (InvestmentAccount.InvestmentType t : InvestmentAccount.InvestmentType.values())
            if (formatInvestmentType(t).equals(display)) return t;
        throw new IllegalArgumentException("Unknown investment type: " + display);
    }

    // ── Account status formatting helpers ─────────────────────────────────────

    private String formatCardStatus(CreditCardAccount.CardStatus s) {
        return switch (s) {
            case ACTIVE    -> "Active";
            case BLOCKED   -> "Blocked";
            case CANCELLED -> "Cancelled";
        };
    }

    private CreditCardAccount.CardStatus parseCardStatus(String display) {
        return switch (display) {
            case "Blocked"   -> CreditCardAccount.CardStatus.BLOCKED;
            case "Cancelled" -> CreditCardAccount.CardStatus.CANCELLED;
            default          -> CreditCardAccount.CardStatus.ACTIVE;
        };
    }

    private static String formatLoanType(LoanAccount.LoanType t) {
        return switch (t) {
            case HOME     -> "Home Loan";
            case VEHICLE  -> "Vehicle Loan";
            case PERSONAL -> "Personal Loan";
        };
    }

    private String formatLoanStatus(LoanAccount.LoanStatus s) {
        return switch (s) {
            case ACTIVE  -> "Active";
            case CLOSED  -> "Closed";
            case SETTLED -> "Settled";
        };
    }

    private LoanAccount.LoanStatus parseLoanStatus(String display) {
        return switch (display) {
            case "Closed"  -> LoanAccount.LoanStatus.CLOSED;
            case "Settled" -> LoanAccount.LoanStatus.SETTLED;
            default        -> LoanAccount.LoanStatus.ACTIVE;
        };
    }

    private String formatInvestmentStatus(InvestmentAccount.InvestmentStatus s) {
        return switch (s) {
            case ACTIVE   -> "Active";
            case CLOSED   -> "Closed";
            case REDEEMED -> "Redeemed";
        };
    }

    private InvestmentAccount.InvestmentStatus parseInvestmentStatus(String display) {
        return switch (display) {
            case "Closed"   -> InvestmentAccount.InvestmentStatus.CLOSED;
            case "Redeemed" -> InvestmentAccount.InvestmentStatus.REDEEMED;
            default         -> InvestmentAccount.InvestmentStatus.ACTIVE;
        };
    }

    /** Returns the richest available status string for an account, in title case. */
    private String formatAccountStatus(Account acc) {
        if (acc instanceof CreditCardAccount cc)
            return formatCardStatus(cc.getCardStatus());
        if (acc instanceof LoanAccount la)
            return formatLoanStatus(la.getLoanStatus());
        if (acc instanceof InvestmentAccount ia)
            return formatInvestmentStatus(ia.getInvestmentStatus());
        return acc.isActive() ? "Active" : "Closed";
    }

    /** Null-safe helper for pre-filling optional text fields. */
    private String nvl(String s) { return s != null ? s : ""; }

    // ── Form helpers ──────────────────────────────────────────────────────────

    private GridPane formGrid() {
        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(16));
        ColumnConstraints c1 = new ColumnConstraints(160);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);
        return g;
    }

    private ScrollPane scrolled(GridPane g) {
        ScrollPane sp = new ScrollPane(g);
        sp.setFitToWidth(true);
        sp.setPrefHeight(380);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return sp;
    }

    private Dialog<Void> dialog(String title) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(title);
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(500);
        UiUtils.applyStylesheet(dlg);
        return dlg;
    }

    /** Editable ComboBox pre-populated with family member names; free-text entry allowed. */
    private ComboBox<String> memberCombo(String currentValue) {
        ComboBox<String> cb = new ComboBox<>();
        cb.setEditable(true);
        cb.setMaxWidth(Double.MAX_VALUE);
        DataStore.getInstance().getFamilyMembers().forEach(m -> cb.getItems().add(m.getName()));
        if (currentValue != null && !currentValue.isBlank()) cb.setValue(currentValue);
        return cb;
    }

    private TextField tf(String val, String prompt) {
        TextField tf = new TextField(val);
        tf.setPromptText(prompt);
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private void addRow(GridPane g, int row, String lbl, Node ctrl) {
        Label l = new Label(lbl);
        l.getStyleClass().add("form-label");
        l.setStyle("-fx-text-fill: #1A1A2E;");
        l.setMinWidth(155);
        g.add(l, 0, row);
        g.add(ctrl, 1, row);
        GridPane.setFillWidth(ctrl, true);
    }

    private void addField(VBox container, String label, String value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label + ":");
        lbl.setMinWidth(180);
        lbl.setStyle("-fx-text-fill: #1A1A2E; -fx-font-weight: bold;");
        Label val = new Label(value != null ? value : "—");
        val.setStyle("-fx-text-fill: #595959;");
        row.getChildren().addAll(lbl, val);
        container.getChildren().add(row);
    }

    private <T> TableColumn<T, String> col(String title, int prefWidth,
                                           java.util.function.Function<T, String> extractor) {
        TableColumn<T, String> c = new TableColumn<>(title.toUpperCase());
        c.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(
                        extractor.apply(cd.getValue())));
        if (prefWidth > 0) c.setPrefWidth(prefWidth);
        return c;
    }

    private <T> TableColumn<T, String> col(String title, int prefWidth,
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

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    public static Label typeBadge(Transaction.Type type) {
        String text; String bg; String fg;
        switch (type) {
            case EXPENSE      -> { text = "Expense";      bg = "#FFE8E8"; fg = "#B71C1C"; }
            case INCOME       -> { text = "Income";       bg = "#E8F5E9"; fg = "#1B5E20"; }
            case TRANSFER     -> { text = "Transfer";     bg = "#E3F2FD"; fg = "#0D47A1"; }
            case INVESTMENT   -> { text = "Investment";   bg = "#F3E5F5"; fg = "#4A148C"; }
            case CC_PAYMENT   -> { text = "CC Payment";   bg = "#E0F2F1"; fg = "#004D40"; }
            case REFUND       -> { text = "Refund";       bg = "#E8F5E9"; fg = "#1B5E20"; }
            case REDEEM       -> { text = "Redeem";       bg = "#FFF8E1"; fg = "#E65100"; }
            case LOAN_PAYMENT -> { text = "Loan Payment"; bg = "#FFF3E0"; fg = "#E65100"; }
            case GAIN         -> { text = "Gain";         bg = "#E8F5E9"; fg = "#1B5E20"; }
            case LOSE         -> { text = "Loss";         bg = "#FFE8E8"; fg = "#B71C1C"; }
            default           -> { text = type.name();    bg = "#EEEEEE"; fg = "#333333"; }
        }
        Label lbl = new Label(text);
        lbl.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; "
                + "-fx-background-radius: 4; -fx-padding: 2 7 2 7; "
                + "-fx-font-size: 10px; -fx-font-weight: bold;");
        return lbl;
    }
}