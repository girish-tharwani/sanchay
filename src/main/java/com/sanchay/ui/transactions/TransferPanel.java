package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.ui.UiUtils;
import com.sanchay.ui.common.AccountCombos;
import com.sanchay.ui.common.CategoryComboWiring;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Type-specific panel for TRANSFER transactions. */
class TransferPanel implements TransactionPanel {

    private final TransactionDialog parent;

    final ComboBox<Category> catCb;
    final ComboBox<Category> subCatCb;
    final List<Category>     catMaster    = new ArrayList<>();
    final List<Category>     subCatMaster = new ArrayList<>();
    final ComboBox<Account>  fromCb;
    final ComboBox<Account>  toCb;

    private final Node node;

    TransferPanel(TransactionDialog parent) {
        this.parent = parent;

        catMaster.addAll(parent.ds.getExpenseCategories());
        catCb    = CategoryComboWiring.catCombo(catMaster, "Select category (optional)");
        parent.setNodeId(catCb, "txn-transfer-category-combo");
        subCatCb = CategoryComboWiring.subCatCombo();
        parent.setNodeId(subCatCb, "txn-transfer-subcategory-combo");
        CategoryComboWiring.wire(catCb, subCatCb, subCatMaster, parent.ds);
        UiUtils.wireAutoComplete(catCb,    catMaster);
        UiUtils.wireAutoComplete(subCatCb, subCatMaster);

        fromCb = AccountCombos.bankCombo(parent.ds);
        parent.setNodeId(fromCb, "txn-transfer-from-account-combo");
        toCb   = AccountCombos.bankCombo(parent.ds);
        parent.setNodeId(toCb, "txn-transfer-to-account-combo");

        node = buildNode();
    }

    @Override public Node getNode() { return node; }

    private Node buildNode() {
        GridPane g = parent.panelGrid();
        g.setId("txn-transfer-panel");
        int r = 0;
        parent.row(g, r++, "From Account*", fromCb);
        parent.row(g, r++, "To Account*",   toCb);
        parent.row(g, r++, "Category",      catCb);
        parent.row(g, r,   "Sub-category",  subCatCb);
        return g;
    }

    @Override public Transaction save() {
        LocalDate date = parent.requireDate();
        String    desc = parent.requireText(parent.sharedDesc, "Description");
        long      amt  = parent.parsePaise(parent.sharedAmt);
        Account   from = parent.requireAccount(fromCb, "From Account");
        Account   to   = parent.requireAccount(toCb,   "To Account");
        if (from.getId().equals(to.getId()))
            throw new IllegalArgumentException("From and To accounts must differ.");

        Transaction t = new Transaction(Transaction.Type.TRANSFER, date, desc, amt);
        t.setFromAccountId(from.getId());
        t.setToAccountId(to.getId());
        if (catCb.getValue() != null || subCatCb.getValue() != null) {
            Transaction.Classification cl = new Transaction.Classification();
            if (catCb.getValue()    != null) cl.setCategoryId(catCb.getValue().getId());
            if (subCatCb.getValue() != null) cl.setSubCategoryId(subCatCb.getValue().getId());
            t.setClassification(cl);
        }
        t.setNotes(parent.nullIfBlank(parent.sharedNotes.getText()));
        return parent.persistTransaction(t);
    }

    @Override public void prefill(Transaction t) {
        parent.setAccount(fromCb, t.getFromAccountId());
        parent.setAccount(toCb,   t.getToAccountId());
        parent.prefillCat(catCb, subCatCb, t);
    }

    @Override public ComboBox<Category> getCatCombo()    { return catCb; }
    @Override public List<Category>     getCatMaster()   { return catMaster; }
    @Override public ComboBox<Category> getSubCatCombo() { return subCatCb; }

    @Override public void applyContextAccount(Account acc, boolean isSource) {
        if (acc instanceof BankAccount) {
            if (isSource) parent.setAccount(fromCb, acc.getId());
            else          parent.setAccount(toCb,   acc.getId());
        }
    }

    @Override public boolean focusFirstEmpty() {
        if (fromCb.getValue() == null) { fromCb.requestFocus(); return true; }
        if (toCb.getValue()   == null) { toCb.requestFocus();   return true; }
        return false;
    }
}
