package com.sanchay.ui.common;

import com.sanchay.model.Account;
import com.sanchay.service.DataStore;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

public final class AccountCombos {
    private AccountCombos() {}

    /** Wires a display-name cell factory and button cell on any ComboBox of Account subtypes. */
    public static <T extends Account> void style(ComboBox<T> cb) {
        cb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(T a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? null : a.getName());
            }
        });
        cb.setButtonCell(cb.getCellFactory().call(null));
    }

    /** Creates a styled ComboBox populated with all active bank accounts. */
    public static ComboBox<Account> bankCombo(DataStore ds) {
        ComboBox<Account> cb = base();
        ds.getBankAccounts().forEach(cb.getItems()::add);
        style(cb);
        return cb;
    }

    /** Creates a styled ComboBox populated with bank accounts and credit card accounts. */
    public static ComboBox<Account> bankAndCcCombo(DataStore ds) {
        ComboBox<Account> cb = base();
        ds.getBankAccounts().forEach(cb.getItems()::add);
        ds.getCreditCardAccounts().forEach(cb.getItems()::add);
        style(cb);
        return cb;
    }

    /** Creates a styled ComboBox populated with all active credit card accounts. */
    public static ComboBox<Account> ccCombo(DataStore ds) {
        ComboBox<Account> cb = base();
        ds.getCreditCardAccounts().forEach(cb.getItems()::add);
        style(cb);
        return cb;
    }

    private static ComboBox<Account> base() {
        ComboBox<Account> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("Select account");
        return cb;
    }
}
