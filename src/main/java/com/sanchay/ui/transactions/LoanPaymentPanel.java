package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.service.AmortizationService;
import com.sanchay.service.MoneyFormatter;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.StageStyle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Type-specific panel for LOAN_PAYMENT transactions. */
class LoanPaymentPanel {

    private final TransactionDialog parent;

    final ComboBox<Category>    catCb;
    final ComboBox<Category>    subCatCb;
    final List<Category>        catMaster    = new ArrayList<>();
    final List<Category>        subCatMaster = new ArrayList<>();
    final ComboBox<Account>     fromCb;
    final ComboBox<LoanAccount> toCb;
    final ComboBox<String>      modeCb;
    final TextField             refFld;
    final TextField             principalFld;
    final Label                 interestLbl;

    private final Node node;

    LoanPaymentPanel(TransactionDialog parent) {
        this.parent = parent;

        catMaster.addAll(parent.ds.getExpenseCategories());
        catCb    = parent.makeCatCb(catMaster, "Select category (optional)");
        parent.setNodeId(catCb, "txn-loan-payment-category-combo");
        subCatCb = parent.makeSubCatCb(subCatMaster);
        parent.setNodeId(subCatCb, "txn-loan-payment-subcategory-combo");
        parent.wireCategory(catCb, catMaster, subCatCb, subCatMaster);

        fromCb = parent.accountCombo(true); // bank + CC
        parent.setNodeId(fromCb, "txn-loan-payment-from-account-combo");

        toCb = new ComboBox<>();
        toCb.setId("txn-loan-payment-to-account-combo");
        toCb.setMaxWidth(Double.MAX_VALUE);
        toCb.setPromptText("Select loan account");
        parent.ds.getActiveLoanAccounts().forEach(toCb.getItems()::add);
        toCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(LoanAccount la, boolean empty) {
                super.updateItem(la, empty);
                setText(empty || la == null ? null : la.getName());
            }
        });
        toCb.setButtonCell(toCb.getCellFactory().call(null));

        modeCb        = parent.payModeCombo();
        parent.setNodeId(modeCb, "txn-loan-payment-mode-combo");
        refFld        = parent.tf("optional");
        parent.setNodeId(refFld, "txn-loan-payment-reference-field");
        principalFld  = parent.tf("Principal portion of this payment");
        parent.setNodeId(principalFld, "txn-loan-payment-principal-field");
        interestLbl   = new Label("—");
        interestLbl.setId("txn-loan-payment-interest-label");
        interestLbl.getStyleClass().add("text-hint");

        // Pre-fill principal from schedule when loan account or date changes
        Runnable prefillPrincipal = () -> {
            LoanAccount la = toCb.getValue();
            LocalDate   d  = parent.sharedDate.getValue();
            if (la == null || d == null) return;
            List<AmortizationEntry> schedule = parent.ds.getSchedule(la.getId());
            long sched = AmortizationService.getScheduledPrincipalForDate(schedule, d);
            if (sched > 0 && (principalFld.getText() == null || principalFld.getText().isBlank())) {
                principalFld.setText(String.format("%.0f", sched / 100.0));
            }
        };
        toCb.valueProperty().addListener((obs, o, n) -> prefillPrincipal.run());
        parent.sharedDate.valueProperty().addListener((obs, o, n) -> {
            if (parent.typeCb.getValue() == Transaction.Type.LOAN_PAYMENT) prefillPrincipal.run();
        });

        // Live interest = amount - principal
        Runnable updateInterest = () -> {
            try {
                long amt  = Math.round(Double.parseDouble(parent.sharedAmt.getText().replace(",", "")) * 100);
                long prin = Math.round(Double.parseDouble(principalFld.getText().replace(",", "")) * 100);
                long interest = amt - prin;
                interestLbl.setText(interest >= 0
                        ? MoneyFormatter.format(interest)
                        : "⚠ Principal exceeds EMI amount");
            } catch (NumberFormatException e) {
                interestLbl.setText("—");
            }
        };
        principalFld.textProperty().addListener((obs, o, n) -> updateInterest.run());
        parent.sharedAmt.textProperty().addListener((obs, o, n) -> {
            if (parent.typeCb.getValue() == Transaction.Type.LOAN_PAYMENT) updateInterest.run();
        });

        node = buildNode();
    }

    Node getNode() { return node; }

    private Node buildNode() {
        GridPane g = parent.panelGrid();
        g.setId("txn-loan-payment-panel");
        int r = 0;
        parent.row(g, r++, "From Account*", fromCb);
        parent.row(g, r++, "To Account*",   toCb);
        parent.row(g, r++, "Principal (₹)", principalFld);
        parent.row(g, r++, "Interest (₹)",  interestLbl);
        parent.row(g, r++, "Category",       catCb);
        parent.row(g, r++, "Sub-category",   subCatCb);
        parent.row(g, r++, "Payment Mode",   modeCb);
        parent.row(g, r,   "Ref / UTR No",   refFld);
        return g;
    }

    Transaction save() {
        LocalDate   date = parent.requireDate();
        String      desc = parent.requireText(parent.sharedDesc, "Description");
        long        amt  = parent.parsePaise(parent.sharedAmt);
        Account     from = parent.requireAccount(fromCb, "From Account");
        LoanAccount to   = toCb.getValue();
        if (to == null) throw new IllegalArgumentException("Please select a loan account.");

        Transaction t = new Transaction(Transaction.Type.LOAN_PAYMENT, date, desc, amt);
        t.setFromAccountId(from.getId());
        t.setToAccountId(to.getId());
        Transaction.Classification cl = new Transaction.Classification();
        if (catCb.getValue()    != null) cl.setCategoryId(catCb.getValue().getId());
        if (subCatCb.getValue() != null) cl.setSubCategoryId(subCatCb.getValue().getId());
        t.setClassification(cl);
        parent.applyPayMode(t, modeCb);
        String ref = parent.nullIfBlank(refFld.getText());
        if (ref != null) {
            if (t.getPayment() == null) t.setPayment(new Transaction.Payment());
            t.getPayment().setReferenceNumber(ref);
        }
        t.setNotes(parent.nullIfBlank(parent.sharedNotes.getText()));

        // Store actual principal for accurate outstanding calculation.
        List<AmortizationEntry> lnSchedule = parent.ds.getSchedule(to.getId());
        long scheduledInterest = 0;
        for (AmortizationEntry ae : lnSchedule) {
            if (ae.getPaymentDate().getYear()  == date.getYear()
             && ae.getPaymentDate().getMonth() == date.getMonth()) {
                scheduledInterest = ae.getInterestPaymentPaise();
                break;
            }
        }
        long actualPrincipal = Math.max(0, amt - scheduledInterest);
        if (actualPrincipal > 0) {
            Transaction.RedeemDetails rd = new Transaction.RedeemDetails();
            rd.setPrincipalPaise(actualPrincipal);
            t.setRedeemDetails(rd);
        }

        Transaction saved = parent.persistTransaction(t);
        checkAndHandlePrepayment(saved, to, date);
        return saved;
    }

    void prefill(Transaction t) {
        parent.setAccount(fromCb, t.getFromAccountId());
        if (t.getToAccountId() != null)
            parent.ds.getActiveLoanAccounts().stream()
                    .filter(la -> la.getId().equals(t.getToAccountId()))
                    .findFirst().ifPresent(toCb::setValue);
        long prin = t.getRedeemDetails() != null ? t.getRedeemDetails().getPrincipalPaise() : 0;
        if (prin > 0)
            parent.setText(principalFld, String.format("%.0f", prin / 100.0));
        parent.prefillCat(catCb, subCatCb, t);
        parent.setPayMode(modeCb, t.getPayment() != null ? t.getPayment().getMode() : null);
        parent.setText(refFld, t.getPayment() != null ? t.getPayment().getReferenceNumber() : null);
    }

    // ── Prepayment handling ───────────────────────────────────────────────────

    /**
     * After saving a LOAN_PAYMENT, check if the principal paid exceeds the
     * scheduled principal for that month. If so, prompt for prepayment mode
     * (or use the loan's saved default) and regenerate the amortization schedule.
     */
    private void checkAndHandlePrepayment(Transaction t, LoanAccount loan, LocalDate date) {
        List<AmortizationEntry> schedule = parent.ds.getSchedule(loan.getId());
        if (schedule == null || schedule.isEmpty()) return;

        int entryIndex = -1;
        for (int i = 0; i < schedule.size(); i++) {
            AmortizationEntry e = schedule.get(i);
            if (e.getPaymentDate().getYear()  == date.getYear()
             && e.getPaymentDate().getMonth() == date.getMonth()) {
                entryIndex = i;
                break;
            }
        }
        if (entryIndex < 0) return;

        AmortizationEntry entry         = schedule.get(entryIndex);
        long              scheduledPrin = entry.getPrincipalRepaymentPaise();
        long              actualPrin    = Math.max(0, t.getAmountPaise() - entry.getInterestPaymentPaise());
        long              extraPrin     = actualPrin - scheduledPrin;
        if (extraPrin <= 0) return;

        LoanAccount.PrepaymentMode mode = loan.getDefaultPrepaymentMode();
        if (mode == null) {
            mode = promptPrepaymentMode(loan);
            if (mode == null) return;
        }

        long newBalance = entry.getOpeningBalancePaise() - actualPrin;
        int  nextIndex  = entryIndex + 1;

        if (newBalance <= 0 || nextIndex >= schedule.size()) {
            parent.ds.saveSchedule(loan.getId(), new ArrayList<>(schedule.subList(0, nextIndex)));
            return;
        }

        List<AmortizationEntry> updated = AmortizationService.recalculateFromBalance(
                schedule, nextIndex, loan, newBalance, mode);
        parent.ds.saveSchedule(loan.getId(), updated);

        if (mode == LoanAccount.PrepaymentMode.REDUCE_EMI && nextIndex < updated.size()) {
            offerEmiSyncAfterPrepayment(loan, updated.get(nextIndex).getEmiAmountPaise());
        }
    }

    /**
     * If a LOAN_PAYMENT recurring schedule exists and the new EMI after prepayment
     * differs from the scheduled amount, offers to update it.
     */
    private void offerEmiSyncAfterPrepayment(LoanAccount loan, long newEmiPaise) {
        RecurringTransaction rt = parent.ds.findLoanPaymentSchedule(loan.getId());
        if (rt == null || rt.getAmountPaise() == newEmiPaise) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.getDialogPane().setId("txn-loan-payment-sync-dialog-pane");
        confirm.initOwner(parent.getDialogPane().getScene().getWindow());
        confirm.setTitle("Update Payment Schedule");
        confirm.setHeaderText(null);
        confirm.setContentText(String.format(
                "EMI has changed to \u20b9%.0f after prepayment.\nUpdate the linked payment schedule?",
                newEmiPaise / 100.0));
        Platform.runLater(() -> {
            Button okBtn = (Button) confirm.getDialogPane().lookupButton(ButtonType.OK);
            if (okBtn != null) okBtn.setId("txn-loan-payment-sync-ok-button");
            Button cancelBtn = (Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL);
            if (cancelBtn != null) cancelBtn.setId("txn-loan-payment-sync-cancel-button");
        });
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                rt.setAmountPaise(newEmiPaise);
                parent.ds.saveRecurringNow();
            }
        });
    }

    /**
     * Shows a dialog asking whether the prepayment should reduce tenure or EMI.
     * Offers a "Remember my choice" checkbox. Returns the chosen mode, or null if cancelled.
     */
    private LoanAccount.PrepaymentMode promptPrepaymentMode(LoanAccount loan) {
        ButtonType reduceTenure = new ButtonType("Reduce Tenure", ButtonBar.ButtonData.OTHER);
        ButtonType reduceEmi    = new ButtonType("Reduce EMI",    ButtonBar.ButtonData.OTHER);
        ButtonType cancel       = new ButtonType("Cancel",        ButtonBar.ButtonData.CANCEL_CLOSE);

        Label bodyLabel = new Label(
                "How should the amortization schedule be recalculated?\n\n" +
                "• Reduce Tenure — keep the same EMI, fewer months remaining.\n" +
                "• Reduce EMI    — keep the same tenure, lower monthly EMI.");
        bodyLabel.setId("txn-loan-payment-prepayment-message");
        bodyLabel.setWrapText(true);

        CheckBox rememberChk = new CheckBox("Remember my choice for this loan");
        rememberChk.setId("txn-loan-payment-prepayment-remember-checkbox");
        rememberChk.getStyleClass().add("form-label");

        VBox content = new VBox(12, bodyLabel, rememberChk);
        content.setId("txn-loan-payment-prepayment-body");

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.getDialogPane().setId("txn-loan-payment-prepayment-dialog-pane");
        dlg.initOwner(parent.getDialogPane().getScene().getWindow());
        dlg.initStyle(StageStyle.UNDECORATED);
        dlg.setHeaderText("This payment includes an extra principal amount.");
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(reduceTenure, reduceEmi, cancel);
        dlg.getDialogPane().getStylesheets().addAll(parent.getDialogPane().getStylesheets());

        Button reduceTenureBtn = (Button) dlg.getDialogPane().lookupButton(reduceTenure);
        reduceTenureBtn.setMinWidth(150);
        reduceTenureBtn.setId("txn-loan-payment-reduce-tenure-button");
        Button reduceEmiBtn = (Button) dlg.getDialogPane().lookupButton(reduceEmi);
        if (reduceEmiBtn != null) reduceEmiBtn.setId("txn-loan-payment-reduce-emi-button");
        Button cancelBtn = (Button) dlg.getDialogPane().lookupButton(cancel);
        if (cancelBtn != null) cancelBtn.setId("txn-loan-payment-prepayment-cancel-button");

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() == cancel) return null;

        LoanAccount.PrepaymentMode chosen = (result.get() == reduceTenure)
                ? LoanAccount.PrepaymentMode.REDUCE_TENURE
                : LoanAccount.PrepaymentMode.REDUCE_EMI;

        if (rememberChk.isSelected()) {
            loan.setDefaultPrepaymentMode(chosen);
            parent.ds.saveAccountsNow();
        }

        return chosen;
    }
}
