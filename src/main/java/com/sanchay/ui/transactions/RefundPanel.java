package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Type-specific panel for REFUND transactions. */
class RefundPanel {

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

    RefundPanel(TransactionDialog parent) {
        this.parent = parent;

        catMaster.addAll(parent.ds.getExpenseCategories());
        catCb    = parent.makeCatCb(catMaster, "Select original expense category");
        subCatCb = parent.makeSubCatCb(subCatMaster);
        parent.wireCategory(catCb, catMaster, subCatCb, subCatMaster);

        acctCb    = parent.accountCombo(true); // bank + CC (where refund lands)
        modeCb    = parent.payModeCombo();
        familyFld = parent.tf("optional");
        refFld    = parent.tf("optional");

        node = buildNode();
    }

    Node getNode() { return node; }

    private Node buildNode() {
        GridPane g = parent.panelGrid();
        int r = 0;
        parent.row(g, r++, "To Account*",    acctCb);
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
        Account   acct = parent.requireAccount(acctCb, "To Account");

        Transaction t = new Transaction(Transaction.Type.REFUND, date, desc, amt);
        t.setToAccountId(acct.getId());
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
        parent.setAccount(acctCb, t.getToAccountId());
        parent.prefillCat(catCb, subCatCb, t);
        parent.setPayMode(modeCb, t.getPayment() != null ? t.getPayment().getMode() : null);
        parent.setText(familyFld, t.getClassification() != null ? t.getClassification().getFamilyMember() : null);
        parent.setText(refFld,    t.getPayment() != null ? t.getPayment().getReferenceNumber() : null);
    }
}
