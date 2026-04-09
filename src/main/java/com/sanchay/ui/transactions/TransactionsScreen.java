package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.service.AmortizationService;
import com.sanchay.service.DataStore;
import com.sanchay.service.ImportService;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.MainWindow;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
//import javafx.scene.paint.Color;
//import javafx.scene.shape.Circle;
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
        panel.setId("txn-screen-panel");
        panel.getStyleClass().add("main-panel");
        panel.setPadding(new Insets(24));

        HBox header = new HBox(12);
        header.setId("txn-screen-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Button back = new Button("⬅");
        back.setId("txn-back-button");
        back.getStyleClass().add("btn-secondary");
        back.setTooltip(new Tooltip("Back"));
        back.setOnAction(e -> mainWindow.navigateTo("Accounts"));
        Label title = new Label(account.getName() + " — Transactions");
        title.setId("txn-screen-title");
        title.getStyleClass().add("screen-title");
        header.getChildren().addAll(back, title);

        // Mutable stat labels — updated by refreshHeader after data changes
        final Runnable[] refreshHeader = { null };

        if (account instanceof CreditCardAccount cc) {
            Label outstandingVal = new Label();
            Label availableVal   = new Label();
            outstandingVal.setId("txn-cc-outstanding-value");
            availableVal.setId("txn-cc-available-value");
            outstandingVal.getStyleClass().add("stat-value");
            availableVal.getStyleClass().add("stat-value");

            HBox ccSummary = new HBox(24);
            ccSummary.getStyleClass().add("card");
            ccSummary.setPadding(new Insets(12, 16, 12, 16));
            ccSummary.setAlignment(Pos.CENTER_LEFT);
            ccSummary.getChildren().addAll(
                    ccStat("Credit Limit",  MoneyFormatter.format(cc.getCreditLimitPaise()), "-text-neutral"),
                    ccStatWithLabel("Outstanding",   outstandingVal, "-color-expense"),
                    ccStatWithLabel("Available",     availableVal,   "-color-income"),
                    ccStat("Billing Date",  cc.getBillingCycleDate() + " of month", "-text-neutral"),
                    ccStat("Payment Due",   cc.getPaymentDueDays() + " days after billing", "-text-neutral")
            );
            panel.getChildren().addAll(header, ccSummary);

            refreshHeader[0] = () -> {
                long out = DataStore.getInstance().getCreditCardOutstandingPaise(cc.getId());
                long avail = Math.min(cc.getCreditLimitPaise(), cc.getCreditLimitPaise() - out);
                outstandingVal.setText(MoneyFormatter.format(out));
                availableVal.setText(MoneyFormatter.format(avail));
            };
        } else {
            DataStore ds2 = DataStore.getInstance();
            if (account instanceof BankAccount ba) {
                Label balVal = new Label();
                balVal.setId("txn-balance-value");
                balVal.getStyleClass().add("stat-value");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                header.getChildren().addAll(spacer, ccStatWithLabel("Balance", balVal, "-brand-dark"));
                refreshHeader[0] = () -> {
                    long bal = ba.getOpeningBalancePaise();
                    for (Transaction t : DataStore.getInstance().getTransactions()) {
                        if (ba.getId().equals(t.getFromAccountId())) bal -= t.getAmountPaise();
                        if (ba.getId().equals(t.getToAccountId()))   bal += t.getAmountPaise();
                    }
                    balVal.setText(MoneyFormatter.format(bal));
                };
            } else if (account instanceof LoanAccount la) {
                Label outVal = new Label();
                outVal.setId("txn-loan-outstanding-value");
                outVal.getStyleClass().add("stat-value");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                header.getChildren().addAll(spacer, ccStatWithLabel("Outstanding", outVal, "-color-error"));
                refreshHeader[0] = () -> {
                    long outstanding = ds2.getLoanOutstandingPaise(la);
                    outVal.setText(MoneyFormatter.formatNoDecimal(outstanding));
                    outVal.setStyle("-fx-text-fill: " + (outstanding > 0 ? "-color-error" : "-brand-dark") + ";");
                };
            } else if (account instanceof InvestmentAccount ia) {
                Label investedVal = new Label();
                investedVal.setId("txn-invested-value");
                investedVal.getStyleClass().add("stat-value");
                if (isMarketValueAccount(ia)) {
                    MarketValueEntry mv = ds2.getLatestMarketValue(ia.getId());
                    if (mv != null) {
                        Label mvVal = new Label();
                        Label glVal = new Label();
                        mvVal.setId("txn-market-value");
                        glVal.setId("txn-gain-loss-value");
                        mvVal.getStyleClass().add("stat-value");
                        glVal.getStyleClass().add("stat-value");
                        Region spacer2 = new Region();
                        HBox.setHgrow(spacer2, Priority.ALWAYS);
                        header.getChildren().addAll(
                                spacer2,
                                ccStatWithLabel("Market Value", mvVal, "-brand-dark"),
                                ccStatWithLabel("Gain / Loss",  glVal, "-brand-dark")
                        );
                        refreshHeader[0] = () -> {
                            MarketValueEntry latest = DataStore.getInstance().getLatestMarketValue(ia.getId());
                            if (latest != null) {
                                long gl = latest.getGainLossPaise();
                                mvVal.setText(MoneyFormatter.formatNoDecimal(latest.getMarketValuePaise()));
                                glVal.setText((gl >= 0 ? "+" : "") + MoneyFormatter.formatNoDecimal(Math.abs(gl)));
                                glVal.setStyle("-fx-text-fill: " + (gl >= 0 ? "-color-income" : "-color-error") + ";");
                            }
                        };
                    }
                } else {
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    header.getChildren().addAll(spacer, ccStatWithLabel("Invested", investedVal, "-brand-dark"));
                    refreshHeader[0] = () -> {
                        long invested = ds2.getBaseInvestedPaise(ia);
                        for (Transaction t : DataStore.getInstance().getTransactions()) {
                            if (t.getType() == Transaction.Type.INVESTMENT && ia.getId().equals(t.getToAccountId()))
                                invested += t.getAmountPaise();
                            if (t.getType() == Transaction.Type.REDEEM && ia.getId().equals(t.getFromAccountId())) {
                                long rdPrin = t.getRedeemDetails() != null ? t.getRedeemDetails().getPrincipalPaise() : 0;
                                invested -= rdPrin > 0 ? rdPrin : t.getAmountPaise();
                            }
                        }
                        investedVal.setText(MoneyFormatter.formatNoDecimal(Math.max(0, invested)));
                    };
                }
            }
            panel.getChildren().add(header);
        }

        // Run once to populate initial values
        if (refreshHeader[0] != null) refreshHeader[0].run();

        // ── Filters ────────────────────────────────────────────────────────────
        TextField search = new TextField();
        search.setId("txn-search-field");

        search.setPromptText("Search description or notes…");
        search.getStyleClass().add("filter-field");
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);

        DataStore ds = DataStore.getInstance();
        //DatePicker fromPicker = new DatePicker(ds.getActiveFYStart());
        //DatePicker toPicker   = new DatePicker(ds.getActiveFYEnd());
        // Get today's date
        LocalDate today = LocalDate.now();

        // From date: 6 months before current month, first day
        YearMonth fromMonth = YearMonth.from(today).minusMonths(6);
        LocalDate fromDate = fromMonth.atDay(1);

        // To date: 3 months after current month, last day
        YearMonth toMonth = YearMonth.from(today).plusMonths(3);
        LocalDate toDate = toMonth.atEndOfMonth();

        // Create DatePickers
        DatePicker fromPicker = new DatePicker(fromDate);
        DatePicker toPicker   = new DatePicker(toDate);
        fromPicker.setId("txn-from-date-picker");
        toPicker.setId("txn-to-date-picker");

        fromPicker.setPrefWidth(130);
        toPicker.setPrefWidth(130);
        fromPicker.getStyleClass().add("filter-field");
        toPicker.getStyleClass().add("filter-field");
        UiUtils.applySmartDateConverter(fromPicker);
        UiUtils.applySmartDateConverter(toPicker);
        UiUtils.styleOnShow(fromPicker);
        UiUtils.styleOnShow(toPicker);

        Label fromLbl = new Label("FROM");
        fromLbl.setId("txn-from-date-label");
        fromLbl.getStyleClass().add("filter-label");
        Label toLbl = new Label("TO");
        toLbl.setId("txn-to-date-label");
        toLbl.getStyleClass().add("filter-label");

        CheckBox pendingOnly = new CheckBox("Show pending review only");
        pendingOnly.setId("txn-pending-only-checkbox");
        pendingOnly.getStyleClass().add("text-hint");

        ComboBox<Transaction.Type> typeFilter = new ComboBox<>();
        typeFilter.setId("txn-type-filter-combo");
        typeFilter.getItems().add(null);
        typeFilter.getItems().addAll(
                Transaction.Type.EXPENSE, Transaction.Type.INCOME, Transaction.Type.TRANSFER,
                Transaction.Type.REFUND, Transaction.Type.INVESTMENT, Transaction.Type.CC_PAYMENT,
                Transaction.Type.REDEEM, Transaction.Type.LOAN_PAYMENT
        );
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

        Region filterSep = new Region();
        filterSep.getStyleClass().add("filter-separator");

        HBox filterRow = new HBox(10);
        filterRow.setId("txn-filter-row");
        filterRow.getStyleClass().add("filter-bar");
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.getChildren().addAll(
                fromLbl, fromPicker,
                toLbl, toPicker,
                filterSep,
                typeFilter,
                search,
                pendingOnly
        );

        // ── Transaction table ──────────────────────────────────────────────────
        TableView<Transaction> table = new TableView<>();
        table.setId("txn-table");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);


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
                setText(MoneyFormatter.formatSigned(paise));
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
                    .filter(t -> typeFilter.getValue() == null || t.getType() == typeFilter.getValue())
                    .filter(t -> q.isEmpty()
                            || t.getDescription().toLowerCase().contains(q)
                            || (t.getNotes() != null && t.getNotes().toLowerCase().contains(q)))
                    .filter(t -> !pendingOnly.isSelected()
                            || t.getSourceIndicator() == Transaction.SourceIndicator.AUTO_CATEGORIZED
                            || t.getSourceIndicator() == Transaction.SourceIndicator.IMPORTED
                            || t.getSourceIndicator() == Transaction.SourceIndicator.MANUAL)
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
            if (refreshHeader[0] != null) refreshHeader[0].run();
        };

        search.textProperty().addListener((obs, o, n) -> applyFilter.run());
        fromPicker.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        toPicker.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilter.run());
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
                    setId(empty || t == null ? null : "txn-table-row-" + sanitizeId(t.getId()));
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
            } else if (e.getCode() == KeyCode.V && e.isControlDown()
                    && (account instanceof BankAccount || account instanceof CreditCardAccount)) {
                String clip = Clipboard.getSystemClipboard().getString();
                if (clip == null || clip.isBlank()) return;
                List<String[]> rows = ImportService.parseText(clip);
                if (rows.isEmpty() || rows.get(0).length < 2) return;
                doImportRows(rows, null, applyFilter);
                e.consume();
            }
        });

        TableColumn<Transaction, Void> actionsCol = new TableColumn<>("");
        actionsCol.setMinWidth(36);
        actionsCol.setMaxWidth(36);
        actionsCol.setCellFactory(tc -> new TableCell<>() {
            private final Label deleteBtn = new Label("×");
            {
                deleteBtn.setId("txn-delete-action");
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
                deleteBtn.setId("txn-delete-action-" + sanitizeId(getTableRow().getItem().getId()));
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
                        badge.setTooltip(new Tooltip("Imported from file — right-click to merge with existing"));
                        ContextMenu cm = new ContextMenu();
                        MenuItem mergeItem = new MenuItem("Merge with existing…");
                        mergeItem.setOnAction(ev -> openMergeDialog(t, applyFilter));
                        cm.getItems().add(mergeItem);
                        badge.setContextMenu(cm);
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
                        tip.append("\nLeft-click to accept · Right-click to merge with existing");
                        badge.setTooltip(new Tooltip(tip.toString()));
                        badge.setOnMouseClicked(e -> {
                            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                                t.setSourceIndicator(Transaction.SourceIndicator.RECONCILED);
                                DataStore.getInstance().saveTransactionsNow();
                                applyFilter.run();
                                e.consume();
                            }
                        });
                        ContextMenu cmAc = new ContextMenu();
                        MenuItem mergeItemAc = new MenuItem("Merge with existing…");
                        mergeItemAc.setOnAction(ev -> openMergeDialog(t, applyFilter));
                        cmAc.getItems().add(mergeItemAc);
                        badge.setContextMenu(cmAc);
                    }
                    case RECONCILED -> {
                        badge.setText("R");
                        badge.getStyleClass().add("badge-reconciled");
                        badge.setTooltip(new Tooltip("Reconciled with import"));
                    }
                    default -> {
                        badge.setText("M");
                        badge.getStyleClass().add("badge-manual");
                        badge.setTooltip(new Tooltip("Manually entered — right-click to mark as reconciled"));
                        ContextMenu cmM = new ContextMenu();
                        MenuItem markItem = new MenuItem("Mark as Reconciled");
                        markItem.setOnAction(ev -> {
                            t.setSourceIndicator(Transaction.SourceIndicator.RECONCILED);
                            DataStore.getInstance().saveTransactionsNow();
                            applyFilter.run();
                        });
                        cmM.getItems().add(markItem);
                        badge.setContextMenu(cmM);
                    }
                }
                badge.setId("txn-source-badge-" + sanitizeId(t.getId()));
                setGraphic(badge);
            }
        });

        boolean isPf = account instanceof InvestmentAccount pfIa
                && pfIa.getInvestmentType() == InvestmentAccount.InvestmentType.PROVIDENT_FUND;
        table.getColumns().addAll(dateCol, descCol, typeCol);
        if (!isPf) table.getColumns().add(acctCol);
        table.getColumns().addAll(specialtyCols);
        table.getColumns().addAll(amtCol, srcCol, actionsCol);
        table.getSortOrder().add(dateCol);

        Button exportBtn = new Button("Export CSV");
        exportBtn.setId("txn-export-button");
        exportBtn.getStyleClass().add("btn-gold");
        exportBtn.setOnAction(e -> exportCsv(table.getItems()));

        HBox footerRow = new HBox(8);
        footerRow.setId("txn-footer-row");
        footerRow.getStyleClass().add("table-footer");
        footerRow.setAlignment(Pos.CENTER_LEFT);
        Label hintLbl = UiUtils.hintLabel(
                (account instanceof BankAccount || account instanceof CreditCardAccount)
                ? "Double-click a row to edit  ·  Ctrl+V to paste from Excel / CSV"
                : "Double-click a row to edit");
        hintLbl.setId("txn-footer-hint");
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        footerRow.getChildren().addAll(hintLbl, footerSpacer);
        if (account instanceof BankAccount || account instanceof CreditCardAccount) {
            Button importBtn = new Button("Import CSV");
            importBtn.setId("txn-import-button");
            importBtn.getStyleClass().add("btn-gold");
            importBtn.setOnAction(e -> doImportCsv(applyFilter));
            footerRow.getChildren().add(importBtn);
        }
        footerRow.getChildren().add(exportBtn);

        VBox tableCard = new VBox();
        tableCard.setId("txn-table-card");
        tableCard.getStyleClass().add("table-card");
        table.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        tableCard.getChildren().addAll(table, footerRow);

        VBox.setVgrow(tableCard, Priority.ALWAYS);

        panel.getChildren().addAll(filterRow, tableCard);

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setId("txn-screen-scroll-pane");
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
                return p > 0 ? MoneyFormatter.formatNoDecimal(p) : null;
            });
            TableColumn<Transaction, String> interestCol = col("Interest", 100, t -> {
                if (t.getType() != Transaction.Type.LOAN_PAYMENT) return null;
                long principal = effectiveLoanPrincipalPaise(t, ds);
                if (principal <= 0) return null;
                long interest = t.getAmountPaise() - principal;
                return interest > 0 ? MoneyFormatter.formatNoDecimal(interest) : null;
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
                    return p != null ? MoneyFormatter.format(p) : null;
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

            if (invType == InvestmentAccount.InvestmentType.PROVIDENT_FUND) {
                return List.of(); // no specialty columns for PF
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

    private VBox ccStat(String label, String value, String colour) {
        VBox b = new VBox(2);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("stat-label");
        Label val = new Label(value);
        val.getStyleClass().add("stat-value");
        // Inline required: colour is runtime data (balance direction / CC outstanding)
        val.setStyle("-fx-text-fill: " + colour + ";");
        b.getChildren().addAll(lbl, val);
        return b;
    }

    /** Like ccStat but accepts a pre-created value Label so the caller can update it later. */
    private VBox ccStatWithLabel(String label, Label val, String colour) {
        VBox b = new VBox(2);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("stat-label");
        val.setStyle("-fx-text-fill: " + colour + ";");
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

        List<String[]> rows;
        try { rows = ImportService.parseCsv(file); }
        catch (IOException ex) { info("Import Failed", "Could not read file:\n" + ex.getMessage()); return; }

        if (rows.isEmpty()) { info("Import Failed", "The file is empty."); return; }

        String importDir = file.getParentFile() != null ? file.getParentFile().getAbsolutePath() : null;
        doImportRows(rows, importDir, refreshTable);
    }

    /**
     * Core import flow shared by file import and clipboard paste.
     * {@code importDir} is the directory of the source file (used to remember the last import
     * path); pass {@code null} when importing from the clipboard.
     */
    private void doImportRows(List<String[]> rows, String importDir, Runnable refreshTable) {
        DataStore ds = DataStore.getInstance();

        // Validate header
        if (!ImportService.isLikelyHeader(rows.get(0))) {
            info("Import Rejected",
                    "The first row does not look like a header row.\n"
                    + "Please ensure the data has column headers in the first row.");
            return;
        }

        ImportMapping saved = ImportService.findMapping(account.getId(), ds.getImportMappings());
        String snapshot    = String.join(",", rows.get(0));
        boolean snapshotOk = saved != null && snapshot.equals(saved.getHeaderSnapshot());

        // Always show mapping dialog — pre-filled when snapshot matches
        String[] sampleRow = rows.size() > 1 ? rows.get(1) : null;
        ImportMappingDialog dlg = new ImportMappingDialog(account, rows.get(0),
                snapshotOk ? saved : null, sampleRow);
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
        new ImportCompleteDialog(result).show();
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

        DataStore ds = DataStore.getInstance();
        boolean isPf = account instanceof InvestmentAccount pfIa
                && pfIa.getInvestmentType() == InvestmentAccount.InvestmentType.PROVIDENT_FUND;

        // Determine specialty column header and per-row extractor based on account type.
        // Extractor returns already-formatted, CSV-safe strings (quoted where needed).
        String specialtyHeader;
        java.util.function.Function<Transaction, List<String>> specialtyExtractor;

        if (isPf) {
            specialtyHeader = null;
            specialtyExtractor = t -> List.of();
        } else if (account instanceof LoanAccount) {
            specialtyHeader = "Principal,Interest";
            specialtyExtractor = t -> {
                if (t.getType() != Transaction.Type.LOAN_PAYMENT) return List.of("", "");
                long p = effectiveLoanPrincipalPaise(t, ds);
                String principal = p > 0 ? String.format("%.2f", p / 100.0) : "";
                long interest = p > 0 ? t.getAmountPaise() - p : 0;
                String interestStr = interest > 0 ? String.format("%.2f", interest / 100.0) : "";
                return List.of(principal, interestStr);
            };
        } else if (account instanceof InvestmentAccount ia) {
            InvestmentAccount.InvestmentType invType = ia.getInvestmentType();
            if (invType == InvestmentAccount.InvestmentType.MUTUAL_FUNDS
                    || invType == InvestmentAccount.InvestmentType.EQUITY) {
                specialtyHeader = "Scheme / Script,Units / NAV";
                specialtyExtractor = t -> {
                    if (t.getInvestmentDetails() == null) return List.of("", "");
                    String scheme = t.getInvestmentDetails().getSchemeScriptName() != null
                            ? "\"" + t.getInvestmentDetails().getSchemeScriptName().replace("\"", "\"\"") + "\""
                            : "";
                    String units = t.getInvestmentDetails().getUnitsNav() != null
                            ? String.format("%.4f", t.getInvestmentDetails().getUnitsNav()) : "";
                    return List.of(scheme, units);
                };
            } else if (invType == InvestmentAccount.InvestmentType.FIXED_DEPOSIT
                    || invType == InvestmentAccount.InvestmentType.DEBT_BONDS) {
                specialtyHeader = "Maturity Date,Maturity Amount";
                specialtyExtractor = t -> {
                    if (t.getInvestmentDetails() == null || t.getInvestmentDetails().getFd() == null)
                        return List.of("", "");
                    LocalDate d = t.getInvestmentDetails().getFd().getMaturityDate();
                    Long p = t.getInvestmentDetails().getFd().getMaturityAmountPaise();
                    return List.of(
                            d != null ? d.format(dateFmt()) : "",
                            p != null ? String.format("%.2f", p / 100.0) : "");
                };
            } else if (invType == InvestmentAccount.InvestmentType.RECURRING_DEPOSIT) {
                specialtyHeader = "Maturity Date";
                specialtyExtractor = t -> {
                    if (t.getRecurring() == null || t.getRecurring().getRecurringId() == null)
                        return List.of("");
                    RecurringTransaction r = ds.findRecurringById(t.getRecurring().getRecurringId());
                    if (r == null) return List.of("");
                    LocalDate d = r.getMaturityDate();
                    return List.of(d != null ? d.format(dateFmt()) : "");
                };
            } else {
                specialtyHeader = "Category,Sub-category";
                specialtyExtractor = t -> List.of(
                        "\"" + ds.getCategoryName(t.getClassification() != null ? t.getClassification().getCategoryId() : null) + "\"",
                        "\"" + ds.getCategoryName(t.getClassification() != null ? t.getClassification().getSubCategoryId() : null) + "\"");
            }
        } else {
            // Bank / Credit Card
            specialtyHeader = "Category,Sub-category";
            specialtyExtractor = t -> List.of(
                    "\"" + ds.getCategoryName(t.getClassification() != null ? t.getClassification().getCategoryId() : null) + "\"",
                    "\"" + ds.getCategoryName(t.getClassification() != null ? t.getClassification().getSubCategoryId() : null) + "\"");
        }

        // Build header
        List<String> headerCols = new ArrayList<>(List.of("Date", "Description", "Type"));
        if (!isPf) headerCols.add("To / From Account");
        if (specialtyHeader != null) headerCols.addAll(List.of(specialtyHeader.split(",")));
        headerCols.add("Amount");

        // Pre-build group-id → account-id maps for REDEEM split transactions
        Map<String, String> groupFromId = new java.util.HashMap<>();
        Map<String, String> groupToId   = new java.util.HashMap<>();
        for (Transaction gt : ds.getTransactions()) {
            String gid = gt.getGroupTransactionId();
            if (gid == null) continue;
            if (gt.getFromAccountId() != null) groupFromId.put(gid, gt.getFromAccountId());
            if (gt.getToAccountId()   != null) groupToId  .put(gid, gt.getToAccountId());
        }

        try {
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println(String.join(",", headerCols));
                for (Transaction t : txs) {
                    // Export signed amounts so re-importing preserves EXPENSE vs INCOME.
                    long signedPaise;
                    if (t.getType() == Transaction.Type.INCOME) {
                        signedPaise = t.getAmountPaise();
                    } else if (t.getType() == Transaction.Type.EXPENSE) {
                        signedPaise = -t.getAmountPaise();
                    } else {
                        signedPaise = account.getId().equals(t.getFromAccountId())
                                ? -t.getAmountPaise() : t.getAmountPaise();
                    }

                    List<String> row = new ArrayList<>();
                    row.add(t.getDate().format(dateFmt()));
                    row.add("\"" + t.getDescription().replace("\"", "\"\"") + "\"");
                    row.add(t.getType().name());

                    if (!isPf) {
                        String secondId = account.getId().equals(t.getFromAccountId())
                                ? t.getToAccountId() : t.getFromAccountId();
                        if (secondId == null && t.getGroupTransactionId() != null) {
                            String gid = t.getGroupTransactionId();
                            secondId = account.getId().equals(t.getFromAccountId())
                                    ? groupToId.get(gid) : groupFromId.get(gid);
                        }
                        String acctName = ds.getAccountName(secondId);
                        row.add("\"" + ("—".equals(acctName) ? "" : acctName) + "\"");
                    }

                    row.addAll(specialtyExtractor.apply(t));
                    row.add(String.format("%.2f", signedPaise / 100.0));
                    pw.println(String.join(",", row));
                }
            }
            info("Export Complete", "Saved " + txs.size() + " transaction(s) to:\n" + file.getAbsolutePath());
        } catch (IOException ex) {
            info("Export Failed", "Could not write file: " + ex.getMessage());
        }
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        UiUtils.initDialog(a, title, "i", 400);
        Label content = new Label(msg);
        content.setId("txn-info-dialog-message");
        content.setWrapText(true);
        content.getStyleClass().add("dialog-body-text");
        a.getDialogPane().setContent(content);
        a.showAndWait();
    }

    public static Label typeBadge(Transaction.Type type) {
        Label lbl = new Label(UiUtils.badgeText(type));
        lbl.setId("txn-type-badge-" + type.name().toLowerCase(Locale.ENGLISH));
        lbl.getStyleClass().addAll(UiUtils.badgeStyle(type), "badge-sm");
        return lbl;
    }

    // ── Merge imported with manual ────────────────────────────────────────────

    private void openMergeDialog(Transaction imported, Runnable refresh) {
        DataStore ds = DataStore.getInstance();
        LocalDate date = imported.getDate();
        List<Transaction> candidates = ds.getTransactions().stream()
                .filter(t -> t.getSourceIndicator() == Transaction.SourceIndicator.MANUAL)
                .filter(t -> account.getId().equals(t.getFromAccountId())
                          || account.getId().equals(t.getToAccountId()))
                .filter(t -> !t.getDate().isBefore(date.minusDays(10))
                          && !t.getDate().isAfter(date.plusDays(10)))
                .sorted(Comparator.comparingLong(t -> Math.abs(
                        ChronoUnit.DAYS.between(t.getDate(), date))))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            info("No Candidates Found",
                    "No manually-entered transactions exist within ±10 days of "
                    + date.format(dateFmt()) + " for this account.");
            return;
        }

        new MergeWithManualDialog(imported, candidates, dateFmt())
                .showAndWait()
                .ifPresent(chosen -> {
                    ImportService.reconcile(imported, chosen, ds);
                    ds.deleteTransactionByIdInternal(imported.getId());
                    ds.saveTransactionsNow();
                    refresh.run();
                });
    }

    // ── Styled Delete Transaction dialog ──────────────────────────────────────

    private boolean showDeleteTxnConfirm(Transaction t) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.getDialogPane().setId("txn-delete-dialog-pane");
        dlg.setTitle("Delete Transaction");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(400);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "⚠", "Delete Transaction");

        boolean isGrouped = t.getGroupTransactionId() != null;

        VBox body = new VBox(14);
        body.setId("txn-delete-dialog-body");
        body.setPadding(new Insets(16));

        HBox warnRow = new HBox(12);
        warnRow.setId("txn-delete-dialog-warning-row");
        warnRow.setAlignment(Pos.CENTER_LEFT);
        Label warnIcon = new Label("⚠");
        warnIcon.setId("txn-delete-dialog-warning-icon");
        warnIcon.getStyleClass().add("icon-danger");
        VBox warnText = new VBox(4);
        Label headline = new Label(isGrouped ? "Delete linked redemption group?" : "Delete this transaction?");
        headline.setId("txn-delete-dialog-headline");
        headline.getStyleClass().add("text-section-title");
        String subMsg = "This action cannot be undone."
                + (isGrouped ? " This will also delete the related principal and gain/loss entries." : "");
        Label subLbl = new Label(subMsg);
        subLbl.setId("txn-delete-dialog-subtext");
        subLbl.getStyleClass().add("text-hint");
        subLbl.setWrapText(true);
        subLbl.setMaxWidth(310);
        warnText.getChildren().addAll(headline, subLbl);
        warnRow.getChildren().addAll(warnIcon, warnText);

        VBox txnBlock = new VBox(5);
        txnBlock.setId("txn-delete-dialog-transaction-block");
        txnBlock.getStyleClass().add("dialog-danger-block");
        Label desc = new Label(t.getDescription());
        desc.setId("txn-delete-dialog-description");
        desc.getStyleClass().add("text-body-strong");
        desc.setWrapText(true);
        HBox meta = new HBox(8);
        meta.setId("txn-delete-dialog-meta");
        meta.setAlignment(Pos.CENTER_LEFT);
        Label amt = new Label(t.getAmountInr());
        amt.setId("txn-delete-dialog-amount");
        amt.getStyleClass().add("text-amount-error");
        Label sep = new Label("·");
        sep.setId("txn-delete-dialog-separator");
        sep.getStyleClass().add("text-hint");
        Label date = new Label(t.getDate().format(dateFmt()));
        date.setId("txn-delete-dialog-date");
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
            deleteButton.getStyleClass().add("btn-danger");
            deleteButton.setId("txn-delete-dialog-confirm-button");
        }
        Button cancelButton = (Button) dlg.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelButton != null) cancelButton.setId("txn-delete-dialog-cancel-button");

        return dlg.showAndWait().filter(b -> b == deleteBtn).isPresent();
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

    private String sanitizeId(String raw) {
        return raw == null ? "unknown" : raw.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
