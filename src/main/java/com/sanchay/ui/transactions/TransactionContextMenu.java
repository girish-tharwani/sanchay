package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.ImportService;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Builds the source-indicator column (with merge/reconcile context menus) and the delete action column. */
class TransactionContextMenu {

    private final Account account;
    private final DataStore ds;

    TransactionContextMenu(Account account, DataStore ds) {
        this.account = account;
        this.ds = ds;
    }

    TableColumn<Transaction, Void> buildActionsCol(Runnable refresh) {
        TableColumn<Transaction, Void> actionsCol = new TableColumn<>("");
        actionsCol.setMinWidth(36);
        actionsCol.setMaxWidth(36);
        actionsCol.setCellFactory(tc -> new TableCell<>() {
            private final Label deleteBtn = new Label("×");
            {
                deleteBtn.setId("txn-delete-action");
                deleteBtn.getStyleClass().add("btn-row-remove");
                deleteBtn.setTooltip(new Tooltip("Delete transaction"));
                deleteBtn.setOnMouseClicked(e -> {
                    Transaction t = getTableRow().getItem();
                    if (t == null) return;
                    if (showDeleteTxnConfirm(t)) {
                        DataStore.getInstance().deleteTransaction(t.getId());
                        refresh.run();
                    }
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                deleteBtn.setId("txn-delete-action-" + sanitizeId(getTableRow().getItem().getId()));
                setGraphic(deleteBtn);
            }
        });
        return actionsCol;
    }

    TableColumn<Transaction, Void> buildSrcCol(Runnable refresh) {
        TableColumn<Transaction, Void> srcCol = new TableColumn<>("");
        srcCol.setMinWidth(32); srcCol.setMaxWidth(32);
        srcCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                Transaction t = getTableRow().getItem();
                Label badge = new Label();
                switch (t.getSourceIndicator()) {
                    case IMPORTED -> {
                        badge.setText("I");
                        badge.getStyleClass().add("badge-imported");
                        badge.setTooltip(new Tooltip("Imported from file — right-click to merge with existing"));
                        ContextMenu cm = new ContextMenu();
                        MenuItem mergeItem = new MenuItem("Merge with existing…");
                        mergeItem.setOnAction(ev -> openMergeDialog(t, refresh));
                        cm.getItems().add(mergeItem);
                        badge.setContextMenu(cm);
                    }
                    case AUTO_CATEGORIZED -> {
                        badge.setText("?");
                        badge.getStyleClass().add("badge-auto-cat");
                        StringBuilder tip = new StringBuilder();
                        boolean isTypeSuggested = t.getType() != Transaction.Type.EXPENSE
                                                && t.getType() != Transaction.Type.INCOME;
                        if (isTypeSuggested) {
                            tip.append("Type auto-filled: ").append(t.getType().toString());
                            String secondId = account.getId().equals(t.getFromAccountId())
                                    ? t.getToAccountId() : t.getFromAccountId();
                            String acctName = ds.getAccountName(secondId);
                            if (!"—".equals(acctName)) tip.append(" → ").append(acctName);
                        } else {
                            tip.append("Category auto-filled");
                            String tipCatId    = t.getClassification() != null ? t.getClassification().getCategoryId() : null;
                            String tipSubCatId = t.getClassification() != null ? t.getClassification().getSubCategoryId() : null;
                            if (tipCatId != null)
                                tip.append(": ").append(ds.getCategoryName(tipCatId));
                            if (tipSubCatId != null)
                                tip.append(" / ").append(ds.getCategoryName(tipSubCatId));
                        }
                        tip.append("\nLeft-click to accept · Right-click to merge with existing");
                        badge.setTooltip(new Tooltip(tip.toString()));
                        badge.setOnMouseClicked(e -> {
                            if (e.getButton() == MouseButton.PRIMARY) {
                                t.setSourceIndicator(Transaction.SourceIndicator.RECONCILED);
                                DataStore.getInstance().saveTransactionsNow();
                                refresh.run();
                                e.consume();
                            }
                        });
                        ContextMenu cmAc = new ContextMenu();
                        MenuItem mergeItemAc = new MenuItem("Merge with existing…");
                        mergeItemAc.setOnAction(ev -> openMergeDialog(t, refresh));
                        cmAc.getItems().add(mergeItemAc);
                        badge.setContextMenu(cmAc);
                    }
                    case RECONCILED -> {
                        badge.setText("R");
                        badge.getStyleClass().add("badge-reconciled");
                        badge.setTooltip(new Tooltip("Reconciled with import"));
                    }
                    default -> {
                        badge.setText("M");
                        badge.getStyleClass().add("badge-manual");
                        badge.setTooltip(new Tooltip("Manually entered — right-click to mark as reconciled"));
                        ContextMenu cmM = new ContextMenu();
                        MenuItem markItem = new MenuItem("Mark as Reconciled");
                        markItem.setOnAction(ev -> {
                            t.setSourceIndicator(Transaction.SourceIndicator.RECONCILED);
                            DataStore.getInstance().saveTransactionsNow();
                            refresh.run();
                        });
                        cmM.getItems().add(markItem);
                        badge.setContextMenu(cmM);
                    }
                }
                badge.setId("txn-source-badge-" + sanitizeId(t.getId()));
                setGraphic(badge);
            }
        });
        return srcCol;
    }

    private void openMergeDialog(Transaction imported, Runnable refresh) {
        LocalDate date = imported.getDate();
        List<Transaction> candidates = ds.getTransactions().stream()
                .filter(t -> t.getSourceIndicator() == Transaction.SourceIndicator.MANUAL)
                .filter(t -> account.getId().equals(t.getFromAccountId())
                          || account.getId().equals(t.getToAccountId()))
                .filter(t -> !t.getDate().isBefore(date.minusDays(10))
                          && !t.getDate().isAfter(date.plusDays(10)))
                .sorted(Comparator.comparingLong(t -> Math.abs(
                        ChronoUnit.DAYS.between(t.getDate(), date))))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            info("No Candidates Found",
                    "No manually-entered transactions exist within ±10 days of "
                    + date.format(dateFmt()) + " for this account.");
            return;
        }

        new MergeWithManualDialog(imported, candidates, dateFmt())
                .showAndWait()
                .ifPresent(chosen -> {
                    ImportService.reconcile(imported, chosen, ds);
                    ds.deleteTransactionByIdInternal(imported.getId());
                    ds.saveTransactionsNow();
                    refresh.run();
                });
    }

    private boolean showDeleteTxnConfirm(Transaction t) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.getDialogPane().setId("txn-delete-dialog-pane");
        dlg.setTitle("Delete Transaction");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(400);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "⚠", "Delete Transaction");

        boolean isGrouped = t.getGroupTransactionId() != null;

        VBox body = new VBox(14);
        body.setId("txn-delete-dialog-body");
        body.setPadding(new Insets(16));

        HBox warnRow = new HBox(12);
        warnRow.setId("txn-delete-dialog-warning-row");
        warnRow.setAlignment(Pos.CENTER_LEFT);
        Label warnIcon = new Label("⚠");
        warnIcon.setId("txn-delete-dialog-warning-icon");
        warnIcon.getStyleClass().add("icon-danger");
        VBox warnText = new VBox(4);
        Label headline = new Label(isGrouped ? "Delete linked redemption group?" : "Delete this transaction?");
        headline.setId("txn-delete-dialog-headline");
        headline.getStyleClass().add("text-section-title");
        String subMsg = "This action cannot be undone."
                + (isGrouped ? " This will also delete the related principal and gain/loss entries." : "");
        Label subLbl = new Label(subMsg);
        subLbl.setId("txn-delete-dialog-subtext");
        subLbl.getStyleClass().add("text-hint");
        subLbl.setWrapText(true);
        subLbl.setMaxWidth(310);
        warnText.getChildren().addAll(headline, subLbl);
        warnRow.getChildren().addAll(warnIcon, warnText);

        VBox txnBlock = new VBox(5);
        txnBlock.setId("txn-delete-dialog-transaction-block");
        txnBlock.getStyleClass().add("dialog-danger-block");
        Label desc = new Label(t.getDescription());
        desc.setId("txn-delete-dialog-description");
        desc.getStyleClass().add("text-body-strong");
        desc.setWrapText(true);
        HBox meta = new HBox(8);
        meta.setId("txn-delete-dialog-meta");
        meta.setAlignment(Pos.CENTER_LEFT);
        Label amt = new Label(t.getAmountInr());
        amt.setId("txn-delete-dialog-amount");
        amt.getStyleClass().add("text-amount-error");
        Label sep = new Label("·");
        sep.setId("txn-delete-dialog-separator");
        sep.getStyleClass().add("text-hint");
        Label date = new Label(t.getDate().format(dateFmt()));
        date.setId("txn-delete-dialog-date");
        date.getStyleClass().add("text-hint");
        meta.getChildren().addAll(amt, sep, date);
        txnBlock.getChildren().addAll(desc, meta);

        body.getChildren().addAll(warnRow, txnBlock);
        dlg.getDialogPane().setContent(body);

        ButtonType deleteBtn = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, deleteBtn);
        Button deleteButton = (Button) dlg.getDialogPane().lookupButton(deleteBtn);
        if (deleteButton != null) {
            ButtonBar.setButtonUniformSize(deleteButton, false);
            deleteButton.getStyleClass().add("btn-danger");
            deleteButton.setId("txn-delete-dialog-confirm-button");
        }
        Button cancelButton = (Button) dlg.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelButton != null) cancelButton.setId("txn-delete-dialog-cancel-button");

        return dlg.showAndWait().filter(b -> b == deleteBtn).isPresent();
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        UiUtils.initDialog(a, title, "i", 400);
        Label content = new Label(msg);
        content.setId("txn-info-dialog-message");
        content.setWrapText(true);
        content.getStyleClass().add("dialog-body-text");
        a.getDialogPane().setContent(content);
        a.showAndWait();
    }

    private DateTimeFormatter dateFmt() {
        return DataStore.getInstance().getDateFormatter();
    }

    private String sanitizeId(String raw) {
        return raw == null ? "unknown" : raw.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
