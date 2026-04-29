package com.sanchay.ui.recurring;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Owns the investment-destination combo, type-hint label, and all type-specific
 * sub-fields (scheme/NAV, FD/Bond details, RD details). Wires the dest-combo
 * listener internally; parent calls {@link #getDestCombo()}, {@link #getTypeLbl()},
 * {@link #getDynamicBox()} when building the "to-account" section.
 */
class InvestmentRecurringPanel {

    private final ComboBox<InvestmentAccount> destCb;
    private final Label typeLbl;
    private final VBox dynamicBox;

    private final TextField  invSchemeFld;
    private final TextField  invUnitsFld;
    private final TextField  invFdRefFld;
    private final TextField  invFdRateFld;
    private final DatePicker invFdMaturityPicker;
    private final TextField  invFdMaturityAmtFld;
    private final TextField  invRdRefFld;
    private final TextField  invRdRateFld;
    private final DatePicker invRdMaturityPicker;
    private final TextField  invRdOpeningBalFld;
    private final TextField  invRdMaturityAmtFld;

    InvestmentRecurringPanel(DataStore ds) {
        destCb = new ComboBox<>();
        destCb.setPromptText("Select investment account");
        destCb.setMaxWidth(Double.MAX_VALUE);
        ds.getInvestmentAccounts().forEach(destCb.getItems()::add);
        destCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(InvestmentAccount item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + "  (" + item.getAccountType() + ")");
            }
        });
        destCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(InvestmentAccount item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + "  (" + item.getAccountType() + ")");
            }
        });

        typeLbl = new Label("—");
        typeLbl.getStyleClass().add("inv-type-hint");

        dynamicBox = new VBox(0);
        dynamicBox.setVisible(false);
        dynamicBox.setManaged(false);

        invSchemeFld = field("e.g. HDFC Flexi Cap Fund, RELIANCE (optional)");
        invUnitsFld  = field("Units / NAV at investment time (optional)");
        invFdRefFld  = field("Reference number (optional)");
        invFdRateFld = field("Annual interest rate, e.g. 6.5");

        invFdMaturityPicker = UiUtils.createDatePicker(null);
        invFdMaturityPicker.setPromptText("Maturity date");

        invFdMaturityAmtFld = field("Expected maturity amount (optional)");
        invRdRefFld         = field("e.g. HDFC-RD-2024 (required)");
        invRdRateFld        = field("Annual interest rate, e.g. 7.0");

        invRdMaturityPicker = UiUtils.createDatePicker(null);
        invRdMaturityPicker.setPromptText("Maturity date");

        invRdOpeningBalFld  = field("Total instalments paid before setup (optional)");
        invRdMaturityAmtFld = field("Expected maturity amount (optional)");

        destCb.setOnAction(e -> refreshDynamicFields());
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    ComboBox<InvestmentAccount> getDestCombo() { return destCb; }
    Label getTypeLbl()                         { return typeLbl; }
    VBox getDynamicBox()                       { return dynamicBox; }

    /** Called by parent when transaction type changes away from INVESTMENT. */
    void clearForTypeSwitch() {
        dynamicBox.getChildren().clear();
        dynamicBox.setVisible(false);
        dynamicBox.setManaged(false);
        typeLbl.setText("—");
    }

    /** Called by parent after INVESTMENT type is selected, to render dest-combo sub-fields. */
    void triggerRefresh() {
        refreshDynamicFields();
    }

    // ── Prefill ───────────────────────────────────────────────────────────────

    void prefill(RecurringTransaction existing) {
        if (existing.getRdOpeningBalancePaise() > 0)
            invRdOpeningBalFld.setText(fmt(existing.getRdOpeningBalancePaise()));
        if (existing.getRdMaturityAmountPaise() > 0)
            invRdMaturityAmtFld.setText(fmt(existing.getRdMaturityAmountPaise()));
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
            invFdMaturityAmtFld.setText(fmt(existing.getFdMaturityAmountPaise()));
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /** Sets all investment fields on {@code r}; nulls them if type is not INVESTMENT. */
    void applyTo(RecurringTransaction r, Transaction.Type type) {
        if (type != Transaction.Type.INVESTMENT || destCb.getValue() == null) {
            r.setSchemeScript(null); r.setUnitsNav(null);
            r.setFdRef(null); r.setRdRef(null);
            r.setInterestRate(null); r.setMaturityDate(null);
            r.setFdMaturityAmountPaise(0);
            r.setRdOpeningBalancePaise(0); r.setRdMaturityAmountPaise(0);
            return;
        }
        // Clear all first, then set only what's relevant for this subtype
        r.setSchemeScript(null); r.setUnitsNav(null);
        r.setFdRef(null); r.setRdRef(null);
        r.setInterestRate(null); r.setMaturityDate(null);
        r.setFdMaturityAmountPaise(0);
        r.setRdOpeningBalancePaise(0); r.setRdMaturityAmountPaise(0);

        switch (destCb.getValue().getInvestmentType()) {
            case MUTUAL_FUNDS, EQUITY -> {
                r.setSchemeScript(nullIfBlank(invSchemeFld.getText()));
                r.setUnitsNav(nullIfBlank(invUnitsFld.getText()));
            }
            case FIXED_DEPOSIT, DEBT_BONDS -> {
                r.setFdRef(nullIfBlank(invFdRefFld.getText()));
                r.setInterestRate(parseRate(invFdRateFld.getText()));
                r.setMaturityDate(invFdMaturityPicker.getValue());
                r.setFdMaturityAmountPaise(parsePaise(invFdMaturityAmtFld.getText()));
            }
            case RECURRING_DEPOSIT -> {
                r.setRdRef(nullIfBlank(invRdRefFld.getText()));
                r.setInterestRate(parseRate(invRdRateFld.getText()));
                r.setMaturityDate(invRdMaturityPicker.getValue());
                r.setRdOpeningBalancePaise(parsePaise(invRdOpeningBalFld.getText()));
                r.setRdMaturityAmountPaise(parsePaise(invRdMaturityAmtFld.getText()));
            }
            default -> { /* PROVIDENT_FUND — no extra fields */ }
        }
    }

    boolean isRd() {
        return destCb.getValue() != null &&
               destCb.getValue().getInvestmentType() == InvestmentAccount.InvestmentType.RECURRING_DEPOSIT;
    }

    boolean isRdRefBlank() {
        return invRdRefFld.getText().trim().isEmpty();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void refreshDynamicFields() {
        dynamicBox.getChildren().clear();
        InvestmentAccount sel = destCb.getValue();
        if (sel == null) {
            typeLbl.setText("—");
            dynamicBox.setVisible(false);
            dynamicBox.setManaged(false);
            return;
        }
        typeLbl.setText(sel.getAccountType());
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
                UiUtils.addFormRow(dg, 0, "RD Reference No*",    invRdRefFld);
                UiUtils.addFormRow(dg, 1, "Interest Rate (%)",   invRdRateFld);
                UiUtils.addFormRow(dg, 2, "Maturity Date",       invRdMaturityPicker);
                UiUtils.addFormRow(dg, 3, "Opening Balance (₹)", invRdOpeningBalFld);
                UiUtils.addFormRow(dg, 4, "Maturity Amount (₹)", invRdMaturityAmtFld);
            }
            case PROVIDENT_FUND -> { /* no additional fields */ }
        }
        dynamicBox.getChildren().add(dg);
        dynamicBox.setVisible(true);
        dynamicBox.setManaged(true);
    }

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

    private static TextField field(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private static String fmt(long paise) {
        return String.format("%.2f", paise / 100.0);
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Double parseRate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static long parsePaise(String s) {
        if (s == null) return 0;
        String raw = s.trim().replace(",", "").replace(MoneyFormatter.symbol(), "");
        if (raw.isEmpty()) return 0;
        try { return Math.round(Double.parseDouble(raw) * 100); } catch (NumberFormatException e) { return 0; }
    }
}
