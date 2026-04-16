package com.sanchay.ui.common;

import com.sanchay.model.Account;
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
}
