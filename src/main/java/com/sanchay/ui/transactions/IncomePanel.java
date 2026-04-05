package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Type-specific panel for INCOME transactions. */
class IncomePanel {

    private final TransactionDialog parent;

    final ComboBox<Category> catCb;
    final ComboBox<Category> subCatCb;
    final List<Category>     catMaster    = new ArrayList<>();
    final List<Category>     subCatMaster = new ArrayList<>();
    final ComboBox<Account>  acctCb;
    final TextField          familyFld;

    private final Node node;

    IncomePanel(TransactionDialog parent) {
        this.parent = parent;

        catMaster.addAll(parent.ds.getIncomeCategories());
        catCb    = parent.makeCatCb(catMaster, "Select category");
        subCatCb = parent.makeSubCatCb(subCatMaster);
        parent.wireCategory(catCb, catMaster, subCatCb, subCatMaster);

        acctCb    = parent.accountCombo(false); // bank only
        familyFld = parent.tf("optional");

        node = buildNode();
    }

    Node getNode() { return node; }

    private Node buildNode() {
        GridPane g = parent.panelGrid();
        int r = 0;
        parent.row(g, r++, "To Account*",   acctCb);
        parent.row(g, r++, "Category",       catCb);
        parent.row(g, r++, "Sub-category",   subCatCb);
        parent.row(g, r,   "Family Member",  familyFld);
        return g;
    }

    Transaction save() {
        LocalDate date = parent.requireDate();
        String    desc = parent.requireText(parent.sharedDesc, "Description");
        long      amt  = parent.parsePaise(parent.sharedAmt);
        Account   acct = parent.requireAccount(acctCb, "To Account");

        Transaction t = new Transaction(Transaction.Type.INCOME, date, desc, amt);
        t.setToAccountId(acct.getId());
        Transaction.Classification cl = new Transaction.Classification();
        if (catCb.getValue()    != null) cl.setCategoryId(catCb.getValue().getId());
        if (subCatCb.getValue() != null) cl.setSubCategoryId(subCatCb.getValue().getId());
        cl.setFamilyMember(parent.nullIfBlank(familyFld.getText()));
        t.setClassification(cl);
        t.setNotes(parent.nullIfBlank(parent.sharedNotes.getText()));
        return parent.persistTransaction(t);
    }

    void prefill(Transaction t) {
        String id = t.getToAccountId() != null ? t.getToAccountId() : t.getFromAccountId();
        parent.contextAccountId = id;
        parent.contextIsSource  = false;
        parent.setAccount(acctCb, id);
        parent.prefillCat(catCb, subCatCb, t);
        parent.setText(familyFld, t.getClassification() != null ? t.getClassification().getFamilyMember() : null);
    }
}
