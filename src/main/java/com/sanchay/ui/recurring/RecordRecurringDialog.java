package com.sanchay.ui.recurring;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;

/**
 * Dialog for recording a single occurrence of a recurring transaction.
 * Extracted from MainWindow.recordRecurring().
 */
public class RecordRecurringDialog {

    private final RecurringTransaction r;

    public RecordRecurringDialog(RecurringTransaction r) {
        this.r = r;
    }

    public void show(Runnable onComplete, Runnable postRefresh) {
        Dialog<Boolean> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Record — " + r.getDescription(), "↺", 420);

        Label subtitle = new Label("Confirm the details for this occurrence:");
        subtitle.getStyleClass().add("dialog-subtitle");
        subtitle.setWrapText(true);

        GridPane g = UiUtils.buildFormGrid(140);
        g.setPadding(new Insets(12, 14, 12, 14));
        g.getStyleClass().add("info-box");

        int row = 0;

        addDialogLabel(g, "Transaction:", r.getDescription(), row++);
        addDialogField(g, "Type:",        UiUtils.typeBadge(r.getTransactionType()), row++);

        DatePicker datePicker = UiUtils.createDatePicker(LocalDate.now());
        addDialogField(g, "Date:", datePicker, row++);

        TextField amtFld = new TextField(
                r.getAmountPaise() > 0
                        ? String.format("%.2f", r.getAmountPaise() / 100.0)
                        : "");
        amtFld.setPromptText("Enter amount");
        amtFld.setMaxWidth(Double.MAX_VALUE);
        addDialogField(g, "Amount (₹):", amtFld, row++);

        DataStore ds = DataStore.getInstance();
        Transaction.Type rType = r.getTransactionType();

        // From Account — shown for all types that debit a bank/CC account
        ComboBox<Account> fromAccountCb = new ComboBox<>();
        boolean showFromAccount = rType == Transaction.Type.EXPENSE
                || rType == Transaction.Type.CC_PAYMENT
                || rType == Transaction.Type.TRANSFER
                || rType == Transaction.Type.LOAN_PAYMENT
                || rType == Transaction.Type.INVESTMENT;
        if (showFromAccount) {
            if (rType == Transaction.Type.EXPENSE) {
                ds.getBankAccounts().forEach(fromAccountCb.getItems()::add);
                ds.getCreditCardAccounts().forEach(fromAccountCb.getItems()::add);
            } else {
                ds.getBankAccounts().forEach(fromAccountCb.getItems()::add);
            }
            if (r.getFromAccountId() != null) {
                fromAccountCb.getItems().stream()
                        .filter(a -> a.getId().equals(r.getFromAccountId()))
                        .findFirst().ifPresent(fromAccountCb::setValue);
                if (fromAccountCb.getValue() == null && !fromAccountCb.getItems().isEmpty())
                    fromAccountCb.setValue(fromAccountCb.getItems().get(0));
            }
            fromAccountCb.setMaxWidth(Double.MAX_VALUE);
            addDialogField(g, "From Account:", fromAccountCb, row++);
        }

        // To Account — destination account, varies by type
        ComboBox<Account> toAccountCb = new ComboBox<>();
        boolean showToAccount = rType == Transaction.Type.INCOME
                || rType == Transaction.Type.TRANSFER
                || rType == Transaction.Type.CC_PAYMENT
                || rType == Transaction.Type.INVESTMENT
                || rType == Transaction.Type.LOAN_PAYMENT;
        if (showToAccount) {
            if (rType == Transaction.Type.INCOME || rType == Transaction.Type.TRANSFER) {
                ds.getBankAccounts().forEach(toAccountCb.getItems()::add);
            } else if (rType == Transaction.Type.CC_PAYMENT) {
                ds.getCreditCardAccounts().forEach(toAccountCb.getItems()::add);
            } else if (rType == Transaction.Type.INVESTMENT) {
                ds.getInvestmentAccounts().forEach(toAccountCb.getItems()::add);
            } else if (rType == Transaction.Type.LOAN_PAYMENT) {
                ds.getActiveLoanAccounts().forEach(toAccountCb.getItems()::add);
            }
            if (r.getToAccountId() != null)
                toAccountCb.getItems().stream()
                        .filter(a -> a.getId().equals(r.getToAccountId()))
                        .findFirst().ifPresent(toAccountCb::setValue);
            if (toAccountCb.getValue() == null && !toAccountCb.getItems().isEmpty())
                toAccountCb.setValue(toAccountCb.getItems().get(0));
            toAccountCb.setMaxWidth(Double.MAX_VALUE);
            addDialogField(g, "To Account:", toAccountCb, row++);
        }

        VBox dialogContent = new VBox(12, subtitle, g);
        dialogContent.setPadding(new Insets(16));
        dlg.getDialogPane().setContent(dialogContent);
        ButtonType recordBtn = new ButtonType("Record", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(recordBtn, ButtonType.CANCEL);

        // Validate before allowing the dialog to close so it stays open on error
        Platform.runLater(() -> {
            javafx.scene.control.Button btn =
                    (javafx.scene.control.Button) dlg.getDialogPane().lookupButton(recordBtn);
            if (btn == null) return;
            btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                String amtRaw = amtFld.getText().trim().replace(",", "").replace(MoneyFormatter.symbol(), "");
                boolean amtOk = true;
                try { if (Double.parseDouble(amtRaw) <= 0) amtOk = false; }
                catch (NumberFormatException ignored) { amtOk = false; }
                if (!amtOk) {
                    showStyledError("Enter a valid positive amount.");
                    ev.consume();
                    return;
                }
                if (showFromAccount && showToAccount
                        && fromAccountCb.getValue() != null && toAccountCb.getValue() != null
                        && fromAccountCb.getValue().getId().equals(toAccountCb.getValue().getId())) {
                    showStyledError("From and To accounts must differ.");
                    ev.consume();
                }
            });
        });

        dlg.setResultConverter(bt -> bt == recordBtn);
        dlg.showAndWait().ifPresent(confirmed -> {
            if (!confirmed) return;
            String amtRaw = amtFld.getText().trim().replace(",", "").replace(MoneyFormatter.symbol(), "");
            long paise = Math.round(Double.parseDouble(amtRaw) * 100);
            LocalDate txDate = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();
            Transaction t = new Transaction(rType, txDate, r.getDescription(), paise);
            if (showFromAccount && fromAccountCb.getValue() != null)
                t.setFromAccountId(fromAccountCb.getValue().getId());
            else
                t.setFromAccountId(r.getFromAccountId());
            if (showToAccount && toAccountCb.getValue() != null)
                t.setToAccountId(toAccountCb.getValue().getId());
            else
                t.setToAccountId(r.getToAccountId());
            if (r.getSourceInvestment() != null) {
                Transaction.SourceInvestment si = new Transaction.SourceInvestment();
                si.setSrcAccount(r.getSourceInvestment().getSrcAccount());
                si.setRefId(r.getSourceInvestment().getRefId());
                t.setSourceInvestment(si);
            }
            if (r.getCategoryId() != null || r.getSubCategoryId() != null) {
                Transaction.Classification cl = new Transaction.Classification();
                cl.setCategoryId(r.getCategoryId());
                cl.setSubCategoryId(r.getSubCategoryId());
                t.setClassification(cl);
            }
            if (r.getPaymentMode() != null) {
                if (t.getPayment() == null) t.setPayment(new Transaction.Payment());
                t.getPayment().setMode(r.getPaymentMode());
            }
            t.setRecurring(new Transaction.Recurring(r.getId()));
            // For RD investment schedules, store the RD reference in investment details.
            String rdRef = r.getRdRef();
            if (rdRef != null && !rdRef.isBlank()) {
                Transaction.FdDetails fd = new Transaction.FdDetails();
                fd.setRef(rdRef.trim());
                Transaction.InvestmentDetails rdInv = new Transaction.InvestmentDetails();
                rdInv.setFd(fd);
                t.setInvestmentDetails(rdInv);
            }
            ds.addTransaction(t);
            r.incrementPaymentsMade();
            r.markRecorded(txDate);
            if (r.isPaymentLimitReached()) {
                ds.deleteRecurring(r.getId());
            } else {
                ds.saveRecurringNow();
            }
            if (onComplete != null) onComplete.run();
            if (postRefresh != null) postRefresh.run();
        });
    }

    // ── Dialog helpers ────────────────────────────────────────────────────────

    private static void showStyledError(String message) {
        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Validation Error", "⚠", 380);

        VBox body = new VBox(10);
        body.setPadding(new Insets(16));

        HBox iconRow = new HBox(14);
        iconRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label iconLbl = new Label("⚠");
        iconLbl.getStyleClass().addAll("dialog-icon-box-lg", "dialog-icon-box-lg--error");
        Label msgLbl = new Label(message);
        msgLbl.getStyleClass().add("text-body-muted");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(280);
        iconRow.getChildren().addAll(iconLbl, msgLbl);
        body.getChildren().add(iconRow);

        dlg.getDialogPane().setContent(body);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dlg.showAndWait();
    }

    private static void addDialogLabel(GridPane g, String labelText, String value, int row) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        Label val = new Label(value);
        val.getStyleClass().add("text-form-value");
        g.add(lbl, 0, row);
        g.add(val, 1, row);
    }

    private static void addDialogField(GridPane g, String labelText, Node field, int row) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        g.add(lbl, 0, row);
        g.add(field, 1, row);
    }
}
