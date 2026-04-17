package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import com.sanchay.ui.common.AccountCombos;
import com.sanchay.ui.common.CategoryComboWiring;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Type-specific panel for REDEEM transactions (and editing GAIN/LOSE). */
class RedeemPanel implements TransactionPanel {

    private final TransactionDialog parent;

    final ComboBox<Category>          catCb;
    final ComboBox<Category>          subCatCb;
    final List<Category>              catMaster    = new ArrayList<>();
    final List<Category>              subCatMaster = new ArrayList<>();
    final ComboBox<InvestmentAccount> fromCb;
    final ComboBox<Account>           toCb;
    final ComboBox<String>            fdRefCb;
    final Label                       fdRefRowLbl;
    final TextField                   principalFld;
    final Label                       gainLossLbl;

    private boolean showingExpenseCats = false;
    private final Node node;

    RedeemPanel(TransactionDialog parent) {
        this.parent = parent;

        catMaster.addAll(parent.ds.getIncomeCategories());
        catCb    = CategoryComboWiring.catCombo(catMaster, "Select gain category (optional)");
        parent.setNodeId(catCb, "txn-redeem-category-combo");
        subCatCb = CategoryComboWiring.subCatCombo();
        parent.setNodeId(subCatCb, "txn-redeem-subcategory-combo");
        CategoryComboWiring.wire(catCb, subCatCb, subCatMaster, parent.ds);
        UiUtils.wireAutoComplete(catCb,    catMaster);
        UiUtils.wireAutoComplete(subCatCb, subCatMaster);

        fromCb = new ComboBox<>();
        fromCb.setId("txn-redeem-from-account-combo");
        fromCb.setMaxWidth(Double.MAX_VALUE);
        fromCb.setPromptText("Select investment account");
        parent.ds.getInvestmentAccounts().forEach(fromCb.getItems()::add);
        AccountCombos.style(fromCb);

        toCb = AccountCombos.bankCombo(parent.ds);
        parent.setNodeId(toCb, "txn-redeem-to-account-combo");

        fdRefRowLbl = new Label("Reference No.");
        fdRefRowLbl.setId("txn-redeem-fd-reference-label");
        fdRefRowLbl.getStyleClass().add("form-label");
        fdRefCb = new ComboBox<>();
        fdRefCb.setId("txn-redeem-fd-reference-combo");
        fdRefCb.setMaxWidth(Double.MAX_VALUE);
        fdRefCb.setPromptText("Select FD reference");
        fdRefRowLbl.setVisible(false);
        fdRefRowLbl.setManaged(false);
        fdRefCb.setVisible(false);
        fdRefCb.setManaged(false);
        fromCb.valueProperty().addListener((obs, old, ia) -> refreshFdRefRow(ia));

        principalFld = parent.tf("Original invested amount being returned");
        parent.setNodeId(principalFld, "txn-redeem-principal-field");
        gainLossLbl  = new Label("—");
        gainLossLbl.setId("txn-redeem-gain-loss-label");
        gainLossLbl.getStyleClass().add("text-hint");

        principalFld.textProperty().addListener((obs, old, val) -> updateGainLoss());
        parent.sharedAmt.textProperty().addListener((obs, old, val) -> {
            if (parent.typeCb.getValue() == Transaction.Type.REDEEM) updateGainLoss();
        });

        node = buildNode();
    }

    @Override public Node getNode() { return node; }

    private Node buildNode() {
        GridPane g = parent.panelGrid();
        g.setId("txn-redeem-panel");
        parent.row(g, 0, "From Account*",  fromCb);
        parent.row(g, 1, "To Account*",    toCb);
        // Row 2: FD reference — hidden by default, shown for Fixed Deposit accounts
        g.add(fdRefRowLbl, 0, 2);
        g.add(fdRefCb,     1, 2);
        parent.row(g, 3, "Principal (₹)*", principalFld);
        parent.row(g, 4, "Gain / Loss",    gainLossLbl);
        parent.row(g, 5, "Category",       catCb);
        parent.row(g, 6, "Sub-category",   subCatCb);
        return g;
    }

    @Override public Transaction save() {
        LocalDate         date      = parent.requireDate();
        String            desc      = parent.requireText(parent.sharedDesc, "Description");
        long              total     = parent.parsePaise(parent.sharedAmt);
        long              principal = parent.parsePaise(principalFld);
        InvestmentAccount from      = fromCb.getValue();
        if (from == null) throw new IllegalArgumentException("From Investment Account is required.");
        Account           to        = parent.requireAccount(toCb, "To Bank Account");

        String  notes    = parent.nullIfBlank(parent.sharedNotes.getText());
        String  fdRef    = fdRefCb.isVisible() ? fdRefCb.getValue() : null;
        String  groupId  = UUID.randomUUID().toString();
        long    gainLoss = total - principal;

        // 1. Investment-side: outgoing REDEEM from investment account (amount = principal)
        Transaction invTxn = new Transaction(Transaction.Type.REDEEM, date, desc, principal);
        invTxn.setFromAccountId(from.getId());
        Transaction.RedeemDetails invRd = new Transaction.RedeemDetails();
        invRd.setPrincipalPaise(principal);
        invRd.setOrgnlFDRef(fdRef);
        invTxn.setRedeemDetails(invRd);
        invTxn.setGroupTransactionId(groupId);
        invTxn.setNotes(notes);
        invTxn.setSourceIndicator(Transaction.SourceIndicator.MANUAL);

        // 2. Bank-side: incoming REDEEM (principal returned to bank)
        boolean existingWasImported = parent.existing != null && parent.existing.getImportHash() != null;
        Transaction bankPrincipal = new Transaction(Transaction.Type.REDEEM, date, desc, principal);
        bankPrincipal.setToAccountId(to.getId());
        Transaction.RedeemDetails bankRd = new Transaction.RedeemDetails();
        bankRd.setPrincipalPaise(principal);
        bankRd.setOrgnlFDRef(fdRef);
        bankPrincipal.setRedeemDetails(bankRd);
        bankPrincipal.setGroupTransactionId(groupId);
        bankPrincipal.setNotes(notes);
        if (existingWasImported) {
            bankPrincipal.setImportHash(parent.existing.getImportHash());
            bankPrincipal.setSourceIndicator(Transaction.SourceIndicator.RECONCILED);
        } else {
            bankPrincipal.setSourceIndicator(Transaction.SourceIndicator.MANUAL);
        }

        // 3. Bank-side: GAIN or LOSE (only if gain/loss is non-zero)
        Transaction gainLossTxn = null;
        if (gainLoss > 0) {
            gainLossTxn = new Transaction(Transaction.Type.GAIN, date, desc, gainLoss);
            gainLossTxn.setToAccountId(to.getId());
        } else if (gainLoss < 0) {
            gainLossTxn = new Transaction(Transaction.Type.LOSE, date, desc, -gainLoss);
            gainLossTxn.setFromAccountId(to.getId());
        }
        if (gainLossTxn != null) {
            if (fdRef != null) {
                Transaction.RedeemDetails glRd = new Transaction.RedeemDetails();
                glRd.setOrgnlFDRef(fdRef);
                gainLossTxn.setRedeemDetails(glRd);
            }
            gainLossTxn.setGroupTransactionId(groupId);
            gainLossTxn.setNotes(notes);
            if (existingWasImported) {
                gainLossTxn.setImportHash(parent.existing.getImportHash());
                gainLossTxn.setSourceIndicator(Transaction.SourceIndicator.RECONCILED);
            } else {
                gainLossTxn.setSourceIndicator(Transaction.SourceIndicator.MANUAL);
            }
            if (catCb.getValue() != null || subCatCb.getValue() != null) {
                Transaction.Classification gainCl = new Transaction.Classification();
                if (catCb.getValue()    != null) gainCl.setCategoryId(catCb.getValue().getId());
                if (subCatCb.getValue() != null) gainCl.setSubCategoryId(subCatCb.getValue().getId());
                gainLossTxn.setClassification(gainCl);
            }
        }

        // If editing, delete the old group or old single REDEEM
        if (parent.existing != null) {
            String oldGroup = parent.existing.getGroupTransactionId();
            if (oldGroup != null) {
                parent.ds.deleteTransactionGroupInternal(oldGroup);
            } else {
                parent.ds.deleteTransactionByIdInternal(parent.existing.getId());
            }
        }

        // Persist all new transactions atomically
        parent.ds.addTransactionInternal(invTxn);
        parent.ds.addTransactionInternal(bankPrincipal);
        if (gainLossTxn != null) parent.ds.addTransactionInternal(gainLossTxn);
        parent.ds.saveTransactionsNow();

        if (gainLossTxn != null) parent.ds.learnFromTransaction(gainLossTxn);
        return invTxn; // representative for table selection restore
    }

    /**
     * Fills the Redeem panel fields. Handles both the old single-transaction format and
     * the new grouped format (three linked transactions sharing a groupTransactionId).
     * Also called when editing a GAIN or LOSE transaction (which belong to a REDEEM group).
     */
    @Override public void prefill(Transaction t) {
        if (t.getGroupTransactionId() != null) {
            List<Transaction> group = parent.ds.getTransactions().stream()
                    .filter(tx -> t.getGroupTransactionId().equals(tx.getGroupTransactionId()))
                    .collect(Collectors.toList());

            Transaction invTxn = group.stream()
                    .filter(tx -> tx.getType() == Transaction.Type.REDEEM && tx.getFromAccountId() != null)
                    .findFirst().orElse(null);
            Transaction bankTxn = group.stream()
                    .filter(tx -> tx.getType() == Transaction.Type.REDEEM
                               && tx.getToAccountId() != null && tx.getFromAccountId() == null)
                    .findFirst().orElse(null);
            Transaction gainLossTxn = group.stream()
                    .filter(tx -> tx.getType() == Transaction.Type.GAIN || tx.getType() == Transaction.Type.LOSE)
                    .findFirst().orElse(null);

            if (invTxn != null && invTxn.getFromAccountId() != null)
                parent.ds.getInvestmentAccounts().stream()
                        .filter(ia -> ia.getId().equals(invTxn.getFromAccountId()))
                        .findFirst().ifPresent(fromCb::setValue);

            if (invTxn != null && invTxn.getRedeemDetails() != null
                    && invTxn.getRedeemDetails().getOrgnlFDRef() != null)
                fdRefCb.setValue(invTxn.getRedeemDetails().getOrgnlFDRef());

            String bankId = bankTxn != null ? bankTxn.getToAccountId()
                    : gainLossTxn != null && gainLossTxn.getType() == Transaction.Type.GAIN
                            ? gainLossTxn.getToAccountId()
                    : gainLossTxn != null ? gainLossTxn.getFromAccountId()
                    : null;
            if (bankId != null) parent.setAccount(toCb, bankId);

            long principal = invTxn != null ? invTxn.getAmountPaise() : 0;
            if (principal > 0)
                principalFld.setText(String.format("%.2f", principal / 100.0));
            long total = principal + (gainLossTxn == null ? 0
                    : gainLossTxn.getType() == Transaction.Type.GAIN
                            ?  gainLossTxn.getAmountPaise()
                            : -gainLossTxn.getAmountPaise());
            parent.sharedAmt.setText(String.format("%.2f", total / 100.0));

            if (gainLossTxn != null) {
                switchCatList(gainLossTxn.getType() == Transaction.Type.LOSE);
                parent.prefillCat(catCb, subCatCb, gainLossTxn);
            }

        } else {
            // Old single-transaction format (backward compatibility)
            if (t.getFromAccountId() != null)
                parent.ds.getInvestmentAccounts().stream()
                        .filter(ia -> ia.getId().equals(t.getFromAccountId()))
                        .findFirst().ifPresent(fromCb::setValue);
            parent.setAccount(toCb, t.getToAccountId());
            long rdePrin = t.getRedeemDetails() != null ? t.getRedeemDetails().getPrincipalPaise() : 0;
            if (rdePrin > 0)
                principalFld.setText(String.format("%.2f", rdePrin / 100.0));
            if (t.getRedeemDetails() != null && t.getRedeemDetails().getOrgnlFDRef() != null)
                fdRefCb.setValue(t.getRedeemDetails().getOrgnlFDRef());
            parent.prefillCat(catCb, subCatCb, t);
        }
    }

    @Override public ComboBox<Category> getCatCombo()    { return catCb; }
    @Override public List<Category>     getCatMaster()   { return catMaster; }
    @Override public ComboBox<Category> getSubCatCombo() { return subCatCb; }

    @Override public void applyContextAccount(Account acc, boolean isSource) {
        if (acc instanceof InvestmentAccount)
            fromCb.getItems().stream()
                    .filter(a -> a.getId().equals(acc.getId()))
                    .findFirst().ifPresent(fromCb::setValue);
        else if (acc instanceof BankAccount)
            parent.setAccount(toCb, acc.getId());
    }

    @Override public boolean focusFirstEmpty() {
        if (fromCb.getValue() == null)          { fromCb.requestFocus();       return true; }
        if (toCb.getValue() == null)            { toCb.requestFocus();         return true; }
        if (principalFld.getText().isBlank())   { principalFld.requestFocus(); return true; }
        return false;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void updateGainLoss() {
        try {
            long total     = Math.round(Double.parseDouble(parent.sharedAmt.getText().replace(",", "")) * 100);
            long principal = Math.round(Double.parseDouble(principalFld.getText().replace(",", "")) * 100);
            long gainLoss  = total - principal;
            String sign  = gainLoss >= 0 ? "+" : "";
            // Inline required: runtime gain/loss colour is data-driven
            String color = gainLoss >= 0 ? "-color-success-dark" : "-color-error";
            gainLossLbl.setText(sign + MoneyFormatter.format(Math.abs(gainLoss)));
            gainLossLbl.getStyleClass().remove("text-hint");
            gainLossLbl.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
            switchCatList(gainLoss < 0);
        } catch (NumberFormatException e) {
            gainLossLbl.setText("—");
            gainLossLbl.setStyle(""); // clears inline; text-hint class re-applies
        }
    }

    private void refreshFdRefRow(InvestmentAccount ia) {
        boolean isFd = ia != null
                && ia.getInvestmentType() == InvestmentAccount.InvestmentType.FIXED_DEPOSIT;
        fdRefRowLbl.setVisible(isFd);
        fdRefRowLbl.setManaged(isFd);
        fdRefCb.setVisible(isFd);
        fdRefCb.setManaged(isFd);
        if (isFd) {
            List<String> refs = parent.ds.getTransactions().stream()
                    .filter(t -> t.getType() == Transaction.Type.INVESTMENT
                              && ia.getId().equals(t.getToAccountId())
                              && t.getInvestmentDetails() != null
                              && t.getInvestmentDetails().getFd() != null
                              && t.getInvestmentDetails().getFd().getRef() != null
                              && !t.getInvestmentDetails().getFd().getRef().isBlank())
                    .map(t -> t.getInvestmentDetails().getFd().getRef())
                    .distinct().sorted()
                    .collect(Collectors.toList());
            fdRefCb.getItems().setAll(refs);
            fdRefCb.setValue(null);
        }
    }

    private void switchCatList(boolean toLoss) {
        if (toLoss == showingExpenseCats) return;
        showingExpenseCats = toLoss;
        catMaster.clear();
        catMaster.addAll(toLoss ? parent.ds.getExpenseCategories() : parent.ds.getIncomeCategories());
        catCb.setValue(null);
        catCb.getEditor().clear();
        catCb.getItems().setAll(catMaster);
        catCb.setPromptText(toLoss ? "Select loss category (optional)" : "Select gain category (optional)");
    }
}
