package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Type-specific panel for EXPENSE transactions. */
class ExpensePanel {

    private final TransactionDialog parent;

    final ComboBox<Category> catCb;
    final ComboBox<Category> subCatCb;
    final List<Category>     catMaster    = new ArrayList<>();
    final List<Category>     subCatMaster = new ArrayList<>();
    final ComboBox<Account>  acctCb;
    final ComboBox<String>   modeCb;
    final TextField          familyFld;
    final TextField          refFld;

    private final Node node;

    ExpensePanel(TransactionDialog parent) {
        this.parent = parent;

        catMaster.addAll(parent.ds.getExpenseCategories());
        catCb    = parent.makeCatCb(catMaster, "Select category");
        parent.setNodeId(catCb, "txn-expense-category-combo");
        subCatCb = parent.makeSubCatCb(subCatMaster);
        parent.setNodeId(subCatCb, "txn-expense-subcategory-combo");
        parent.wireCategory(catCb, catMaster, subCatCb, subCatMaster);

        acctCb    = parent.accountCombo(true); // bank + CC
        parent.setNodeId(acctCb, "txn-expense-account-combo");
        modeCb    = parent.payModeCombo();
        parent.setNodeId(modeCb, "txn-expense-payment-mode-combo");
        acctCb.valueProperty().addListener((obs, old, acct) -> {
            if (acct instanceof CreditCardAccount) modeCb.setValue("Credit Card");
            else if (acct != null)                 modeCb.setValue("Net Banking");
        });
        familyFld = parent.tf("optional");
        parent.setNodeId(familyFld, "txn-expense-family-member-field");
        refFld    = parent.tf("optional");
        parent.setNodeId(refFld, "txn-expense-reference-field");

        node = buildNode();
    }

    Node getNode() { return node; }

    private Node buildNode() {
        GridPane g = parent.panelGrid();
        g.setId("txn-expense-panel");
        int r = 0;
        parent.row(g, r++, "From Account*",  acctCb);
        parent.row(g, r++, "Category",        catCb);
        parent.row(g, r++, "Sub-category",    subCatCb);
        parent.row(g, r++, "Payment Mode",    modeCb);
        parent.row(g, r++, "Family Member",   familyFld);
        parent.row(g, r,   "Ref / UTR No",    refFld);
        return g;
    }

    Transaction save() {
        LocalDate date = parent.requireDate();
        String    desc = parent.requireText(parent.sharedDesc, "Description");
        long      amt  = parent.parsePaise(parent.sharedAmt);
        Account   acct = parent.requireAccount(acctCb, "From Account");

        Transaction t = new Transaction(Transaction.Type.EXPENSE, date, desc, amt);
        t.setFromAccountId(acct.getId());
        Transaction.Classification cl = new Transaction.Classification();
        if (catCb.getValue()    != null) cl.setCategoryId(catCb.getValue().getId());
        if (subCatCb.getValue() != null) cl.setSubCategoryId(subCatCb.getValue().getId());
        cl.setFamilyMember(parent.nullIfBlank(familyFld.getText()));
        t.setClassification(cl);
        parent.applyPayMode(t, modeCb);
        String ref = parent.nullIfBlank(refFld.getText());
        if (ref != null) {
            if (t.getPayment() == null) t.setPayment(new Transaction.Payment());
            t.getPayment().setReferenceNumber(ref);
        }
        t.setNotes(parent.nullIfBlank(parent.sharedNotes.getText()));
        return parent.persistTransaction(t);
    }

    void prefill(Transaction t) {
        parent.contextAccountId = t.getFromAccountId();
        parent.contextIsSource  = true;
        parent.setAccount(acctCb, t.getFromAccountId());
        parent.prefillCat(catCb, subCatCb, t);
        parent.setPayMode(modeCb, t.getPayment() != null ? t.getPayment().getMode() : null);
        parent.setText(familyFld, t.getClassification() != null ? t.getClassification().getFamilyMember() : null);
        parent.setText(refFld,    t.getPayment() != null ? t.getPayment().getReferenceNumber() : null);
    }
}
