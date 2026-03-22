package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.model.Transaction.Type;
import com.sanchay.service.DataStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Modal dialog for creating or editing any transaction type.
 * A single form with a Type dropdown at the top; type-specific fields
 * swap in below the shared Date / Description / Amount rows.
 */
public class TransactionDialog extends Dialog<Transaction> {

    private final DataStore ds = DataStore.getInstance();
    private Transaction existing;

    // ── Shared fields (always visible) ───────────────────────────────────────
    private ComboBox<Type> typeCb;
    private DatePicker     sharedDate;
    private TextField      sharedDesc;
    private TextField      sharedAmt;
    private TextField      sharedNotes;

    // ── Category combos ───────────────────────────────────────────────────────
    private ComboBox<Category> expCatCb,  expSubCatCb;
    private ComboBox<Category> incCatCb;
    private ComboBox<Category> trfCatCb,  trfSubCatCb;
    private ComboBox<Category> refCatCb,  refSubCatCb;

    // ── Category master lists for autocomplete ────────────────────────────────
    private final List<Category> expCatMaster    = new ArrayList<>();
    private final List<Category> incCatMaster    = new ArrayList<>();
    private final List<Category> trfCatMaster    = new ArrayList<>();
    private final List<Category> refCatMaster    = new ArrayList<>();
    private final List<Category> expSubCatMaster = new ArrayList<>();
    private final List<Category> trfSubCatMaster = new ArrayList<>();
    private final List<Category> refSubCatMaster = new ArrayList<>();

    // ── Account combos (per type) ─────────────────────────────────────────────
    private ComboBox<Account> expAcctCb;
    private ComboBox<Account> incAcctCb;
    private ComboBox<Account> trfFromCb, trfToCb;
    private ComboBox<Account> invFromCb;
    private ComboBox<Account> ccBankCb,  ccCardCb;
    private ComboBox<Account> refAcctCb;

    // ── Other type-specific fields ────────────────────────────────────────────
    private ComboBox<String> expModeCb, refModeCb;
    private TextField        expFamilyFld, expRefFld;
    private TextField        incSrcFld,    incFamilyFld;
    private TextField        refFamilyFld, refRefFld;

    // ── Redeem-specific ───────────────────────────────────────────────────────
    private ComboBox<InvestmentAccount> rdeFromCb;
    private ComboBox<Account>           rdeToCb;
    private TextField                   rdePrincipalFld;
    private Label                       rdeGainLossLbl;
    private ComboBox<Category>          rdeCatCb, rdeSubCatCb;
    private final List<Category>        rdeCatMaster    = new ArrayList<>();
    private final List<Category>        rdeSubCatMaster = new ArrayList<>();

    // ── Investment-specific ───────────────────────────────────────────────────
    private ComboBox<InvestmentAccount> invDestCb;
    private Label     invTypeLbl;
    private TextField invSchemeFld, invUnitsFld;
    private TextField invFdRefFld,  invFdRateFld, invFdMaturityAmtFld;
    private DatePicker invFdMaturityPicker;
    private TextField invRdRefFld,  invRdRateFld;
    private DatePicker invRdMaturityPicker;
    private VBox      invDynamicBox;

    // ── Type → panel mapping ──────────────────────────────────────────────────
    private final Map<Type, Node> panels = new EnumMap<>(Type.class);
    private VBox typeSection;

    // ─────────────────────────────────────────────────────────────────────────

    public TransactionDialog() {
        setTitle("New Transaction");
        setHeaderText(null);
        getDialogPane().setPrefWidth(560);
        getDialogPane().getStyleClass().add("dialog-pane");

        // Shared fields
        sharedDate  = new DatePicker(LocalDate.now());
        sharedDesc  = new TextField();
        sharedDesc.setPromptText("e.g. Electricity Bill");
        sharedAmt   = new TextField();
        sharedAmt.setPromptText("0.00");
        sharedNotes = new TextField();
        sharedNotes.setPromptText("optional");

        // Type selector
        typeCb = new ComboBox<>();
        typeCb.getItems().addAll(
                Type.EXPENSE, Type.INCOME, Type.TRANSFER,
                Type.REFUND, Type.INVESTMENT, Type.CC_PAYMENT, Type.REDEEM);
        typeCb.setValue(Type.EXPENSE);
        typeCb.setMaxWidth(Double.MAX_VALUE);
        typeCb.setConverter(typeNameConverter());

        // Build all panels up front
        panels.put(Type.EXPENSE,    buildExpensePanel());
        panels.put(Type.INCOME,     buildIncomePanel());
        panels.put(Type.TRANSFER,   buildTransferPanel());
        panels.put(Type.REFUND,     buildRefundPanel());
        panels.put(Type.INVESTMENT, buildInvestmentPanel());
        panels.put(Type.CC_PAYMENT, buildCCPaymentPanel());
        panels.put(Type.REDEEM,     buildRedeemPanel());

        // Shared top grid: Type, Date, Description, Amount
        GridPane topGrid = form();
        topGrid.setPadding(new Insets(10, 10, 4, 10));
        int r = 0;
        row(topGrid, r++, "Type*",        typeCb);
        row(topGrid, r++, "Date*",        sharedDate);
        row(topGrid, r++, "Description*", sharedDesc);
        row(topGrid, r,   "Amount (₹)*",  sharedAmt);

        // Shared bottom grid: Notes
        GridPane botGrid = form();
        botGrid.setPadding(new Insets(4, 10, 10, 10));
        row(botGrid, 0, "Notes", sharedNotes);

        // Swappable middle section
        typeSection = new VBox();
        typeSection.getChildren().add(panels.get(Type.EXPENSE));

        typeCb.valueProperty().addListener((obs, old, type) ->
                typeSection.getChildren().setAll(panels.get(type)));

        wireAutoSuggest();

        VBox content = new VBox();
        content.getChildren().addAll(topGrid, typeSection, botGrid);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        getDialogPane().setContent(scroll);

        ButtonType saveBtn   = new ButtonType("Save",   ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(saveBtn, cancelBtn);

        setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            try { return save(); }
            catch (Exception ex) { showError("Validation Error", ex.getMessage()); return null; }
        });

        Platform.runLater(this::focusFirstEmpty);
    }

    /** Edit / re-classify constructor. */
    public TransactionDialog(Transaction existing) {
        this();
        this.existing = existing;
        setTitle("Edit Transaction");
        prefillFromTransaction(existing);
        Platform.runLater(this::focusFirstEmpty);
    }

    // ── Type name converter ───────────────────────────────────────────────────

    private StringConverter<Type> typeNameConverter() {
        return new StringConverter<>() {
            @Override public String toString(Type t) {
                if (t == null) return "";
                return switch (t) {
                    case EXPENSE    -> "Expense";
                    case INCOME     -> "Income";
                    case TRANSFER   -> "Transfer";
                    case INVESTMENT -> "Investment";
                    case CC_PAYMENT -> "CC Payment";
                    case REFUND     -> "Refund";
                    case REDEEM     -> "Redeem";
                };
            }
            @Override public Type fromString(String s) { return null; }
        };
    }

    // ── Panel builders ────────────────────────────────────────────────────────

    private Node buildExpensePanel() {
        expCatMaster.addAll(ds.getExpenseCategories());
        expCatCb    = makeCatCb(expCatMaster, "Select category");
        expSubCatCb = makeSubCatCb(expSubCatMaster);
        wireCatSubCat(expCatCb, expSubCatCb, expSubCatMaster);
        makeAutoComplete(expCatCb,    expCatMaster);
        makeAutoComplete(expSubCatCb, expSubCatMaster);

        expAcctCb  = accountCombo(false);
        expModeCb  = payModeCombo();
        expFamilyFld = tf("optional");
        expRefFld    = tf("optional");

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "Paid From*",    expAcctCb);
        row(g, r++, "Category",      expCatCb);
        row(g, r++, "Sub-category",  expSubCatCb);
        row(g, r++, "Payment Mode",  expModeCb);
        row(g, r++, "Family Member", expFamilyFld);
        row(g, r,   "Ref / UTR No",  expRefFld);
        return g;
    }

    private Node buildIncomePanel() {
        incCatMaster.addAll(ds.getIncomeCategories());
        incCatCb = makeCatCb(incCatMaster, "Select category");
        makeAutoComplete(incCatCb, incCatMaster);

        incAcctCb    = accountCombo(false);
        incSrcFld    = tf("e.g. Barclays");
        incFamilyFld = tf("optional");

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "Into Account*",  incAcctCb);
        row(g, r++, "Category",       incCatCb);
        row(g, r++, "Source",         incSrcFld);
        row(g, r,   "Family Member",  incFamilyFld);
        return g;
    }

    private Node buildTransferPanel() {
        trfCatMaster.addAll(ds.getExpenseCategories());
        trfCatCb    = makeCatCb(trfCatMaster, "Select category (optional)");
        trfSubCatCb = makeSubCatCb(trfSubCatMaster);
        wireCatSubCat(trfCatCb, trfSubCatCb, trfSubCatMaster);
        makeAutoComplete(trfCatCb,    trfCatMaster);
        makeAutoComplete(trfSubCatCb, trfSubCatMaster);

        // From: bank + investment accounts
        trfFromCb = new ComboBox<>();
        trfFromCb.setMaxWidth(Double.MAX_VALUE);
        trfFromCb.setPromptText("Select account");
        ds.getBankAccounts().forEach(trfFromCb.getItems()::add);
        ds.getInvestmentAccounts().forEach(trfFromCb.getItems()::add);
        styleAccountCombo(trfFromCb);

        // To: bank + loan accounts (bank-only when From is investment)
        trfToCb = new ComboBox<>();
        trfToCb.setMaxWidth(Double.MAX_VALUE);
        trfToCb.setPromptText("Select account");
        ds.getBankAccounts().forEach(trfToCb.getItems()::add);
        ds.getActiveLoanAccounts().forEach(trfToCb.getItems()::add);
        styleAccountCombo(trfToCb);

        trfFromCb.valueProperty().addListener((obs, old, sel) -> {
            Account prev = trfToCb.getValue();
            trfToCb.getItems().clear();
            ds.getBankAccounts().forEach(trfToCb.getItems()::add);
            if (!(sel instanceof InvestmentAccount))
                ds.getActiveLoanAccounts().forEach(trfToCb.getItems()::add);
            if (prev != null && trfToCb.getItems().contains(prev)) trfToCb.setValue(prev);
        });

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "From Account*",  trfFromCb);
        row(g, r++, "To Account*",    trfToCb);
        row(g, r++, "Category",       trfCatCb);
        row(g, r,   "Sub-category",   trfSubCatCb);
        return g;
    }

    private Node buildRefundPanel() {
        refCatMaster.addAll(ds.getExpenseCategories());
        refCatCb    = makeCatCb(refCatMaster, "Select original expense category");
        refSubCatCb = makeSubCatCb(refSubCatMaster);
        wireCatSubCat(refCatCb, refSubCatCb, refSubCatMaster);
        makeAutoComplete(refCatCb,    refCatMaster);
        makeAutoComplete(refSubCatCb, refSubCatMaster);

        refAcctCb    = accountCombo(true);  // bank + CC (where refund lands)
        refModeCb    = payModeCombo();
        refFamilyFld = tf("optional");
        refRefFld    = tf("optional");

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "Refunded Into*",  refAcctCb);
        row(g, r++, "Category",        refCatCb);
        row(g, r++, "Sub-category",    refSubCatCb);
        row(g, r++, "Payment Mode",    refModeCb);
        row(g, r++, "Family Member",   refFamilyFld);
        row(g, r,   "Ref / UTR No",    refRefFld);
        return g;
    }

    private Node buildInvestmentPanel() {
        invFromCb = accountCombo(false);
        invFromCb.getItems().removeIf(a -> !(a instanceof BankAccount));

        invDestCb = new ComboBox<>();
        invDestCb.setMaxWidth(Double.MAX_VALUE);
        invDestCb.setPromptText("Select investment account");
        ds.getInvestmentAccounts().forEach(invDestCb.getItems()::add);
        invDestCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(InvestmentAccount ia, boolean empty) {
                super.updateItem(ia, empty);
                setText(empty || ia == null ? null : ia.getName());
            }
        });
        invDestCb.setButtonCell(invDestCb.getCellFactory().call(null));

        invTypeLbl    = new Label("—");
        invDynamicBox = new VBox(8);

        invDestCb.valueProperty().addListener((obs, old, ia) ->
                refreshInvestmentDynamicFields(ia == null ? null : ia.getInvestmentType()));

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "From Account*",    invFromCb);
        row(g, r++, "To Inv. Account*", invDestCb);
        GridPane.setColumnSpan(invDynamicBox, 2);
        g.add(invDynamicBox, 0, r);
        return g;
    }

    private Node buildCCPaymentPanel() {
        ccBankCb = accountCombo(false);
        ccBankCb.getItems().removeIf(a -> !(a instanceof BankAccount));

        ccCardCb = accountCombo(true);
        ccCardCb.getItems().removeIf(a -> !(a instanceof CreditCardAccount));

        GridPane g = panelGrid();
        row(g, 0, "From Bank Acc*",  ccBankCb);
        row(g, 1, "To Credit Card*", ccCardCb);
        return g;
    }

    private Node buildRedeemPanel() {
        rdeCatMaster.addAll(ds.getIncomeCategories());
        rdeCatCb    = makeCatCb(rdeCatMaster, "Select gain category (optional)");
        rdeSubCatCb = makeSubCatCb(rdeSubCatMaster);
        wireCatSubCat(rdeCatCb, rdeSubCatCb, rdeSubCatMaster);
        makeAutoComplete(rdeCatCb,    rdeCatMaster);
        makeAutoComplete(rdeSubCatCb, rdeSubCatMaster);

        rdeFromCb = new ComboBox<>();
        rdeFromCb.setMaxWidth(Double.MAX_VALUE);
        rdeFromCb.setPromptText("Select investment account");
        ds.getInvestmentAccounts().forEach(rdeFromCb.getItems()::add);
        rdeFromCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(InvestmentAccount ia, boolean empty) {
                super.updateItem(ia, empty);
                setText(empty || ia == null ? null : ia.getName());
            }
        });
        rdeFromCb.setButtonCell(rdeFromCb.getCellFactory().call(null));

        rdeToCb = accountCombo(false); // bank accounts only

        rdePrincipalFld = tf("Original invested amount being returned");
        rdeGainLossLbl  = new Label("—");
        rdeGainLossLbl.setStyle("-fx-text-fill: #595959;");

        // Live gain/loss computation
        rdePrincipalFld.textProperty().addListener((obs, old, val) -> updateRedeemGainLoss());
        sharedAmt.textProperty().addListener((obs, old, val) -> {
            if (typeCb.getValue() == Type.REDEEM) updateRedeemGainLoss();
        });

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "From Inv. Account*", rdeFromCb);
        row(g, r++, "To Bank Account*",   rdeToCb);
        row(g, r++, "Principal (₹)*",     rdePrincipalFld);
        row(g, r++, "Gain / Loss",        rdeGainLossLbl);
        row(g, r++, "Category",           rdeCatCb);
        row(g, r,   "Sub-category",       rdeSubCatCb);
        return g;
    }

    private void updateRedeemGainLoss() {
        try {
            long total     = Math.round(Double.parseDouble(sharedAmt.getText().replace(",", "")) * 100);
            long principal = Math.round(Double.parseDouble(rdePrincipalFld.getText().replace(",", "")) * 100);
            long gainLoss  = total - principal;
            String sign  = gainLoss >= 0 ? "+" : "";
            String color = gainLoss >= 0 ? "#2E7D32" : "#C62828";
            rdeGainLossLbl.setText(sign + String.format("₹%,.2f", gainLoss / 100.0));
            rdeGainLossLbl.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        } catch (NumberFormatException e) {
            rdeGainLossLbl.setText("—");
            rdeGainLossLbl.setStyle("-fx-text-fill: #595959;");
        }
    }

    // ── Category combo factories ──────────────────────────────────────────────

    private ComboBox<Category> makeCatCb(List<Category> master, String prompt) {
        ComboBox<Category> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText(prompt);
        cb.getItems().setAll(master);
        cb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.getName());
            }
        });
        return cb;
    }

    private ComboBox<Category> makeSubCatCb(List<Category> master) {
        ComboBox<Category> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("Select sub-category (optional)");
        cb.setVisible(false);
        cb.setManaged(false);
        cb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : "  └ " + c.getName());
                setStyle(empty || c == null ? "" : "-fx-text-fill: #1A1A2E;");
            }
        });
        return cb;
    }

    private void wireCatSubCat(ComboBox<Category> catCb, ComboBox<Category> subCatCb,
                                List<Category> subMaster) {
        catCb.valueProperty().addListener((obs, old, sel) -> {
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
            subMaster.clear();
            subMaster.addAll(subCatCb.getItems());
        });
    }

    // ── Autocomplete ──────────────────────────────────────────────────────────

    private void makeAutoComplete(ComboBox<Category> combo, List<Category> masterList) {
        combo.setEditable(true);
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(Category c)   { return c == null ? "" : c.getName(); }
            @Override public Category fromString(String s) {
                if (s == null || s.isBlank()) return null;
                return masterList.stream()
                        .filter(c -> c.getName().equalsIgnoreCase(s.trim()))
                        .findFirst().orElse(null);
            }
        });
        combo.getEditor().textProperty().addListener((obs, old, text) -> {
            Category selected = combo.getValue();
            if (selected != null && selected.getName().equals(text)) {
                if (combo.getItems().size() < masterList.size())
                    combo.getItems().setAll(masterList);
                return;
            }
            String lower = text == null ? "" : text.toLowerCase();
            List<Category> filtered = lower.isEmpty()
                    ? new ArrayList<>(masterList)
                    : masterList.stream()
                            .filter(c -> c.getName().toLowerCase().contains(lower))
                            .collect(Collectors.toList());
            combo.getItems().setAll(filtered);
            if (!filtered.isEmpty() && !lower.isEmpty()) combo.show();
        });
    }

    // ── Auto-suggest (unified, routes by current type) ────────────────────────

    private void wireAutoSuggest() {
        sharedDesc.focusedProperty().addListener((obs, was, isNow) -> {
            if (isNow) return;
            String desc = sharedDesc.getText();
            if (desc == null || desc.isBlank()) return;

            Type               type      = typeCb.getValue();
            ComboBox<Category> catCb     = catCbFor(type);
            List<Category>     catMaster = catMasterFor(type);
            ComboBox<Category> subCatCb  = subCatCbFor(type);
            if (catCb == null || catMaster == null) return;

            List<Category> sorted = ds.sortCategoriesByUsage(new ArrayList<>(catMaster), desc, type);
            catMaster.clear();
            catMaster.addAll(sorted);
            catCb.getItems().setAll(catMaster);

            if (catCb.getValue() != null) return;
            ds.suggestCategoryForDescription(desc.trim(), type).ifPresent(rule ->
                    catCb.getItems().stream()
                            .filter(c -> c.getId().equals(rule.getCategoryId()))
                            .findFirst()
                            .ifPresent(cat -> {
                                catCb.setValue(cat);
                                if (subCatCb != null && rule.getSubCategoryId() != null)
                                    subCatCb.getItems().stream()
                                            .filter(s -> s.getId().equals(rule.getSubCategoryId()))
                                            .findFirst()
                                            .ifPresent(subCatCb::setValue);
                            }));
        });
    }

    private ComboBox<Category> catCbFor(Type type) {
        return switch (type) {
            case EXPENSE  -> expCatCb;
            case INCOME   -> incCatCb;
            case TRANSFER -> trfCatCb;
            case REFUND   -> refCatCb;
            case REDEEM   -> rdeCatCb;
            default       -> null;
        };
    }

    private List<Category> catMasterFor(Type type) {
        return switch (type) {
            case EXPENSE  -> expCatMaster;
            case INCOME   -> incCatMaster;
            case TRANSFER -> trfCatMaster;
            case REFUND   -> refCatMaster;
            case REDEEM   -> rdeCatMaster;
            default       -> null;
        };
    }

    private ComboBox<Category> subCatCbFor(Type type) {
        return switch (type) {
            case EXPENSE  -> expSubCatCb;
            case TRANSFER -> trfSubCatCb;
            case REFUND   -> refSubCatCb;
            case REDEEM   -> rdeSubCatCb;
            default       -> null;
        };
    }

    // ── Save routing ──────────────────────────────────────────────────────────

    private Transaction save() {
        return switch (typeCb.getValue()) {
            case EXPENSE    -> saveExpense();
            case INCOME     -> saveIncome();
            case TRANSFER   -> saveTransfer();
            case INVESTMENT -> saveInvestment();
            case CC_PAYMENT -> saveCCPayment();
            case REFUND     -> saveRefund();
            case REDEEM     -> saveRedeem();
        };
    }

    private Transaction saveExpense() {
        LocalDate date = requireDate();
        String    desc = requireText(sharedDesc, "Description");
        long      amt  = parsePaise(sharedAmt);
        Account   acct = requireAccount(expAcctCb, "Paid From account");

        Transaction t = new Transaction(Type.EXPENSE, date, desc, amt);
        t.setFromAccountId(acct.getId());
        if (expCatCb.getValue()    != null) t.setCategoryId(expCatCb.getValue().getId());
        if (expSubCatCb.getValue() != null) t.setSubCategoryId(expSubCatCb.getValue().getId());
        applyPayMode(t, expModeCb);
        t.setFamilyMember(nullIfBlank(expFamilyFld.getText()));
        t.setReferenceNumber(nullIfBlank(expRefFld.getText()));
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    private Transaction saveIncome() {
        LocalDate date = requireDate();
        String    desc = requireText(sharedDesc, "Description");
        long      amt  = parsePaise(sharedAmt);
        Account   acct = requireAccount(incAcctCb, "Into Account");

        Transaction t = new Transaction(Type.INCOME, date, desc, amt);
        t.setToAccountId(acct.getId());
        if (incCatCb.getValue() != null) t.setCategoryId(incCatCb.getValue().getId());
        t.setSource(nullIfBlank(incSrcFld.getText()));
        t.setFamilyMember(nullIfBlank(incFamilyFld.getText()));
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    private Transaction saveTransfer() {
        LocalDate date = requireDate();
        String    desc = requireText(sharedDesc, "Description");
        long      amt  = parsePaise(sharedAmt);
        Account   from = requireAccount(trfFromCb, "From Account");
        Account   to   = requireAccount(trfToCb,   "To Account");
        if (from.getId().equals(to.getId()))
            throw new IllegalArgumentException("From and To accounts must differ.");

        Transaction t = new Transaction(Type.TRANSFER, date, desc, amt);
        t.setFromAccountId(from.getId());
        t.setToAccountId(to.getId());
        if (trfCatCb.getValue()    != null) t.setCategoryId(trfCatCb.getValue().getId());
        if (trfSubCatCb.getValue() != null) t.setSubCategoryId(trfSubCatCb.getValue().getId());
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    private Transaction saveInvestment() {
        LocalDate         date = requireDate();
        String            desc = requireText(sharedDesc, "Description");
        long              amt  = parsePaise(sharedAmt);
        Account           from = requireAccount(invFromCb, "From Account");
        InvestmentAccount dest = invDestCb.getValue();
        if (dest == null) throw new IllegalArgumentException("Please select an investment account.");

        Transaction t = new Transaction(Type.INVESTMENT, date, desc, amt);
        t.setFromAccountId(from.getId());
        t.setToAccountId(dest.getId());

        String userNotes = nullIfBlank(sharedNotes.getText());
        switch (dest.getInvestmentType()) {
            case MUTUAL_FUNDS, EQUITY, DEBT_BONDS -> {
                if (invSchemeFld != null) t.setSchemeScriptName(nullIfBlank(invSchemeFld.getText()));
                if (invUnitsFld  != null && !invUnitsFld.getText().isBlank()) {
                    try { t.setUnitsNav(Double.parseDouble(invUnitsFld.getText().trim())); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException("Units/NAV must be a number."); }
                }
                t.setNotes(userNotes);
            }
            case FIXED_DEPOSIT -> {
                StringBuilder sb = new StringBuilder();
                appendNote(sb, "FD Ref",         invFdRefFld);
                appendNote(sb, "Interest Rate",   invFdRateFld);
                if (invFdMaturityPicker != null && invFdMaturityPicker.getValue() != null)
                    sb.append("Maturity Date: ").append(
                            invFdMaturityPicker.getValue().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n");
                appendNote(sb, "Maturity Amount", invFdMaturityAmtFld);
                if (userNotes != null) sb.append("Notes: ").append(userNotes);
                t.setNotes(sb.toString().stripTrailing());
            }
            case RECURRING_DEPOSIT -> {
                StringBuilder sb = new StringBuilder();
                appendNote(sb, "RD Ref",        invRdRefFld);
                appendNote(sb, "Interest Rate",  invRdRateFld);
                if (invRdMaturityPicker != null && invRdMaturityPicker.getValue() != null)
                    sb.append("Maturity Date: ").append(
                            invRdMaturityPicker.getValue().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n");
                if (userNotes != null) sb.append("Notes: ").append(userNotes);
                t.setNotes(sb.toString().stripTrailing());
            }
            default -> t.setNotes(userNotes);
        }
        return persistTransaction(t);
    }

    private Transaction saveCCPayment() {
        LocalDate date = requireDate();
        long      amt  = parsePaise(sharedAmt);
        Account   bank = requireAccount(ccBankCb, "From Bank Account");
        Account   card = requireAccount(ccCardCb, "To Credit Card");

        String desc = nullIfBlank(sharedDesc.getText());
        if (desc == null) desc = (existing != null && existing.getDescription() != null)
                ? existing.getDescription() : "CC Payment — " + card.getName();

        Transaction t = new Transaction(Type.CC_PAYMENT, date, desc, amt);
        t.setFromAccountId(bank.getId());
        t.setToAccountId(card.getId());
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    private Transaction saveRefund() {
        LocalDate date = requireDate();
        String    desc = requireText(sharedDesc, "Description");
        long      amt  = parsePaise(sharedAmt);
        Account   acct = requireAccount(refAcctCb, "Refunded Into account");

        Transaction t = new Transaction(Type.REFUND, date, desc, amt);
        t.setToAccountId(acct.getId());
        if (refCatCb.getValue()    != null) t.setCategoryId(refCatCb.getValue().getId());
        if (refSubCatCb.getValue() != null) t.setSubCategoryId(refSubCatCb.getValue().getId());
        applyPayMode(t, refModeCb);
        t.setFamilyMember(nullIfBlank(refFamilyFld.getText()));
        t.setReferenceNumber(nullIfBlank(refRefFld.getText()));
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    private Transaction saveRedeem() {
        LocalDate         date      = requireDate();
        String            desc      = requireText(sharedDesc, "Description");
        long              total     = parsePaise(sharedAmt);
        long              principal = parsePaise(rdePrincipalFld);
        InvestmentAccount from      = rdeFromCb.getValue();
        if (from == null) throw new IllegalArgumentException("From Investment Account is required.");
        Account           to        = requireAccount(rdeToCb, "To Bank Account");

        Transaction t = new Transaction(Type.REDEEM, date, desc, total);
        t.setFromAccountId(from.getId());
        t.setToAccountId(to.getId());
        t.setPrincipalPaise(principal);
        if (rdeCatCb.getValue()    != null) t.setCategoryId(rdeCatCb.getValue().getId());
        if (rdeSubCatCb.getValue() != null) t.setSubCategoryId(rdeSubCatCb.getValue().getId());
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    // ── Persist ───────────────────────────────────────────────────────────────

    private Transaction persistTransaction(Transaction t) {
        if (existing != null) {
            t.setSourceIndicator(existing.getSourceIndicator());
            t.setImportHash(existing.getImportHash());
            ds.deleteTransaction(existing.getId());
        }
        ds.addTransaction(t);
        ds.learnFromTransaction(t);
        return t;
    }

    // ── Prefill (edit mode) ───────────────────────────────────────────────────

    private void prefillFromTransaction(Transaction t) {
        // Shared fields
        if (t.getDate()        != null) sharedDate.setValue(t.getDate());
        if (t.getDescription() != null) sharedDesc.setText(t.getDescription());
        sharedAmt.setText(String.format("%.2f", Math.abs(t.getAmountPaise()) / 100.0));
        if (t.getNotes()       != null) sharedNotes.setText(t.getNotes());

        // Set type — triggers panel swap via valueProperty listener
        typeCb.setValue(t.getType());

        // Type-specific fields
        switch (t.getType()) {
            case EXPENSE -> {
                setAccount(expAcctCb, t.getFromAccountId());
                prefillCat(expCatCb, expSubCatCb, t);
                setPayMode(expModeCb, t.getPaymentMode());
                setText(expFamilyFld, t.getFamilyMember());
                setText(expRefFld,    t.getReferenceNumber());
            }
            case INCOME -> {
                String id = t.getToAccountId() != null ? t.getToAccountId() : t.getFromAccountId();
                setAccount(incAcctCb, id);
                prefillCat(incCatCb, null, t);
                setText(incSrcFld,    t.getSource());
                setText(incFamilyFld, t.getFamilyMember());
            }
            case TRANSFER -> {
                setAccount(trfFromCb, t.getFromAccountId());
                setAccount(trfToCb,   t.getToAccountId());
                prefillCat(trfCatCb, trfSubCatCb, t);
            }
            case INVESTMENT -> prefillInvestment(t);
            case CC_PAYMENT -> {
                setAccount(ccBankCb, t.getFromAccountId());
                setAccount(ccCardCb, t.getToAccountId());
            }
            case REFUND -> {
                setAccount(refAcctCb, t.getToAccountId());
                prefillCat(refCatCb, refSubCatCb, t);
                setPayMode(refModeCb, t.getPaymentMode());
                setText(refFamilyFld, t.getFamilyMember());
                setText(refRefFld,    t.getReferenceNumber());
            }
            case REDEEM -> {
                if (t.getFromAccountId() != null)
                    ds.getInvestmentAccounts().stream()
                            .filter(ia -> ia.getId().equals(t.getFromAccountId()))
                            .findFirst().ifPresent(rdeFromCb::setValue);
                setAccount(rdeToCb, t.getToAccountId());
                if (t.getPrincipalPaise() > 0)
                    rdePrincipalFld.setText(String.format("%.2f", t.getPrincipalPaise() / 100.0));
                prefillCat(rdeCatCb, rdeSubCatCb, t);
            }
        }
    }

    private void prefillCat(ComboBox<Category> catCb, ComboBox<Category> subCatCb, Transaction t) {
        if (t.getCategoryId() != null) {
            catCb.getItems().stream()
                    .filter(c -> c.getId().equals(t.getCategoryId()))
                    .findFirst().ifPresent(cat -> {
                        catCb.setValue(cat);
                        if (subCatCb != null && t.getSubCategoryId() != null)
                            subCatCb.getItems().stream()
                                    .filter(s -> s.getId().equals(t.getSubCategoryId()))
                                    .findFirst().ifPresent(subCatCb::setValue);
                    });
        } else if (t.getDescription() != null) {
            ds.suggestCategoryForDescription(t.getDescription(), t.getType())
              .ifPresent(rule -> catCb.getItems().stream()
                      .filter(c -> c.getId().equals(rule.getCategoryId()))
                      .findFirst().ifPresent(cat -> {
                          catCb.setValue(cat);
                          if (subCatCb != null && rule.getSubCategoryId() != null)
                              subCatCb.getItems().stream()
                                      .filter(s -> s.getId().equals(rule.getSubCategoryId()))
                                      .findFirst().ifPresent(subCatCb::setValue);
                      }));
        }
    }

    private void prefillInvestment(Transaction t) {
        setAccount(invFromCb, t.getFromAccountId());
        if (t.getToAccountId() == null) return;
        ds.getInvestmentAccounts().stream()
                .filter(ia -> ia.getId().equals(t.getToAccountId()))
                .findFirst().ifPresent(ia -> {
                    invDestCb.setValue(ia); // triggers refreshInvestmentDynamicFields
                    switch (ia.getInvestmentType()) {
                        case MUTUAL_FUNDS, EQUITY, DEBT_BONDS -> {
                            setText(invSchemeFld, t.getSchemeScriptName());
                            if (t.getUnitsNav() != null && invUnitsFld != null)
                                invUnitsFld.setText(t.getUnitsNav().toString());
                        }
                        case FIXED_DEPOSIT -> {
                            setText(invFdRefFld,         parseNote(t.getNotes(), "FD Ref"));
                            setText(invFdRateFld,        parseNote(t.getNotes(), "Interest Rate"));
                            setText(invFdMaturityAmtFld, parseNote(t.getNotes(), "Maturity Amount"));
                            setDateFromNote(invFdMaturityPicker, parseNote(t.getNotes(), "Maturity Date"));
                            String userNotes = parseNote(t.getNotes(), "Notes");
                            sharedNotes.setText(userNotes != null ? userNotes : "");
                        }
                        case RECURRING_DEPOSIT -> {
                            setText(invRdRefFld,  parseNote(t.getNotes(), "RD Ref"));
                            setText(invRdRateFld,  parseNote(t.getNotes(), "Interest Rate"));
                            setDateFromNote(invRdMaturityPicker, parseNote(t.getNotes(), "Maturity Date"));
                            String userNotes = parseNote(t.getNotes(), "Notes");
                            sharedNotes.setText(userNotes != null ? userNotes : "");
                        }
                        default -> {}
                    }
                });
    }

    // ── Investment dynamic fields ─────────────────────────────────────────────

    private void refreshInvestmentDynamicFields(InvestmentAccount.InvestmentType itype) {
        invDynamicBox.getChildren().clear();
        invSchemeFld = invUnitsFld = null;
        invFdRefFld  = invFdRateFld = invFdMaturityAmtFld = null;
        invFdMaturityPicker = null;
        invRdRefFld  = invRdRateFld = null;
        invRdMaturityPicker = null;
        if (itype == null) return;

        GridPane g = panelGrid();
        switch (itype) {
            case MUTUAL_FUNDS, EQUITY, DEBT_BONDS -> {
                invSchemeFld = tf("optional");
                invUnitsFld  = tf("e.g. 100.5");
                dynRow(g, 0, "Scheme / Script",   invSchemeFld);
                dynRow(g, 1, "Units / NAV",        invUnitsFld);
            }
            case FIXED_DEPOSIT -> {
                invFdRefFld         = tf("optional");
                invFdRateFld        = tf("e.g. 7.5");
                invFdMaturityPicker = new DatePicker();
                invFdMaturityAmtFld = tf("optional");
                dynRow(g, 0, "FD Reference No",    invFdRefFld);
                dynRow(g, 1, "Interest Rate (%)",   invFdRateFld);
                dynRow(g, 2, "Maturity Date",       invFdMaturityPicker);
                dynRow(g, 3, "Maturity Amount",     invFdMaturityAmtFld);
            }
            case RECURRING_DEPOSIT -> {
                invRdRefFld         = tf("optional");
                invRdRateFld        = tf("e.g. 6.5");
                invRdMaturityPicker = new DatePicker();
                dynRow(g, 0, "RD Reference No",    invRdRefFld);
                dynRow(g, 1, "Interest Rate (%)",   invRdRateFld);
                dynRow(g, 2, "Maturity Date",       invRdMaturityPicker);
            }
            default -> { /* PROVIDENT_FUND — no extra fields */ }
        }
        invDynamicBox.getChildren().add(g);
    }

    private void dynRow(GridPane g, int row, String label, Node field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        if (field instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        g.add(lbl, 0, row);
        g.add(field, 1, row);
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    /** Standard 2-column form GridPane (label 120px | field expands). */
    private GridPane form() {
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10);
        g.setPadding(new Insets(10));
        ColumnConstraints cc1 = new ColumnConstraints(120);
        ColumnConstraints cc2 = new ColumnConstraints(0, 200, Double.MAX_VALUE);
        cc2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(cc1, cc2);
        return g;
    }

    /** Panel grid with tighter top/bottom padding so panels flow into shared sections. */
    private GridPane panelGrid() {
        GridPane g = form();
        g.setPadding(new Insets(4, 10, 4, 10));
        return g;
    }

    private void row(GridPane g, int row, String label, Node field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        if (field instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        g.add(lbl, 0, row);
        g.add(field, 1, row);
    }

    private TextField tf(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        return tf;
    }

    private ComboBox<Account> accountCombo(boolean includeCreditCards) {
        ComboBox<Account> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("Select account");
        ds.getBankAccounts().forEach(cb.getItems()::add);
        if (includeCreditCards) ds.getCreditCardAccounts().forEach(cb.getItems()::add);
        styleAccountCombo(cb);
        return cb;
    }

    private void styleAccountCombo(ComboBox<Account> cb) {
        cb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Account a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? null : a.getName());
            }
        });
        cb.setButtonCell(cb.getCellFactory().call(null));
    }

    private ComboBox<String> payModeCombo() {
        ComboBox<String> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("optional");
        cb.getItems().addAll("UPI", "Net Banking", "Debit Card", "Credit Card",
                "Cash", "Cheque", "Auto-debit", "NEFT", "IMPS");
        cb.setValue("UPI");
        return cb;
    }

    // ── Value helpers ─────────────────────────────────────────────────────────

    private LocalDate requireDate() {
        LocalDate d = sharedDate.getValue();
        if (d == null) throw new IllegalArgumentException("Date is required.");
        return d;
    }

    private String requireText(TextField tf, String label) {
        String s = tf.getText();
        if (s == null || s.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return s.trim();
    }

    private Account requireAccount(ComboBox<Account> cb, String label) {
        Account a = cb.getValue();
        if (a == null) throw new IllegalArgumentException(label + " is required.");
        return a;
    }

    private long parsePaise(TextField tf) {
        String s = tf.getText() == null ? "" : tf.getText().trim().replace(",", "");
        if (s.isBlank()) throw new IllegalArgumentException("Amount is required.");
        try { return Math.round(Double.parseDouble(s) * 100); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Amount must be a number."); }
    }

    private void applyPayMode(Transaction t, ComboBox<String> cb) {
        if (cb.getValue() == null || cb.getValue().isBlank()) return;
        try { t.setPaymentMode(Transaction.PaymentMode.valueOf(cb.getValue().replace(' ', '_').toUpperCase())); }
        catch (IllegalArgumentException ignored) {}
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void setText(TextField tf, String value) {
        if (tf != null && value != null) tf.setText(value);
    }

    private void setAccount(ComboBox<Account> cb, String accountId) {
        if (accountId == null || cb == null) return;
        cb.getItems().stream()
                .filter(a -> a.getId().equals(accountId))
                .findFirst().ifPresent(cb::setValue);
    }

    private void setPayMode(ComboBox<String> cb, Transaction.PaymentMode mode) {
        if (cb == null || mode == null) return;
        String s = mode.name().replace('_', ' ');
        // match case-insensitively against items
        cb.getItems().stream()
                .filter(item -> item.equalsIgnoreCase(s))
                .findFirst().ifPresent(cb::setValue);
    }

    private void setDateFromNote(DatePicker dp, String dateStr) {
        if (dp == null || dateStr == null) return;
        try { dp.setValue(LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"))); }
        catch (Exception ignored) {}
    }

    // ── Note parsing ──────────────────────────────────────────────────────────

    private void appendNote(StringBuilder sb, String key, TextField tf) {
        if (tf != null && !tf.getText().isBlank())
            sb.append(key).append(": ").append(tf.getText().trim()).append("\n");
    }

    private String parseNote(String notes, String key) {
        if (notes == null) return null;
        for (String line : notes.split("\n"))
            if (line.startsWith(key + ": ")) return line.substring((key + ": ").length()).trim();
        return null;
    }

    // ── Error ─────────────────────────────────────────────────────────────────

    /** Moves focus to the first field in document order that has no value. */
    private void focusFirstEmpty() {
        if (sharedDesc.getText().isBlank()) { sharedDesc.requestFocus(); return; }
        if (sharedAmt.getText().isBlank())  { sharedAmt.requestFocus();  return; }
        switch (typeCb.getValue()) {
            case EXPENSE -> {
                if (expAcctCb.getValue()  == null) { expAcctCb.requestFocus();  return; }
                if (expCatCb.getValue()   == null) { expCatCb.requestFocus();   return; }
                if (expSubCatCb.getValue()== null) { expSubCatCb.requestFocus();return; }
            }
            case INCOME -> {
                if (incAcctCb.getValue()  == null) { incAcctCb.requestFocus();  return; }
                if (incCatCb.getValue()   == null) { incCatCb.requestFocus();   return; }
            }
            case TRANSFER -> {
                if (trfFromCb.getValue()  == null) { trfFromCb.requestFocus();  return; }
                if (trfToCb.getValue()    == null) { trfToCb.requestFocus();    return; }
            }
            case REFUND -> {
                if (refAcctCb.getValue()  == null) { refAcctCb.requestFocus();  return; }
                if (refCatCb.getValue()   == null) { refCatCb.requestFocus();   return; }
            }
            case CC_PAYMENT -> {
                if (ccBankCb.getValue()   == null) { ccBankCb.requestFocus();   return; }
                if (ccCardCb.getValue()   == null) { ccCardCb.requestFocus();   return; }
            }
            case INVESTMENT -> {
                if (invFromCb.getValue()  == null) { invFromCb.requestFocus();  return; }
                if (invDestCb.getValue()  == null) { invDestCb.requestFocus();  return; }
            }
            case REDEEM -> {
                if (rdeFromCb.getValue()              == null) { rdeFromCb.requestFocus();      return; }
                if (rdeToCb.getValue()                == null) { rdeToCb.requestFocus();        return; }
                if (rdePrincipalFld.getText().isBlank())       { rdePrincipalFld.requestFocus(); return; }
            }
        }
        if (sharedNotes.getText().isBlank()) sharedNotes.requestFocus();
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
