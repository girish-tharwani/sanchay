package com.sanchay.ui.recurring;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.scene.Node;
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
        boolean isNew = (existing == null);
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(isNew ? "New Recurring Schedule" : "Edit Recurring Schedule");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(520);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, isNew ? "+" : "✎", isNew ? "New Recurring Schedule" : "Edit Recurring Schedule");

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(16));
        ColumnConstraints c1 = new ColumnConstraints(150);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        // ── Description ───────────────────────────────────────────────────────
        TextField descFld = new TextField(isNew ? "" : existing.getDescription());
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
        typeCb.setValue(isNew ? Transaction.Type.EXPENSE : existing.getTransactionType());
        typeCb.setMaxWidth(Double.MAX_VALUE);

        // ── Frequency ─────────────────────────────────────────────────────────
        ComboBox<RecurringTransaction.Frequency> freqCb = new ComboBox<>();
        freqCb.getItems().addAll(RecurringTransaction.Frequency.values());
        freqCb.setValue(isNew ? RecurringTransaction.Frequency.MONTHLY : existing.getFrequency());
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
        Spinner<Integer> daySpinner = new Spinner<>(1, 28, isNew ? 1 : existing.getDueDayOfMonth());
        daySpinner.setMaxWidth(Double.MAX_VALUE);

        // ── Start date ────────────────────────────────────────────────────────
        DatePicker startPicker = new DatePicker(isNew ? LocalDate.now()
                : (existing.getStartDate() != null ? existing.getStartDate() : LocalDate.now()));
        startPicker.setMaxWidth(Double.MAX_VALUE);
        UiUtils.applySmartDateConverter(startPicker);
        UiUtils.styleOnShow(startPicker);

        // ── Amount ────────────────────────────────────────────────────────────
        TextField amtFld = new TextField(isNew ? ""
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
        invFdRefFld.setPromptText("FD reference number (optional)");
        invFdRefFld.setMaxWidth(Double.MAX_VALUE);

        TextField invFdRateFld        = new TextField();
        invFdRateFld.setPromptText("Annual interest rate, e.g. 6.5");
        invFdRateFld.setMaxWidth(Double.MAX_VALUE);

        DatePicker invFdMaturityPicker = new DatePicker();
        invFdMaturityPicker.setPromptText("Maturity date");
        invFdMaturityPicker.setMaxWidth(Double.MAX_VALUE);
        UiUtils.styleOnShow(invFdMaturityPicker);
        UiUtils.applySmartDateConverter(invFdMaturityPicker);

        TextField invFdMaturityAmtFld = new TextField();
        invFdMaturityAmtFld.setPromptText("Expected maturity amount (optional)");
        invFdMaturityAmtFld.setMaxWidth(Double.MAX_VALUE);

        TextField invRdRefFld         = new TextField();
        invRdRefFld.setPromptText("RD reference number (optional)");
        invRdRefFld.setMaxWidth(Double.MAX_VALUE);

        TextField invRdRateFld        = new TextField();
        invRdRateFld.setPromptText("Annual interest rate, e.g. 7.0");
        invRdRateFld.setMaxWidth(Double.MAX_VALUE);

        DatePicker invRdMaturityPicker = new DatePicker();
        invRdMaturityPicker.setPromptText("Maturity date");
        invRdMaturityPicker.setMaxWidth(Double.MAX_VALUE);
        UiUtils.styleOnShow(invRdMaturityPicker);
        UiUtils.applySmartDateConverter(invRdMaturityPicker);

        // ── Dynamic containers ────────────────────────────────────────────────
        VBox toAccountSection = new VBox(0);
        toAccountSection.setVisible(false);
        toAccountSection.setManaged(false);

        VBox invDynamicBox = new VBox(0);
        invDynamicBox.setVisible(false);
        invDynamicBox.setManaged(false);

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
                case MUTUAL_FUNDS, EQUITY, DEBT_BONDS -> {
                    formRow(dg, 0, "Scheme / Script", invSchemeFld);
                    formRow(dg, 1, "Units / NAV",     invUnitsFld);
                }
                case FIXED_DEPOSIT -> {
                    formRow(dg, 0, "FD Reference No",   invFdRefFld);
                    formRow(dg, 1, "Interest Rate (%)", invFdRateFld);
                    formRow(dg, 2, "Maturity Date",     invFdMaturityPicker);
                    formRow(dg, 3, "Maturity Amount",   invFdMaturityAmtFld);
                }
                case RECURRING_DEPOSIT -> {
                    formRow(dg, 0, "RD Reference No",   invRdRefFld);
                    formRow(dg, 1, "Interest Rate (%)", invRdRateFld);
                    formRow(dg, 2, "Maturity Date",     invRdMaturityPicker);
                }
                case PROVIDENT_FUND -> { /* no additional fields */ }
            }
            invDynamicBox.getChildren().add(dg);
            invDynamicBox.setVisible(true);
            invDynamicBox.setManaged(true);
        };

        Runnable refreshToAccount = () -> {
            Transaction.Type t = typeCb.getValue();

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
            } else if (!isNew && existing.getFromAccountId() != null) {
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
                formRow(tg, 0, "To Account", transferToCb);
            } else if (t == Transaction.Type.INVESTMENT) {
                formRow(tg, 0, "To Account",      invDestCb);
                formRow(tg, 1, "Investment Type", invTypeLbl);
            } else if (t == Transaction.Type.CC_PAYMENT) {
                formRow(tg, 0, "To Account", ccpCardCb);
            } else {
                formRow(tg, 0, "To Account", loanToCb);
            }
            toAccountSection.getChildren().add(tg);
            if (t == Transaction.Type.INVESTMENT) refreshInvFields.run();
        };

        typeCb.setOnAction(e -> refreshToAccount.run());
        invDestCb.setOnAction(e -> refreshInvFields.run());

        // ── Category / Sub-category ───────────────────────────────────────────
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
                setStyle(empty || item == null ? "" : "-fx-text-fill: #1A1A2E;");
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

        if (!isNew && existing.getCategoryId() != null) {
            ds.getCategories().stream()
                    .filter(c -> c.getId().equals(existing.getCategoryId()))
                    .findFirst().ifPresent(catCb::setValue);
            if (existing.getSubCategoryId() != null) {
                subMaster.stream()
                        .filter(s -> s.getId().equals(existing.getSubCategoryId()))
                        .findFirst().ifPresent(subCatCb::setValue);
            }
        }

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

        if (!isNew && existing.getAutoRecordAfterDays() > 0) {
            autoRecordCb.setSelected(true);
            autoRecordDaysSp.getValueFactory().setValue(existing.getAutoRecordAfterDays());
        }

        // ── Pre-select to-account when editing ────────────────────────────────
        if (!isNew && existing.getToAccountId() != null) {
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

        // ── Layout ────────────────────────────────────────────────────────────
        int row = 0;
        formRow(g, row++, "Description*",     descFld);
        formRow(g, row++, "Type",             typeCb);
        formRow(g, row++, "Frequency",        freqCb);
        formRow(g, row++, "Due Day of Month", daySpinner);
        formRow(g, row++, "Start Date",       startPicker);
        formRow(g, row++, "Amount (₹)",       amtFld);
        formRow(g, row++, "From Account",     accountCb);
        g.add(toAccountSection, 0, row++, 2, 1);
        g.add(invDynamicBox,    0, row++, 2, 1);
        formRow(g, row++, "Category",         catCb);
        formRow(g, row++, "Sub-category",     subCatCb);
        g.add(autoRecordBox,    0, row,   2, 1);

        ScrollPane sp = new ScrollPane(g);
        sp.setFitToWidth(true);
        sp.setPrefHeight(580);
        sp.getStyleClass().add("scroll-transparent");
        dlg.getDialogPane().setContent(sp);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String desc = descFld.getText().trim();
            if (desc.isEmpty()) { alert("Validation Error", "Description is required."); return null; }

            Transaction.Type type               = typeCb.getValue();
            RecurringTransaction.Frequency freq = freqCb.getValue();
            int day   = daySpinner.getValue();
            LocalDate start = startPicker.getValue() != null ? startPicker.getValue() : LocalDate.now();

            if (type == Transaction.Type.TRANSFER && transferToCb.getValue() == null) {
                alert("Validation Error", "Select a destination account for the transfer."); return null;
            }
            if (type == Transaction.Type.TRANSFER
                    && accountCb.getValue() != null && transferToCb.getValue() != null
                    && accountCb.getValue().getId().equals(transferToCb.getValue().getId())) {
                alert("Validation Error", "From and To accounts must differ."); return null;
            }
            if (type == Transaction.Type.INVESTMENT && invDestCb.getValue() == null) {
                alert("Validation Error", "Select a destination investment account."); return null;
            }
            if (type == Transaction.Type.CC_PAYMENT && ccpCardCb.getValue() == null) {
                alert("Validation Error", "Select a credit card for the payment."); return null;
            }
            if (type == Transaction.Type.LOAN_PAYMENT && loanToCb.getValue() == null) {
                alert("Validation Error", "Select a loan account for the payment."); return null;
            }

            long paise = 0;
            String amtRaw = amtFld.getText().trim().replace(",", "").replace("₹", "");
            if (!amtRaw.isEmpty()) {
                try { paise = Math.round(Double.parseDouble(amtRaw) * 100); }
                catch (NumberFormatException e) { alert("Validation Error", "Invalid amount."); return null; }
            }

            if (autoRecordCb.isSelected() && paise == 0) {
                alert("Validation Error",
                        "Auto-record requires a fixed amount. Enter an amount or uncheck auto-record.");
                return null;
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

            String invNotes = null;
            if (type == Transaction.Type.INVESTMENT && invDestCb.getValue() != null) {
                invNotes = buildInvNotes(invDestCb.getValue(),
                        invSchemeFld, invUnitsFld,
                        invFdRefFld, invFdRateFld, invFdMaturityPicker, invFdMaturityAmtFld,
                        invRdRefFld, invRdRateFld, invRdMaturityPicker);
            }

            String fromAccountId = accountCb.getValue() != null ? accountCb.getValue().getId() : null;
            int autoRecordDays   = autoRecordCb.isSelected() ? autoRecordDaysSp.getValue() : 0;

            if (isNew) {
                RecurringTransaction r = new RecurringTransaction(desc, type, freq, day, start, paise);
                r.setFromAccountId(fromAccountId);
                r.setToAccountId(toAccountId);
                if (catCb.getValue() != null)    r.setCategoryId(catCb.getValue().getId());
                if (subCatCb.getValue() != null) r.setSubCategoryId(subCatCb.getValue().getId());
                r.setNotes(invNotes);
                r.setAutoRecordAfterDays(autoRecordDays);
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
                existing.setNotes(invNotes);
                existing.setAutoRecordAfterDays(autoRecordDays);
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

    private static String buildInvNotes(InvestmentAccount dest,
            TextField schemeFld, TextField unitsFld,
            TextField fdRefFld, TextField fdRateFld,
            DatePicker fdMaturityPicker, TextField fdMaturityAmtFld,
            TextField rdRefFld, TextField rdRateFld, DatePicker rdMaturityPicker) {
        StringBuilder sb = new StringBuilder();
        switch (dest.getInvestmentType()) {
            case MUTUAL_FUNDS, EQUITY, DEBT_BONDS -> {
                appendNote(sb, "Scheme/Script", schemeFld.getText().trim());
                appendNote(sb, "Units/NAV",     unitsFld.getText().trim());
            }
            case FIXED_DEPOSIT -> {
                appendNote(sb, "FD Ref",          fdRefFld.getText().trim());
                appendNote(sb, "Interest Rate",   fdRateFld.getText().trim());
                appendNote(sb, "Maturity Date",
                        fdMaturityPicker.getValue() != null ? fdMaturityPicker.getValue().toString() : "");
                appendNote(sb, "Maturity Amount", fdMaturityAmtFld.getText().trim());
            }
            case RECURRING_DEPOSIT -> {
                appendNote(sb, "RD Ref",        rdRefFld.getText().trim());
                appendNote(sb, "Interest Rate", rdRateFld.getText().trim());
                appendNote(sb, "Maturity Date",
                        rdMaturityPicker.getValue() != null ? rdMaturityPicker.getValue().toString() : "");
            }
            case PROVIDENT_FUND -> { /* no additional fields */ }
        }
        return sb.length() > 0 ? sb.toString().trim() : null;
    }

    private static void appendNote(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank())
            sb.append(key).append(": ").append(value).append("\n");
    }

    private static void formRow(GridPane g, int rowIdx, String labelText, Node control) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        lbl.setMinWidth(145);
        g.add(lbl,     0, rowIdx);
        g.add(control, 1, rowIdx);
        GridPane.setFillWidth(control, true);
    }

    private static String formatFrequency(RecurringTransaction.Frequency f) {
        if (f == null) return "—";
        return switch (f) {
            case MONTHLY        -> "Monthly";
            case QUARTERLY      -> "Quarterly";
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
