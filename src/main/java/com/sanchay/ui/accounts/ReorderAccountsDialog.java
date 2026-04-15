package com.sanchay.ui.accounts;

import com.sanchay.model.Account;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal dialog that lets the user reorder accounts within a group using
 * Up / Down buttons. On Save, sequential displayOrder values are written
 * back to the accounts and persisted.
 */
class ReorderAccountsDialog {

    /** All accounts in the group (active + closed), sorted by current displayOrder. */
    private final List<Account> items;

    ReorderAccountsDialog(String groupHeading, List<Account> allInGroup) {
        // Work on a mutable copy — displayOrder-sorted, ties keep storage order (stable sort)
        this.items = new ArrayList<>(allInGroup);
        this.items.sort(java.util.Comparator.comparingInt(Account::getDisplayOrder));

        Dialog<ButtonType> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Reorder " + groupHeading, "⇅", 420);
        ButtonType saveType = UiUtils.addSaveCancel(dlg.getDialogPane());

        VBox listBox = new VBox(6);
        listBox.setPadding(new Insets(4, 0, 4, 0));
        rebuildRows(listBox);

        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(360);
        scroll.getStyleClass().add("scroll-page-bg");

        dlg.getDialogPane().setContent(scroll);

        dlg.showAndWait().ifPresent(bt -> {
            if (bt == saveType) {
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setDisplayOrder(i);
                }
                DataStore.getInstance().saveAccountsNow();
            }
        });
    }

    private void rebuildRows(VBox listBox) {
        listBox.getChildren().clear();
        for (int i = 0; i < items.size(); i++) {
            listBox.getChildren().add(buildRow(listBox, i));
        }
    }

    private HBox buildRow(VBox listBox, int index) {
        Account acc = items.get(index);

        Label nameLbl = new Label(acc.getName());
        nameLbl.getStyleClass().add("stat-value");
        nameLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        if (!acc.isActive()) {
            Label badge = new Label("Closed");
            badge.getStyleClass().add("status-closed");
            HBox nameRow = new HBox(8, nameLbl, badge);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            nameRow.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nameRow, Priority.ALWAYS);
            return buildRowWith(listBox, index, nameRow);
        }
        return buildRowWith(listBox, index, nameLbl);
    }

    private HBox buildRowWith(VBox listBox, int index, javafx.scene.Node nameNode) {
        Button upBtn = new Button("↑");
        upBtn.getStyleClass().add("btn-icon");
        upBtn.setDisable(index == 0);
        upBtn.setOnAction(e -> {
            Account moved = items.remove(index);
            items.add(index - 1, moved);
            rebuildRows(listBox);
        });

        Button downBtn = new Button("↓");
        downBtn.getStyleClass().add("btn-icon");
        downBtn.setDisable(index == items.size() - 1);
        downBtn.setOnAction(e -> {
            Account moved = items.remove(index);
            items.add(index + 1, moved);
            rebuildRows(listBox);
        });

        HBox row = new HBox(8, nameNode, upBtn, downBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card");
        row.setPadding(new Insets(8, 12, 8, 12));
        return row;
    }
}
