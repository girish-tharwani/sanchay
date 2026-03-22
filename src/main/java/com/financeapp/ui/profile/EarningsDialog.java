package com.financeapp.ui.profile;

import com.financeapp.model.*;
import com.financeapp.service.DataStore;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;

/**
 * Dialog for configuring earnings for a family member.
 *
 * Two tabs:
 *   Simple  — single amount + frequency → creates one INCOME recurring schedule.
 *   Salary  — structured breakdown (Basic+DA, HRA, Other Allowances, tax rate) →
 *             computes net in-hand live and creates one monthly INCOME schedule.
 *
 * On Save: updates the FamilyMember model and creates or updates the linked
 * RecurringTransaction. The schedule ID is stored on the member so it can be
 * paused / resumed / updated when the member's earning flag changes.
 */
public class EarningsDialog extends Dialog<Boolean> {

    private final FamilyMember member;
    private final DataStore    ds = DataStore.getInstance();

    // ── Simple form field refs ────────────────────────────────────────────────
    private TextField                                simpleDescFld;
    private TextField                                simpleAmtFld;
    private ComboBox<RecurringTransaction.Frequency> simpleFreqCb;
    private ComboBox<Account>                        simpleAcctCb;
    private Spinner<Integer>                         simpleDaySp;
    private ComboBox<Category>                       simpleCatCb;

    // ── Salary form field refs ────────────────────────────────────────────────
    private TextField          salDescFld;
    private TextField          salBasicFld;
    private TextField          salHraFld;
    private TextField          salOtherFld;
    private TextField          salTaxFld;
    private TextField          salVpfFld;
    private ComboBox<Account>  salAcctCb;
    private Spinner<Integer>   salDaySp;
    private ComboBox<Category> salCatCb;

    // ── Salary form extras ────────────────────────────────────────────────────
    private CheckBox gratuityChk;

    // ── Salary calc labels ────────────────────────────────────────────────────
    private Label calcGross, calcEmpPf, calcTds, calcInHand;
    private Label calcEmpEpf, calcEps, calcTotalEmp, calcPfDeposit, calcEpsDeposit;
    private Label calcGratuity;

    // ── PF account selector ───────────────────────────────────────────────────
    private ComboBox<InvestmentAccount> salPfAcctCb;

    public EarningsDialog(FamilyMember member) {
        this.member = member;
        setTitle("Earnings — " + member.getName());
        setHeaderText(null);
        getDialogPane().setPrefWidth(780);
        getDialogPane().setPrefHeight(600);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab simpleTab = new Tab("Simple",            buildSimpleForm());
        Tab salaryTab = new Tab("Structured Salary", buildSalaryForm());
        tabs.getTabs().addAll(simpleTab, salaryTab);

        // Pre-select tab based on existing earning type
        if (member.getEarningType() == FamilyMember.EarningType.SALARY)
            tabs.getSelectionModel().select(1);

        getDialogPane().setContent(tabs);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            try {
                Tab active = tabs.getSelectionModel().getSelectedItem();
                if (active == simpleTab) saveSimple();
                else                    saveSalary();
                return Boolean.TRUE;
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
                return null;
            }
        });
    }

    // ── Simple form ───────────────────────────────────────────────────────────

    private Node buildSimpleForm() {
        GridPane g = formGrid();

        boolean isSim = member.getEarningType() == FamilyMember.EarningType.SIMPLE;

        simpleDescFld = tf(isSim && member.getScheduleDescription() != null
                ? member.getScheduleDescription() : member.getName() + " — Income");
        simpleAmtFld  = tf(isSim && member.getSimpleAmountPaise() > 0
                ? fmtAmt(member.getSimpleAmountPaise()) : "");
        simpleAmtFld.setPromptText("0.00");

        simpleFreqCb = new ComboBox<>();
        simpleFreqCb.getItems().addAll(RecurringTransaction.Frequency.values());
        simpleFreqCb.setValue(RecurringTransaction.Frequency.MONTHLY);
        simpleFreqCb.setMaxWidth(Double.MAX_VALUE);
        simpleFreqCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(RecurringTransaction.Frequency item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatFrequency(item));
            }
        });
        simpleFreqCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(RecurringTransaction.Frequency item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatFrequency(item));
            }
        });
        if (isSim && member.getSimpleFrequency() != null) {
            try {
                simpleFreqCb.setValue(RecurringTransaction.Frequency
                        .valueOf(member.getSimpleFrequency()));
            } catch (IllegalArgumentException ignored) {}
        }

        simpleAcctCb = accountCombo();
        prefillAccount(simpleAcctCb, isSim ? member.getDepositAccountId() : null);

        simpleDaySp = new Spinner<>(1, 28,
                isSim && member.getDepositDay() > 0 ? member.getDepositDay() : 1);
        simpleDaySp.setMaxWidth(Double.MAX_VALUE);

        simpleCatCb = categoryCombo();
        prefillCategory(simpleCatCb, existingScheduleCategoryId());

        int r = 0;
        row(g, r++, "Description*",  simpleDescFld);
        row(g, r++, "Amount (₹)*",   simpleAmtFld);
        row(g, r++, "Frequency*",    simpleFreqCb);
        row(g, r++, "Into Account*", simpleAcctCb);
        row(g, r++, "Day of Month*", simpleDaySp);
        row(g, r,   "Category",      simpleCatCb);

        return scroll(g);
    }

    private void saveSimple() {
        String desc = req(simpleDescFld, "Description");
        long   amt  = parsePaise(simpleAmtFld, "Amount");

        if (simpleFreqCb.getValue() == null)
            throw new IllegalArgumentException("Select a frequency.");
        RecurringTransaction.Frequency freq = simpleFreqCb.getValue();

        Account acct = reqAccount(simpleAcctCb);
        int     day  = simpleDaySp.getValue();

        member.setEarningType(FamilyMember.EarningType.SIMPLE);
        member.setSimpleAmountPaise(amt);
        member.setSimpleFrequency(freq.name());
        member.setDepositAccountId(acct.getId());
        member.setDepositDay(day);
        member.setScheduleDescription(desc);

        createOrUpdateSchedule(amt, freq, acct.getId(), day, desc,
                simpleCatCb.getValue() != null ? simpleCatCb.getValue().getId() : null);
        ds.updateFamilyMember(member);
    }

    // ── Salary form ───────────────────────────────────────────────────────────

    private Node buildSalaryForm() {
        boolean isSal = member.getEarningType() == FamilyMember.EarningType.SALARY;

        GridPane g = formGrid();

        salDescFld  = tf(isSal && member.getScheduleDescription() != null
                ? member.getScheduleDescription() : member.getName() + " — Salary");
        salBasicFld = tf(isSal && member.getBasicDaPaise() > 0
                ? fmtAmt(member.getBasicDaPaise()) : "");
        salBasicFld.setPromptText("e.g. 50000");
        salHraFld   = tf(isSal && member.getHraPaise() > 0
                ? fmtAmt(member.getHraPaise()) : "");
        salHraFld.setPromptText("optional");
        salOtherFld = tf(isSal && member.getOtherAllowancesPaise() > 0
                ? fmtAmt(member.getOtherAllowancesPaise()) : "");
        salOtherFld.setPromptText("optional");
        salTaxFld   = tf(isSal && member.getEstimatedTaxRatePct() > 0
                ? String.valueOf(member.getEstimatedTaxRatePct()) : "");
        salTaxFld.setPromptText("e.g. 20.0");
        salVpfFld   = tf(isSal && member.getVpfPct() > 0
                ? String.valueOf(member.getVpfPct()) : "");
        salVpfFld.setPromptText("optional, e.g. 5.0");

        salAcctCb = accountCombo();
        prefillAccount(salAcctCb, isSal ? member.getDepositAccountId() : null);

        salDaySp = new Spinner<>(1, 28,
                isSal && member.getDepositDay() > 0 ? member.getDepositDay() : 1);
        salDaySp.setMaxWidth(Double.MAX_VALUE);

        salCatCb = categoryCombo();
        prefillCategory(salCatCb, existingScheduleCategoryId());

        // PF account selector
        salPfAcctCb = pfAccountCombo();
        prefillPfAccount(salPfAcctCb, isSal ? member.getPfScheduleId() : null);

        Hyperlink createPfLink = new Hyperlink("+ Add PF Account");
        createPfLink.setStyle("-fx-font-size: 11px;");
        createPfLink.setOnAction(e -> {
            InvestmentAccount created = showCreatePfDialog();
            if (created != null) {
                salPfAcctCb.getItems().add(created);
                salPfAcctCb.setValue(created);
            }
        });

        gratuityChk = new CheckBox("Include in breakdown");
        gratuityChk.setStyle("-fx-font-size: 12px;");
        gratuityChk.setSelected(isSal && member.isGratuityEnabled());

        int r = 0;
        row(g, r++, "Description*",           salDescFld);
        row(g, r++, "Basic + DA (₹/mo)*",     salBasicFld);
        row(g, r++, "HRA (₹/month)",           salHraFld);
        row(g, r++, "Other Allowances",        salOtherFld);
        row(g, r++, "Estimated Tax Rate (%)",  salTaxFld);
        row(g, r++, "VPF (%)",                 salVpfFld);
        row(g, r++, "Into Account*",           salAcctCb);
        row(g, r++, "Day of Month*",           salDaySp);
        row(g, r++, "Category",                salCatCb);
        row(g, r++, "PF Account",              salPfAcctCb);
        g.add(createPfLink, 1, r++);
        row(g, r,   "Gratuity",               gratuityChk);

        // ── Calculation panel ─────────────────────────────────────────────────
        calcGross    = calcVal(); calcEmpPf   = calcVal(); calcTds      = calcVal();
        calcInHand   = calcVal(); calcEmpEpf  = calcVal(); calcEps      = calcVal("₹1,250.00");
        calcTotalEmp = calcVal(); calcPfDeposit = calcVal(); calcEpsDeposit = calcVal(); calcGratuity = calcVal();
        calcInHand.setStyle(calcInHand.getStyle()
                + " -fx-font-weight: bold; -fx-text-fill: #1B5E20; -fx-font-size: 13px;");

        GridPane calc = new GridPane();
        calc.setHgap(12); calc.setVgap(6);
        calc.setPadding(new Insets(12, 16, 12, 16));
        calc.setStyle("-fx-background-color: #F0F4F8; -fx-background-radius: 6;");
        ColumnConstraints cc1 = new ColumnConstraints(220);
        ColumnConstraints cc2 = new ColumnConstraints(); cc2.setHgrow(Priority.ALWAYS);
        calc.getColumnConstraints().addAll(cc1, cc2);

        int cr = 0;
        calcRow(calc, cr++, "Gross Monthly:",             calcGross);
        calcRow(calc, cr++, "Employee PF (12% + VPF):",   calcEmpPf);
        calcRow(calc, cr++, "Estimated Monthly TDS:",     calcTds);
        calc.add(new Separator(), 0, cr++, 2, 1);
        calcRow(calc, cr++, "Net In-hand:",               calcInHand);
        calc.add(new Separator(), 0, cr++, 2, 1);
        calcRow(calc, cr++, "Employer EPF:",              calcEmpEpf);
        calcRow(calc, cr++, "EPS:",                       calcEps);
        calcRow(calc, cr++, "Total Employer Cost:",       calcTotalEmp);
        calc.add(new Separator(), 0, cr++, 2, 1);
        calcRow(calc, cr++, "Monthly PF Deposit:",        calcPfDeposit);
        calcRow(calc, cr++, "Monthly EPS Deposit:",       calcEpsDeposit);
        calc.add(new Separator(), 0, cr++, 2, 1);
        calcRow(calc, cr,   "Gratuity (per year of service):", calcGratuity);

        // Wire live updates
        Runnable recalc = this::recalcSalary;
        salBasicFld .textProperty().addListener((o, ov, nv) -> recalc.run());
        salHraFld   .textProperty().addListener((o, ov, nv) -> recalc.run());
        salOtherFld .textProperty().addListener((o, ov, nv) -> recalc.run());
        salTaxFld   .textProperty().addListener((o, ov, nv) -> recalc.run());
        salVpfFld   .textProperty().addListener((o, ov, nv) -> recalc.run());
        gratuityChk .selectedProperty().addListener((o, ov, nv) -> recalc.run());
        if (isSal) recalc.run(); // seed with existing values

        Label calcTitle = new Label("Salary Breakdown (Monthly)");
        calcTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; "
                + "-fx-text-fill: #1F4E79; -fx-padding: 8 0 4 0;");

        // ── Side-by-side layout ───────────────────────────────────────────────
        ScrollPane leftScroll = new ScrollPane(g);
        leftScroll.setFitToWidth(true);
        leftScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        HBox.setHgrow(leftScroll, Priority.ALWAYS);

        VBox rightPanel = new VBox(8, calcTitle, calc);
        rightPanel.setPadding(new Insets(4, 4, 8, 8));
        rightPanel.setMinWidth(330);
        rightPanel.setMaxWidth(360);

        HBox outer = new HBox(12, leftScroll, rightPanel);
        outer.setPadding(new Insets(0, 0, 8, 0));
        return outer;
    }

    private void recalcSalary() {
        long   basic   = parseAmountStr(salBasicFld.getText());
        long   hra     = parseAmountStr(salHraFld.getText());
        long   other   = parseAmountStr(salOtherFld.getText());
        double taxPct  = parseDoubleStr(salTaxFld.getText());
        double vpfPct  = parseDoubleStr(salVpfFld.getText());

        long gross        = basic + hra + other;
        long mandatoryEpf = Math.round(basic * 0.12);
        long totalEmpPf   = Math.round(basic * (0.12 + vpfPct / 100.0));
        long tds          = Math.round((gross - mandatoryEpf) * taxPct / 100.0);
        long inHand       = gross - totalEmpPf - tds;
        long empEpf    = Math.max(0L, Math.round(basic * 0.12) - 125_000L);
        long eps       = 125_000L;   // ₹1,250 in paise
        long totalEmp  = gross + Math.round(basic * 0.12);

        long pfDeposit  = totalEmpPf + empEpf;  // employee (incl. VPF) + employer EPF, excl. EPS
        long epsDeposit = eps;

        // Gratuity = (Basic+DA) × 15/26 per year of service (Payment of Gratuity Act)
        boolean showGratuity = gratuityChk != null && gratuityChk.isSelected();
        long gratuityPerYear = showGratuity ? Math.round(basic * 15.0 / 26.0) : 0;

        calcGross    .setText(fmtR(gross));
        calcEmpPf    .setText(fmtR(totalEmpPf));
        calcTds      .setText(fmtR(tds));
        calcInHand   .setText(fmtR(inHand));
        calcEmpEpf   .setText(fmtR(empEpf));
        calcEps      .setText(fmtR(eps));
        calcTotalEmp .setText(fmtR(totalEmp));
        calcPfDeposit .setText(fmtR(pfDeposit));
        calcEpsDeposit.setText(fmtR(epsDeposit));
        if (calcGratuity != null)
            calcGratuity.setText(showGratuity ? fmtR(gratuityPerYear) : "—");
    }

    private void saveSalary() {
        String desc    = req(salDescFld, "Description");
        long   basicDa = parsePaise(salBasicFld, "Basic + DA");
        long   hra     = parseOptionalPaise(salHraFld);
        long   other   = parseOptionalPaise(salOtherFld);
        double taxPct  = parseOptionalDouble(salTaxFld);
        double vpfPct  = parseOptionalDouble(salVpfFld);
        Account acct   = reqAccount(salAcctCb);
        int    day     = salDaySp.getValue();

        long gross        = basicDa + hra + other;
        long mandatoryEpf = Math.round(basicDa * 0.12);
        long totalEmpPf   = Math.round(basicDa * (0.12 + vpfPct / 100.0));
        long tds          = Math.round((gross - mandatoryEpf) * taxPct / 100.0);
        long inHand       = gross - totalEmpPf - tds;

        if (inHand <= 0)
            throw new IllegalArgumentException(
                    "Computed in-hand is zero or negative — check inputs.");

        member.setEarningType(FamilyMember.EarningType.SALARY);
        member.setBasicDaPaise(basicDa);
        member.setHraPaise(hra);
        member.setOtherAllowancesPaise(other);
        member.setEstimatedTaxRatePct(taxPct);
        member.setVpfPct(vpfPct);
        member.setGratuityEnabled(gratuityChk.isSelected());
        member.setDepositAccountId(acct.getId());
        member.setDepositDay(day);
        member.setScheduleDescription(desc);

        createOrUpdateSchedule(inHand, RecurringTransaction.Frequency.MONTHLY,
                acct.getId(), day, desc,
                salCatCb.getValue() != null ? salCatCb.getValue().getId() : null);

        // PF schedule — only if user selected a PF account
        InvestmentAccount pfAcct = salPfAcctCb.getValue();
        if (pfAcct != null) {
            long empPf   = Math.round(basicDa * (0.12 + vpfPct / 100.0));
            long empEpf  = Math.max(0L, Math.round(basicDa * 0.12) - 125_000L);
            long pfDeposit = empPf + empEpf; // excludes EPS (₹1,250 goes to pension scheme)
            createOrUpdatePfSchedule(pfDeposit, pfAcct.getId(), day,
                    member.getName() + " — PF Deposit");
        }

        ds.updateFamilyMember(member);
    }

    // ── Schedule lifecycle ────────────────────────────────────────────────────

    private void createOrUpdateSchedule(long amountPaise,
                                        RecurringTransaction.Frequency freq,
                                        String accountId, int day, String description,
                                        String categoryId) {
        RecurringTransaction existing =
                ds.findRecurringById(member.getRecurringScheduleId());

        if (existing != null) {
            existing.setAmountPaise(amountPaise);
            existing.setFrequency(freq);
            existing.setToAccountId(accountId);
            existing.setDueDayOfMonth(day);
            existing.setDescription(description);
            existing.setCategoryId(categoryId);
            existing.setStatus(RecurringTransaction.Status.ACTIVE);
            ds.saveRecurringNow();
        } else {
            RecurringTransaction rt = new RecurringTransaction(
                    description, Transaction.Type.INCOME,
                    freq, day, LocalDate.now(), amountPaise);
            rt.setToAccountId(accountId);
            rt.setCategoryId(categoryId);
            ds.addRecurring(rt);
            member.setRecurringScheduleId(rt.getId());
        }
    }

    /** Returns the categoryId already saved on the linked recurring schedule, or null. */
    private String existingScheduleCategoryId() {
        RecurringTransaction sched = ds.findRecurringById(member.getRecurringScheduleId());
        return sched != null ? sched.getCategoryId() : null;
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private GridPane formGrid() {
        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(16));
        ColumnConstraints c1 = new ColumnConstraints(160);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);
        return g;
    }

    private ScrollPane scroll(GridPane g) {
        ScrollPane sp = new ScrollPane(g);
        sp.setFitToWidth(true);
        sp.setPrefHeight(260);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return sp;
    }

    private void row(GridPane g, int rowIdx, String labelText, Node control) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #1A1A2E; -fx-font-size: 12px;");
        lbl.setMinWidth(155);
        g.add(lbl, 0, rowIdx);
        g.add(control, 1, rowIdx);
        GridPane.setFillWidth(control, true);
    }

    private void calcRow(GridPane g, int row, String labelText, Label val) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #595959; -fx-font-size: 12px;");
        g.add(lbl, 0, row);
        g.add(val, 1, row);
    }

    private TextField tf(String value) {
        TextField tf = new TextField(value);
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private ComboBox<Account> accountCombo() {
        ComboBox<Account> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("Select bank account");
        ds.getBankAccounts().forEach(cb.getItems()::add);
        return cb;
    }

    private void prefillAccount(ComboBox<Account> cb, String accountId) {
        if (accountId == null) return;
        cb.getItems().stream()
                .filter(a -> accountId.equals(a.getId()))
                .findFirst().ifPresent(cb::setValue);
    }

    private ComboBox<Category> categoryCombo() {
        ComboBox<Category> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("Select category (optional)");
        ds.getIncomeCategories().forEach(cb.getItems()::add);
        return cb;
    }

    private void prefillCategory(ComboBox<Category> cb, String categoryId) {
        if (categoryId == null) return;
        cb.getItems().stream()
                .filter(c -> categoryId.equals(c.getId()))
                .findFirst().ifPresent(cb::setValue);
    }

    private Label calcVal()           { return calcVal("—"); }
    private Label calcVal(String txt) {
        Label l = new Label(txt);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: #1A1A2E;");
        return l;
    }

    // ── Value extractors ──────────────────────────────────────────────────────

    private String req(TextField tf, String fieldName) {
        String v = tf.getText().trim();
        if (v.isEmpty()) throw new IllegalArgumentException(fieldName + " is required.");
        return v;
    }

    private long parsePaise(TextField tf, String fieldName) {
        String raw = tf.getText().replace(",", "").replace("₹", "").trim();
        if (raw.isEmpty()) throw new IllegalArgumentException(fieldName + " is required.");
        try {
            double v = Double.parseDouble(raw);
            if (v <= 0) throw new NumberFormatException();
            return Math.round(v * 100);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid positive amount for " + fieldName + ".");
        }
    }

    private long parseOptionalPaise(TextField tf) {
        String raw = tf.getText().replace(",", "").replace("₹", "").trim();
        if (raw.isEmpty()) return 0;
        try { return Math.round(Double.parseDouble(raw) * 100); }
        catch (NumberFormatException e) { return 0; }
    }

    private double parseOptionalDouble(TextField tf) {
        String raw = tf.getText().replace(",", "").trim();
        if (raw.isEmpty()) return 0;
        try { return Double.parseDouble(raw); }
        catch (NumberFormatException e) { return 0; }
    }

    private Account reqAccount(ComboBox<Account> cb) {
        Account a = cb.getValue();
        if (a != null) return a;
        throw new IllegalArgumentException("Select an account.");
    }

    private long   parseAmountStr(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Math.round(Double.parseDouble(
                s.replace(",", "").replace("₹", "").trim()) * 100); }
        catch (NumberFormatException e) { return 0; }
    }

    private double parseDoubleStr(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Double.parseDouble(s.replace(",", "").trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private String fmtAmt(long paise) { return String.format("%.2f", paise / 100.0); }
    private String fmtR  (long paise) { return String.format("₹%,.2f", paise / 100.0); }

    private void createOrUpdatePfSchedule(long amountPaise, String pfAccountId,
                                          int day, String description) {
        RecurringTransaction existing = ds.findRecurringById(member.getPfScheduleId());
        if (existing != null) {
            existing.setAmountPaise(amountPaise);
            existing.setToAccountId(pfAccountId);
            existing.setDueDayOfMonth(day);
            existing.setDescription(description);
            existing.setStatus(RecurringTransaction.Status.ACTIVE);
            ds.saveRecurringNow();
        } else {
            RecurringTransaction rt = new RecurringTransaction(
                    description, Transaction.Type.INVESTMENT,
                    RecurringTransaction.Frequency.MONTHLY, day,
                    LocalDate.now(), amountPaise);
            rt.setToAccountId(pfAccountId);
            // fromAccountId intentionally null — PF is a direct deposit, not from salary account
            ds.addRecurring(rt);
            member.setPfScheduleId(rt.getId());
        }
    }

    private ComboBox<InvestmentAccount> pfAccountCombo() {
        ComboBox<InvestmentAccount> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("Select PF account (optional)");
        ds.getInvestmentAccounts().stream()
                .filter(a -> a.getInvestmentType()
                        == InvestmentAccount.InvestmentType.PROVIDENT_FUND)
                .forEach(cb.getItems()::add);
        return cb;
    }

    private void prefillPfAccount(ComboBox<InvestmentAccount> cb, String pfScheduleId) {
        if (pfScheduleId == null) return;
        RecurringTransaction sched = ds.findRecurringById(pfScheduleId);
        if (sched == null || sched.getToAccountId() == null) return;
        String acctId = sched.getToAccountId();
        cb.getItems().stream()
                .filter(a -> acctId.equals(a.getId()))
                .findFirst().ifPresent(cb::setValue);
    }

    private InvestmentAccount showCreatePfDialog() {
        Dialog<InvestmentAccount> dlg = new Dialog<>();
        dlg.setTitle("Add PF Account");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(360);

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(16));
        ColumnConstraints c1 = new ColumnConstraints(130);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        TextField nameFld = new TextField(); nameFld.setPromptText("e.g. EPF — Employer");
        nameFld.setMaxWidth(Double.MAX_VALUE);
        TextField uanFld  = new TextField(); uanFld.setPromptText("UAN number (optional)");
        uanFld.setMaxWidth(Double.MAX_VALUE);
        TextField balFld  = new TextField(); balFld.setPromptText("0.00");
        balFld.setMaxWidth(Double.MAX_VALUE);

        Label nameLbl = new Label("Account Name*");
        nameLbl.setStyle("-fx-text-fill: #1A1A2E; -fx-font-size: 12px;");
        Label uanLbl  = new Label("UAN / A/C No.");
        uanLbl.setStyle("-fx-text-fill: #1A1A2E; -fx-font-size: 12px;");
        Label balLbl  = new Label("Opening Balance (₹)");
        balLbl.setStyle("-fx-text-fill: #1A1A2E; -fx-font-size: 12px;");
        g.add(nameLbl, 0, 0); g.add(nameFld, 1, 0); GridPane.setFillWidth(nameFld, true);
        g.add(uanLbl,  0, 1); g.add(uanFld,  1, 1); GridPane.setFillWidth(uanFld,  true);
        g.add(balLbl,  0, 2); g.add(balFld,  1, 2); GridPane.setFillWidth(balFld,  true);

        dlg.getDialogPane().setContent(g);
        ButtonType saveBtn = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { showError("Account name is required."); return null; }
            InvestmentAccount acc = new InvestmentAccount(
                    name, InvestmentAccount.InvestmentType.PROVIDENT_FUND);
            acc.setFolioAccountNumber(uanFld.getText().trim());
            acc.setInvestedAmountPaise(parseAmountStr(balFld.getText()));
            ds.addAccount(acc);
            return acc;
        });

        return dlg.showAndWait().orElse(null);
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

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Validation Error"); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}
