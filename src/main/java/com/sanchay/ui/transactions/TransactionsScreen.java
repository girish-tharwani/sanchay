package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.service.AmortizationService;
import com.sanchay.service.DataStore;
import com.sanchay.service.ImportService;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.NavigationContext;
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
import java.util.*;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.stage.FileChooser;

/** Transactions screen for viewing and managing transactions for a specific account. */
public class TransactionsScreen {

    private static final PseudoClass PC_IMPORTED   = PseudoClass.getPseudoClass("row-imported");
    private static final PseudoClass PC_RECONCILED = PseudoClass.getPseudoClass("row-reconciled");

    private final NavigationContext navCtx;
    private final Account account;

    // view is initialised ONCE; never reassigned so that the reference held by MainWindow stays valid
    private final StackPane view;

    public TransactionsScreen(Account account, NavigationContext navCtx) {
        this.navCtx = navCtx;
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
        back.setOnAction(e -> navCtx.navigateBack());
        Label title = new Label(account.getName() + " — Transactions");
        title.setId("txn-screen-title");
        title.getStyleClass().add("screen-title");
        header.getChildren().addAll(back, title);

        TransactionStatsPanel statsPanel = new TransactionStatsPanel(account);
        statsPanel.addToLayout(header, panel);

        // ── Filters ────────────────────────────────────────────────────────────
        TextField search = new TextField();
        search.setId("txn-search-field");
        search.setPromptText("Search description or notes…");
        search.getStyleClass().add("filter-field");
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);

        DataStore ds = DataStore.getInstance();
        LocalDate today = LocalDate.now();

        YearMonth fromMonth = YearMonth.from(today).minusMonths(6);
        LocalDate fromDate = fromMonth.atDay(1);
        YearMonth toMonth = YearMonth.from(today).plusMonths(3);
        LocalDate toDate = toMonth.atEndOfMonth();

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
                String gid = t.getGroupTransactionId();
                secondId = account.getId().equals(t.getFromAccountId())
                        ? groupToId.get(gid) : groupFromId.get(gid);
            }
            String name = ds.getAccountName(secondId);
            return "—".equals(name) ? "" : name;
        });
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
            statsPanel.refresh();
        };

        search.textProperty().addListener((obs, o, n) -> applyFilter.run());
        fromPicker.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        toPicker.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        typeFilter.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        table.comparatorProperty().addListener((obs, o, n) -> applyFilter.run());
        pendingOnly.selectedProperty().addListener((obs, o, n) -> applyFilter.run());
        applyFilter.run();
        navCtx.setOnTransactionSaved(applyFilter);
        navCtx.setContextAccount(account);

        ImportOrchestrator importer = new ImportOrchestrator(account);

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
                importer.doImportRows(rows, null, applyFilter);
                e.consume();
            }
        });

        TransactionContextMenu ctxMenu = new TransactionContextMenu(account, ds);
        TableColumn<Transaction, Void> actionsCol = ctxMenu.buildActionsCol(applyFilter);
        TableColumn<Transaction, Void> srcCol = ctxMenu.buildSrcCol(applyFilter);

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
            importBtn.setOnAction(e -> importer.doImportCsv(applyFilter));
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

    private boolean isForAccount(Transaction t, Account acc) {
        String id = acc.getId();
        return id.equals(t.getFromAccountId()) || id.equals(t.getToAccountId());
    }

    // ── Export CSV ────────────────────────────────────────────────────────────

    private void exportCsv(List<Transaction> txs) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Transactions as CSV");
        fc.setInitialFileName(account.getName().replaceAll("\\s+", "_") + "_transactions.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        String savedDir = navCtx.getLastExportDir();
        if (savedDir != null) {
            File dir = new File(savedDir);
            if (dir.isDirectory()) fc.setInitialDirectory(dir);
        }
        File file = fc.showSaveDialog(null);
        if (file == null) return;
        navCtx.setLastExportDir(file.getParent());

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
