package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.model.Transaction.Type;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
    private Transaction pendingResult;

    // ── Shared fields (always visible) ───────────────────────────────────────
    private ComboBox<Type> typeCb;
    private DatePicker     sharedDate;
    private TextField      sharedDesc;
    private TextField      sharedAmt;
    private TextField      sharedNotes;

    // ── Category combos ───────────────────────────────────────────────────────
    private ComboBox<Category> expCatCb,  expSubCatCb;
    private ComboBox<Category> incCatCb,  incSubCatCb;
    private ComboBox<Category> trfCatCb,  trfSubCatCb;
    private ComboBox<Category> refCatCb,  refSubCatCb;
    private ComboBox<Category> lnCatCb,   lnSubCatCb;

    // ── Category master lists for autocomplete ────────────────────────────────
    private final List<Category> expCatMaster    = new ArrayList<>();
    private final List<Category> incCatMaster    = new ArrayList<>();
    private final List<Category> trfCatMaster    = new ArrayList<>();
    private final List<Category> refCatMaster    = new ArrayList<>();
    private final List<Category> lnCatMaster     = new ArrayList<>();
    private final List<Category> expSubCatMaster = new ArrayList<>();
    private final List<Category> incSubCatMaster = new ArrayList<>();
    private final List<Category> trfSubCatMaster = new ArrayList<>();
    private final List<Category> refSubCatMaster = new ArrayList<>();
    private final List<Category> lnSubCatMaster  = new ArrayList<>();

    // ── Account combos (per type) ─────────────────────────────────────────────
    private ComboBox<Account> expAcctCb;
    private ComboBox<Account> incAcctCb;
    private ComboBox<Account> trfFromCb, trfToCb;
    private ComboBox<Account> invFromCb;
    private ComboBox<Account> ccBankCb,  ccCardCb;
    private ComboBox<Account> refAcctCb;
    private ComboBox<Account> lnFromCb;
    private ComboBox<LoanAccount> lnToCb;

    // ── Other type-specific fields ────────────────────────────────────────────
    private ComboBox<String> expModeCb, refModeCb, lnModeCb;
    private TextField        expFamilyFld, expRefFld;
    private TextField        incSrcFld,    incFamilyFld;
    private TextField        refFamilyFld, refRefFld;
    private TextField        lnRefFld;

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
    private ComboBox<String> invRdRefCb;
    private TextField invRdRateFld;
    private DatePicker invRdMaturityPicker;
    private VBox      invDynamicBox;

    // ── Type → panel mapping ──────────────────────────────────────────────────
    private final Map<Type, Node> panels = new EnumMap<>(Type.class);
    private VBox typeSection;

    // ── Context account (edit-mode type changes) ──────────────────────────────
    private String  contextAccountId; // account in context when editing Expense/Income
    private boolean contextIsSource;  // true = "from" account (Expense), false = "to" (Income)
    private boolean rdeShowingExpenseCats = false;

    // ─────────────────────────────────────────────────────────────────────────

    public TransactionDialog() {
        setTitle("New Transaction");
        setHeaderText(null);
        getDialogPane().setPrefWidth(560);
        getDialogPane().getStyleClass().add("dialog-pane");
        UiUtils.applyStylesheet(this);
        UiUtils.setDialogHeader(this, "+", "New Transaction");

        // Shared fields
        sharedDate  = new DatePicker(LocalDate.now());
        UiUtils.styleOnShow(sharedDate);
        sharedDesc  = new TextField();
        sharedDesc.setPromptText("e.g. Electricity Bill");
        UiUtils.wireDescriptionAutocomplete(sharedDesc, ds.getDistinctTransactionDescriptions());
        sharedAmt   = new TextField();
        sharedAmt.setPromptText("0.00");
        sharedNotes = new TextField();
        sharedNotes.setPromptText("optional");

        // Type selector
        typeCb = new ComboBox<>();
        typeCb.getItems().addAll(
                Type.EXPENSE, Type.INCOME, Type.TRANSFER,
                Type.REFUND, Type.INVESTMENT, Type.CC_PAYMENT, Type.REDEEM, Type.LOAN_PAYMENT);
        typeCb.setValue(Type.EXPENSE);
        typeCb.setMaxWidth(Double.MAX_VALUE);
        typeCb.setConverter(typeNameConverter());

        // Build all panels up front
        panels.put(Type.EXPENSE,      buildExpensePanel());
        panels.put(Type.INCOME,       buildIncomePanel());
        panels.put(Type.TRANSFER,     buildTransferPanel());
        panels.put(Type.REFUND,       buildRefundPanel());
        panels.put(Type.INVESTMENT,   buildInvestmentPanel());
        panels.put(Type.CC_PAYMENT,   buildCCPaymentPanel());
        panels.put(Type.REDEEM,       buildRedeemPanel());
        panels.put(Type.LOAN_PAYMENT, buildLoanPaymentPanel());

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

        typeCb.valueProperty().addListener((obs, old, type) -> {
            typeSection.getChildren().setAll(panels.get(type));
            if (contextAccountId != null)
                applyContextAccount(type);
        });

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

        setResultConverter(bt -> bt == saveBtn ? pendingResult : null);

        Platform.runLater(() -> {
            javafx.scene.control.Button btn =
                    (javafx.scene.control.Button) getDialogPane().lookupButton(saveBtn);
            if (btn != null) btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                try { pendingResult = save(); }
                catch (Exception ex) { showError(ex.getMessage()); ev.consume(); }
            });
            focusFirstEmpty();
        });
    }

    /** Edit / re-classify constructor. */
    public TransactionDialog(Transaction existing) {
        this();
        this.existing = existing;
        setTitle("Edit Transaction");
        UiUtils.setDialogHeader(this, "✎", "Edit Transaction");
        prefillFromTransaction(existing);
        Platform.runLater(this::focusFirstEmpty);
    }

    // ── Type name converter ───────────────────────────────────────────────────

    private StringConverter<Type> typeNameConverter() {
        return new StringConverter<>() {
            @Override public String toString(Type t) {
                if (t == null) return "";
                return switch (t) {
                    case EXPENSE      -> "Expense";
                    case INCOME       -> "Income";
                    case TRANSFER     -> "Transfer";
                    case INVESTMENT   -> "Investment";
                    case CC_PAYMENT   -> "CC Payment";
                    case REFUND       -> "Refund";
                    case REDEEM       -> "Redeem";
                    case LOAN_PAYMENT -> "Loan Payment";
                    case GAIN         -> "Gain";
                    case LOSE         -> "Loss";
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
        wireCategory(expCatCb, expCatMaster, expSubCatCb, expSubCatMaster);

        expAcctCb  = accountCombo(true);  // bank + CC
        expModeCb  = payModeCombo();
        expFamilyFld = tf("optional");
        expRefFld    = tf("optional");

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "From Account*",  expAcctCb);
        row(g, r++, "Category",      expCatCb);
        row(g, r++, "Sub-category",  expSubCatCb);
        row(g, r++, "Payment Mode",  expModeCb);
        row(g, r++, "Family Member", expFamilyFld);
        row(g, r,   "Ref / UTR No",  expRefFld);
        return g;
    }

    private Node buildIncomePanel() {
        incCatMaster.addAll(ds.getIncomeCategories());
        incCatCb    = makeCatCb(incCatMaster, "Select category");
        incSubCatCb = makeSubCatCb(incSubCatMaster);
        wireCategory(incCatCb, incCatMaster, incSubCatCb, incSubCatMaster);

        incAcctCb    = accountCombo(false);
        incSrcFld    = tf("e.g. Barclays");
        incFamilyFld = tf("optional");

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "To Account*",    incAcctCb);
        row(g, r++, "Category",       incCatCb);
        row(g, r++, "Sub-category",   incSubCatCb);
        row(g, r++, "Source",         incSrcFld);
        row(g, r,   "Family Member",  incFamilyFld);
        return g;
    }

    private Node buildTransferPanel() {
        trfCatMaster.addAll(ds.getExpenseCategories());
        trfCatCb    = makeCatCb(trfCatMaster, "Select category (optional)");
        trfSubCatCb = makeSubCatCb(trfSubCatMaster);
        wireCategory(trfCatCb, trfCatMaster, trfSubCatCb, trfSubCatMaster);

        trfFromCb = accountCombo(false); // bank only
        trfToCb   = accountCombo(false); // bank only

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
        wireCategory(refCatCb, refCatMaster, refSubCatCb, refSubCatMaster);

        refAcctCb    = accountCombo(true);  // bank + CC (where refund lands)
        refModeCb    = payModeCombo();
        refFamilyFld = tf("optional");
        refRefFld    = tf("optional");

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "To Account*",     refAcctCb);
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
        row(g, r++, "To Account*",      invDestCb);
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
        row(g, 0, "From Account*", ccBankCb);
        row(g, 1, "To Account*",   ccCardCb);
        return g;
    }

    private Node buildLoanPaymentPanel() {
        lnCatMaster.addAll(ds.getExpenseCategories());
        lnCatCb    = makeCatCb(lnCatMaster, "Select category (optional)");
        lnSubCatCb = makeSubCatCb(lnSubCatMaster);
        wireCategory(lnCatCb, lnCatMaster, lnSubCatCb, lnSubCatMaster);

        lnFromCb = accountCombo(true); // bank + CC

        lnToCb = new ComboBox<>();
        lnToCb.setMaxWidth(Double.MAX_VALUE);
        lnToCb.setPromptText("Select loan account");
        ds.getActiveLoanAccounts().forEach(lnToCb.getItems()::add);
        lnToCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(LoanAccount la, boolean empty) {
                super.updateItem(la, empty);
                setText(empty || la == null ? null : la.getName());
            }
        });
        lnToCb.setButtonCell(lnToCb.getCellFactory().call(null));

        lnModeCb = payModeCombo();
        lnRefFld = tf("optional");

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "From Account*", lnFromCb);
        row(g, r++, "To Account*",   lnToCb);
        row(g, r++, "Category",       lnCatCb);
        row(g, r++, "Sub-category",   lnSubCatCb);
        row(g, r++, "Payment Mode",   lnModeCb);
        row(g, r,   "Ref / UTR No",   lnRefFld);
        return g;
    }

    private Node buildRedeemPanel() {
        rdeCatMaster.addAll(ds.getIncomeCategories());
        rdeCatCb    = makeCatCb(rdeCatMaster, "Select gain category (optional)");
        rdeSubCatCb = makeSubCatCb(rdeSubCatMaster);
        wireCategory(rdeCatCb, rdeCatMaster, rdeSubCatCb, rdeSubCatMaster);

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
        rdeGainLossLbl.getStyleClass().add("text-hint");

        // Live gain/loss computation
        rdePrincipalFld.textProperty().addListener((obs, old, val) -> updateRedeemGainLoss());
        sharedAmt.textProperty().addListener((obs, old, val) -> {
            if (typeCb.getValue() == Type.REDEEM) updateRedeemGainLoss();
        });

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "From Account*", rdeFromCb);
        row(g, r++, "To Account*",   rdeToCb);
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
            // Inline required: runtime gain/loss colour is data-driven
            String color = gainLoss >= 0 ? "-color-success-dark" : "-color-error";
            rdeGainLossLbl.setText(sign + String.format("₹%,.2f", gainLoss / 100.0));
            rdeGainLossLbl.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
            switchRedeemCatList(gainLoss < 0);
        } catch (NumberFormatException e) {
            rdeGainLossLbl.setText("—");
            rdeGainLossLbl.setStyle(""); // clears inline; text-hint class applies
        }
    }

    private void switchRedeemCatList(boolean toLoss) {
        if (toLoss == rdeShowingExpenseCats) return;
        rdeShowingExpenseCats = toLoss;
        rdeCatMaster.clear();
        rdeCatMaster.addAll(toLoss ? ds.getExpenseCategories() : ds.getIncomeCategories());
        rdeCatCb.setValue(null);
        rdeCatCb.getEditor().clear();
        rdeCatCb.getItems().setAll(rdeCatMaster);
        rdeCatCb.setPromptText(toLoss ? "Select loss category (optional)" : "Select gain category (optional)");
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

    private void wireCategory(ComboBox<Category> catCb, List<Category> catMaster,
                              ComboBox<Category> subCatCb, List<Category> subMaster) {
        wireCatSubCat(catCb, subCatCb, subMaster);
        UiUtils.wireAutoComplete(catCb,    catMaster);
        UiUtils.wireAutoComplete(subCatCb, subMaster);
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
            case EXPENSE      -> expCatCb;
            case INCOME       -> incCatCb;
            case TRANSFER     -> trfCatCb;
            case REFUND       -> refCatCb;
            case REDEEM       -> rdeCatCb;
            case LOAN_PAYMENT -> lnCatCb;
            default           -> null;
        };
    }

    private List<Category> catMasterFor(Type type) {
        return switch (type) {
            case EXPENSE      -> expCatMaster;
            case INCOME       -> incCatMaster;
            case TRANSFER     -> trfCatMaster;
            case REFUND       -> refCatMaster;
            case REDEEM       -> rdeCatMaster;
            case LOAN_PAYMENT -> lnCatMaster;
            default           -> null;
        };
    }

    private ComboBox<Category> subCatCbFor(Type type) {
        return switch (type) {
            case EXPENSE      -> expSubCatCb;
            case INCOME       -> incSubCatCb;
            case TRANSFER     -> trfSubCatCb;
            case REFUND       -> refSubCatCb;
            case REDEEM       -> rdeSubCatCb;
            case LOAN_PAYMENT -> lnSubCatCb;
            default           -> null;
        };
    }

    // ── Save routing ──────────────────────────────────────────────────────────

    private Transaction save() {
        return switch (typeCb.getValue()) {
            case EXPENSE      -> saveExpense();
            case INCOME       -> saveIncome();
            case TRANSFER     -> saveTransfer();
            case INVESTMENT   -> saveInvestment();
            case CC_PAYMENT   -> saveCCPayment();
            case REFUND       -> saveRefund();
            case REDEEM       -> saveRedeem();
            case LOAN_PAYMENT -> saveLoanPayment();
            case GAIN, LOSE   -> throw new IllegalStateException("GAIN/LOSE cannot be saved directly");
        };
    }

    private Transaction saveExpense() {
        LocalDate date = requireDate();
        String    desc = requireText(sharedDesc, "Description");
        long      amt  = parsePaise(sharedAmt);
        Account   acct = requireAccount(expAcctCb, "From Account");

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
        Account   acct = requireAccount(incAcctCb, "To Account");

        Transaction t = new Transaction(Type.INCOME, date, desc, amt);
        t.setToAccountId(acct.getId());
        if (incCatCb.getValue()    != null) t.setCategoryId(incCatCb.getValue().getId());
        if (incSubCatCb.getValue() != null) t.setSubCategoryId(incSubCatCb.getValue().getId());
        t.setSource(nullIfBlank(incSrcFld.getText()));
        t.setFamilyMember(nullIfBlank(incFamilyFld.getText()));
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    private Transaction saveLoanPayment() {
        LocalDate   date = requireDate();
        String      desc = requireText(sharedDesc, "Description");
        long        amt  = parsePaise(sharedAmt);
        Account     from = requireAccount(lnFromCb, "From Account");
        LoanAccount to   = lnToCb.getValue();
        if (to == null) throw new IllegalArgumentException("Please select a loan account.");

        Transaction t = new Transaction(Type.LOAN_PAYMENT, date, desc, amt);
        t.setFromAccountId(from.getId());
        t.setToAccountId(to.getId());
        if (lnCatCb.getValue()    != null) t.setCategoryId(lnCatCb.getValue().getId());
        if (lnSubCatCb.getValue() != null) t.setSubCategoryId(lnSubCatCb.getValue().getId());
        applyPayMode(t, lnModeCb);
        t.setReferenceNumber(nullIfBlank(lnRefFld.getText()));
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
                List<String> rdRefs = ds.getRdRefsForAccount(dest.getId());
                if (rdRefs.isEmpty())
                    throw new IllegalArgumentException(
                            "No RD schedules found for this account. Please create a recurring schedule first.");
                String rdRef = invRdRefCb != null ? invRdRefCb.getValue() : null;
                if (rdRef == null || rdRef.isBlank())
                    throw new IllegalArgumentException("Please select an RD reference number.");
                StringBuilder sb = new StringBuilder();
                appendNote(sb, "RD Ref", rdRef);
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
        Account   acct = requireAccount(refAcctCb, "To Account");

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
        //if (principal > total)
        //    throw new IllegalArgumentException("Principal cannot exceed total redemption amount.");

        String  notes    = nullIfBlank(sharedNotes.getText());
        String  groupId  = UUID.randomUUID().toString();
        long    gainLoss = total - principal;

        // 1. Investment-side: outgoing REDEEM from investment account (amount = principal)
        Transaction invTxn = new Transaction(Type.REDEEM, date, desc, principal);
        invTxn.setFromAccountId(from.getId());
        invTxn.setPrincipalPaise(principal);
        invTxn.setGroupTransactionId(groupId);
        invTxn.setNotes(notes);
        invTxn.setSourceIndicator(Transaction.SourceIndicator.MANUAL);

        // 2. Bank-side: incoming REDEEM (principal returned to bank).
        // If we're editing an imported transaction, carry its hash over so it stays reconciled.
        boolean existingWasImported = existing != null && existing.getImportHash() != null;
        Transaction bankPrincipal = new Transaction(Type.REDEEM, date, desc, principal);
        bankPrincipal.setToAccountId(to.getId());
        bankPrincipal.setPrincipalPaise(principal);
        bankPrincipal.setGroupTransactionId(groupId);
        bankPrincipal.setNotes(notes);
        if (existingWasImported) {
            bankPrincipal.setImportHash(existing.getImportHash());
            bankPrincipal.setSourceIndicator(Transaction.SourceIndicator.RECONCILED);
        } else {
            bankPrincipal.setSourceIndicator(Transaction.SourceIndicator.MANUAL);
        }

        // 3. Bank-side: GAIN or LOSE (only if gain/loss is non-zero)
        Transaction gainLossTxn = null;
        if (gainLoss > 0) {
            gainLossTxn = new Transaction(Type.GAIN, date, desc, gainLoss);
            gainLossTxn.setToAccountId(to.getId());
        } else if (gainLoss < 0) {
            gainLossTxn = new Transaction(Type.LOSE, date, desc, -gainLoss);
            gainLossTxn.setFromAccountId(to.getId());
        }
        if (gainLossTxn != null) {
            gainLossTxn.setGroupTransactionId(groupId);
            gainLossTxn.setNotes(notes);
            if (existingWasImported) {
                gainLossTxn.setImportHash(existing.getImportHash());
                gainLossTxn.setSourceIndicator(Transaction.SourceIndicator.RECONCILED);
            } else {
                gainLossTxn.setSourceIndicator(Transaction.SourceIndicator.MANUAL);
            }
            if (rdeCatCb.getValue()    != null) gainLossTxn.setCategoryId(rdeCatCb.getValue().getId());
            if (rdeSubCatCb.getValue() != null) gainLossTxn.setSubCategoryId(rdeSubCatCb.getValue().getId());
        }

        // If editing, delete the old group or old single REDEEM (no save yet)
        if (existing != null) {
            String oldGroup = existing.getGroupTransactionId();
            if (oldGroup != null) {
                ds.deleteTransactionGroupInternal(oldGroup);
            } else {
                ds.deleteTransactionByIdInternal(existing.getId());
            }
        }

        // Persist all new transactions atomically
        ds.addTransactionInternal(invTxn);
        ds.addTransactionInternal(bankPrincipal);
        if (gainLossTxn != null) ds.addTransactionInternal(gainLossTxn);
        ds.saveTransactionsNow();

        if (gainLossTxn != null) ds.learnFromTransaction(gainLossTxn);
        return invTxn; // representative for table selection restore
    }

    // ── Persist ───────────────────────────────────────────────────────────────

    private Transaction persistTransaction(Transaction t) {
        if (existing != null) {
            // Editing an imported/auto-categorized transaction means the user has
            // reviewed it — upgrade to RECONCILED (green border) to mark it as confirmed.
            // MANUAL stays MANUAL; RECONCILED stays RECONCILED.
            Transaction.SourceIndicator indicator = existing.getSourceIndicator();
            if (indicator == Transaction.SourceIndicator.AUTO_CATEGORIZED
                    || indicator == Transaction.SourceIndicator.IMPORTED)
                indicator = Transaction.SourceIndicator.RECONCILED;
            t.setSourceIndicator(indicator);
            t.setImportHash(existing.getImportHash());
            ds.updateTransactionInPlace(existing.getId(), t);
        } else {
            ds.addTransaction(t);
        }
        // Learn a type rule when the user reclassifies an imported EXPENSE/INCOME
        // to a more specific type (e.g. CC_PAYMENT, LOAN_PAYMENT, TRANSFER).
        if (existing != null
                && (existing.getType() == Transaction.Type.EXPENSE
                    || existing.getType() == Transaction.Type.INCOME)
                && (existing.getSourceIndicator() == Transaction.SourceIndicator.IMPORTED
                    || existing.getSourceIndicator() == Transaction.SourceIndicator.AUTO_CATEGORIZED)
                && t.getType() != existing.getType()) {
            ds.learnTypeRule(existing.getType(), t);
        }
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

        // GAIN/LOSE are internal types — display them as REDEEM for editing
        Type displayType = (t.getType() == Type.GAIN || t.getType() == Type.LOSE)
                ? Type.REDEEM : t.getType();
        typeCb.setValue(displayType);

        // Type-specific fields
        switch (t.getType()) {
            case EXPENSE -> {
                contextAccountId = t.getFromAccountId();
                contextIsSource  = true;
                setAccount(expAcctCb, t.getFromAccountId());
                prefillCat(expCatCb, expSubCatCb, t);
                setPayMode(expModeCb, t.getPaymentMode());
                setText(expFamilyFld, t.getFamilyMember());
                setText(expRefFld,    t.getReferenceNumber());
            }
            case INCOME -> {
                String id = t.getToAccountId() != null ? t.getToAccountId() : t.getFromAccountId();
                contextAccountId = id;
                contextIsSource  = false;
                setAccount(incAcctCb, id);
                prefillCat(incCatCb, incSubCatCb, t);
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
            case REDEEM, GAIN, LOSE -> prefillRedeemForm(t);
            case LOAN_PAYMENT -> {
                setAccount(lnFromCb, t.getFromAccountId());
                if (t.getToAccountId() != null)
                    ds.getActiveLoanAccounts().stream()
                            .filter(la -> la.getId().equals(t.getToAccountId()))
                            .findFirst().ifPresent(lnToCb::setValue);
                prefillCat(lnCatCb, lnSubCatCb, t);
                setPayMode(lnModeCb, t.getPaymentMode());
                setText(lnRefFld, t.getReferenceNumber());
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
                            if (invRdRefCb != null) {
                                String rdRef = parseNote(t.getNotes(), "RD Ref");
                                if (rdRef != null) invRdRefCb.setValue(rdRef);
                                invRdRefCb.setDisable(true);
                                invRdRefCb.getStyleClass().add("combo-locked");
                            }
                            String userNotes = parseNote(t.getNotes(), "Notes");
                            sharedNotes.setText(userNotes != null ? userNotes : "");
                        }
                        default -> {}
                    }
                });
    }

    /**
     * Fills the Redeem panel fields. Handles both the old single-transaction format and
     * the new grouped format (three linked transactions sharing a groupTransactionId).
     * Also called when editing a GAIN or LOSE transaction (which belong to a REDEEM group).
     */
    private void prefillRedeemForm(Transaction t) {
        if (t.getGroupTransactionId() != null) {
            // New grouped format: load all siblings and reconstruct the form
            List<Transaction> group = ds.getTransactions().stream()
                    .filter(tx -> t.getGroupTransactionId().equals(tx.getGroupTransactionId()))
                    .collect(Collectors.toList());

            Transaction invTxn = group.stream()
                    .filter(tx -> tx.getType() == Type.REDEEM && tx.getFromAccountId() != null)
                    .findFirst().orElse(null);
            Transaction bankTxn = group.stream()
                    .filter(tx -> tx.getType() == Type.REDEEM
                               && tx.getToAccountId() != null && tx.getFromAccountId() == null)
                    .findFirst().orElse(null);
            Transaction gainLossTxn = group.stream()
                    .filter(tx -> tx.getType() == Type.GAIN || tx.getType() == Type.LOSE)
                    .findFirst().orElse(null);

            // Investment account
            if (invTxn != null && invTxn.getFromAccountId() != null)
                ds.getInvestmentAccounts().stream()
                        .filter(ia -> ia.getId().equals(invTxn.getFromAccountId()))
                        .findFirst().ifPresent(rdeFromCb::setValue);

            // Bank account (from the bank-side REDEEM or the GAIN/LOSE transaction)
            String bankId = bankTxn != null ? bankTxn.getToAccountId()
                    : gainLossTxn != null && gainLossTxn.getType() == Type.GAIN
                            ? gainLossTxn.getToAccountId()
                    : gainLossTxn != null ? gainLossTxn.getFromAccountId()
                    : null;
            if (bankId != null) setAccount(rdeToCb, bankId);

            // Principal and total
            long principal = invTxn != null ? invTxn.getAmountPaise() : 0;
            if (principal > 0)
                rdePrincipalFld.setText(String.format("%.2f", principal / 100.0));
            long total = principal + (gainLossTxn == null ? 0
                    : gainLossTxn.getType() == Type.GAIN
                            ?  gainLossTxn.getAmountPaise()
                            : -gainLossTxn.getAmountPaise());
            sharedAmt.setText(String.format("%.2f", total / 100.0));

            // Category from the GAIN/LOSE transaction — switch list to expense if LOSE
            if (gainLossTxn != null) {
                switchRedeemCatList(gainLossTxn.getType() == Type.LOSE);
                prefillCat(rdeCatCb, rdeSubCatCb, gainLossTxn);
            }

        } else {
            // Old single-transaction format (backward compatibility)
            if (t.getFromAccountId() != null)
                ds.getInvestmentAccounts().stream()
                        .filter(ia -> ia.getId().equals(t.getFromAccountId()))
                        .findFirst().ifPresent(rdeFromCb::setValue);
            setAccount(rdeToCb, t.getToAccountId());
            if (t.getPrincipalPaise() > 0)
                rdePrincipalFld.setText(String.format("%.2f", t.getPrincipalPaise() / 100.0));
            // sharedAmt already set from t.getAmountPaise() = total for old format
            prefillCat(rdeCatCb, rdeSubCatCb, t);
        }
    }

    // ── Context account pre-population on type change ─────────────────────────

    private void applyContextAccount(Type newType) {
        Account acct = ds.getAccounts().stream()
                .filter(a -> a.getId().equals(contextAccountId))
                .findFirst().orElse(null);
        if (acct == null) return;
        switch (newType) {
            case EXPENSE -> {
                // Bank and CC are both valid expense accounts
                if (acct instanceof BankAccount || acct instanceof CreditCardAccount)
                    setAccount(expAcctCb, contextAccountId);
            }
            case INCOME -> {
                // Income lands in a bank account
                if (acct instanceof BankAccount)
                    setAccount(incAcctCb, contextAccountId);
            }
            case TRANSFER -> {
                // Bank → bank; use contextIsSource to decide direction
                if (acct instanceof BankAccount) {
                    if (contextIsSource) setAccount(trfFromCb, contextAccountId);
                    else                 setAccount(trfToCb,   contextAccountId);
                }
            }
            case INVESTMENT -> {
                // Bank funds the investment; investment account is the destination
                if (acct instanceof BankAccount)
                    setAccount(invFromCb, contextAccountId);
                else if (acct instanceof InvestmentAccount)
                    invDestCb.getItems().stream()
                            .filter(a -> a.getId().equals(contextAccountId))
                            .findFirst().ifPresent(invDestCb::setValue);
            }
            case CC_PAYMENT -> {
                // Bank pays the CC; CC is the destination
                if (acct instanceof BankAccount)
                    setAccount(ccBankCb, contextAccountId);
                else if (acct instanceof CreditCardAccount)
                    setAccount(ccCardCb, contextAccountId);
            }
            case REFUND -> {
                // Refund returns to the account that was originally charged (bank or CC)
                if (acct instanceof BankAccount || acct instanceof CreditCardAccount)
                    setAccount(refAcctCb, contextAccountId);
            }
            case REDEEM -> {
                // Investment is redeemed from; bank receives the proceeds
                if (acct instanceof InvestmentAccount)
                    rdeFromCb.getItems().stream()
                            .filter(a -> a.getId().equals(contextAccountId))
                            .findFirst().ifPresent(rdeFromCb::setValue);
                else if (acct instanceof BankAccount)
                    setAccount(rdeToCb, contextAccountId);
            }
            case LOAN_PAYMENT -> {
                // Payment comes from bank or CC; loan account is the destination
                if (acct instanceof LoanAccount)
                    lnToCb.getItems().stream()
                            .filter(a -> a.getId().equals(contextAccountId))
                            .findFirst().ifPresent(lnToCb::setValue);
                else if (acct instanceof BankAccount || acct instanceof CreditCardAccount)
                    setAccount(lnFromCb, contextAccountId);
            }
            default -> {}
        }
    }

    /**
     * Pre-populate account fields when a new transaction is opened from within
     * an account's transaction view.  Sets the context account so that
     * subsequent type-changes continue to track the correct account.
     */
    public void setContextAccount(Account acc) {
        if (acc == null) return;
        contextAccountId = acc.getId();

        if (acc instanceof LoanAccount) {
            contextIsSource = false;
            typeCb.setValue(Type.LOAN_PAYMENT);
            lnToCb.getItems().stream()
                    .filter(a -> a.getId().equals(acc.getId()))
                    .findFirst().ifPresent(lnToCb::setValue);
        } else if (acc instanceof InvestmentAccount) {
            contextIsSource = false;
            typeCb.setValue(Type.INVESTMENT);
            invDestCb.getItems().stream()
                    .filter(a -> a.getId().equals(acc.getId()))
                    .findFirst().ifPresent(invDestCb::setValue);
        } else if (acc instanceof CreditCardAccount) {
            // CC is the "to" side for CC_PAYMENT; for EXPENSE it is also the charge account
            contextIsSource = false;
            setAccount(expAcctCb, acc.getId());
        } else {
            // BankAccount — source/from for most transaction types
            contextIsSource = true;
            setAccount(expAcctCb, acc.getId());
        }
    }

    // ── Investment dynamic fields ─────────────────────────────────────────────

    private void refreshInvestmentDynamicFields(InvestmentAccount.InvestmentType itype) {
        invDynamicBox.getChildren().clear();
        invSchemeFld = invUnitsFld = null;
        invFdRefFld  = invFdRateFld = invFdMaturityAmtFld = null;
        invFdMaturityPicker = null;
        invRdRefCb  = null;
        invRdRateFld = null;
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
                UiUtils.styleOnShow(invFdMaturityPicker);
                invFdMaturityAmtFld = tf("optional");
                dynRow(g, 0, "FD Reference No",    invFdRefFld);
                dynRow(g, 1, "Interest Rate (%)",   invFdRateFld);
                dynRow(g, 2, "Maturity Date",       invFdMaturityPicker);
                dynRow(g, 3, "Maturity Amount",     invFdMaturityAmtFld);
            }
            case RECURRING_DEPOSIT -> {
                InvestmentAccount rdAcc = invDestCb.getValue();
                List<String> rdRefs = rdAcc != null
                        ? ds.getRdRefsForAccount(rdAcc.getId())
                        : java.util.Collections.emptyList();
                if (rdRefs.isEmpty()) {
                    Label err = new Label("No RD schedules found for this account.\nCreate a recurring schedule first.");
                    err.getStyleClass().add("text-error");
                    err.setWrapText(true);
                    g.add(err, 1, 0);
                } else {
                    invRdRefCb = new ComboBox<>();
                    invRdRefCb.getItems().addAll(rdRefs);
                    invRdRefCb.setEditable(false);
                    invRdRefCb.setMaxWidth(Double.MAX_VALUE);
                    invRdRefCb.setPromptText("Select RD reference");
                    dynRow(g, 0, "RD Reference No*", invRdRefCb);
                }
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

    private void appendNote(StringBuilder sb, String key, String val) {
        if (val != null && !val.isBlank())
            sb.append(key).append(": ").append(val.trim()).append("\n");
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

    private void showError(String msg) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Validation Error");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(380);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "⚠", "Validation Error");

        VBox body = new VBox(10);
        body.setPadding(new Insets(16));

        HBox iconRow = new HBox(14);
        iconRow.setAlignment(Pos.CENTER_LEFT);
        Label iconLbl = new Label("⚠");
        iconLbl.getStyleClass().addAll("dialog-icon-box-lg", "dialog-icon-box-lg--error");
        Label msgLbl = new Label(msg);
        msgLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: -brand-dark;");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(280);
        iconRow.getChildren().addAll(iconLbl, msgLbl);
        body.getChildren().add(iconRow);

        dlg.getDialogPane().setContent(body);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dlg.showAndWait();
    }
}
