package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.ui.UiUtils;
import com.sanchay.ui.common.AccountCombos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Type-specific panel for INVESTMENT transactions.
 * Manages a side-by-side layout: left form (account selectors + dynamic fields)
 * and right preview panel (FD/Bond schedule preview).
 */
class InvestmentPanel implements TransactionPanel {

    private final TransactionDialog parent;

    final ComboBox<Account>           fromCb;
    final ComboBox<InvestmentAccount> destCb;

    // ── Dynamic fields (null when not shown) ─────────────────────────────────
    TextField invSchemeFld, invUnitsFld;
    TextField invFdRefFld, invFdRateFld, invFdMaturityAmtFld;
    DatePicker invFdMaturityPicker;
    ComboBox<Transaction.InterestPayable> invFdInterestPayableCb;
    ComboBox<Transaction.PayoutAnchor>    invFdPayoutAnchorCb;
    ComboBox<Month>                       invFdPayoutMonthCb;
    Spinner<Integer>                      invFdPayoutDaySp;
    ComboBox<String>                      invRdRefCb;

    // ── Right-panel preview nodes ─────────────────────────────────────────────
    private final VBox  dynamicBox;
    private final VBox  previewPanel;
    private final VBox  prevScheduleBox;
    private final Label prevPrincipal, prevAnnualInt, prevTotalInt, prevTenor;

    // ── Layout containers exposed to TransactionDialog ────────────────────────
    final VBox leftContent;
    final HBox content;   // full side-by-side layout (left | separator | right)
    private final VBox rightPanel;

    private final Node node; // basic from/to GridPane

    InvestmentPanel(TransactionDialog parent) {
        this.parent = parent;

        fromCb = AccountCombos.bankCombo(parent.ds);
        parent.setNodeId(fromCb, "txn-investment-from-account-combo");

        destCb = new ComboBox<>();
        destCb.setId("txn-investment-destination-account-combo");
        destCb.setMaxWidth(Double.MAX_VALUE);
        destCb.setPromptText("Select investment account");
        parent.ds.getInvestmentAccounts().forEach(destCb.getItems()::add);
        AccountCombos.style(destCb);
        destCb.valueProperty().addListener((obs, old, ia) ->
                refreshDynamicFields(ia == null ? null : ia.getInvestmentType()));

        // ── Right panel (preview) ─────────────────────────────────────────────
        dynamicBox      = new VBox(0);
        dynamicBox.setId("txn-investment-dynamic-box");
        prevPrincipal   = previewVal("txn-investment-preview-principal");
        prevAnnualInt   = previewVal("txn-investment-preview-annual-interest");
        prevTotalInt    = previewVal("txn-investment-preview-total-interest");
        prevTenor       = previewVal("txn-investment-preview-tenor");
        prevScheduleBox = new VBox(4);
        prevScheduleBox.setId("txn-investment-preview-schedule");

        GridPane prevSummaryGrid = new GridPane();
        prevSummaryGrid.setHgap(8); prevSummaryGrid.setVgap(6);
        prevSummaryGrid.setPadding(new Insets(8, 8, 4, 8));
        ColumnConstraints pc1 = new ColumnConstraints(110);
        ColumnConstraints pc2 = new ColumnConstraints(0, 100, Double.MAX_VALUE);
        pc2.setHgrow(Priority.ALWAYS);
        prevSummaryGrid.getColumnConstraints().addAll(pc1, pc2);
        prevRow(prevSummaryGrid, 0, "Principal",       prevPrincipal);
        prevRow(prevSummaryGrid, 1, "Annual Interest", prevAnnualInt);
        prevRow(prevSummaryGrid, 2, "Total Interest",  prevTotalInt);
        prevRow(prevSummaryGrid, 3, "Tenor",           prevTenor);

        VBox schedSection = new VBox(4);
        schedSection.setPadding(new Insets(4, 8, 8, 8));
        Label schedHeader = new Label("Payout Schedule");
        schedHeader.setId("txn-investment-preview-schedule-header");
        schedHeader.getStyleClass().add("text-body-muted");
        schedSection.getChildren().addAll(schedHeader, prevScheduleBox);

        Label fdPreviewHeader = new Label("FD / Bond Preview");
        fdPreviewHeader.setId("txn-investment-preview-header");
        fdPreviewHeader.getStyleClass().add("text-body-muted");
        fdPreviewHeader.setPadding(new Insets(8, 8, 0, 8));

        previewPanel = new VBox(0, fdPreviewHeader, prevSummaryGrid, schedSection);
        previewPanel.setId("txn-investment-preview-panel");
        previewPanel.setVisible(false);
        previewPanel.setManaged(false);

        rightPanel = new VBox(0);
        rightPanel.setId("txn-investment-right-panel");
        rightPanel.setMinWidth(256);
        rightPanel.setMaxWidth(256);
        rightPanel.setVisible(false);
        rightPanel.setManaged(false);
        rightPanel.getChildren().add(dynamicBox);

        // ── Layout containers ─────────────────────────────────────────────────
        leftContent = new VBox();
        leftContent.setId("txn-investment-left-content");
        HBox.setHgrow(leftContent, Priority.ALWAYS);
        Separator sep = new Separator(Orientation.VERTICAL);
        sep.setId("txn-investment-separator");
        content = new HBox(0, leftContent, sep, rightPanel);
        content.setId("txn-investment-content");

        node = buildNode();
    }

    @Override public Node getNode() { return node; }

    private Node buildNode() {
        GridPane g = parent.panelGrid();
        g.setId("txn-investment-panel");
        parent.row(g, 0, "From Account*", fromCb);
        parent.row(g, 1, "To Account*",   destCb);
        return g;
    }

    // ── Dynamic fields ────────────────────────────────────────────────────────

    void refreshDynamicFields(InvestmentAccount.InvestmentType itype) {
        dynamicBox.getChildren().clear();
        invSchemeFld = invUnitsFld = null;
        invFdRefFld  = invFdRateFld = invFdMaturityAmtFld = null;
        invFdMaturityPicker = null;
        invFdInterestPayableCb = null;
        invFdPayoutAnchorCb = null;
        invFdPayoutMonthCb  = null;
        invFdPayoutDaySp    = null;
        invRdRefCb  = null;
        prevScheduleBox.getChildren().clear();

        if (itype == null || itype == InvestmentAccount.InvestmentType.PROVIDENT_FUND) {
            rightPanel.setVisible(false);
            rightPanel.setManaged(false);
            parent.resizeDialog(560);
            return;
        }
        rightPanel.setVisible(true);
        rightPanel.setManaged(true);
        parent.resizeDialog(828);

        VBox g = new VBox(4);
        g.setPadding(new Insets(8, 10, 4, 10));
        switch (itype) {
            case MUTUAL_FUNDS, EQUITY -> {
                invSchemeFld = parent.tf("optional");
                parent.setNodeId(invSchemeFld, "txn-investment-scheme-field");
                invUnitsFld  = parent.tf("e.g. 100.5");
                parent.setNodeId(invUnitsFld, "txn-investment-units-field");
                dynStackRow(g, "Scheme / Script", invSchemeFld);
                dynStackRow(g, "Units / NAV",     invUnitsFld);
            }
            case FIXED_DEPOSIT, DEBT_BONDS -> buildFdFields(g);
            case RECURRING_DEPOSIT         -> buildRdFields(g);
            default -> { /* PROVIDENT_FUND — no extra fields */ }
        }
        dynamicBox.getChildren().add(g);
    }

    private void buildFdFields(VBox g) {
        invFdRefFld            = parent.tf("required");
        parent.setNodeId(invFdRefFld, "txn-investment-fd-reference-field");
        invFdRateFld           = parent.tf("e.g. 7.5");
        parent.setNodeId(invFdRateFld, "txn-investment-fd-rate-field");
        invFdMaturityPicker    = new DatePicker();
        invFdMaturityPicker.setId("txn-investment-fd-maturity-date-picker");
        UiUtils.styleOnShow(invFdMaturityPicker);
        invFdMaturityAmtFld    = parent.tf("optional");
        parent.setNodeId(invFdMaturityAmtFld, "txn-investment-fd-maturity-amount-field");
        invFdInterestPayableCb = new ComboBox<>();
        invFdInterestPayableCb.setId("txn-investment-fd-interest-payable-combo");
        invFdInterestPayableCb.getItems().addAll(Transaction.InterestPayable.values());
        invFdInterestPayableCb.setPromptText("Select");
        invFdInterestPayableCb.setMaxWidth(Double.MAX_VALUE);
        invFdInterestPayableCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Transaction.InterestPayable ip, boolean empty) {
                super.updateItem(ip, empty);
                if (empty || ip == null) { setText(null); return; }
                StringBuilder sb = new StringBuilder();
                for (String word : ip.name().split("_")) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(Character.toUpperCase(word.charAt(0)));
                    sb.append(word.substring(1).toLowerCase());
                }
                setText(sb.toString());
            }
        });
        invFdInterestPayableCb.setButtonCell(invFdInterestPayableCb.getCellFactory().call(null));

        invFdPayoutAnchorCb = new ComboBox<>();
        invFdPayoutAnchorCb.setId("txn-investment-fd-payout-anchor-combo");
        invFdPayoutAnchorCb.getItems().addAll(Transaction.PayoutAnchor.values());
        invFdPayoutAnchorCb.setValue(Transaction.PayoutAnchor.ANNIVERSARY);
        invFdPayoutAnchorCb.setMaxWidth(Double.MAX_VALUE);
        invFdPayoutAnchorCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Transaction.PayoutAnchor a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) { setText(null); return; }
                setText(switch (a) {
                    case ANNIVERSARY -> "Anniversary of investment date";
                    case FIXED_DATE  -> "Fixed calendar date";
                });
            }
        });
        invFdPayoutAnchorCb.setButtonCell(invFdPayoutAnchorCb.getCellFactory().call(null));

        invFdPayoutMonthCb = new ComboBox<>();
        invFdPayoutMonthCb.setId("txn-investment-fd-payout-month-combo");
        invFdPayoutMonthCb.getItems().addAll(Month.values());
        invFdPayoutMonthCb.setPromptText("Month");
        invFdPayoutMonthCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Month m, boolean empty) {
                super.updateItem(m, empty);
                setText(empty || m == null ? null : m.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
            }
        });
        invFdPayoutMonthCb.setButtonCell(invFdPayoutMonthCb.getCellFactory().call(null));

        invFdPayoutDaySp = new Spinner<>(1, 28, 1);
        invFdPayoutDaySp.setId("txn-investment-fd-payout-day-spinner");
        invFdPayoutDaySp.setMaxWidth(Double.MAX_VALUE);
        invFdPayoutDaySp.setEditable(true);
        invFdPayoutDaySp.setPrefWidth(72);
        invFdPayoutDaySp.setMaxWidth(72);

        Label payoutDayLabel = new Label("Day");
        payoutDayLabel.setId("txn-investment-fd-payout-day-label");
        HBox payoutDateBox = new HBox(8, invFdPayoutMonthCb, payoutDayLabel, invFdPayoutDaySp);
        payoutDateBox.setId("txn-investment-fd-payout-date-box");
        payoutDateBox.setAlignment(Pos.CENTER_LEFT);
        payoutDateBox.setVisible(false);
        payoutDateBox.setManaged(false);
        HBox.setHgrow(invFdPayoutMonthCb, Priority.ALWAYS);
        invFdPayoutAnchorCb.valueProperty().addListener((obs, old, val) -> {
            boolean fixed = val == Transaction.PayoutAnchor.FIXED_DATE;
            payoutDateBox.setVisible(fixed);
            payoutDateBox.setManaged(fixed);
        });

        dynStackRow(g, "Reference No*",      invFdRefFld);
        dynStackRow(g, "Interest Rate (%)", invFdRateFld);
        dynStackRow(g, "Maturity Date",     invFdMaturityPicker);
        dynStackRow(g, "Maturity Amount",   invFdMaturityAmtFld);
        dynStackRow(g, "Interest Payable",  invFdInterestPayableCb);
        dynStackRow(g, "Payout Anchor",     invFdPayoutAnchorCb);
        dynStackRow(g, "Payout Date",       payoutDateBox);
    }

    private void buildRdFields(VBox g) {
        InvestmentAccount rdAcc = destCb.getValue();
        List<String> rdRefs = rdAcc != null
                ? parent.ds.getRdRefsForAccount(rdAcc.getId())
                : Collections.emptyList();
        if (rdRefs.isEmpty()) {
            Label err = new Label("No RD schedules found for this account.\nCreate a recurring schedule first.");
            err.setId("txn-investment-rd-missing-schedule-label");
            err.getStyleClass().add("text-error");
            err.setWrapText(true);
            g.getChildren().add(err);
        } else {
            invRdRefCb = new ComboBox<>();
            invRdRefCb.setId("txn-investment-rd-reference-combo");
            invRdRefCb.getItems().addAll(rdRefs);
            invRdRefCb.setEditable(false);
            invRdRefCb.setMaxWidth(Double.MAX_VALUE);
            invRdRefCb.setPromptText("Select RD reference");
            dynStackRow(g, "RD Reference No*", invRdRefCb);
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @Override public Transaction save() {
        LocalDate         date = parent.requireDate();
        String            desc = parent.requireText(parent.sharedDesc, "Description");
        long              amt  = parent.parsePaise(parent.sharedAmt);
        Account           from = parent.requireAccount(fromCb, "From Account");
        InvestmentAccount dest = destCb.getValue();
        if (dest == null) throw new IllegalArgumentException("Please select an investment account.");

        Transaction t = new Transaction(Transaction.Type.INVESTMENT, date, desc, amt);
        t.setFromAccountId(from.getId());
        t.setToAccountId(dest.getId());

        String userNotes = parent.nullIfBlank(parent.sharedNotes.getText());
        switch (dest.getInvestmentType()) {
            case MUTUAL_FUNDS, EQUITY -> {
                Transaction.InvestmentDetails mfInv = new Transaction.InvestmentDetails();
                if (invSchemeFld != null) mfInv.setSchemeScriptName(parent.nullIfBlank(invSchemeFld.getText()));
                if (invUnitsFld  != null && !invUnitsFld.getText().isBlank()) {
                    try { mfInv.setUnitsNav(Double.parseDouble(invUnitsFld.getText().trim())); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException("Units/NAV must be a number."); }
                }
                t.setInvestmentDetails(mfInv);
                t.setNotes(userNotes);
            }
            case FIXED_DEPOSIT, DEBT_BONDS -> {
                if (invFdRefFld == null || invFdRefFld.getText().isBlank())
                    throw new IllegalArgumentException("Reference No is required for Fixed Deposit / Bond transactions.");
                Transaction.FdDetails fd = new Transaction.FdDetails();
                fd.setRef(invFdRefFld.getText().trim());
                if (invFdRateFld != null && !invFdRateFld.getText().isBlank()) {
                    try { fd.setInterestRate(Double.parseDouble(invFdRateFld.getText().trim())); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException("Interest rate must be a number."); }
                }
                if (invFdMaturityPicker != null && invFdMaturityPicker.getValue() != null)
                    fd.setMaturityDate(invFdMaturityPicker.getValue());
                if (invFdMaturityAmtFld != null && !invFdMaturityAmtFld.getText().isBlank()) {
                    try { fd.setMaturityAmountPaise((long)(Double.parseDouble(invFdMaturityAmtFld.getText().trim()) * 100)); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException("Maturity amount must be a number."); }
                }
                if (invFdInterestPayableCb != null) fd.setInterestPayable(invFdInterestPayableCb.getValue());
                if (invFdPayoutAnchorCb != null && invFdPayoutAnchorCb.getValue() != null) {
                    Transaction.PayoutAnchor anchor = invFdPayoutAnchorCb.getValue();
                    fd.setPayoutAnchor(anchor);
                    if (anchor == Transaction.PayoutAnchor.FIXED_DATE) {
                        if (invFdPayoutMonthCb != null && invFdPayoutMonthCb.getValue() != null)
                            fd.setPayoutMonth(invFdPayoutMonthCb.getValue().getValue());
                        if (invFdPayoutDaySp != null)
                            fd.setPayoutDay(invFdPayoutDaySp.getValue());
                    }
                }
                Transaction.InvestmentDetails fdInv = new Transaction.InvestmentDetails();
                fdInv.setFd(fd);
                t.setInvestmentDetails(fdInv);
                t.setNotes(userNotes);
            }
            case RECURRING_DEPOSIT -> {
                List<String> rdRefs = parent.ds.getRdRefsForAccount(dest.getId());
                if (rdRefs.isEmpty())
                    throw new IllegalArgumentException(
                            "No RD schedules found for this account. Please create a recurring schedule first.");
                String rdRef = invRdRefCb != null ? invRdRefCb.getValue() : null;
                if (rdRef == null || rdRef.isBlank())
                    throw new IllegalArgumentException("Please select an RD reference number.");
                Transaction.FdDetails fd = new Transaction.FdDetails();
                fd.setRef(rdRef.trim());
                Transaction.InvestmentDetails rdInv = new Transaction.InvestmentDetails();
                rdInv.setFd(fd);
                t.setInvestmentDetails(rdInv);
                t.setNotes(userNotes);
            }
            default -> t.setNotes(userNotes);
        }
        return parent.persistTransaction(t);
    }

    // ── Prefill ───────────────────────────────────────────────────────────────

    @Override public void prefill(Transaction t) {
        parent.setAccount(fromCb, t.getFromAccountId());
        if (t.getToAccountId() == null) return;
        parent.ds.getInvestmentAccounts().stream()
                .filter(ia -> ia.getId().equals(t.getToAccountId()))
                .findFirst().ifPresent(ia -> {
                    destCb.setValue(ia); // triggers refreshDynamicFields
                    switch (ia.getInvestmentType()) {
                        case MUTUAL_FUNDS, EQUITY -> {
                            Transaction.InvestmentDetails mfInv = t.getInvestmentDetails();
                            if (mfInv != null) {
                                parent.setText(invSchemeFld, mfInv.getSchemeScriptName());
                                if (mfInv.getUnitsNav() != null && invUnitsFld != null)
                                    invUnitsFld.setText(mfInv.getUnitsNav().toString());
                            }
                        }
                        case FIXED_DEPOSIT, DEBT_BONDS -> {
                            Transaction.FdDetails fd = t.getInvestmentDetails() != null
                                    ? t.getInvestmentDetails().getFd() : null;
                            if (fd != null) {
                                parent.setText(invFdRefFld, fd.getRef());
                                if (fd.getInterestRate() != null && invFdRateFld != null)
                                    invFdRateFld.setText(fd.getInterestRate().toString());
                                if (fd.getMaturityDate() != null && invFdMaturityPicker != null)
                                    invFdMaturityPicker.setValue(fd.getMaturityDate());
                                if (fd.getMaturityAmountPaise() != null && invFdMaturityAmtFld != null)
                                    invFdMaturityAmtFld.setText(String.format("%.2f", fd.getMaturityAmountPaise() / 100.0));
                                if (fd.getInterestPayable() != null && invFdInterestPayableCb != null)
                                    invFdInterestPayableCb.setValue(fd.getInterestPayable());
                                if (invFdPayoutAnchorCb != null) {
                                    invFdPayoutAnchorCb.setValue(fd.getPayoutAnchor());
                                    if (fd.getPayoutAnchor() == Transaction.PayoutAnchor.FIXED_DATE) {
                                        if (fd.getPayoutMonth() != null && invFdPayoutMonthCb != null)
                                            invFdPayoutMonthCb.setValue(Month.of(fd.getPayoutMonth()));
                                        if (fd.getPayoutDay() != null && invFdPayoutDaySp != null)
                                            invFdPayoutDaySp.getValueFactory().setValue(fd.getPayoutDay());
                                    }
                                }
                            }
                            parent.sharedNotes.setText(t.getNotes() != null ? t.getNotes() : "");
                        }
                        case RECURRING_DEPOSIT -> {
                            if (invRdRefCb != null) {
                                Transaction.FdDetails fd = t.getInvestmentDetails() != null
                                        ? t.getInvestmentDetails().getFd() : null;
                                String rdRef = fd != null ? fd.getRef() : null;
                                if (rdRef != null) invRdRefCb.setValue(rdRef);
                                invRdRefCb.setDisable(true);
                                invRdRefCb.getStyleClass().add("combo-locked");
                            }
                            parent.sharedNotes.setText(t.getNotes() != null ? t.getNotes() : "");
                        }
                        default -> {}
                    }
                });
    }

    @Override public void applyContextAccount(Account acc, boolean isSource) {
        if (acc instanceof BankAccount)
            parent.setAccount(fromCb, acc.getId());
        else if (acc instanceof InvestmentAccount)
            destCb.getItems().stream()
                    .filter(a -> a.getId().equals(acc.getId()))
                    .findFirst().ifPresent(destCb::setValue);
    }

    @Override public boolean focusFirstEmpty() {
        if (fromCb.getValue() == null) { fromCb.requestFocus();  return true; }
        if (destCb.getValue() == null) { destCb.requestFocus();  return true; }
        return false;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void dynStackRow(VBox container, String labelText, Node field) {
        Label lbl = new Label(labelText);
        if (field.getId() != null && !field.getId().isBlank()) {
            lbl.setId(field.getId() + "-label");
        }
        lbl.getStyleClass().add("form-label");
        if (field instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        VBox wrapper = new VBox(3, lbl, field);
        wrapper.setId((field.getId() != null && !field.getId().isBlank())
                ? field.getId() + "-row"
                : "txn-investment-row-" + id(labelText));
        container.getChildren().add(wrapper);
    }

    private Label previewVal(String id) {
        Label l = new Label("—");
        l.setId(id);
        l.getStyleClass().add("text-form-value");
        return l;
    }

    private void prevRow(GridPane g, int row, String labelText, Label val) {
        Label lbl = new Label(labelText);
        lbl.setId("txn-investment-preview-" + id(labelText) + "-label");
        lbl.getStyleClass().add("text-body-muted");
        g.add(lbl, 0, row);
        g.add(val, 1, row);
    }

    private String id(String text) {
        return text.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
