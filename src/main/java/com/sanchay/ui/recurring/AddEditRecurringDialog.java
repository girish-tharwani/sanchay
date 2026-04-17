package com.sanchay.ui.recurring;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import com.sanchay.ui.common.AccountCombos;
import com.sanchay.ui.common.CategoryComboWiring;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dialog for adding or editing a recurring transaction schedule.
 * Extracted from RecurringScreen.openRecurringForm().
 */
public class AddEditRecurringDialog {

    public static void show(RecurringTransaction existing) {
        showInternal(existing, false);
    }

    /**
     * Opens the dialog pre-populated with values from {@code defaults} but always
     * creates a new record on save — the defaults object is never persisted directly.
     */
    public static void showWithDefaults(RecurringTransaction defaults) {
        showInternal(defaults, true);
    }

    private static void showInternal(RecurringTransaction existing, boolean forceNew) {
        boolean isNew = (existing == null || forceNew);
        boolean hasDefaults = (existing != null);
        String dlgTitle = isNew ? "New Recurring Schedule" : "Edit Recurring Schedule";
        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, dlgTitle, isNew ? "+" : "✎", 520);

        GridPane g = UiUtils.buildFormGrid(150);
        g.setPadding(new Insets(16));

        // ── Description ───────────────────────────────────────────────────────
        TextField descFld = new TextField(!hasDefaults ? "" : existing.getDescription());
        descFld.setPromptText("e.g. SBI Home Loan EMI");
        descFld.setMaxWidth(Double.MAX_VALUE);
        UiUtils.wireDescriptionAutocomplete(descFld, DataStore.getInstance().getDistinctScheduleDescriptions());

        // ── Transaction type ──────────────────────────────────────────────────
        ComboBox<Transaction.Type> typeCb = new ComboBox<>();
        typeCb.getItems().addAll(
                Transaction.Type.EXPENSE, Transaction.Type.INCOME,
                Transaction.Type.TRANSFER, Transaction.Type.INVESTMENT,
                Transaction.Type.CC_PAYMENT, Transaction.Type.LOAN_PAYMENT);
        typeCb.setConverter(new StringConverter<>() {
            @Override public String toString(Transaction.Type t) {
                return t == null ? "" : UiUtils.badgeText(t);
            }
            @Override public Transaction.Type fromString(String s) { return null; }
        });
        typeCb.setValue(!hasDefaults ? Transaction.Type.EXPENSE : existing.getTransactionType());
        typeCb.setMaxWidth(Double.MAX_VALUE);

        // ── Frequency ─────────────────────────────────────────────────────────
        ComboBox<RecurringTransaction.Frequency> freqCb = new ComboBox<>();
        freqCb.getItems().addAll(RecurringTransaction.Frequency.values());
        freqCb.setValue(!hasDefaults ? RecurringTransaction.Frequency.MONTHLY : existing.getFrequency());
        freqCb.setMaxWidth(Double.MAX_VALUE);
        freqCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(RecurringTransaction.Frequency item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatFrequency(item));
            }
        });
        freqCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(RecurringTransaction.Frequency item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatFrequency(item));
            }
        });

        // ── Due day ───────────────────────────────────────────────────────────
        Spinner<Integer> daySpinner = new Spinner<>(1, 28, !hasDefaults ? 1 : existing.getDueDayOfMonth());
        daySpinner.setEditable(true);
        daySpinner.setMaxWidth(Double.MAX_VALUE);

        // ── Start date ────────────────────────────────────────────────────────
        DatePicker startPicker = UiUtils.createDatePicker(!hasDefaults ? LocalDate.now()
                : (existing.getStartDate() != null ? existing.getStartDate() : LocalDate.now()));

        // ── Amount ────────────────────────────────────────────────────────────
        TextField amtFld = new TextField(!hasDefaults ? ""
                : (existing.getAmountPaise() > 0
                ? String.format("%.2f", existing.getAmountPaise() / 100.0) : ""));
        amtFld.setPromptText("Leave blank for variable (e.g. CC Payment)");
        amtFld.setMaxWidth(Double.MAX_VALUE);

        // ── Payment Mode ──────────────────────────────────────────────────────
        ComboBox<String> payModeCb = new ComboBox<>();
        payModeCb.setMaxWidth(Double.MAX_VALUE);
        payModeCb.getItems().addAll("UPI", "Net Banking", "Debit Card", "Credit Card",
                "Cash", "Cheque", "Auto-debit", "Internal Transfer");
        payModeCb.setValue("Net Banking");

        // ── From Account (contents vary by type) ──────────────────────────────
        DataStore ds = DataStore.getInstance();
        ComboBox<Account> accountCb = new ComboBox<>();
        accountCb.setMaxWidth(Double.MAX_VALUE);
        AccountCombos.style(accountCb);

        // ── Investment type-specific fields and destination combo ─────────────
        InvestmentRecurringPanel invPanel = new InvestmentRecurringPanel(ds);

        // ── To Account controls ───────────────────────────────────────────────
        ComboBox<Account> transferToCb = new ComboBox<>();
        transferToCb.setPromptText("Select destination account");
        transferToCb.setMaxWidth(Double.MAX_VALUE);
        ds.getBankAccounts().forEach(transferToCb.getItems()::add);
        AccountCombos.style(transferToCb);

        ComboBox<Account> ccpCardCb = new ComboBox<>();
        ccpCardCb.setPromptText("Select credit card");
        ccpCardCb.setMaxWidth(Double.MAX_VALUE);
        ds.getCreditCardAccounts().forEach(ccpCardCb.getItems()::add);
        AccountCombos.style(ccpCardCb);

        ComboBox<Account> loanToCb = new ComboBox<>();
        loanToCb.setPromptText("Select loan account");
        loanToCb.setMaxWidth(Double.MAX_VALUE);
        ds.getActiveLoanAccounts().forEach(loanToCb.getItems()::add);
        AccountCombos.style(loanToCb);

        if (hasDefaults) invPanel.prefill(existing);

        // ── Dynamic containers ────────────────────────────────────────────────
        VBox toAccountSection = new VBox(0);
        toAccountSection.setVisible(false);
        toAccountSection.setManaged(false);

        // ── Account label (needs a reference so refreshToAccount can relabel it) ─
        Label accountLbl = new Label("From Account");
        accountLbl.getStyleClass().add("form-label");
        accountLbl.setMinWidth(145);

        // ── Category / Sub-category (declared here so refreshToAccount can reload them) ──
        List<Category> catMaster = new ArrayList<>(ds.getExpenseCategories());
        List<Category> subMaster = new ArrayList<>();

        ComboBox<Category> catCb = new ComboBox<>();
        catCb.setMaxWidth(Double.MAX_VALUE);
        catCb.setPromptText("Select category (optional)");
        catCb.getItems().setAll(catMaster);
        UiUtils.wireAutoComplete(catCb, catMaster);

        ComboBox<Category> subCatCb = new ComboBox<>();
        subCatCb.setMaxWidth(Double.MAX_VALUE);
        subCatCb.setPromptText("Select sub-category (optional)");
        subCatCb.setVisible(false);
        subCatCb.setManaged(false);
        CategoryComboWiring.wire(catCb, subCatCb, subMaster, ds);
        UiUtils.wireAutoComplete(subCatCb, subMaster);

        // ── Source Investment fields (INCOME + Interest category only) ───────
        Set<String> redeemedRefs = ds.getTransactions().stream()
                .filter(t -> t.getRedeemDetails() != null && t.getRedeemDetails().getOrgnlFDRef() != null)
                .map(t -> t.getRedeemDetails().getOrgnlFDRef())
                .collect(Collectors.toSet());

        ComboBox<InvestmentAccount> srcInvestCb = new ComboBox<>();
        srcInvestCb.setMaxWidth(Double.MAX_VALUE);
        srcInvestCb.setPromptText("Select bond or FD account");
        ds.getInvestmentAccounts().stream()
                .filter(ia -> ia.getInvestmentStatus() != InvestmentAccount.InvestmentStatus.REDEEMED
                        && (ia.getInvestmentType() == InvestmentAccount.InvestmentType.DEBT_BONDS
                         || ia.getInvestmentType() == InvestmentAccount.InvestmentType.FIXED_DEPOSIT))
                .forEach(srcInvestCb.getItems()::add);
        AccountCombos.style(srcInvestCb);

        ComboBox<String> srcRefCb = new ComboBox<>();
        srcRefCb.setMaxWidth(Double.MAX_VALUE);
        srcRefCb.setPromptText("Select reference (optional)");

        srcInvestCb.setOnAction(e -> {
            srcRefCb.getItems().clear();
            srcRefCb.setValue(null);
            InvestmentAccount ia = srcInvestCb.getValue();
            if (ia == null) return;
            List<String> refs = ds.getTransactions().stream()
                    .filter(t -> t.getType() == Transaction.Type.INVESTMENT
                              && ia.getId().equals(t.getToAccountId())
                              && t.getInvestmentDetails() != null
                              && t.getInvestmentDetails().getFd() != null
                              && t.getInvestmentDetails().getFd().getRef() != null
                              && !t.getInvestmentDetails().getFd().getRef().isBlank()
                              && !redeemedRefs.contains(t.getInvestmentDetails().getFd().getRef()))
                    .map(t -> t.getInvestmentDetails().getFd().getRef())
                    .distinct().sorted()
                    .collect(Collectors.toList());
            srcRefCb.getItems().setAll(refs);
        });

        GridPane sg = miniGrid();
        UiUtils.addFormRow(sg, 0, "Source Investment", srcInvestCb);
        UiUtils.addFormRow(sg, 1, "Reference No.",     srcRefCb);
        VBox srcInvestBox = new VBox(8, sg);
        srcInvestBox.setVisible(false);
        srcInvestBox.setManaged(false);

        Runnable refreshSrcInvest = () -> {
            boolean show = typeCb.getValue() == Transaction.Type.INCOME
                    && catCb.getValue() != null
                    && catCb.getValue().getName().toLowerCase().contains("interest");
            srcInvestBox.setVisible(show);
            srcInvestBox.setManaged(show);
            if (!show) {
                srcInvestCb.setValue(null);
                srcRefCb.getItems().clear();
                srcRefCb.setValue(null);
            }
        };

        catCb.valueProperty().addListener((obs, old, sel) -> refreshSrcInvest.run());

        Runnable refreshToAccount = () -> {
            Transaction.Type t = typeCb.getValue();

            // Relabel the account field
            accountLbl.setText(t == Transaction.Type.INCOME ? "To Account" : "From Account");

            // Reload categories for the selected type
            List<Category> freshCats = t == Transaction.Type.INCOME
                    ? ds.getIncomeCategories()
                    : ds.getExpenseCategories();
            catMaster.clear();
            catMaster.addAll(freshCats);
            catCb.getItems().setAll(catMaster);
            catCb.setValue(null);
            subCatCb.setValue(null);
            subCatCb.setVisible(false);
            subCatCb.setManaged(false);

            Account prevFrom = accountCb.getValue();
            accountCb.getItems().clear();
            if (t == Transaction.Type.EXPENSE) {
                ds.getBankAccounts().forEach(accountCb.getItems()::add);
                ds.getCreditCardAccounts().forEach(accountCb.getItems()::add);
            } else {
                ds.getBankAccounts().forEach(accountCb.getItems()::add);
            }
            if (prevFrom != null && accountCb.getItems().contains(prevFrom)) {
                accountCb.setValue(prevFrom);
            } else if (hasDefaults) {
                String lookupId = t == Transaction.Type.INCOME
                        ? existing.getToAccountId() : existing.getFromAccountId();
                if (lookupId != null) {
                    accountCb.getItems().stream()
                            .filter(a -> a.getId().equals(lookupId))
                            .findFirst().ifPresent(accountCb::setValue);
                }
            }
            if (accountCb.getValue() == null && !accountCb.getItems().isEmpty() && !hasDefaults)
                accountCb.setValue(accountCb.getItems().get(0));

            boolean showTo = t == Transaction.Type.TRANSFER
                          || t == Transaction.Type.INVESTMENT
                          || t == Transaction.Type.CC_PAYMENT
                          || t == Transaction.Type.LOAN_PAYMENT;
            toAccountSection.getChildren().clear();
            invPanel.clearForTypeSwitch();
            toAccountSection.setVisible(showTo);
            toAccountSection.setManaged(showTo);
            if (!showTo) return;

            GridPane tg = miniGrid();
            if (t == Transaction.Type.TRANSFER) {
                UiUtils.addFormRow(tg, 0, "To Account", transferToCb);
            } else if (t == Transaction.Type.INVESTMENT) {
                UiUtils.addFormRow(tg, 0, "To Account",      invPanel.getDestCombo());
                UiUtils.addFormRow(tg, 1, "Investment Type", invPanel.getTypeLbl());
            } else if (t == Transaction.Type.CC_PAYMENT) {
                UiUtils.addFormRow(tg, 0, "To Account", ccpCardCb);
            } else {
                UiUtils.addFormRow(tg, 0, "To Account", loanToCb);
            }
            toAccountSection.getChildren().add(tg);
            if (t == Transaction.Type.INVESTMENT) invPanel.triggerRefresh();
        };

        // Auto-default payment mode: Expense + CC account → "Credit Card", else "Net Banking"
        Runnable refreshPayMode = () -> {
            if (typeCb.getValue() == Transaction.Type.EXPENSE
                    && accountCb.getValue() instanceof CreditCardAccount) {
                payModeCb.setValue("Credit Card");
            } else {
                payModeCb.setValue("Net Banking");
            }
        };
        accountCb.valueProperty().addListener((obs, old, acct) -> refreshPayMode.run());

        typeCb.setOnAction(e -> { refreshToAccount.run(); refreshSrcInvest.run(); refreshPayMode.run(); });

        // ── Auto-record ───────────────────────────────────────────────────────
        AutoRecordSettingsPanel autoRecordPanel = new AutoRecordSettingsPanel();
        if (hasDefaults) autoRecordPanel.prefill(existing.getAutoRecordAfterDays());

        // ── Number of payments ─────────────────────────────────────────────────
        TextField numPaymentsFld = new TextField();
        numPaymentsFld.setPromptText("Blank = no limit (runs indefinitely)");
        numPaymentsFld.setMaxWidth(Double.MAX_VALUE);
        if (hasDefaults && existing.getNumberOfPayments() != null)
            numPaymentsFld.setText(String.valueOf(existing.getNumberOfPayments()));

        Label lastPaymentHint = new Label();
        lastPaymentHint.getStyleClass().add("text-hint");
        lastPaymentHint.setVisible(false);
        lastPaymentHint.setManaged(false);

        Runnable updateLastPaymentHint = () -> {
            String raw = numPaymentsFld.getText().trim();
            LocalDate start = startPicker.getValue();
            RecurringTransaction.Frequency freq = freqCb.getValue();
            if (raw.isEmpty() || start == null || freq == null) {
                lastPaymentHint.setVisible(false); lastPaymentHint.setManaged(false); return;
            }
            try {
                int n = Integer.parseInt(raw);
                if (n <= 0) { lastPaymentHint.setVisible(false); lastPaymentHint.setManaged(false); return; }
                LocalDate d = start;
                for (int i = 1; i < n; i++) d = switch (freq) {
                    case MONTHLY        -> d.plusMonths(1);
                    case QUARTERLY      -> d.plusMonths(3);
                    case HALF_YEARLY    -> d.plusMonths(6);
                    case ANNUALLY       -> d.plusYears(1);
                    case ALTERNATE_YEAR -> d.plusYears(2);
                };
                int actualDay = Math.min(daySpinner.getValue(), d.lengthOfMonth());
                d = d.withDayOfMonth(actualDay);
                lastPaymentHint.setText("Last payment: " + d.format(ds.getDateFormatter()));
                lastPaymentHint.setVisible(true); lastPaymentHint.setManaged(true);
            } catch (NumberFormatException e) {
                lastPaymentHint.setVisible(false); lastPaymentHint.setManaged(false);
            }
        };

        numPaymentsFld.textProperty().addListener((obs, o, n) -> updateLastPaymentHint.run());
        startPicker.valueProperty().addListener((obs, o, n) -> updateLastPaymentHint.run());
        freqCb.valueProperty().addListener((obs, o, n) -> updateLastPaymentHint.run());
        daySpinner.valueProperty().addListener((obs, o, n) -> updateLastPaymentHint.run());
        updateLastPaymentHint.run();

        // ── Pre-select to-account when editing ────────────────────────────────
        if (hasDefaults && existing.getToAccountId() != null) {
            Transaction.Type t = existing.getTransactionType();
            if (t == Transaction.Type.TRANSFER) {
                ds.getBankAccounts().stream()
                        .filter(a -> a.getId().equals(existing.getToAccountId()))
                        .findFirst().ifPresent(transferToCb::setValue);
            } else if (t == Transaction.Type.INVESTMENT) {
                ds.getInvestmentAccounts().stream()
                        .filter(a -> a.getId().equals(existing.getToAccountId()))
                        .findFirst().ifPresent(invPanel.getDestCombo()::setValue);
            } else if (t == Transaction.Type.CC_PAYMENT) {
                ds.getCreditCardAccounts().stream()
                        .filter(a -> a.getId().equals(existing.getToAccountId()))
                        .findFirst().ifPresent(ccpCardCb::setValue);
            } else if (t == Transaction.Type.LOAN_PAYMENT) {
                ds.getActiveLoanAccounts().stream()
                        .filter(a -> a.getId().equals(existing.getToAccountId()))
                        .findFirst().ifPresent(loanToCb::setValue);
            }
        }

        // Trigger initial state (also populates accountCb)
        refreshToAccount.run();

        // Restore saved payment mode when editing (overrides the smart default)
        if (hasDefaults && existing.getPaymentMode() != null) {
            String modeStr = existing.getPaymentMode().name().replace('_', ' ');
            payModeCb.getItems().stream()
                    .filter(item -> item.equalsIgnoreCase(modeStr))
                    .findFirst().ifPresent(payModeCb::setValue);
        }

        if (hasDefaults && existing.getCategoryId() != null) {
            catMaster.stream()
                    .filter(c -> c.getId().equals(existing.getCategoryId()))
                    .findFirst().ifPresent(catCb::setValue);
            if (existing.getSubCategoryId() != null) {
                subMaster.stream()
                        .filter(s -> s.getId().equals(existing.getSubCategoryId()))
                        .findFirst().ifPresent(subCatCb::setValue);
            }
        }

        // Prefill source investment (triggers refreshSrcInvest via catCb listener above)
        if (hasDefaults && existing.getSourceInvestment() != null) {
            String srcAccId = existing.getSourceInvestment().getSrcAccount();
            srcInvestCb.getItems().stream()
                    .filter(ia -> ia.getId().equals(srcAccId))
                    .findFirst().ifPresent(ia -> {
                        srcInvestCb.setValue(ia); // triggers ref list reload via setOnAction
                        String refId = existing.getSourceInvestment().getRefId();
                        if (refId != null) srcRefCb.setValue(refId);
                    });
        }

        // ── Layout ────────────────────────────────────────────────────────────
        int row = 0;
        UiUtils.addFormRow(g, row++, "Description*",     descFld);
        UiUtils.addFormRow(g, row++, "Type",             typeCb);
        UiUtils.addFormRow(g, row++, "Frequency",        freqCb);
        UiUtils.addFormRow(g, row++, "Due Day of Month", daySpinner);
        UiUtils.addFormRow(g, row++, "Start Date",       startPicker);
        UiUtils.addFormRow(g, row++, "Amount (₹)",       amtFld);
        VBox numPaymentsBox = new VBox(4, numPaymentsFld, lastPaymentHint);
        UiUtils.addFormRow(g, row++, "No. of Payments", numPaymentsBox);
        g.add(accountLbl, 0, row); g.add(accountCb, 1, row); GridPane.setFillWidth(accountCb, true); row++;
        UiUtils.addFormRow(g, row++, "Payment Mode",     payModeCb);
        g.add(toAccountSection,              0, row++, 2, 1);
        g.add(invPanel.getDynamicBox(),      0, row++, 2, 1);
        UiUtils.addFormRow(g, row++, "Category",         catCb);
        UiUtils.addFormRow(g, row++, "Sub-category",     subCatCb);
        g.add(srcInvestBox,              0, row++, 2, 1);
        g.add(autoRecordPanel.getView(), 0, row,   2, 1);

        ScrollPane sp = new ScrollPane(g);
        sp.setFitToWidth(true);
        sp.setPrefHeight(645);
        sp.getStyleClass().add("scroll-transparent");
        dlg.getDialogPane().setContent(sp);

        ButtonType saveBtn = UiUtils.addSaveCancel(dlg.getDialogPane());

        // Validate on Save click — consume event to keep dialog open on failure
        Platform.runLater(() -> {
            Button btn = (Button) dlg.getDialogPane().lookupButton(saveBtn);
            if (btn == null) return;
            btn.addEventFilter(ActionEvent.ACTION, ev -> {
                String desc = descFld.getText().trim();
                if (desc.isEmpty()) {
                    alert("Validation Error", "Description is required."); ev.consume(); return;
                }
                Transaction.Type type = typeCb.getValue();
                if (type == Transaction.Type.TRANSFER && transferToCb.getValue() == null) {
                    alert("Validation Error", "Select a destination account for the transfer."); ev.consume(); return;
                }
                if (type == Transaction.Type.TRANSFER
                        && accountCb.getValue() != null && transferToCb.getValue() != null
                        && accountCb.getValue().getId().equals(transferToCb.getValue().getId())) {
                    alert("Validation Error", "From and To accounts must differ."); ev.consume(); return;
                }
                if (type == Transaction.Type.INVESTMENT && invPanel.getDestCombo().getValue() == null) {
                    alert("Validation Error", "Select a destination investment account."); ev.consume(); return;
                }
                if (type == Transaction.Type.INVESTMENT && invPanel.isRd() && invPanel.isRdRefBlank()) {
                    alert("Validation Error", "RD Reference No is required for Recurring Deposit schedules."); ev.consume(); return;
                }
                if (type == Transaction.Type.CC_PAYMENT && ccpCardCb.getValue() == null) {
                    alert("Validation Error", "Select a credit card for the payment."); ev.consume(); return;
                }
                if (type == Transaction.Type.LOAN_PAYMENT && loanToCb.getValue() == null) {
                    alert("Validation Error", "Select a loan account for the payment."); ev.consume(); return;
                }
                String amtRaw = amtFld.getText().trim().replace(",", "").replace(MoneyFormatter.symbol(), "");
                if (!amtRaw.isEmpty()) {
                    try { Double.parseDouble(amtRaw); }
                    catch (NumberFormatException e) {
                        alert("Validation Error", "Invalid amount."); ev.consume(); return;
                    }
                }
                if (autoRecordPanel.isEnabled() && amtRaw.isEmpty()) {
                    alert("Validation Error",
                            "Auto-record requires a fixed amount. Enter an amount or uncheck auto-record.");
                    ev.consume(); return;
                }
                String numPayRaw = numPaymentsFld.getText().trim();
                if (!numPayRaw.isEmpty()) {
                    try {
                        if (Integer.parseInt(numPayRaw) <= 0) {
                            alert("Validation Error", "No. of Payments must be a positive number."); ev.consume();
                        }
                    } catch (NumberFormatException e) {
                        alert("Validation Error", "No. of Payments must be a whole number."); ev.consume();
                    }
                }
            });
        });

        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;

            String desc = descFld.getText().trim();
            Transaction.Type type               = typeCb.getValue();
            RecurringTransaction.Frequency freq = freqCb.getValue();
            int day   = daySpinner.getValue();
            LocalDate start = startPicker.getValue() != null ? startPicker.getValue() : LocalDate.now();

            long paise = 0;
            String amtRaw = amtFld.getText().trim().replace(",", "").replace(MoneyFormatter.symbol(), "");
            if (!amtRaw.isEmpty()) {
                try { paise = Math.round(Double.parseDouble(amtRaw) * 100); }
                catch (NumberFormatException ignored) {}
            }

            String toAccountId = null;
            if (type == Transaction.Type.TRANSFER && transferToCb.getValue() != null)
                toAccountId = transferToCb.getValue().getId();
            else if (type == Transaction.Type.INVESTMENT && invPanel.getDestCombo().getValue() != null)
                toAccountId = invPanel.getDestCombo().getValue().getId();
            else if (type == Transaction.Type.CC_PAYMENT && ccpCardCb.getValue() != null)
                toAccountId = ccpCardCb.getValue().getId();
            else if (type == Transaction.Type.LOAN_PAYMENT && loanToCb.getValue() != null)
                toAccountId = loanToCb.getValue().getId();

            // For INCOME, accountCb is the destination bank account → toAccountId.
            // For all other types, accountCb is the source account → fromAccountId.
            String mainAccountId = accountCb.getValue() != null ? accountCb.getValue().getId() : null;
            String fromAccountId = type == Transaction.Type.INCOME ? null : mainAccountId;
            if (type == Transaction.Type.INCOME) toAccountId = mainAccountId;
            int autoRecordDays = autoRecordPanel.getAutoRecordDays();

            Integer numPayments = null;
            String numPayRaw = numPaymentsFld.getText().trim();
            if (!numPayRaw.isEmpty()) {
                try { int n = Integer.parseInt(numPayRaw); if (n > 0) numPayments = n; }
                catch (NumberFormatException ignored) {}
            }

            if (isNew) {
                RecurringTransaction r = new RecurringTransaction(desc, type, freq, day, start, paise);
                r.setFromAccountId(fromAccountId);
                r.setToAccountId(toAccountId);
                if (catCb.getValue() != null)    r.setCategoryId(catCb.getValue().getId());
                if (subCatCb.getValue() != null) r.setSubCategoryId(subCatCb.getValue().getId());
                invPanel.applyTo(r, type);
                r.setAutoRecordAfterDays(autoRecordDays);
                r.setNumberOfPayments(numPayments);
                r.setSourceInvestment(buildSourceInvestment(srcInvestBox, srcInvestCb, srcRefCb));
                r.setPaymentMode(parsePayMode(payModeCb.getValue()));
                r.setStatus(RecurringTransaction.Status.ACTIVE);
                ds.addRecurring(r);
            } else {
                existing.setDescription(desc);
                existing.setTransactionType(type);
                existing.setFrequency(freq);
                existing.setDueDayOfMonth(day);
                existing.setStartDate(start);
                existing.setAmountPaise(paise);
                existing.setFromAccountId(fromAccountId);
                existing.setToAccountId(toAccountId);
                existing.setCategoryId(catCb.getValue() != null ? catCb.getValue().getId() : null);
                existing.setSubCategoryId(subCatCb.getValue() != null ? subCatCb.getValue().getId() : null);
                invPanel.applyTo(existing, type);
                existing.setAutoRecordAfterDays(autoRecordDays);
                existing.setNumberOfPayments(numPayments);
                existing.setSourceInvestment(buildSourceInvestment(srcInvestBox, srcInvestCb, srcRefCb));
                existing.setPaymentMode(parsePayMode(payModeCb.getValue()));
                ds.saveRecurringNow();
            }
            return null;
        });

        dlg.showAndWait();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static GridPane miniGrid() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setPadding(new Insets(0, 0, 4, 0));
        ColumnConstraints c1 = new ColumnConstraints(150);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);
        return g;
    }

    private static Transaction.PaymentMode parsePayMode(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Transaction.PaymentMode.valueOf(s.replace(' ', '_').toUpperCase()); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static RecurringTransaction.SourceInvestment buildSourceInvestment(
            VBox srcInvestBox, ComboBox<InvestmentAccount> srcInvestCb, ComboBox<String> srcRefCb) {
        if (!srcInvestBox.isVisible() || srcInvestCb.getValue() == null) return null;
        RecurringTransaction.SourceInvestment si = new RecurringTransaction.SourceInvestment();
        si.setSrcAccount(srcInvestCb.getValue().getId());
        si.setRefId(srcRefCb.getValue()); // may be null if no ref selected
        return si;
    }


    private static String formatFrequency(RecurringTransaction.Frequency f) {
        if (f == null) return "—";
        return switch (f) {
            case MONTHLY        -> "Monthly";
            case QUARTERLY      -> "Quarterly";
            case HALF_YEARLY    -> "Half Yearly";
            case ANNUALLY       -> "Annually";
            case ALTERNATE_YEAR -> "Alternate Year";
        };
    }

    private static void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        UiUtils.applyStylesheet(a);
        a.showAndWait();
    }
}
