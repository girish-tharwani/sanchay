package com.sanchay.ui.recurring;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
//import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

        // ── From Account (contents vary by type) ──────────────────────────────
        DataStore ds = DataStore.getInstance();
        ComboBox<Account> accountCb = new ComboBox<>();
        accountCb.setMaxWidth(Double.MAX_VALUE);

        // ── To Account controls ───────────────────────────────────────────────
        ComboBox<Account> transferToCb = new ComboBox<>();
        transferToCb.setPromptText("Select destination account");
        transferToCb.setMaxWidth(Double.MAX_VALUE);
        ds.getBankAccounts().forEach(transferToCb.getItems()::add);

        ComboBox<InvestmentAccount> invDestCb = new ComboBox<>();
        invDestCb.setPromptText("Select investment account");
        invDestCb.setMaxWidth(Double.MAX_VALUE);
        ds.getInvestmentAccounts().forEach(invDestCb.getItems()::add);
        invDestCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(InvestmentAccount item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + "  (" + item.getAccountType() + ")");
            }
        });
        invDestCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(InvestmentAccount item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + "  (" + item.getAccountType() + ")");
            }
        });

        Label invTypeLbl = new Label("—");
        // Inline required: needs italic + hint colour (text-hint class has no italic variant)
        invTypeLbl.setStyle("-fx-text-fill: -text-hint; -fx-font-style: italic;");

        ComboBox<Account> ccpCardCb = new ComboBox<>();
        ccpCardCb.setPromptText("Select credit card");
        ccpCardCb.setMaxWidth(Double.MAX_VALUE);
        ds.getCreditCardAccounts().forEach(ccpCardCb.getItems()::add);

        ComboBox<Account> loanToCb = new ComboBox<>();
        loanToCb.setPromptText("Select loan account");
        loanToCb.setMaxWidth(Double.MAX_VALUE);
        ds.getActiveLoanAccounts().forEach(loanToCb.getItems()::add);

        // ── Investment type-specific fields ───────────────────────────────────
        TextField invSchemeFld        = new TextField();
        invSchemeFld.setPromptText("e.g. HDFC Flexi Cap Fund, RELIANCE (optional)");
        invSchemeFld.setMaxWidth(Double.MAX_VALUE);

        TextField invUnitsFld         = new TextField();
        invUnitsFld.setPromptText("Units / NAV at investment time (optional)");
        invUnitsFld.setMaxWidth(Double.MAX_VALUE);

        TextField invFdRefFld         = new TextField();
        invFdRefFld.setPromptText("Reference number (optional)");
        invFdRefFld.setMaxWidth(Double.MAX_VALUE);

        TextField invFdRateFld        = new TextField();
        invFdRateFld.setPromptText("Annual interest rate, e.g. 6.5");
        invFdRateFld.setMaxWidth(Double.MAX_VALUE);

        DatePicker invFdMaturityPicker = UiUtils.createDatePicker(null);
        invFdMaturityPicker.setPromptText("Maturity date");

        TextField invFdMaturityAmtFld = new TextField();
        invFdMaturityAmtFld.setPromptText("Expected maturity amount (optional)");
        invFdMaturityAmtFld.setMaxWidth(Double.MAX_VALUE);

        TextField invRdRefFld         = new TextField();
        invRdRefFld.setPromptText("e.g. HDFC-RD-2024 (required)");
        invRdRefFld.setMaxWidth(Double.MAX_VALUE);

        TextField invRdRateFld        = new TextField();
        invRdRateFld.setPromptText("Annual interest rate, e.g. 7.0");
        invRdRateFld.setMaxWidth(Double.MAX_VALUE);

        DatePicker invRdMaturityPicker = UiUtils.createDatePicker(null);
        invRdMaturityPicker.setPromptText("Maturity date");

        TextField invRdOpeningBalFld = new TextField();
        invRdOpeningBalFld.setPromptText("Total instalments paid before setup (optional)");
        invRdOpeningBalFld.setMaxWidth(Double.MAX_VALUE);

        TextField invRdMaturityAmtFld = new TextField();
        invRdMaturityAmtFld.setPromptText("Expected maturity amount (optional)");
        invRdMaturityAmtFld.setMaxWidth(Double.MAX_VALUE);

        if (hasDefaults && existing.getRdOpeningBalancePaise() > 0)
            invRdOpeningBalFld.setText(String.format("%.2f", existing.getRdOpeningBalancePaise() / 100.0));
        if (hasDefaults && existing.getRdMaturityAmountPaise() > 0)
            invRdMaturityAmtFld.setText(String.format("%.2f", existing.getRdMaturityAmountPaise() / 100.0));
        if (hasDefaults) {
            if (existing.getSchemeScript() != null) invSchemeFld.setText(existing.getSchemeScript());
            if (existing.getUnitsNav()     != null) invUnitsFld.setText(existing.getUnitsNav());
            if (existing.getFdRef()        != null) invFdRefFld.setText(existing.getFdRef());
            if (existing.getRdRef()        != null) invRdRefFld.setText(existing.getRdRef());
            if (existing.getInterestRate() != null) {
                String rateStr = existing.getInterestRate().toString();
                invFdRateFld.setText(rateStr);
                invRdRateFld.setText(rateStr);
            }
            if (existing.getMaturityDate() != null) {
                invFdMaturityPicker.setValue(existing.getMaturityDate());
                invRdMaturityPicker.setValue(existing.getMaturityDate());
            }
            if (existing.getFdMaturityAmountPaise() > 0)
                invFdMaturityAmtFld.setText(String.format("%.2f", existing.getFdMaturityAmountPaise() / 100.0));
        }

        // ── Dynamic containers ────────────────────────────────────────────────
        VBox toAccountSection = new VBox(0);
        toAccountSection.setVisible(false);
        toAccountSection.setManaged(false);

        VBox invDynamicBox = new VBox(0);
        invDynamicBox.setVisible(false);
        invDynamicBox.setManaged(false);

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
        subCatCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "  └ " + item.getName());
                setStyle(empty || item == null ? "" : "-fx-text-fill: -text-label;");
            }
        });
        subCatCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "  └ " + item.getName());
            }
        });
        UiUtils.wireAutoComplete(subCatCb, subMaster);

        catCb.valueProperty().addListener((obs, old, sel) -> {
            subCatCb.getItems().clear();
            subCatCb.setValue(null);
            subCatCb.getEditor().clear();
            subMaster.clear();
            if (sel != null) {
                List<Category> subs = ds.getSubCategories(sel.getId());
                if (!subs.isEmpty()) {
                    subMaster.addAll(subs);
                    subCatCb.getItems().setAll(subs);
                    subCatCb.setVisible(true);
                    subCatCb.setManaged(true);
                } else {
                    subCatCb.setVisible(false);
                    subCatCb.setManaged(false);
                }
            } else {
                subCatCb.setVisible(false);
                subCatCb.setManaged(false);
            }
        });

        // ── Refresh logic ─────────────────────────────────────────────────────
        Runnable refreshInvFields = () -> {
            invDynamicBox.getChildren().clear();
            InvestmentAccount sel = invDestCb.getValue();
            if (sel == null) {
                invTypeLbl.setText("—");
                invDynamicBox.setVisible(false);
                invDynamicBox.setManaged(false);
                return;
            }
            invTypeLbl.setText(sel.getAccountType());
            GridPane dg = miniGrid();
            switch (sel.getInvestmentType()) {
                case MUTUAL_FUNDS, EQUITY -> {
                    UiUtils.addFormRow(dg, 0, "Scheme / Script", invSchemeFld);
                    UiUtils.addFormRow(dg, 1, "Units / NAV",     invUnitsFld);
                }
                case FIXED_DEPOSIT, DEBT_BONDS -> {
                    UiUtils.addFormRow(dg, 0, "Reference No",      invFdRefFld);
                    UiUtils.addFormRow(dg, 1, "Interest Rate (%)", invFdRateFld);
                    UiUtils.addFormRow(dg, 2, "Maturity Date",     invFdMaturityPicker);
                    UiUtils.addFormRow(dg, 3, "Maturity Amount",   invFdMaturityAmtFld);
                }
                case RECURRING_DEPOSIT -> {
                    UiUtils.addFormRow(dg, 0, "RD Reference No*",   invRdRefFld);
                    UiUtils.addFormRow(dg, 1, "Interest Rate (%)",  invRdRateFld);
                    UiUtils.addFormRow(dg, 2, "Maturity Date",      invRdMaturityPicker);
                    UiUtils.addFormRow(dg, 3, "Opening Balance (₹)", invRdOpeningBalFld);
                    UiUtils.addFormRow(dg, 4, "Maturity Amount (₹)", invRdMaturityAmtFld);
                }
                case PROVIDENT_FUND -> { /* no additional fields */ }
            }
            invDynamicBox.getChildren().add(dg);
            invDynamicBox.setVisible(true);
            invDynamicBox.setManaged(true);
        };

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
            } else if (hasDefaults && existing.getFromAccountId() != null) {
                accountCb.getItems().stream()
                        .filter(a -> a.getId().equals(existing.getFromAccountId()))
                        .findFirst().ifPresent(accountCb::setValue);
            }
            if (accountCb.getValue() == null && !accountCb.getItems().isEmpty())
                accountCb.setValue(accountCb.getItems().get(0));

            boolean showTo = t == Transaction.Type.TRANSFER
                          || t == Transaction.Type.INVESTMENT
                          || t == Transaction.Type.CC_PAYMENT
                          || t == Transaction.Type.LOAN_PAYMENT;
            toAccountSection.getChildren().clear();
            invDynamicBox.getChildren().clear();
            invDynamicBox.setVisible(false);
            invDynamicBox.setManaged(false);
            invTypeLbl.setText("—");
            toAccountSection.setVisible(showTo);
            toAccountSection.setManaged(showTo);
            if (!showTo) return;

            GridPane tg = miniGrid();
            if (t == Transaction.Type.TRANSFER) {
                UiUtils.addFormRow(tg, 0, "To Account", transferToCb);
            } else if (t == Transaction.Type.INVESTMENT) {
                UiUtils.addFormRow(tg, 0, "To Account",      invDestCb);
                UiUtils.addFormRow(tg, 1, "Investment Type", invTypeLbl);
            } else if (t == Transaction.Type.CC_PAYMENT) {
                UiUtils.addFormRow(tg, 0, "To Account", ccpCardCb);
            } else {
                UiUtils.addFormRow(tg, 0, "To Account", loanToCb);
            }
            toAccountSection.getChildren().add(tg);
            if (t == Transaction.Type.INVESTMENT) refreshInvFields.run();
        };

        typeCb.setOnAction(e -> refreshToAccount.run());
        invDestCb.setOnAction(e -> refreshInvFields.run());

        // ── Auto-record ───────────────────────────────────────────────────────
        CheckBox autoRecordCb = new CheckBox("Auto-record after");
        Spinner<Integer> autoRecordDaysSp = new Spinner<>(1, 30, 3);
        autoRecordDaysSp.setPrefWidth(70);
        autoRecordDaysSp.setDisable(true);
        Label autoRecordSuffix = new Label("days overdue");
        autoRecordSuffix.getStyleClass().add("text-hint");
        HBox autoRecordBox = new HBox(8, autoRecordCb, autoRecordDaysSp, autoRecordSuffix);
        autoRecordBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        autoRecordCb.selectedProperty().addListener((obs, o, n) -> autoRecordDaysSp.setDisable(!n));

        if (hasDefaults && existing.getAutoRecordAfterDays() > 0) {
            autoRecordCb.setSelected(true);
            autoRecordDaysSp.getValueFactory().setValue(existing.getAutoRecordAfterDays());
        }

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
                        .findFirst().ifPresent(invDestCb::setValue);
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
        g.add(toAccountSection, 0, row++, 2, 1);
        g.add(invDynamicBox,    0, row++, 2, 1);
        UiUtils.addFormRow(g, row++, "Category",         catCb);
        UiUtils.addFormRow(g, row++, "Sub-category",     subCatCb);
        g.add(autoRecordBox,    0, row,   2, 1);

        ScrollPane sp = new ScrollPane(g);
        sp.setFitToWidth(true);
        sp.setPrefHeight(580);
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
                if (type == Transaction.Type.INVESTMENT && invDestCb.getValue() == null) {
                    alert("Validation Error", "Select a destination investment account."); ev.consume(); return;
                }
                if (type == Transaction.Type.INVESTMENT && invDestCb.getValue() != null
                        && invDestCb.getValue().getInvestmentType() == InvestmentAccount.InvestmentType.RECURRING_DEPOSIT
                        && invRdRefFld.getText().trim().isEmpty()) {
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
                if (autoRecordCb.isSelected() && amtRaw.isEmpty()) {
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
            else if (type == Transaction.Type.INVESTMENT && invDestCb.getValue() != null)
                toAccountId = invDestCb.getValue().getId();
            else if (type == Transaction.Type.CC_PAYMENT && ccpCardCb.getValue() != null)
                toAccountId = ccpCardCb.getValue().getId();
            else if (type == Transaction.Type.LOAN_PAYMENT && loanToCb.getValue() != null)
                toAccountId = loanToCb.getValue().getId();

            // Investment sub-type field values — collected here, applied to record below
            String  invSchemeScript = null, invUnitsNav = null;
            String  invFdRef = null, invRdRefVal = null;
            Double  invInterestRate = null;
            LocalDate invMaturityDate = null;
            long    invFdMatAmt = 0;
            if (type == Transaction.Type.INVESTMENT && invDestCb.getValue() != null) {
                switch (invDestCb.getValue().getInvestmentType()) {
                    case MUTUAL_FUNDS, EQUITY -> {
                        invSchemeScript = nullIfBlank(invSchemeFld.getText());
                        invUnitsNav     = nullIfBlank(invUnitsFld.getText());
                    }
                    case FIXED_DEPOSIT, DEBT_BONDS -> {
                        invFdRef        = nullIfBlank(invFdRefFld.getText());
                        invInterestRate = parseRate(invFdRateFld.getText());
                        invMaturityDate = invFdMaturityPicker.getValue();
                        String raw = invFdMaturityAmtFld.getText().trim().replace(",", "").replace(MoneyFormatter.symbol(), "");
                        if (!raw.isEmpty()) try { invFdMatAmt = Math.round(Double.parseDouble(raw) * 100); } catch (NumberFormatException ignored) {}
                    }
                    case RECURRING_DEPOSIT -> {
                        invRdRefVal     = nullIfBlank(invRdRefFld.getText());
                        invInterestRate = parseRate(invRdRateFld.getText());
                        invMaturityDate = invRdMaturityPicker.getValue();
                    }
                    default -> {}
                }
            }

            long rdOpenBal = 0, rdMatAmt = 0;
            boolean isRd = type == Transaction.Type.INVESTMENT && invDestCb.getValue() != null
                    && invDestCb.getValue().getInvestmentType() == InvestmentAccount.InvestmentType.RECURRING_DEPOSIT;
            if (isRd) {
                String raw = invRdOpeningBalFld.getText().trim().replace(",", "").replace(MoneyFormatter.symbol(), "");
                if (!raw.isEmpty()) try { rdOpenBal = Math.round(Double.parseDouble(raw) * 100); } catch (NumberFormatException ignored) {}
                raw = invRdMaturityAmtFld.getText().trim().replace(",", "").replace(MoneyFormatter.symbol(), "");
                if (!raw.isEmpty()) try { rdMatAmt = Math.round(Double.parseDouble(raw) * 100); } catch (NumberFormatException ignored) {}
            }

            String fromAccountId = accountCb.getValue() != null ? accountCb.getValue().getId() : null;
            int autoRecordDays   = autoRecordCb.isSelected() ? autoRecordDaysSp.getValue() : 0;

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
                r.setSchemeScript(invSchemeScript);
                r.setUnitsNav(invUnitsNav);
                r.setFdRef(invFdRef);
                r.setRdRef(invRdRefVal);
                r.setInterestRate(invInterestRate);
                r.setMaturityDate(invMaturityDate);
                r.setFdMaturityAmountPaise(invFdMatAmt);
                r.setRdOpeningBalancePaise(rdOpenBal);
                r.setRdMaturityAmountPaise(rdMatAmt);
                r.setAutoRecordAfterDays(autoRecordDays);
                r.setNumberOfPayments(numPayments);
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
                existing.setSchemeScript(invSchemeScript);
                existing.setUnitsNav(invUnitsNav);
                existing.setFdRef(invFdRef);
                existing.setRdRef(invRdRefVal);
                existing.setInterestRate(invInterestRate);
                existing.setMaturityDate(invMaturityDate);
                existing.setFdMaturityAmountPaise(invFdMatAmt);
                existing.setRdOpeningBalancePaise(rdOpenBal);
                existing.setRdMaturityAmountPaise(rdMatAmt);
                existing.setAutoRecordAfterDays(autoRecordDays);
                existing.setNumberOfPayments(numPayments);
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

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Double parseRate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
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
        a.showAndWait();
    }
}
