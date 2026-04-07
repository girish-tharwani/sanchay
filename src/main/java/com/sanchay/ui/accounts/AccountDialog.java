package com.sanchay.ui.accounts;

import com.sanchay.model.*;
import com.sanchay.service.AmortizationService;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
//import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.List;
//import java.util.Optional;

/**
 * Static factory methods for the four account CRUD dialogs.
 * Extracted from AccountsScreen inline dialog builders.
 */
public class AccountDialog {

    public static void showForBank(BankAccount existing) {
        boolean isNew = (existing == null);
        Dialog<Void> dlg = dialog(isNew ? "Add Bank Account" : "Edit Bank Account");
        GridPane g = formGrid();

        TextField nameFld   = tf(isNew ? "" : existing.getName(),                  "e.g. HDFC Primary Savings");
        TextField descFld   = tf(isNew ? "" : nvl(existing.getDescription()),       "Short description (optional)");
        TextField bankFld   = tf(isNew ? "" : nvl(existing.getBankName()),          "e.g. HDFC Bank");
        ComboBox<String> holderCb = memberCombo(isNew ? null : existing.getAccountHolder());
        holderCb.setPromptText("Account holder name");
        TextField acctNoFld = tf(isNew ? "" : nvl(existing.getAccountNumber()),     "Account number");
        ComboBox<String> subtypeCb = new ComboBox<>();
        subtypeCb.getItems().addAll("Savings", "Current");
        subtypeCb.setValue(isNew ? "Savings"
                : existing.getSubType() == BankAccount.SubType.SAVINGS ? "Savings" : "Current");
        subtypeCb.setMaxWidth(Double.MAX_VALUE);
        TextField currencyFld = new TextField("INR");
        currencyFld.setEditable(false);
        currencyFld.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> statusCb = new ComboBox<>();
        statusCb.getItems().addAll("Active", "Closed");
        statusCb.setValue(isNew ? "Active" : (existing.isActive() ? "Active" : "Closed"));
        statusCb.setMaxWidth(Double.MAX_VALUE);
        DatePicker openDate = UiUtils.createDatePicker(isNew ? LocalDate.now() : existing.getOpeningDate());
        TextField openBalFld = tf(isNew ? "0.00"
                : String.format("%.2f", existing.getOpeningBalancePaise() / 100.0), "Opening balance");

        CheckBox jointCb = new CheckBox("Joint Account");
        jointCb.setSelected(!isNew && existing.isJointAccount());
        ComboBox<String> secondHolderCb = memberCombo(isNew ? null : existing.getSecondHolderName());
        secondHolderCb.setPromptText("Second holder name");
        secondHolderCb.setVisible(jointCb.isSelected());
        secondHolderCb.setManaged(jointCb.isSelected());
        jointCb.selectedProperty().addListener((obs, o, n) -> {
            secondHolderCb.setVisible(n);
            secondHolderCb.setManaged(n);
        });

        UiUtils.addFormRow(g,  0, "Name*",           nameFld);
        UiUtils.addFormRow(g,  1, "Description",     descFld);
        UiUtils.addFormRow(g,  2, "Bank*",           bankFld);
        UiUtils.addFormRow(g,  3, "Account Holder*", holderCb);
        UiUtils.addFormRow(g,  4, "Account Number",  acctNoFld);
        UiUtils.addFormRow(g,  5, "Sub-Type",        subtypeCb);
        UiUtils.addFormRow(g,  6, "Currency",        currencyFld);
        UiUtils.addFormRow(g,  7, "Status",          statusCb);
        UiUtils.addFormRow(g,  8, "Opening Date",    openDate);
        UiUtils.addFormRow(g,  9, "Opening Balance", openBalFld);
        UiUtils.addFormRow(g, 10, "Joint Account",   jointCb);
        UiUtils.addFormRow(g, 11, "Second Holder",   secondHolderCb);

        dlg.getDialogPane().setContent(scrolled(g));
        ButtonType saveBtn = UiUtils.addSaveCancel(dlg.getDialogPane());
        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { info("Validation", "Name is required."); return null; }
            BankAccount acc = isNew
                    ? new BankAccount(name, BankAccount.SubType.valueOf(subtypeCb.getValue().toUpperCase()))
                    : existing;
            if (!isNew) acc.setName(name);
            acc.setDescription(descFld.getText().trim());
            acc.setBankName(bankFld.getText().trim());
            acc.setAccountHolder(holderCb.getEditor().getText().trim());
            acc.setAccountNumber(acctNoFld.getText().trim());
            acc.setOpeningDate(openDate.getValue());
            try { acc.setOpeningBalancePaise(Math.round(
                    Double.parseDouble(openBalFld.getText().replace(",", "")) * 100));
            } catch (NumberFormatException ignore) {}
            acc.setJointAccount(jointCb.isSelected());
            acc.setSecondHolderName(jointCb.isSelected() ? secondHolderCb.getEditor().getText().trim() : null);
            acc.setStatus("Active".equals(statusCb.getValue()) ? Account.Status.ACTIVE : Account.Status.CLOSED);
            if (isNew) DataStore.getInstance().addAccount(acc);
            else DataStore.getInstance().getPersistence().saveAccounts(DataStore.getInstance());
            Platform.runLater(dlg::close);
            return null;
        });
        dlg.showAndWait();
    }

    public static void showForCreditCard(CreditCardAccount existing) {
        boolean isNew = (existing == null);
        Dialog<Void> dlg = dialog(isNew ? "Add Credit Card" : "Edit Credit Card");
        GridPane g = formGrid();

        TextField nameFld   = tf(isNew ? "" : existing.getName(),              "e.g. HDFC Regalia");
        TextField descFld   = tf(isNew ? "" : nvl(existing.getDescription()),  "Short description (optional)");
        TextField issuerFld = tf(isNew ? "" : nvl(existing.getBankIssuer()),   "e.g. HDFC Bank");
        ComboBox<String> holderCb = memberCombo(isNew ? null : existing.getCardHolderName());
        holderCb.setPromptText("Card holder name");
        TextField cardNoFld = tf(isNew ? "" : "", "16-digit card number");
        TextField limitFld  = tf(isNew ? "0"
                : String.format("%.0f", existing.getCreditLimitPaise() / 100.0), "Credit limit");
        Spinner<Integer> billDay = new Spinner<>(1, 28, isNew ? 1 : existing.getBillingCycleDate());
        billDay.setMaxWidth(Double.MAX_VALUE);
        UiUtils.makeSpinnerEditable(billDay, 1, 28);
        Spinner<Integer> dueDays = new Spinner<>(1, 60, isNew ? 20 : existing.getPaymentDueDays());
        dueDays.setMaxWidth(Double.MAX_VALUE);
        UiUtils.makeSpinnerEditable(dueDays, 1, 60);

        CheckBox addOnCb = new CheckBox("Has Add-on Card");
        addOnCb.setSelected(!isNew && existing.isAddOnCard());
        ComboBox<String> addOnHolderCb = memberCombo(isNew ? null : existing.getAddOnCardHolderName());
        addOnHolderCb.setPromptText("Add-on card holder name");
        addOnHolderCb.setVisible(addOnCb.isSelected());
        addOnHolderCb.setManaged(addOnCb.isSelected());
        addOnCb.selectedProperty().addListener((obs, o, n) -> {
            addOnHolderCb.setVisible(n);
            addOnHolderCb.setManaged(n);
        });

        TextField currencyFld = new TextField("INR");
        currencyFld.setEditable(false);
        currencyFld.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> statusCb = new ComboBox<>();
        statusCb.getItems().addAll("Active", "Blocked", "Cancelled");
        statusCb.setValue(isNew ? "Active" : formatCardStatus(existing.getCardStatus()));
        statusCb.setMaxWidth(Double.MAX_VALUE);

        UiUtils.addFormRow(g,  0, "Name*",              nameFld);
        UiUtils.addFormRow(g,  1, "Description",        descFld);
        UiUtils.addFormRow(g,  2, "Issuer*",            issuerFld);
        UiUtils.addFormRow(g,  3, "Card Holder*",       holderCb);
        UiUtils.addFormRow(g,  4, "Card Number",        cardNoFld);
        UiUtils.addFormRow(g,  5, "Credit Limit*",      limitFld);
        UiUtils.addFormRow(g,  6, "Currency",           currencyFld);
        UiUtils.addFormRow(g,  7, "Status",             statusCb);
        UiUtils.addFormRow(g,  8, "Billing Date",       billDay);
        UiUtils.addFormRow(g,  9, "Payment Due (days)", dueDays);
        UiUtils.addFormRow(g, 10, "Add-on Card",        addOnCb);
        UiUtils.addFormRow(g, 11, "Add-on Card Holder", addOnHolderCb);

        dlg.getDialogPane().setContent(scrolled(g));
        ButtonType saveBtn = UiUtils.addSaveCancel(dlg.getDialogPane());
        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { info("Validation", "Name is required."); return null; }
            CreditCardAccount acc = isNew ? new CreditCardAccount(name) : existing;
            if (!isNew) acc.setName(name);
            acc.setDescription(descFld.getText().trim());
            acc.setBankIssuer(issuerFld.getText().trim());
            acc.setCardHolderName(holderCb.getEditor().getText().trim());
            if (!cardNoFld.getText().isBlank()) acc.setCardNumber(cardNoFld.getText().trim());
            try { acc.setCreditLimitPaise(Math.round(
                    Double.parseDouble(limitFld.getText().replace(",", "")) * 100));
            } catch (NumberFormatException ignore) {}
            acc.setBillingCycleDate(billDay.getValue());
            acc.setPaymentDueDays(dueDays.getValue());
            acc.setAddOnCard(addOnCb.isSelected());
            acc.setAddOnCardHolderName(addOnCb.isSelected() ?
                    addOnHolderCb.getEditor().getText().trim() : null);
            CreditCardAccount.CardStatus cs = parseCardStatus(statusCb.getValue());
            acc.setCardStatus(cs);
            acc.setStatus(cs == CreditCardAccount.CardStatus.ACTIVE ? Account.Status.ACTIVE : Account.Status.CLOSED);
            if (isNew) DataStore.getInstance().addAccount(acc);
            else DataStore.getInstance().getPersistence().saveAccounts(DataStore.getInstance());
            Platform.runLater(dlg::close);
            return null;
        });
        dlg.showAndWait();
    }

    public static void showForLoan(LoanAccount existing) {
        boolean isNew = (existing == null);
        Dialog<Void> dlg = dialog(isNew ? "Add Loan Account" : "Edit Loan Account");
        GridPane g = formGrid();

        TextField nameFld    = tf(isNew ? "" : existing.getName(),              "e.g. ICICI Home Loan");
        TextField descFld    = tf(isNew ? "" : nvl(existing.getDescription()), "Short description (optional)");
        ComboBox<LoanAccount.LoanType> typeCb = new ComboBox<>();
        typeCb.getItems().addAll(LoanAccount.LoanType.values());
        typeCb.setValue(isNew ? LoanAccount.LoanType.HOME : existing.getLoanType());
        typeCb.setMaxWidth(Double.MAX_VALUE);
        typeCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(LoanAccount.LoanType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatLoanType(item));
            }
        });
        typeCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(LoanAccount.LoanType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatLoanType(item));
            }
        });
        TextField lenderFld  = tf(isNew ? "" : nvl(existing.getLenderName()),        "Lender name");
        TextField acctNoFld  = tf(isNew ? "" : nvl(existing.getLoanAccountNumber()), "Loan account number");
        TextField loanAmtFld = tf(isNew ? "0" : String.format("%.0f", existing.getLoanAmountPaise() / 100.0),         "Loan amount");
        TextField rateFld    = tf(isNew ? "0" : String.valueOf(existing.getInterestRate()),                            "Interest rate %");
        TextField tenureFld  = tf(isNew ? "0" : String.valueOf(existing.getTenureMonths()),                            "Tenure in months");
        TextField emiFld     = tf(isNew ? "0" : String.format("%.0f", existing.getEmiAmountPaise() / 100.0),          "EMI amount");
        Spinner<Integer> emiDay = new Spinner<>(1, 28, isNew ? 1 : existing.getEmiDueDay());
        emiDay.setMaxWidth(Double.MAX_VALUE);
        UiUtils.makeSpinnerEditable(emiDay, 1, 28);
        TextField outFld     = tf(isNew ? "0" : String.format("%.0f", existing.getOutstandingPrincipalPaise() / 100.0), "Outstanding principal");

        DatePicker openDatePicker = new DatePicker(isNew ? LocalDate.now() : existing.getOpeningDate());
        openDatePicker.setMaxWidth(Double.MAX_VALUE);

        CheckBox jointCb = new CheckBox("Joint Account");
        jointCb.setSelected(!isNew && existing.isJointAccount());
        ComboBox<String> coApplicantCb = memberCombo(isNew ? null : existing.getCoApplicantName());
        coApplicantCb.setPromptText("Co-applicant name");
        coApplicantCb.setVisible(jointCb.isSelected());
        coApplicantCb.setManaged(jointCb.isSelected());
        jointCb.selectedProperty().addListener((obs, o, n) -> {
            coApplicantCb.setVisible(n);
            coApplicantCb.setManaged(n);
        });

        TextField currencyFld = new TextField("INR");
        currencyFld.setEditable(false);
        currencyFld.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> statusCb = new ComboBox<>();
        statusCb.getItems().addAll("Active", "Closed", "Settled");
        statusCb.setValue(isNew ? "Active" : formatLoanStatus(existing.getLoanStatus()));
        statusCb.setMaxWidth(Double.MAX_VALUE);

        UiUtils.addFormRow(g,  0, "Name*",                      nameFld);
        UiUtils.addFormRow(g,  1, "Description",                descFld);
        UiUtils.addFormRow(g,  2, "Loan Type",                  typeCb);
        UiUtils.addFormRow(g,  3, "Currency",                   currencyFld);
        UiUtils.addFormRow(g,  4, "Status",                     statusCb);
        UiUtils.addFormRow(g,  5, "Lender",                     lenderFld);
        UiUtils.addFormRow(g,  6, "Account No.",                acctNoFld);
        UiUtils.addFormRow(g,  7, "Loan Amount*",               loanAmtFld);
        UiUtils.addFormRow(g,  8, "Interest Rate",              rateFld);
        UiUtils.addFormRow(g,  9, "Tenure (months)",            tenureFld);
        UiUtils.addFormRow(g, 10, "EMI Amount",                 emiFld);
        UiUtils.addFormRow(g, 11, "EMI Due Day",                emiDay);
        UiUtils.addFormRow(g, 12, "Opening Balance",            outFld);
        UiUtils.addFormRow(g, 13, "Opening Date",               openDatePicker);
        UiUtils.addFormRow(g, 14, "Joint Account",              jointCb);
        UiUtils.addFormRow(g, 15, "Co-applicant",               coApplicantCb);

        dlg.getDialogPane().setContent(scrolled(g));
        ButtonType saveBtn = UiUtils.addSaveCancel(dlg.getDialogPane());
        // Capture original values to detect rate/EMI changes on edit
        final double origRate = isNew ? 0 : existing.getInterestRate();
        final long   origEmi  = isNew ? 0 : existing.getEmiAmountPaise();

        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { info("Validation", "Name is required."); return null; }
            LoanAccount.LoanType ltype = typeCb.getValue();
            LoanAccount acc = isNew ? new LoanAccount(name, ltype) : existing;
            if (!isNew) acc.setName(name);
            acc.setDescription(descFld.getText().trim());
            acc.setLenderName(lenderFld.getText().trim());
            acc.setLoanAccountNumber(acctNoFld.getText().trim());
            double newRate = origRate;
            long   newEmi  = origEmi;
            try {
                acc.setLoanAmountPaise(Math.round(Double.parseDouble(loanAmtFld.getText().replace(",", "")) * 100));
                newRate = Double.parseDouble(rateFld.getText().replace(",", ""));
                acc.setInterestRate(newRate);
                acc.setTenureMonths(Integer.parseInt(tenureFld.getText().trim()));
                newEmi = Math.round(Double.parseDouble(emiFld.getText().replace(",", "")) * 100);
                acc.setEmiAmountPaise(newEmi);
                acc.setEmiDueDay(emiDay.getValue());
                acc.setOutstandingPrincipalPaise(Math.round(Double.parseDouble(outFld.getText().replace(",", "")) * 100));
            } catch (NumberFormatException ignore) {}
            acc.setOpeningDate(openDatePicker.getValue() != null ? openDatePicker.getValue() : LocalDate.now());
            acc.setJointAccount(jointCb.isSelected());
            acc.setCoApplicantName(jointCb.isSelected() ? coApplicantCb.getEditor().getText().trim() : null);
            LoanAccount.LoanStatus ls = parseLoanStatus(statusCb.getValue());
            acc.setLoanStatus(ls);
            acc.setStatus(ls == LoanAccount.LoanStatus.ACTIVE ? Account.Status.ACTIVE : Account.Status.CLOSED);

            if (isNew) {
                // Seed initial rate history entry
                acc.getRateHistory().add(new LoanRateChange(
                        acc.getOpeningDate(),
                        newRate, newEmi));
                DataStore.getInstance().addAccount(acc); // also generates and saves schedule
            } else {
                // Check if rate or EMI changed — if so, ask for effective-from date
                final double finalNewRate = newRate;
                final long   finalNewEmi  = newEmi;
                boolean rateChanged = Double.compare(origRate, finalNewRate) != 0 || origEmi != finalNewEmi;
                if (rateChanged) {
                    LocalDate effectiveFrom = askEffectiveFromDate(acc);
                    if (effectiveFrom != null) {
                        // If this is the first rate change, record the original rate from opening date
                        if (acc.getRateHistory().isEmpty()) {
                            acc.getRateHistory().add(new LoanRateChange(acc.getOpeningDate(), origRate, origEmi));
                        }
                        acc.getRateHistory().add(new LoanRateChange(effectiveFrom, finalNewRate, finalNewEmi));
                        DataStore.getInstance().getPersistence().saveAccounts(DataStore.getInstance());
                        // Preserve manual overrides before effectiveFrom — recalculate only from
                        // the first entry on or after the effective date, not the whole schedule.
                        List<AmortizationEntry> savedSchedule = DataStore.getInstance().getSchedule(acc.getId());
                        List<AmortizationEntry> newSchedule;
                        if (savedSchedule.isEmpty()) {
                            newSchedule = AmortizationService.generateSchedule(acc);
                        } else {
                            int fromIndex = savedSchedule.size(); // default: nothing to recalculate
                            for (int i = 0; i < savedSchedule.size(); i++) {
                                if (!savedSchedule.get(i).getPaymentDate().isBefore(effectiveFrom)) {
                                    fromIndex = i;
                                    break;
                                }
                            }
                            newSchedule = AmortizationService.recalculateFrom(savedSchedule, fromIndex, acc);
                        }
                        DataStore.getInstance().saveSchedule(acc.getId(), newSchedule);
                        if (origEmi != finalNewEmi) offerEmiSync(acc, origEmi, finalNewEmi);
                    } else {
                        // User cancelled effective-from — revert to original values
                        acc.setInterestRate(origRate);
                        acc.setEmiAmountPaise(origEmi);
                        DataStore.getInstance().getPersistence().saveAccounts(DataStore.getInstance());
                    }
                } else {
                    DataStore.getInstance().getPersistence().saveAccounts(DataStore.getInstance());
                }
            }
            Platform.runLater(dlg::close);
            return null;
        });
        dlg.showAndWait();
    }

    public static void showForInvestment(InvestmentAccount existing) {
        boolean isNew = (existing == null);
        Dialog<Void> dlg = dialog(isNew ? "Add Investments" : "Edit Investments");
        GridPane g = formGrid();

        TextField nameFld = tf(isNew ? "" : existing.getName(),              "e.g. HDFC Flexi Cap Fund");
        TextField descFld = tf(isNew ? "" : nvl(existing.getDescription()), "Short description (optional)");

        ComboBox<String> typeCb = new ComboBox<>();
        for (InvestmentAccount.InvestmentType t : InvestmentAccount.InvestmentType.values())
            typeCb.getItems().add(formatInvestmentType(t));
        typeCb.setValue(isNew ? formatInvestmentType(InvestmentAccount.InvestmentType.MUTUAL_FUNDS)
                : formatInvestmentType(existing.getInvestmentType()));
        typeCb.setMaxWidth(Double.MAX_VALUE);

        TextField folioFld  = tf(isNew ? "" : nvl(existing.getFolioAccountNumber()), "Folio / account number");
        TextField invAmtFld = tf(isNew ? "0"
                : String.format("%.0f", existing.getInvestedAmountPaise() / 100.0), "Total invested amount");
        TextField currencyFld = new TextField("INR");
        currencyFld.setEditable(false);
        currencyFld.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> statusCb = new ComboBox<>();
        statusCb.getItems().addAll("Active", "Closed", "Redeemed");
        statusCb.setValue(isNew ? "Active" : formatInvestmentStatus(existing.getInvestmentStatus()));
        statusCb.setMaxWidth(Double.MAX_VALUE);

        UiUtils.addFormRow(g, 0, "Name*",                    nameFld);
        UiUtils.addFormRow(g, 1, "Description",              descFld);
        UiUtils.addFormRow(g, 2, "Investment Type",          typeCb);
        UiUtils.addFormRow(g, 3, "Currency",                 currencyFld);
        UiUtils.addFormRow(g, 4, "Status",                   statusCb);
        UiUtils.addFormRow(g, 5, "Account Number",           folioFld);
        UiUtils.addFormRow(g, 6, "Opening Invested Amount*", invAmtFld);

        dlg.getDialogPane().setContent(scrolled(g));
        ButtonType saveBtn = UiUtils.addSaveCancel(dlg.getDialogPane());
        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { info("Validation", "Name is required."); return null; }
            InvestmentAccount.InvestmentType itype = parseInvestmentType(typeCb.getValue());
            InvestmentAccount acc = isNew ? new InvestmentAccount(name, itype) : existing;
            if (!isNew) acc.setName(name);
            acc.setDescription(descFld.getText().trim());
            acc.setFolioAccountNumber(folioFld.getText().trim());
            try { acc.setInvestedAmountPaise(Math.round(
                    Double.parseDouble(invAmtFld.getText().replace(",", "")) * 100));
            } catch (NumberFormatException ignore) {}
            InvestmentAccount.InvestmentStatus is = parseInvestmentStatus(statusCb.getValue());
            acc.setInvestmentStatus(is);
            acc.setStatus(is == InvestmentAccount.InvestmentStatus.ACTIVE ? Account.Status.ACTIVE : Account.Status.CLOSED);
            if (isNew) DataStore.getInstance().addAccount(acc);
            else DataStore.getInstance().getPersistence().saveAccounts(DataStore.getInstance());
            Platform.runLater(dlg::close);
            return null;
        });
        dlg.showAndWait();
    }

    // ── Format / parse helpers (dialog-exclusive) ─────────────────────────────

    private static String formatInvestmentType(InvestmentAccount.InvestmentType t) {
        return switch (t) {
            case MUTUAL_FUNDS      -> "Mutual Funds";
            case EQUITY            -> "Equities";
            case DEBT_BONDS        -> "Debt Bonds";
            case FIXED_DEPOSIT     -> "Fixed Deposits";
            case RECURRING_DEPOSIT -> "Recurring Deposits";
            case PROVIDENT_FUND    -> "Provident Fund";
        };
    }

    private static InvestmentAccount.InvestmentType parseInvestmentType(String display) {
        for (InvestmentAccount.InvestmentType t : InvestmentAccount.InvestmentType.values())
            if (formatInvestmentType(t).equals(display)) return t;
        throw new IllegalArgumentException("Unknown investment type: " + display);
    }

    private static String formatCardStatus(CreditCardAccount.CardStatus s) {
        return switch (s) {
            case ACTIVE    -> "Active";
            case BLOCKED   -> "Blocked";
            case CANCELLED -> "Cancelled";
        };
    }

    private static CreditCardAccount.CardStatus parseCardStatus(String display) {
        return switch (display) {
            case "Blocked"   -> CreditCardAccount.CardStatus.BLOCKED;
            case "Cancelled" -> CreditCardAccount.CardStatus.CANCELLED;
            default          -> CreditCardAccount.CardStatus.ACTIVE;
        };
    }

    static String formatLoanType(LoanAccount.LoanType t) {
        return switch (t) {
            case HOME     -> "Home Loan";
            case VEHICLE  -> "Vehicle Loan";
            case PERSONAL -> "Personal Loan";
        };
    }

    private static String formatLoanStatus(LoanAccount.LoanStatus s) {
        return switch (s) {
            case ACTIVE  -> "Active";
            case CLOSED  -> "Closed";
            case SETTLED -> "Settled";
        };
    }

    private static LoanAccount.LoanStatus parseLoanStatus(String display) {
        return switch (display) {
            case "Closed"  -> LoanAccount.LoanStatus.CLOSED;
            case "Settled" -> LoanAccount.LoanStatus.SETTLED;
            default        -> LoanAccount.LoanStatus.ACTIVE;
        };
    }

    private static String formatInvestmentStatus(InvestmentAccount.InvestmentStatus s) {
        return switch (s) {
            case ACTIVE   -> "Active";
            case CLOSED   -> "Closed";
            case REDEEMED -> "Redeemed";
        };
    }

    private static InvestmentAccount.InvestmentStatus parseInvestmentStatus(String display) {
        return switch (display) {
            case "Closed"   -> InvestmentAccount.InvestmentStatus.CLOSED;
            case "Redeemed" -> InvestmentAccount.InvestmentStatus.REDEEMED;
            default         -> InvestmentAccount.InvestmentStatus.ACTIVE;
        };
    }

    // ── Form helpers ──────────────────────────────────────────────────────────

    private static GridPane formGrid() {
        GridPane g = UiUtils.buildFormGrid(160);
        g.setPadding(new Insets(16));
        return g;
    }

    private static ScrollPane scrolled(GridPane g) {
        ScrollPane sp = new ScrollPane(g);
        sp.setFitToWidth(true);
        sp.setPrefHeight(560);
        sp.getStyleClass().add("scroll-transparent");
        return sp;
    }

    private static Dialog<Void> dialog(String title) {
        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, title, title.startsWith("Edit") ? "✎" : "+", 500);
        return dlg;
    }

    /** Editable ComboBox pre-populated with family member names; free-text entry allowed. */
    private static ComboBox<String> memberCombo(String currentValue) {
        ComboBox<String> cb = new ComboBox<>();
        cb.setEditable(true);
        cb.setMaxWidth(Double.MAX_VALUE);
        DataStore.getInstance().getFamilyMembers().forEach(m -> cb.getItems().add(m.getName()));
        if (currentValue != null && !currentValue.isBlank()) cb.setValue(currentValue);
        return cb;
    }

    private static TextField tf(String val, String prompt) {
        TextField tf = new TextField(val);
        tf.setPromptText(prompt);
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }


    private static String nvl(String s) { return s != null ? s : ""; }

    private static void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    /**
     * If a LOAN_PAYMENT recurring schedule exists for this loan and the EMI has
     * changed, offers to update the schedule's amount.
     */
    private static void offerEmiSync(LoanAccount loan, long oldEmiPaise, long newEmiPaise) {
        RecurringTransaction rt = DataStore.getInstance().findLoanPaymentSchedule(loan.getId());
        if (rt == null || rt.getAmountPaise() == newEmiPaise) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Update Payment Schedule");
        confirm.setHeaderText(null);
        confirm.setContentText(String.format(
                "EMI has changed from \u20b9%.0f to \u20b9%.0f.\nUpdate the linked payment schedule?",
                oldEmiPaise / 100.0, newEmiPaise / 100.0));
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                rt.setAmountPaise(newEmiPaise);
                DataStore.getInstance().saveRecurringNow();
            }
        });
    }

    /**
     * Shows a small dialog asking the user for an "Effective From" date when
     * interest rate or EMI changes. Returns the chosen date, or null if cancelled.
     */
    static LocalDate askEffectiveFromDate(LoanAccount acc) {
        Dialog<LocalDate> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Rate / EMI Change", "✎", 380);

        GridPane g = UiUtils.buildFormGrid(140);
        g.setPadding(new Insets(16));

        DatePicker picker = UiUtils.createDatePicker(LocalDate.now());

        Label hint = new Label("The new rate/EMI will apply from this date onwards in the schedule.");
        hint.setWrapText(true);
        hint.getStyleClass().add("text-hint");
        hint.setStyle("-fx-font-size: 11px;"); // Inline required: 11px is smaller than base hint size

        Label lbl = new Label("Effective From*");
        lbl.getStyleClass().add("form-label");
        g.add(lbl, 0, 0);
        g.add(picker, 1, 0);
        g.add(hint, 0, 1, 2, 1);

        dlg.getDialogPane().setContent(g);
        ButtonType saveBtn = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            LocalDate d = picker.getValue();
            if (d == null) { info("Validation", "Please select an effective from date."); return null; }
            LocalDate minDate = acc.getOpeningDate() != null ? acc.getOpeningDate() : LocalDate.now();
            if (d.isBefore(minDate)) {
                info("Validation", "Effective from date cannot be before the loan opening date.");
                return null;
            }
            return d;
        });
        return dlg.showAndWait().orElse(null);
    }
}
