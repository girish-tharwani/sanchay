package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.service.AmortizationService;
import com.sanchay.service.DataStore;
import com.sanchay.service.ImportService;
import com.sanchay.ui.MainWindow;
import com.sanchay.ui.UiUtils;
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

/** Transactions screen for viewing and managing transactions for a specific account. */
public class TransactionsScreen {

    private static final PseudoClass PC_IMPORTED   = PseudoClass.getPseudoClass("row-imported");
    private static final PseudoClass PC_RECONCILED = PseudoClass.getPseudoClass("row-reconciled");

    private final MainWindow mainWindow;
    private final Account account;

    // view is initialised ONCE; never reassigned so that the reference held by MainWindow stays valid
    private final StackPane view;

    public TransactionsScreen(MainWindow mainWindow, Account account) {
        this.mainWindow = mainWindow;
        this.account = account;
        this.view = new StackPane();
        buildTransactionsView();
    }

    public Node getView() { return view; }

    private DateTimeFormatter dateFmt() {
        return DataStore.getInstance().getDateFormatter();
    }

    // ── Account Transactions view ─────────────────────────────────────────────

    private void buildTransactionsView() {
        VBox panel = new VBox(14);
        panel.getStyleClass().add("main-panel");
        panel.setPadding(new Insets(24));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Button back = new Button("⬅");
        back.getStyleClass().add("btn-secondary");
        back.setTooltip(new Tooltip("Back"));
        back.setOnAction(e -> mainWindow.navigateTo("Accounts"));
        Label title = new Label(account.getName() + " — Transactions");
        title.getStyleClass().add("screen-title");
        header.getChildren().addAll(back, title);

        if (account instanceof CreditCardAccount cc) {
            long outstanding = DataStore.getInstance().getCreditCardOutstandingPaise(cc.getId());
            long available   = Math.min(cc.getCreditLimitPaise(), cc.getCreditLimitPaise() - outstanding);
            HBox ccSummary = new HBox(24);
            ccSummary.getStyleClass().add("card");
            ccSummary.setPadding(new Insets(12, 16, 12, 16));
            ccSummary.setAlignment(Pos.CENTER_LEFT);
            ccSummary.getChildren().addAll(
                    ccStat("Credit Limit",  "₹" + String.format("%,.2f", cc.getCreditLimitPaise() / 100.0), "#595959"),
                    ccStat("Outstanding",   "₹" + String.format("%,.2f", outstanding / 100.0), "#E74C3C"),
                    ccStat("Available",     "₹" + String.format("%,.2f", available / 100.0), "#27AE60"),
                    ccStat("Billing Date",  cc.getBillingCycleDate() + " of month", "#595959"),
                    ccStat("Payment Due",   cc.getPaymentDueDays() + " days after billing", "#595959")
            );
            panel.getChildren().addAll(header, ccSummary);
        } else {
            DataStore ds2 = DataStore.getInstance();
            String statLabel; String statValue; String statColour;
            if (account instanceof BankAccount ba) {
                long bal = ba.getOpeningBalancePaise();
                for (Transaction t : ds2.getTransactions()) {
                    if (ba.getId().equals(t.getFromAccountId())) bal -= t.getAmountPaise();
                    if (ba.getId().equals(t.getToAccountId()))   bal += t.getAmountPaise();
                }
                statLabel  = "Balance";
                statValue  = "₹" + String.format("%,.2f", bal / 100.0);
                statColour = "#0f3d4a";
            } else if (account instanceof LoanAccount la) {
                long outstanding = ds2.getLoanOutstandingPaise(la);
                statLabel  = "Outstanding";
                statValue  = "₹" + String.format("%,.0f", outstanding / 100.0);
                statColour = outstanding > 0 ? "#C62828" : "#0f3d4a";
            } else if (account instanceof InvestmentAccount ia) {
                long invested = ds2.getBaseInvestedPaise(ia);
                for (Transaction t : ds2.getTransactions()) {
                    if (t.getType() == Transaction.Type.INVESTMENT && ia.getId().equals(t.getToAccountId()))
                        invested += t.getAmountPaise();
                    if (t.getType() == Transaction.Type.REDEEM && ia.getId().equals(t.getFromAccountId())) {
                        long rdPrin = t.getRedeemDetails() != null ? t.getRedeemDetails().getPrincipalPaise() : 0;
                        invested -= rdPrin > 0 ? rdPrin : t.getAmountPaise();
                    }
                }
                statLabel  = "Invested";
                statValue  = "₹" + String.format("%,.0f", Math.max(0, invested) / 100.0);
                statColour = "#0f3d4a";
                if (isMarketValueAccount(ia)) {
                    MarketValueEntry mv = ds2.getLatestMarketValue(ia.getId());
                    if (mv != null) {
                        long gl = mv.getGainLossPaise();
                        String glColor = gl >= 0 ? "#27AE60" : "#C62828";
                        Region spacer2 = new Region();
                        HBox.setHgrow(spacer2, Priority.ALWAYS);
                        header.getChildren().addAll(
                                spacer2,
                                ccStat("Market Value", "₹" + String.format("%,.0f", mv.getMarketValuePaise() / 100.0), "#0f3d4a"),
                                ccStat("Gain / Loss",  (gl >= 0 ? "+" : "") + "₹" + String.format("%,.0f", gl / 100.0), glColor)
                        );
                        statLabel = null; // suppress the default single-stat path
                    }
                }
            } else {
                statLabel = statValue = statColour = null;
            }
            if (statLabel != null) {
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                header.getChildren().addAll(spacer, ccStat(statLabel, statValue, statColour));
            }
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
        pendingOnly.getStyleClass().add("text-hint");

        // Inline required: vertical separator Region — no CSS class covers exact rgba brand tint + pixel sizing
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
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        //table.setPrefHeight(400);


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
        // For REDEEM group transactions only one side's account ID is set on each record
        // (investment-side has fromAccountId only; bank-side has toAccountId only).
        // Pre-build a map of groupId → {from, to} so the column can resolve the counterpart.
        Map<String, String> groupFromId = new java.util.HashMap<>();
        Map<String, String> groupToId   = new java.util.HashMap<>();
        for (Transaction gt : ds.getTransactions()) {
            String gid = gt.getGroupTransactionId();
            if (gid == null) continue;
            if (gt.getFromAccountId() != null) groupFromId.put(gid, gt.getFromAccountId());
            if (gt.getToAccountId()   != null) groupToId  .put(gid, gt.getToAccountId());
        }

        TableColumn<Transaction, String> acctCol = col("To / From Account", 140, t -> {
            String secondId = account.getId().equals(t.getFromAccountId())
                    ? t.getToAccountId() : t.getFromAccountId();
            if (secondId == null && t.getGroupTransactionId() != null) {
                // Resolve counterpart for REDEEM split transactions
                String gid = t.getGroupTransactionId();
                secondId = account.getId().equals(t.getFromAccountId())
                        ? groupToId.get(gid) : groupFromId.get(gid);
            }
            String name = ds.getAccountName(secondId);
            return "—".equals(name) ? "" : name;
        });
        TableColumn<Transaction, String> catCol  = col("Category", 100,
                t -> ds.getCategoryName(t.getClassification() != null
                        ? t.getClassification().getCategoryId() : null));
        TableColumn<Transaction, String> subCatCol = col("Sub-category", 100,
                t -> ds.getCategoryName(t.getClassification() != null
                        ? t.getClassification().getSubCategoryId() : null));

        // ── Account-type-specific columns (replace Category/Sub-category) ────────
        List<TableColumn<Transaction, ?>> specialtyCols = buildSpecialtyCols(account, ds);

        TableColumn<Transaction, Long> amtCol = new TableColumn<>("AMOUNT");
        amtCol.setPrefWidth(90);
        amtCol.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleObjectProperty<>(
                cd.getValue().getSignedAmountPaise(account.getId())));
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
                    .filter(t -> isForAccount(t, account))
                    .filter(t -> from == null || !t.getDate().isBefore(from))
                    .filter(t -> to   == null || !t.getDate().isAfter(to))
                    .filter(t -> q.isEmpty()
                            || t.getDescription().toLowerCase().contains(q)
                            || (t.getNotes() != null && t.getNotes().toLowerCase().contains(q)))
                    .filter(t -> !pendingOnly.isSelected()
                            || t.getSourceIndicator() == Transaction.SourceIndicator.AUTO_CATEGORIZED
                            || t.getSourceIndicator() == Transaction.SourceIndicator.IMPORTED)
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
        mainWindow.setTransactionContextAccount(account);

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
                deleteBtn.getStyleClass().add("btn-row-remove");
                deleteBtn.setTooltip(new Tooltip("Delete transaction"));
                deleteBtn.setOnMouseClicked(e -> {
                    Transaction t = getTableRow().getItem();
                    if (t == null) return;
                    if (showDeleteTxnConfirm(t)) {
                        DataStore.getInstance().deleteTransaction(t.getId());
                        applyFilter.run();
                    }
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
                        badge.getStyleClass().add("badge-imported");
                        badge.setTooltip(new Tooltip("Imported from file"));
                    }
                    case AUTO_CATEGORIZED -> {
                        badge.setText("?");
                        badge.getStyleClass().add("badge-auto-cat");
                        // Build tooltip showing what was auto-suggested
                        StringBuilder tip = new StringBuilder();
                        boolean isTypeSuggested = t.getType() != Transaction.Type.EXPENSE
                                                && t.getType() != Transaction.Type.INCOME;
                        if (isTypeSuggested) {
                            tip.append("Type auto-filled: ").append(t.getType().toString());
                            String secondId = account.getId().equals(t.getFromAccountId())
                                    ? t.getToAccountId() : t.getFromAccountId();
                            String acctName = ds.getAccountName(secondId);
                            if (!"—".equals(acctName)) tip.append(" → ").append(acctName);
                        } else {
                            tip.append("Category auto-filled");
                            String tipCatId    = t.getClassification() != null ? t.getClassification().getCategoryId() : null;
                            String tipSubCatId = t.getClassification() != null ? t.getClassification().getSubCategoryId() : null;
                            if (tipCatId != null)
                                tip.append(": ").append(ds.getCategoryName(tipCatId));
                            if (tipSubCatId != null)
                                tip.append(" / ").append(ds.getCategoryName(tipSubCatId));
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
                        badge.getStyleClass().add("badge-reconciled");
                        badge.setTooltip(new Tooltip("Reconciled with import"));
                    }
                    default -> {
                        badge.setText("M");
                        badge.getStyleClass().add("badge-manual");
                        badge.setTooltip(new Tooltip("Manually entered"));
                    }
                }
                setGraphic(badge);
            }
        });

        table.getColumns().addAll(dateCol, descCol, typeCol, acctCol);
        table.getColumns().addAll(specialtyCols);
        table.getColumns().addAll(amtCol, srcCol, actionsCol);
        table.getSortOrder().add(dateCol);

        Button exportBtn = new Button("Export CSV");
        exportBtn.getStyleClass().add("btn-gold");
        exportBtn.setOnAction(e -> exportCsv(table.getItems()));

        HBox footerRow = new HBox(8);
        footerRow.getStyleClass().add("table-footer");
        footerRow.setAlignment(Pos.CENTER_LEFT);
        Label hintLbl = UiUtils.hintLabel("Double-click a row to edit");
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        footerRow.getChildren().addAll(hintLbl, footerSpacer);
        if (account instanceof BankAccount || account instanceof CreditCardAccount) {
            Button importBtn = new Button("Import CSV");
            importBtn.getStyleClass().add("btn-gold");
            importBtn.setOnAction(e -> doImportCsv(applyFilter));
            footerRow.getChildren().add(importBtn);
        }
        footerRow.getChildren().add(exportBtn);

        VBox tableCard = new VBox();
        tableCard.getStyleClass().add("table-card");
        table.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        tableCard.getChildren().addAll(table, footerRow);

        VBox.setVgrow(tableCard, Priority.ALWAYS);

        panel.getChildren().addAll(filterRow, tableCard);

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToHeight(true);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-page-bg");

        view.getChildren().setAll(scroll);
    }

    /**
     * Returns the two columns that replace Category/Sub-category in the transaction table,
     * chosen based on the account type being viewed.
     */
    private List<TableColumn<Transaction, ?>> buildSpecialtyCols(Account acc, DataStore ds) {
        if (acc instanceof LoanAccount) {
            TableColumn<Transaction, String> principalCol = col("Principal", 100, t -> {
                if (t.getType() != Transaction.Type.LOAN_PAYMENT) return null;
                long p = effectiveLoanPrincipalPaise(t, ds);
                return p > 0 ? String.format("₹%,.0f", p / 100.0) : null;
            });
            TableColumn<Transaction, String> interestCol = col("Interest", 100, t -> {
                if (t.getType() != Transaction.Type.LOAN_PAYMENT) return null;
                long principal = effectiveLoanPrincipalPaise(t, ds);
                if (principal <= 0) return null;
                long interest = t.getAmountPaise() - principal;
                return interest > 0 ? String.format("₹%,.0f", interest / 100.0) : null;
            });
            return List.of(principalCol, interestCol);
        }

        if (acc instanceof InvestmentAccount ia) {
            InvestmentAccount.InvestmentType invType = ia.getInvestmentType();
            if (invType == InvestmentAccount.InvestmentType.EQUITY
                    || invType == InvestmentAccount.InvestmentType.MUTUAL_FUNDS) {
                TableColumn<Transaction, String> schemeCol = col("Scheme / Script", 160,
                        t -> t.getInvestmentDetails() != null
                                ? t.getInvestmentDetails().getSchemeScriptName() : null);
                TableColumn<Transaction, String> unitsCol = col("Units / NAV", 90, t -> {
                    if (t.getInvestmentDetails() == null
                            || t.getInvestmentDetails().getUnitsNav() == null) return null;
                    return String.format("%.4f", t.getInvestmentDetails().getUnitsNav());
                });
                return List.of(schemeCol, unitsCol);
            }

            if (invType == InvestmentAccount.InvestmentType.DEBT_BONDS
                    || invType == InvestmentAccount.InvestmentType.FIXED_DEPOSIT) {
                TableColumn<Transaction, String> matDateCol = col("Maturity Date", 110, t -> {
                    if (t.getInvestmentDetails() == null
                            || t.getInvestmentDetails().getFd() == null) return null;
                    LocalDate d = t.getInvestmentDetails().getFd().getMaturityDate();
                    return d != null ? d.format(dateFmt()) : null;
                });
                TableColumn<Transaction, String> matAmtCol = col("Maturity Amount", 110, t -> {
                    if (t.getInvestmentDetails() == null
                            || t.getInvestmentDetails().getFd() == null) return null;
                    Long p = t.getInvestmentDetails().getFd().getMaturityAmountPaise();
                    return p != null ? String.format("₹%,.2f", p / 100.0) : null;
                });
                return List.of(matDateCol, matAmtCol);
            }

            if (invType == InvestmentAccount.InvestmentType.RECURRING_DEPOSIT) {
                TableColumn<Transaction, String> matDateCol = col("Maturity Date", 110, t -> {
                    if (t.getRecurring() == null
                            || t.getRecurring().getRecurringId() == null) return null;
                    RecurringTransaction r = ds.findRecurringById(t.getRecurring().getRecurringId());
                    if (r == null) return null;
                    LocalDate d = r.getMaturityDate();
                    return d != null ? d.format(dateFmt()) : null;
                });
                return List.of(matDateCol);
            }
        }

        // Bank, Credit Card, and any unhandled investment sub-type → Category + Sub-category
        TableColumn<Transaction, String> catCol = col("Category", 100,
                t -> ds.getCategoryName(t.getClassification() != null

                        ? t.getClassification().getCategoryId() : null));
        TableColumn<Transaction, String> subCatCol = col("Sub-category", 100,
                t -> ds.getCategoryName(t.getClassification() != null
                        ? t.getClassification().getSubCategoryId() : null));
        return List.of(catCol, subCatCol);
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

    private boolean isMarketValueAccount(InvestmentAccount ia) {
        return ia.getInvestmentType() == InvestmentAccount.InvestmentType.MUTUAL_FUNDS
                || ia.getInvestmentType() == InvestmentAccount.InvestmentType.EQUITY;
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
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: 600; -fx-text-fill: -text-hint;");
        Label val = new Label(value);
        // Inline required: colour is runtime data (balance direction / CC outstanding)
        val.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + colour + ";");
        b.getChildren().addAll(lbl, val);
        return b;
    }

    // ── Import CSV ────────────────────────────────────────────────────────────

    private void doImportCsv(Runnable refreshTable) {
        DataStore ds = DataStore.getInstance();

        FileChooser fc = new FileChooser();
        fc.setTitle("Import Bank / CC Statement");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        ImportMapping prior = ImportService.findMapping(account.getId(), ds.getImportMappings());
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

        ImportMapping saved = ImportService.findMapping(account.getId(), ds.getImportMappings());
        String importDir    = file.getParentFile() != null ? file.getParentFile().getAbsolutePath() : null;
        String snapshot     = String.join(",", rows.get(0));
        boolean snapshotOk  = saved != null && snapshot.equals(saved.getHeaderSnapshot());

        // Always show mapping dialog — pre-filled when snapshot matches
        ImportMappingDialog dlg = new ImportMappingDialog(account, rows.get(0),
                snapshotOk ? saved : null);
        Optional<ImportMapping> mappingOpt = dlg.showAndWait();
        if (mappingOpt.isEmpty() || mappingOpt.get() == null) return;

        ImportMapping mapping = mappingOpt.get();
        mapping.setHeaderSnapshot(snapshot);
        if (importDir != null) mapping.setLastImportPath(importDir);
        ds.saveOrUpdateImportMapping(mapping);

        // Execute import
        ImportService.ImportResult result = ImportService.executeImport(rows, mapping, account, ds);

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
                            Transaction.Classification cl = new Transaction.Classification();
                            cl.setCategoryId(rule.getCategoryId());
                            cl.setSubCategoryId(rule.getSubCategoryId());
                            am.imported.setClassification(cl);
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
                                Transaction.Classification cl = new Transaction.Classification();
                                cl.setCategoryId(rule.getCategoryId());
                                cl.setSubCategoryId(rule.getSubCategoryId());
                                am.imported.setClassification(cl);
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
                            Transaction.Classification cl = new Transaction.Classification();
                            cl.setCategoryId(rule.getCategoryId());
                            cl.setSubCategoryId(rule.getSubCategoryId());
                            rm.imported.setClassification(cl);
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

        showImportCompleteDialog(result);
    }

    private void exportCsv(List<Transaction> txs) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Transactions as CSV");
        fc.setInitialFileName(account.getName().replaceAll("\\s+", "_") + "_transactions.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        String savedDir = mainWindow.getLastAccountExportDir();
        if (savedDir != null) {
            File dir = new File(savedDir);
            if (dir.isDirectory()) fc.setInitialDirectory(dir);
        }
        File file = fc.showSaveDialog(null);
        if (file == null) return;
        mainWindow.setLastAccountExportDir(file.getParent());
        try {
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println("Date,Description,Type,Category,Sub-category,Amount");
                DataStore ds = DataStore.getInstance();
                for (Transaction t : txs) {
                    // Export signed amounts so re-importing preserves EXPENSE vs INCOME.
                    // INCOME / transfer arriving here → positive; EXPENSE → negative; REST as per funds direction
                    long signedPaise;
                    if (t.getType() == Transaction.Type.INCOME) {
                        // INCOME
                        signedPaise = t.getAmountPaise();
                    } else if(t.getType() == Transaction.Type.EXPENSE) {
                        // EXPENSE
                        signedPaise = -t.getAmountPaise();
                    } else {
                        // All other TXN Types
                        signedPaise = account.getId().equals(t.getFromAccountId())
                                ? -t.getAmountPaise() : t.getAmountPaise();
                    }
                    pw.println(String.join(",",
                            t.getDate().format(dateFmt()),
                            "\"" + t.getDescription().replace("\"", "\"\"") + "\"",
                            t.getType().name(),
                            "\"" + ds.getCategoryName(t.getClassification() != null ? t.getClassification().getCategoryId() : null) + "\"",
                            "\"" + ds.getCategoryName(t.getClassification() != null ? t.getClassification().getSubCategoryId() : null) + "\"",
                            String.format("%.2f", signedPaise / 100.0)
                    ));
                }
            }
            info("Export Complete", "Saved " + txs.size() + " transaction(s) to:\n" + file.getAbsolutePath());
        } catch (IOException ex) {
            info("Export Failed", "Could not write file: " + ex.getMessage());
        }
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    public static Label typeBadge(Transaction.Type type) {
        Label lbl = new Label(UiUtils.badgeText(type));
        lbl.getStyleClass().addAll(UiUtils.badgeStyle(type), "badge-sm");
        return lbl;
    }

    // ── Styled Delete Transaction dialog ──────────────────────────────────────

    private boolean showDeleteTxnConfirm(Transaction t) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Delete Transaction");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(400);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "⚠", "Delete Transaction");

        boolean isGrouped = t.getGroupTransactionId() != null;

        VBox body = new VBox(14);
        body.setPadding(new Insets(16));

        HBox warnRow = new HBox(12);
        warnRow.setAlignment(Pos.CENTER_LEFT);
        Label warnIcon = new Label("⚠");
        warnIcon.setStyle("-fx-font-size: 22px; -fx-text-fill: -color-error;");
        VBox warnText = new VBox(4);
        Label headline = new Label(isGrouped ? "Delete linked redemption group?" : "Delete this transaction?");
        headline.getStyleClass().add("text-section-title");
        String subMsg = "This action cannot be undone."
                + (isGrouped ? " This will also delete the related principal and gain/loss entries." : "");
        Label subLbl = new Label(subMsg);
        subLbl.getStyleClass().add("text-hint");
        subLbl.setWrapText(true);
        subLbl.setMaxWidth(310);
        warnText.getChildren().addAll(headline, subLbl);
        warnRow.getChildren().addAll(warnIcon, warnText);

        VBox txnBlock = new VBox(5);
        // Danger-tinted preview block — specific red tint has no CSS token; keep inline
        txnBlock.setStyle(
                "-fx-background-color: #fef2f2; -fx-border-color: #fecaca; "
                + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-border-width: 1; "
                + "-fx-padding: 12 14;");
        Label desc = new Label(t.getDescription());
        desc.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: -brand-dark;");
        desc.setWrapText(true);
        HBox meta = new HBox(8);
        meta.setAlignment(Pos.CENTER_LEFT);
        Label amt = new Label(t.getAmountInr());
        amt.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: -color-error;");
        Label sep = new Label("·");
        sep.getStyleClass().add("text-hint");
        Label date = new Label(t.getDate().format(dateFmt()));
        date.getStyleClass().add("text-hint");
        meta.getChildren().addAll(amt, sep, date);
        txnBlock.getChildren().addAll(desc, meta);

        body.getChildren().addAll(warnRow, txnBlock);
        dlg.getDialogPane().setContent(body);

        ButtonType deleteBtn = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, deleteBtn);
        Button deleteButton = (Button) dlg.getDialogPane().lookupButton(deleteBtn);
        if (deleteButton != null) {
            ButtonBar.setButtonUniformSize(deleteButton, false);
            Platform.runLater(() -> {
                // Inline required: dialog button styling is applied post-show via Platform.runLater
                deleteButton.setStyle("-fx-background-color: -color-error; -fx-text-fill: white; "
                        + "-fx-font-weight: 600; -fx-background-radius: 8; -fx-padding: 7 20;");
            });
        }

        return dlg.showAndWait().filter(b -> b == deleteBtn).isPresent();
    }

    // ── Styled Import Complete dialog ─────────────────────────────────────────

    private void showImportCompleteDialog(ImportService.ImportResult result) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Import Complete");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(420);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "✓", "Import Complete");

        VBox body = new VBox(0);
        body.setPadding(new Insets(16));

        HBox titleRow = new HBox(14);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setPadding(new Insets(0, 0, 14, 0));
        Label iconLbl = new Label("✓");
        // Inline required: gradient circle icon has no CSS class equivalent
        iconLbl.setStyle(
                "-fx-background-color: linear-gradient(135deg, #2a8a7a, #3db89a); "
                + "-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; "
                + "-fx-min-width: 40; -fx-max-width: 40; -fx-min-height: 40; -fx-max-height: 40; "
                + "-fx-background-radius: 20; -fx-alignment: CENTER; -fx-padding: 0;");
        Label titleLbl = new Label("Import Complete");
        titleLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: -brand-dark;");
        titleRow.getChildren().addAll(iconLbl, titleLbl);

        VBox lines = new VBox(0);
        lines.getStyleClass().add("info-box");
        lines.getChildren().addAll(
                importLine(result.newCount, "new transaction(s) added", true),
                importLine(result.reconciledCount, "reconciled with existing manual entries", true),
                importLine(result.recurringReconciledCount, "recorded against recurring schedule", true),
                importLine(result.skippedCount, "skipped (already imported)", false));

        body.getChildren().addAll(titleRow, lines);

        if (!result.ambiguous.isEmpty()) {
            Label warn = new Label("⚠  " + result.ambiguous.size() + " ambiguous match(es) resolved manually");
            // #856404 is amber warning text; -color-warning (#B7450D) is a different hue — keep hex
            warn.setStyle("-fx-text-fill: #856404; -fx-font-size: 12px; -fx-padding: 10 0 0 0;");
            body.getChildren().add(warn);
        }

        dlg.getDialogPane().setContent(body);
        ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().add(ok);
        dlg.showAndWait();
    }

    private HBox importLine(int count, String desc, boolean successStyle) {
        HBox line = new HBox(10);
        line.setAlignment(Pos.CENTER_LEFT);
        line.setPadding(new Insets(8, 14, 8, 14));
        // Bottom-border divider between import result rows
        line.setStyle("-fx-border-color: transparent transparent rgba(42,138,122,0.12) transparent; "
                + "-fx-border-width: 1;");
        String checkBg  = (successStyle && count > 0) ? "#f0fdf4" : "#f8fbfc";
        String checkFg  = (successStyle && count > 0) ? "#16a34a" : "#7aa4b0";
        String checkBdr = (successStyle && count > 0) ? "#bbf7d0" : "rgba(42,138,122,0.15)";
        String symbol   = successStyle ? "✓" : "⊘";
        Label check = new Label(symbol);
        // Inline required: success/failure badge colours are runtime data
        check.setStyle("-fx-background-color: " + checkBg + "; -fx-text-fill: " + checkFg + "; "
                + "-fx-border-color: " + checkBdr + "; "
                + "-fx-border-radius: 10; -fx-background-radius: 10; "
                + "-fx-font-size: 10px; -fx-font-weight: bold; "
                + "-fx-min-width: 20; -fx-max-width: 20; -fx-min-height: 20; -fx-max-height: 20; "
                + "-fx-padding: 0; -fx-alignment: CENTER;");
        Label cnt = new Label(String.valueOf(count));
        // Inline required: count text colour depends on whether count > 0
        cnt.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; "
                + "-fx-text-fill: " + (count > 0 ? "-brand-dark" : "-text-hint") + "; "
                + "-fx-min-width: 28; -fx-alignment: CENTER_RIGHT;");
        Label txt = new Label(desc);
        txt.getStyleClass().add("text-body-muted");
        line.getChildren().addAll(check, cnt, txt);
        return line;
    }

    /**
     * Returns the principal for a LOAN_PAYMENT transaction in paise.
     * Reads from redeemDetails if stored; otherwise falls back to the amortization schedule.
     */
    private static long effectiveLoanPrincipalPaise(Transaction t, DataStore ds) {
        long p = t.getRedeemDetails() != null ? t.getRedeemDetails().getPrincipalPaise() : 0;
        if (p <= 0 && t.getToAccountId() != null) {
            p = AmortizationService.getScheduledPrincipalForDate(
                    ds.getSchedule(t.getToAccountId()), t.getDate());
        }
        return p;
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
}
