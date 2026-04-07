package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;

/** Type-specific panel for CC_PAYMENT transactions. */
class CCPaymentPanel {

    private final TransactionDialog parent;

    final ComboBox<Account> bankCb;
    final ComboBox<Account> cardCb;

    private final Node node;

    CCPaymentPanel(TransactionDialog parent) {
        this.parent = parent;

        bankCb = parent.accountCombo(false);
        parent.setNodeId(bankCb, "txn-cc-payment-from-account-combo");
        bankCb.getItems().removeIf(a -> !(a instanceof BankAccount));

        cardCb = parent.accountCombo(true);
        parent.setNodeId(cardCb, "txn-cc-payment-to-account-combo");
        cardCb.getItems().removeIf(a -> !(a instanceof CreditCardAccount));

        node = buildNode();
    }

    Node getNode() { return node; }

    private Node buildNode() {
        GridPane g = parent.panelGrid();
        g.setId("txn-cc-payment-panel");
        parent.row(g, 0, "From Account*", bankCb);
        parent.row(g, 1, "To Account*",   cardCb);
        return g;
    }

    Transaction save() {
        LocalDate date = parent.requireDate();
        long      amt  = parent.parsePaise(parent.sharedAmt);
        Account   bank = parent.requireAccount(bankCb, "From Bank Account");
        Account   card = parent.requireAccount(cardCb, "To Credit Card");

        String desc = parent.nullIfBlank(parent.sharedDesc.getText());
        if (desc == null) desc = (parent.existing != null && parent.existing.getDescription() != null)
                ? parent.existing.getDescription() : "CC Payment — " + card.getName();

        Transaction t = new Transaction(Transaction.Type.CC_PAYMENT, date, desc, amt);
        t.setFromAccountId(bank.getId());
        t.setToAccountId(card.getId());
        t.setNotes(parent.nullIfBlank(parent.sharedNotes.getText()));
        return parent.persistTransaction(t);
    }

    void prefill(Transaction t) {
        parent.setAccount(bankCb, t.getFromAccountId());
        parent.setAccount(cardCb, t.getToAccountId());
    }
}
