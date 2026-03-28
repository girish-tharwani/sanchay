package com.sanchay.ui.recurring;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
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
        dlg.setTitle("Record — " + r.getDescription());
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(420);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "↺", "Record — " + r.getDescription());

        Label subtitle = new Label("Confirm the details for this occurrence:");
        subtitle.getStyleClass().add("dialog-subtitle");
        subtitle.setWrapText(true);

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(12, 14, 12, 14));
        g.getStyleClass().add("info-box");
        ColumnConstraints c1 = new ColumnConstraints(140);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        int row = 0;

        addDialogLabel(g, "Transaction:", r.getDescription(), row++);
        addDialogField(g, "Type:",        UiUtils.typeBadge(r.getTransactionType()), row++);

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setMaxWidth(Double.MAX_VALUE);
        UiUtils.applySmartDateConverter(datePicker);
        UiUtils.styleOnShow(datePicker);
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

        // From Account — shown for all types that debit an account
        ComboBox<Account> fromAccountCb = new ComboBox<>();
        boolean showFromAccount = rType == Transaction.Type.EXPENSE
                || rType == Transaction.Type.CC_PAYMENT
                || rType == Transaction.Type.TRANSFER
                || rType == Transaction.Type.LOAN_PAYMENT
                || rType == Transaction.Type.INVESTMENT;
        if (showFromAccount) {
            ds.getAccounts().stream()
                    .filter(a -> a.isActive() && !(a instanceof CreditCardAccount))
                    .forEach(fromAccountCb.getItems()::add);
            if (r.getFromAccountId() != null)
                fromAccountCb.getItems().stream()
                        .filter(a -> a.getId().equals(r.getFromAccountId()))
                        .findFirst().ifPresent(fromAccountCb::setValue);
            if (fromAccountCb.getValue() == null && !fromAccountCb.getItems().isEmpty())
                fromAccountCb.setValue(fromAccountCb.getItems().get(0));
            fromAccountCb.setMaxWidth(Double.MAX_VALUE);
            addDialogField(g, "From Account:", fromAccountCb, row++);
        }

        // To Account — shown for Income and Transfer
        ComboBox<Account> toAccountCb = new ComboBox<>();
        boolean showToAccount = rType == Transaction.Type.INCOME
                || rType == Transaction.Type.TRANSFER;
        if (showToAccount) {
            ds.getAccounts().stream()
                    .filter(a -> a.isActive() && a instanceof BankAccount)
                    .forEach(toAccountCb.getItems()::add);
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
                String amtRaw = amtFld.getText().trim().replace(",", "").replace("₹", "");
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
            String amtRaw = amtFld.getText().trim().replace(",", "").replace("₹", "");
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
            if (r.getCategoryId() != null || r.getSubCategoryId() != null) {
                Transaction.Classification cl = new Transaction.Classification();
                cl.setCategoryId(r.getCategoryId());
                cl.setSubCategoryId(r.getSubCategoryId());
                t.setClassification(cl);
            }
            t.setRecurring(new Transaction.Recurring(r.getId()));
            // For RD investment schedules, copy the RD Ref into the transaction notes
            String rdRef = r.getRdRef();
            if (rdRef != null && !rdRef.isBlank())
                t.setNotes("RD Ref: " + rdRef);
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
        dlg.setTitle("Validation Error");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(380);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "⚠", "Validation Error");

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
