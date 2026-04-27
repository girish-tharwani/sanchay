package com.sanchay.ui.profile;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import com.sanchay.ui.common.AccountCombos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

class SalaryEarningsPanel implements EarningFormPanel {

    private final EarningSource src;
    private final FamilyMember  member;
    private final DataStore     ds;

    private TextField               descFld, basicFld, hraFld, otherFld, taxFld, vpfFld, esppAmtFld;
    private ComboBox<Account>       acctCb;
    private Spinner<Integer>        daySp;
    private ComboBox<Category>      catCb;
    private ComboBox<InvestmentAccount> pfAcctCb, esppAcctCb;
    private CheckBox                gratuityChk, esppChk;

    SalaryEarningsPanel(EarningSource src, FamilyMember member, DataStore ds) {
        this.src    = src;
        this.member = member;
        this.ds     = ds;
    }

    InvestmentAccount getSelectedPfAccount()   { return pfAcctCb.getValue(); }
    InvestmentAccount getSelectedEsppAccount() { return esppAcctCb.getValue(); }

    @Override
    public Node buildPanel() {
        boolean configured = src.getBasicDaPaise() > 0;
        GridPane g = formGrid();

        descFld  = tf(src.getScheduleDescription() != null
                ? src.getScheduleDescription()
                : member.getName() + " — " + (src.getSourceName() != null ? src.getSourceName() : "Salary"));
        basicFld = tf(configured ? fmtAmt(src.getBasicDaPaise()) : "");
        basicFld.setPromptText("e.g. 600000");
        hraFld   = tf(configured && src.getHraPaise() > 0 ? fmtAmt(src.getHraPaise()) : "");
        hraFld.setPromptText("optional");
        otherFld = tf(configured && src.getOtherAllowancesPaise() > 0 ? fmtAmt(src.getOtherAllowancesPaise()) : "");
        otherFld.setPromptText("optional");
        taxFld   = tf(src.getEstimatedTaxRatePct() > 0 ? String.valueOf(src.getEstimatedTaxRatePct()) : "");
        taxFld.setPromptText("e.g. 20.0");
        vpfFld   = tf(src.getVpfPct() > 0 ? String.valueOf(src.getVpfPct()) : "");
        vpfFld.setPromptText("optional, e.g. 5.0");

        acctCb = new ComboBox<>();
        acctCb.setMaxWidth(Double.MAX_VALUE);
        acctCb.setPromptText("Select bank account");
        ds.getBankAccounts().forEach(acctCb.getItems()::add);
        AccountCombos.style(acctCb);
        if (src.getDepositAccountId() != null) {
            acctCb.getItems().stream().filter(a -> src.getDepositAccountId().equals(a.getId()))
                    .findFirst().ifPresent(acctCb::setValue);
        }

        daySp = new Spinner<>(1, 28, src.getDepositDay() > 0 ? src.getDepositDay() : 1);
        daySp.setEditable(true);
        daySp.setMaxWidth(Double.MAX_VALUE);

        catCb = new ComboBox<>();
        catCb.setMaxWidth(Double.MAX_VALUE);
        catCb.setPromptText("Select category (optional)");
        ds.getIncomeCategories().forEach(catCb.getItems()::add);
        if (src.getCategoryId() != null) {
            catCb.getItems().stream().filter(c -> src.getCategoryId().equals(c.getId()))
                    .findFirst().ifPresent(catCb::setValue);
        }

        pfAcctCb = new ComboBox<>();
        pfAcctCb.setMaxWidth(Double.MAX_VALUE);
        pfAcctCb.setPromptText("Select PF account (optional)");
        ds.getInvestmentAccounts().stream()
                .filter(a -> a.getInvestmentType() == InvestmentAccount.InvestmentType.PROVIDENT_FUND)
                .forEach(pfAcctCb.getItems()::add);
        AccountCombos.style(pfAcctCb);
        if (src.getPfScheduleId() != null) {
            RecurringTransaction pf = ds.findRecurringById(src.getPfScheduleId());
            if (pf != null && pf.getToAccountId() != null) {
                String id = pf.getToAccountId();
                pfAcctCb.getItems().stream().filter(a -> id.equals(a.getId()))
                        .findFirst().ifPresent(pfAcctCb::setValue);
            }
        }

        Hyperlink createPfLink = new Hyperlink("+ Add PF Account");
        createPfLink.getStyleClass().add("link-teal");
        createPfLink.setOnAction(e -> {
            InvestmentAccount created = showCreatePfDialog();
            if (created != null) {
                pfAcctCb.getItems().add(created);
                pfAcctCb.setValue(created);
            }
        });

        gratuityChk = new CheckBox("Include in breakdown");
        gratuityChk.setSelected(src.isGratuityEnabled());

        esppChk = new CheckBox("Include in breakdown");
        esppChk.setSelected(src.isEsppEnabled());

        esppAmtFld = tf(src.getEsppAmountPaise() > 0
                ? fmtAmt(src.getEsppAmountPaise()) : "");
        esppAmtFld.setPromptText("monthly amount");

        esppAcctCb = new ComboBox<>();
        esppAcctCb.setMaxWidth(Double.MAX_VALUE);
        esppAcctCb.setPromptText("Select equity account (optional)");
        ds.getInvestmentAccounts().stream()
                .filter(a -> a.getInvestmentType() == InvestmentAccount.InvestmentType.EQUITY)
                .forEach(esppAcctCb.getItems()::add);
        AccountCombos.style(esppAcctCb);
        if (src.getEsppScheduleId() != null) {
            RecurringTransaction espp = ds.findRecurringById(src.getEsppScheduleId());
            if (espp != null && espp.getToAccountId() != null) {
                String id = espp.getToAccountId();
                esppAcctCb.getItems().stream().filter(a -> id.equals(a.getId()))
                        .findFirst().ifPresent(esppAcctCb::setValue);
            }
        }

        Label esppAmtLbl  = new Label("SPP Amount (monthly, ₹)*");
        esppAmtLbl.getStyleClass().add("text-form-value");
        esppAmtLbl.setMinWidth(155);
        Label esppAcctLbl = new Label("SPP Investment Account");
        esppAcctLbl.getStyleClass().add("text-form-value");
        esppAcctLbl.setMinWidth(155);

        Runnable toggleEspp = () -> {
            boolean on = esppChk.isSelected();
            boolean hasEsppDetails = src.getEsppAmountPaise() > 0
                    || src.getEsppScheduleId() != null
                    || parseAmt(esppAmtFld.getText()) > 0
                    || esppAcctCb.getValue() != null;
            boolean showFields = on || hasEsppDetails;
            esppAmtLbl.setVisible(showFields);    esppAmtLbl.setManaged(showFields);
            esppAmtFld.setVisible(showFields);    esppAmtFld.setManaged(showFields);
            esppAcctLbl.setVisible(showFields);   esppAcctLbl.setManaged(showFields);
            esppAcctCb.setVisible(showFields);    esppAcctCb.setManaged(showFields);
            esppAmtLbl.setDisable(!on);
            esppAmtFld.setDisable(!on);
            esppAcctLbl.setDisable(!on);
            esppAcctCb.setDisable(!on);
        };
        toggleEspp.run();
        esppChk.selectedProperty().addListener((o, ov, nv) -> toggleEspp.run());

        int r = 0;
        row(g, r++, "Description*",             descFld);
        row(g, r++, "Basic + DA (annual)*",      basicFld);
        row(g, r++, "HRA (annual)",              hraFld);
        row(g, r++, "Other Allowances (annual)", otherFld);
        row(g, r++, "Estimated Tax Rate (%)",    taxFld);
        row(g, r++, "VPF (%)",                   vpfFld);
        row(g, r++, "To Account*",               acctCb);
        row(g, r++, "Day of Month*",             daySp);
        row(g, r++, "Category",                  catCb);
        row(g, r++, "PF Account",                pfAcctCb);
        g.add(createPfLink, 1, r++);
        row(g, r++, "Gratuity",                  gratuityChk);
        row(g, r++, "Share Purchase Plan",       esppChk);
        g.add(esppAmtLbl,  0, r); g.add(esppAmtFld,  1, r++); GridPane.setFillWidth(esppAmtFld,  true);
        g.add(esppAcctLbl, 0, r); g.add(esppAcctCb, 1, r);    GridPane.setFillWidth(esppAcctCb, true);

        Node rightPanel = new SalaryDeductionPanel().build(configured);

        ScrollPane leftScroll = new ScrollPane(g);
        leftScroll.setFitToWidth(true);
        leftScroll.getStyleClass().add("scroll-transparent");
        HBox.setHgrow(leftScroll, Priority.ALWAYS);

        HBox outer = new HBox(12, leftScroll, rightPanel);
        outer.setPadding(new Insets(0, 0, 8, 0));
        return outer;
    }

    @Override
    public void collectValues(EarningSource src) {
        src.setScheduleDescription(req(descFld, "Description"));
        src.setBasicDaPaise(parsePaise(basicFld, "Basic + DA"));
        src.setHraPaise(parseOptionalPaise(hraFld));
        src.setOtherAllowancesPaise(parseOptionalPaise(otherFld));
        src.setEstimatedTaxRatePct(parseOptional(taxFld));
        src.setVpfPct(parseOptional(vpfFld));
        Account acct = acctCb.getValue();
        if (acct == null) throw new IllegalArgumentException("Select an account.");
        src.setDepositAccountId(acct.getId());
        src.setDepositDay(daySp.getValue());
        src.setCategoryId(catCb.getValue() != null ? catCb.getValue().getId() : null);
        src.setGratuityEnabled(gratuityChk.isSelected());
        src.setEsppEnabled(esppChk.isSelected());
        if (src.isEsppEnabled()) {
            src.setEsppAmountPaise(parsePaise(esppAmtFld, "SPP Monthly Amount"));
        } else {
            src.setEsppAmountPaise(parseOptionalPaise(esppAmtFld));
        }
        if (src.computeScheduleAmountPaise() <= 0)
            throw new IllegalArgumentException(
                    "Computed in-hand is zero or negative for \"" + src.getSourceName() + "\" — check inputs.");
    }

    // ── Salary breakdown panel ────────────────────────────────────────────────

    private class SalaryDeductionPanel {

        private final Label calcGross, calcEmpPf, calcTds, calcEspp, calcInHand,
                            calcEmpEpf, calcEps, calcPfDeposit, calcEpsDeposit, calcGratuity;

        SalaryDeductionPanel() {
            calcGross      = calcLbl();
            calcEmpPf      = calcLbl();
            calcTds        = calcLbl();
            calcEspp       = calcLbl();
            calcInHand     = calcLbl();
            calcEmpEpf     = calcLbl();
            calcEps        = calcLbl();
            calcPfDeposit  = calcLbl();
            calcEpsDeposit = calcLbl();
            calcGratuity   = calcLbl();
            calcInHand.getStyleClass().addAll("text-success", "text-result-value");
        }

        Node build(boolean runInitialCalc) {
            GridPane calc = new GridPane();
            calc.setHgap(12); calc.setVgap(6);
            calc.setPadding(new Insets(12, 16, 12, 16));
            calc.getStyleClass().add("info-box");
            ColumnConstraints cc1 = new ColumnConstraints(190);
            ColumnConstraints cc2 = new ColumnConstraints(); cc2.setHgrow(Priority.ALWAYS);
            calc.getColumnConstraints().addAll(cc1, cc2);

            int cr = 0;
            calcRow(calc, cr++, "Gross Monthly:",              calcGross);
            calcRow(calc, cr++, "Employee PF (12% + VPF):",    calcEmpPf);
            calcRow(calc, cr++, "Estimated Monthly TDS:",      calcTds);
            calcRow(calc, cr++, "Share Purchase Plan:",        calcEspp);
            calc.add(new Separator(), 0, cr++, 2, 1);
            calcRow(calc, cr++, "Net In-hand:",                calcInHand);
            calc.add(new Separator(), 0, cr++, 2, 1);
            calcRow(calc, cr++, "Employer EPF:",               calcEmpEpf);
            calcRow(calc, cr++, "EPS:",                        calcEps);
            calc.add(new Separator(), 0, cr++, 2, 1);
            calcRow(calc, cr++, "Monthly PF Deposit:",         calcPfDeposit);
            calcRow(calc, cr++, "Monthly EPS Deposit:",        calcEpsDeposit);
            calc.add(new Separator(), 0, cr++, 2, 1);
            calcRow(calc, cr, "Gratuity (per year of service):", calcGratuity);

            Runnable recalc = this::recalc;
            basicFld   .textProperty().addListener((o, ov, nv) -> recalc.run());
            hraFld     .textProperty().addListener((o, ov, nv) -> recalc.run());
            otherFld   .textProperty().addListener((o, ov, nv) -> recalc.run());
            taxFld     .textProperty().addListener((o, ov, nv) -> recalc.run());
            vpfFld     .textProperty().addListener((o, ov, nv) -> recalc.run());
            gratuityChk.selectedProperty().addListener((o, ov, nv) -> recalc.run());
            esppChk    .selectedProperty().addListener((o, ov, nv) -> recalc.run());
            esppAmtFld .textProperty().addListener((o, ov, nv) -> recalc.run());
            if (runInitialCalc) recalc.run();

            Label title = new Label("Salary Breakdown (Monthly)");
            title.getStyleClass().add("text-section-title");
            VBox panel = new VBox(8, title, calc);
            panel.setPadding(new Insets(4, 4, 8, 8));
            panel.setMinWidth(330);
            panel.setMaxWidth(360);
            return panel;
        }

        private void recalc() {
            long   basic  = parseAmt(basicFld.getText());
            long   hra    = parseAmt(hraFld.getText());
            long   other  = parseAmt(otherFld.getText());
            double taxPct = parseD(taxFld.getText());
            double vpfPct = parseD(vpfFld.getText());

            long basicMo = basic / 12;
            long hraMo   = hra   / 12;
            long otherMo = other / 12;

            long gross        = basicMo + hraMo + otherMo;
            long mandatoryEpf = Math.round(basicMo * 0.12);
            long totalEmpPf   = Math.round(basicMo * (0.12 + vpfPct / 100.0));
            long tds          = Math.round((gross - mandatoryEpf) * taxPct / 100.0);
            boolean showEspp  = esppChk.isSelected();
            long espp         = showEspp ? parseAmt(esppAmtFld.getText()) : 0;
            long inHand       = gross - totalEmpPf - tds - espp;
            long empEpf       = Math.max(0L, Math.round(basicMo * 0.12) - 125_000L);
            long eps          = basicMo > 0 ? Math.min(125_000L, Math.round(basicMo * 0.0833)) : 0;
            long pfDeposit    = totalEmpPf + empEpf;
            boolean showGrat  = gratuityChk.isSelected();
            long gratuityPerYear = showGrat ? Math.round(basicMo * 15.0 / 26.0) : 0;

            calcGross     .setText(fmtR(gross));
            calcEmpPf     .setText(fmtR(totalEmpPf));
            calcTds       .setText(fmtR(tds));
            calcEspp      .setText(showEspp ? fmtR(espp) : "—");
            calcInHand    .setText(fmtR(inHand));
            calcEmpEpf    .setText(fmtR(empEpf));
            calcEps       .setText(basicMo > 0 ? fmtR(eps) : "—");
            calcPfDeposit .setText(fmtR(pfDeposit));
            calcEpsDeposit.setText(fmtR(pfDeposit == 0 ? 0 : eps));
            calcGratuity  .setText(showGrat ? fmtR(gratuityPerYear) : "—");
        }

        private Label calcLbl() {
            Label l = new Label("—");
            l.getStyleClass().add("text-form-value");
            return l;
        }

        private void calcRow(GridPane g, int row, String labelText, Label val) {
            Label lbl = new Label(labelText);
            lbl.getStyleClass().add("text-body-muted");
            g.add(lbl, 0, row);
            g.add(val, 1, row);
        }
    }

    // ── Create PF account inline dialog ──────────────────────────────────────

    private InvestmentAccount showCreatePfDialog() {
        Dialog<InvestmentAccount> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Add PF Account", "+", 360);

        GridPane g = UiUtils.buildFormGrid(130);
        g.setPadding(new Insets(16));

        TextField nameFld = tf(""); nameFld.setPromptText("e.g. EPF — Employer");
        TextField uanFld  = tf(""); uanFld.setPromptText("UAN number (optional)");
        TextField balFld  = tf(""); balFld.setPromptText("0.00");

        Label nameLbl = new Label("Account Name*"); nameLbl.getStyleClass().add("text-form-value");
        Label uanLbl  = new Label("UAN / A/C No.");  uanLbl.getStyleClass().add("text-form-value");
        Label balLbl  = new Label("Opening Balance (₹)"); balLbl.getStyleClass().add("text-form-value");
        g.add(nameLbl, 0, 0); g.add(nameFld, 1, 0); GridPane.setFillWidth(nameFld, true);
        g.add(uanLbl,  0, 1); g.add(uanFld,  1, 1); GridPane.setFillWidth(uanFld,  true);
        g.add(balLbl,  0, 2); g.add(balFld,  1, 2); GridPane.setFillWidth(balFld,  true);

        dlg.getDialogPane().setContent(g);
        ButtonType saveBtn = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) {
                Alert err = new Alert(Alert.AlertType.ERROR, "Account name is required.", ButtonType.OK);
                UiUtils.applyStylesheet(err);
                err.showAndWait();
                return null;
            }
            InvestmentAccount acc = new InvestmentAccount(name, InvestmentAccount.InvestmentType.PROVIDENT_FUND);
            acc.setFolioAccountNumber(uanFld.getText().trim());
            acc.setInvestedAmountPaise(parseAmt(balFld.getText()));
            ds.addAccount(acc);
            return acc;
        });

        return dlg.showAndWait().orElse(null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GridPane formGrid() {
        GridPane g = UiUtils.buildFormGrid(160);
        g.setPadding(new Insets(16));
        return g;
    }

    private TextField tf(String value) {
        TextField f = new TextField(value);
        f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }

    private void row(GridPane g, int rowIdx, String label, Node control) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("text-form-value");
        lbl.setMinWidth(155);
        g.add(lbl, 0, rowIdx);
        g.add(control, 1, rowIdx);
        GridPane.setFillWidth(control, true);
    }

    private String req(TextField f, String fieldName) {
        String v = f.getText().trim();
        if (v.isEmpty()) throw new IllegalArgumentException(fieldName + " is required.");
        return v;
    }

    private long parsePaise(TextField f, String fieldName) {
        String raw = f.getText().replace(",", "").replace(MoneyFormatter.symbol(), "").trim();
        if (raw.isEmpty()) throw new IllegalArgumentException(fieldName + " is required.");
        try {
            double v = Double.parseDouble(raw);
            if (v <= 0) throw new NumberFormatException();
            return Math.round(v * 100);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid positive amount for " + fieldName + ".");
        }
    }

    private long parseOptionalPaise(TextField f) {
        String raw = f.getText().replace(",", "").replace(MoneyFormatter.symbol(), "").trim();
        if (raw.isEmpty()) return 0;
        try { return Math.round(Double.parseDouble(raw) * 100); }
        catch (NumberFormatException e) { return 0; }
    }

    private double parseOptional(TextField f) {
        String raw = f.getText().replace(",", "").trim();
        if (raw.isEmpty()) return 0;
        try { return Double.parseDouble(raw); }
        catch (NumberFormatException e) { return 0; }
    }

    private long parseAmt(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Math.round(Double.parseDouble(
                s.replace(",", "").replace(MoneyFormatter.symbol(), "").trim()) * 100); }
        catch (NumberFormatException e) { return 0; }
    }

    private double parseD(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Double.parseDouble(s.replace(",", "").trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private String fmtAmt(long paise) { return String.format("%.2f", paise / 100.0); }
    private String fmtR(long paise)   { return MoneyFormatter.format(paise); }
}
