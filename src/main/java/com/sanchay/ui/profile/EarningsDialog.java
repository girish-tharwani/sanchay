package com.sanchay.ui.profile;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import com.sanchay.ui.common.AccountCombos;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Dialog for configuring earnings for a family member.
 * Supports multiple income sources (EarningSource), each creating its own
 * recurring INCOME schedule. Each source gets its own tab; a "+ Income" tab
 * appends new sources.
 *
 * SIMPLE sources: gross amount entered, net = gross × (1 − tax%). Schedule uses net amount.
 * SALARY sources: annual inputs entered, monthly net computed via PF + TDS deductions.
 *
 * On Save: all tabs are validated and their EarningSource objects are persisted.
 */
public class EarningsDialog extends Dialog<Boolean> {

    private final FamilyMember member;
    private final DataStore    ds = DataStore.getInstance();

    private final List<EarningSource>         workingSources = new ArrayList<>();
    private final Map<String, SourceFormRefs> formRefs       = new LinkedHashMap<>();
    private final List<EarningSource>         pendingDeletes = new ArrayList<>();
    private Boolean                           pendingResult  = null;

    // ── Inner class holding all form field refs for one source tab ────────────
    private static class SourceFormRefs {
        TextField sourceNameFld, descFld;
        // SIMPLE
        TextField simpleAmtFld, simpleTaxFld;
        ComboBox<RecurringTransaction.Frequency> simpleFreqCb;
        ComboBox<Account> simpleAcctCb;
        Spinner<Integer>  simpleDaySp;
        ComboBox<Category> simpleCatCb;
        Label netAmountHint;
        // SALARY
        TextField salBasicFld, salHraFld, salOtherFld, salTaxFld, salVpfFld;
        ComboBox<Account> salAcctCb;
        Spinner<Integer>  salDaySp;
        ComboBox<Category> salCatCb;
        ComboBox<InvestmentAccount> salPfAcctCb;
        CheckBox gratuityChk;
        CheckBox esppChk;
        TextField esppAmtFld;
        ComboBox<InvestmentAccount> esppAcctCb;
    }

    public EarningsDialog(FamilyMember member) {
        this.member = member;
        UiUtils.initDialog(this, "Earnings — " + member.getName(), MoneyFormatter.symbol(), 950);
        getDialogPane().setMinHeight(720);

        TabPane tabs = buildTabPane();
        getDialogPane().setContent(tabs);

        ButtonType saveBtn = UiUtils.addSaveCancel(getDialogPane());

        // Intercept Save click: validate, keep dialog open on error (same pattern as TransactionDialog)
        Platform.runLater(() -> {
            Button btn = (Button) getDialogPane().lookupButton(saveBtn);
            if (btn != null) btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                Boolean result = saveAll(tabs);
                if (result == null) {
                    ev.consume(); // validation failed — keep dialog open
                } else {
                    pendingResult = result;
                }
            });
        });

        setResultConverter(bt -> bt == saveBtn ? pendingResult : null);
    }

    // ── Tab pane construction ─────────────────────────────────────────────────

    private TabPane buildTabPane() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        List<EarningSource> existing = member.getEarningSources();
        if (existing == null || existing.isEmpty()) {
            EarningSource blank = new EarningSource();
            blank.setSourceName("Income - Structured");
            blank.setType(FamilyMember.EarningType.SALARY);
            existing = List.of(blank);
        }
        for (EarningSource src : existing) {
            workingSources.add(src);
            tabs.getTabs().add(buildSourceTab(src, tabs));
        }

        Tab addTab = new Tab("+ Income (Simple / Structured)");
        addTab.setClosable(false);
        tabs.getTabs().add(addTab);

        tabs.getSelectionModel().selectedItemProperty().addListener((obs, prev, curr) -> {
            if (curr == addTab) {
                Platform.runLater(() -> {
                    if (prev != null) tabs.getSelectionModel().select(prev);
                });
                showAddSourceDialog(tabs, addTab);
            }
        });

        return tabs;
    }

    private Tab buildSourceTab(EarningSource src, TabPane tabs) {
        Tab tab = new Tab(src.getSourceName() != null ? src.getSourceName() : "Income");
        tab.setClosable(false);

        SourceFormRefs refs = new SourceFormRefs();
        formRefs.put(src.getId(), refs);

        refs.sourceNameFld = tf(src.getSourceName() != null ? src.getSourceName() : "");
        refs.sourceNameFld.setPromptText("e.g. Main Job, Freelance, Rental...");
        refs.sourceNameFld.textProperty().addListener((o, ov, nv) ->
                tab.setText(nv.isBlank() ? "Income" : nv));

        GridPane nameGrid = UiUtils.buildFormGrid(160);
        nameGrid.setPadding(new Insets(12, 16, 8, 16));
        row(nameGrid, 0, "Source Name*", refs.sourceNameFld);

        Node formContent = src.getType() == FamilyMember.EarningType.SALARY
                ? buildSalaryForm(src, refs)
                : buildSimpleForm(src, refs);

        Button removeBtn = new Button("Remove this source");
        removeBtn.getStyleClass().add("btn-secondary");
        removeBtn.setOnAction(e -> removeSource(src, tab, tabs));
        HBox removeRow = new HBox(removeBtn);
        removeRow.setPadding(new Insets(6, 16, 10, 16));

        VBox wrapper = new VBox(0, nameGrid, new Separator(), formContent, removeRow);

        ScrollPane scroll = new ScrollPane(wrapper);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-transparent");
        tab.setContent(scroll);
        return tab;
    }

    private void showAddSourceDialog(TabPane tabs, Tab addTab) {
        Dialog<EarningSource> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Add Income Source", "+", 380);

        GridPane g = UiUtils.buildFormGrid(130);
        g.setVgap(12);
        g.setPadding(new Insets(16));

        TextField nameFld = tf("");
        nameFld.setPromptText("e.g. Main Job, Freelance...");

        ComboBox<String> typeCb = new ComboBox<>();
        typeCb.getItems().addAll("Simple Income", "Structured Salary");
        typeCb.setValue("Simple Income");
        typeCb.setMaxWidth(Double.MAX_VALUE);

        row(g, 0, "Source Name*", nameFld);
        row(g, 1, "Type*", typeCb);

        dlg.getDialogPane().setContent(g);
        ButtonType addBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt != addBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { showError("Source Name is required."); return null; }
            EarningSource src = new EarningSource();
            src.setSourceName(name);
            boolean isSalary = "Structured Salary".equals(typeCb.getValue());
            src.setType(isSalary ? FamilyMember.EarningType.SALARY : FamilyMember.EarningType.SIMPLE);
            if (!isSalary) src.setSimpleFrequency(RecurringTransaction.Frequency.MONTHLY.name());
            return src;
        });

        dlg.showAndWait().ifPresent(src -> {
            workingSources.add(src);
            int insertIdx = tabs.getTabs().indexOf(addTab);
            Tab newTab = buildSourceTab(src, tabs);
            tabs.getTabs().add(insertIdx, newTab);
            tabs.getSelectionModel().select(newTab);
        });
    }

    private void removeSource(EarningSource src, Tab tab, TabPane tabs) {
        if (workingSources.size() == 1) {
            showError("At least one income source is required.");
            return;
        }
        if (src.getRecurringScheduleId() != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Remove Income Source");
            confirm.setHeaderText(null);
            confirm.setContentText(
                    "This will also delete the linked recurring income schedule. Continue?");
            UiUtils.applyStylesheet(confirm);
            if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isEmpty()) return;
            pendingDeletes.add(src);
        }
        workingSources.remove(src);
        formRefs.remove(src.getId());
        tabs.getTabs().remove(tab);
    }

    // ── Simple form ───────────────────────────────────────────────────────────

    private Node buildSimpleForm(EarningSource src, SourceFormRefs refs) {
        GridPane g = formGrid();
        boolean configured = src.getSimpleAmountPaise() > 0;

        refs.descFld = tf(src.getScheduleDescription() != null
                ? src.getScheduleDescription()
                : member.getName() + " — " + (src.getSourceName() != null ? src.getSourceName() : "Income"));
        refs.simpleAmtFld = tf(configured ? fmtAmt(src.getSimpleAmountPaise()) : "");
        refs.simpleAmtFld.setPromptText("0.00 (gross)");

        refs.simpleFreqCb = new ComboBox<>();
        refs.simpleFreqCb.getItems().addAll(RecurringTransaction.Frequency.values());
        refs.simpleFreqCb.setMaxWidth(Double.MAX_VALUE);
        refs.simpleFreqCb.setCellFactory(lv -> freqCell());
        refs.simpleFreqCb.setButtonCell(freqCell());
        RecurringTransaction.Frequency freq = RecurringTransaction.Frequency.MONTHLY;
        if (src.getSimpleFrequency() != null) {
            try { freq = RecurringTransaction.Frequency.valueOf(src.getSimpleFrequency()); }
            catch (IllegalArgumentException ignored) {}
        }
        refs.simpleFreqCb.setValue(freq);

        refs.simpleTaxFld = tf(src.getEstimatedTaxRatePct() > 0
                ? String.valueOf(src.getEstimatedTaxRatePct()) : "");
        refs.simpleTaxFld.setPromptText("e.g. 10.0 (optional)");

        refs.netAmountHint = new Label("");
        refs.netAmountHint.getStyleClass().add("text-body-muted");

        refs.simpleAcctCb = accountCombo();
        prefillAccount(refs.simpleAcctCb, src.getDepositAccountId());

        refs.simpleDaySp = new Spinner<>(1, 28, src.getDepositDay() > 0 ? src.getDepositDay() : 1);
        refs.simpleDaySp.setEditable(true);
        refs.simpleDaySp.setMaxWidth(Double.MAX_VALUE);

        refs.simpleCatCb = categoryCombo();
        prefillCategory(refs.simpleCatCb, src.getCategoryId());

        Runnable updateHint = () -> updateSimpleNetHint(refs);
        refs.simpleAmtFld.textProperty().addListener((o, ov, nv) -> updateHint.run());
        refs.simpleTaxFld.textProperty().addListener((o, ov, nv) -> updateHint.run());
        if (configured) updateHint.run();

        int r = 0;
        row(g, r++, "Description*",           refs.descFld);
        row(g, r++, "Amount (₹, gross)*",      refs.simpleAmtFld);
        g.add(refs.netAmountHint, 1, r++);
        row(g, r++, "Frequency*",              refs.simpleFreqCb);
        row(g, r++, "Estimated Tax Rate (%)",  refs.simpleTaxFld);
        row(g, r++, "Into Account*",           refs.simpleAcctCb);
        row(g, r++, "Day of Month*",           refs.simpleDaySp);
        row(g, r,   "Category",                refs.simpleCatCb);

        return scrollPane(g);
    }

    private void updateSimpleNetHint(SourceFormRefs refs) {
        long   gross = parseAmountStr(refs.simpleAmtFld.getText());
        double tax   = parseDoubleStr(refs.simpleTaxFld.getText());
        long   net   = Math.round(gross * (1.0 - tax / 100.0));
        refs.netAmountHint.setText(gross > 0 ? "Net schedule amount: " + fmtR(net) : "");
    }

    // ── Salary form ───────────────────────────────────────────────────────────

    private Node buildSalaryForm(EarningSource src, SourceFormRefs refs) {
        boolean configured = src.getBasicDaPaise() > 0;
        GridPane g = formGrid();

        refs.descFld = tf(src.getScheduleDescription() != null
                ? src.getScheduleDescription()
                : member.getName() + " — " + (src.getSourceName() != null ? src.getSourceName() : "Salary"));
        refs.salBasicFld = tf(configured ? fmtAmt(src.getBasicDaPaise()) : "");
        refs.salBasicFld.setPromptText("e.g. 600000");
        refs.salHraFld   = tf(configured && src.getHraPaise() > 0 ? fmtAmt(src.getHraPaise()) : "");
        refs.salHraFld.setPromptText("optional");
        refs.salOtherFld = tf(configured && src.getOtherAllowancesPaise() > 0 ? fmtAmt(src.getOtherAllowancesPaise()) : "");
        refs.salOtherFld.setPromptText("optional");
        refs.salTaxFld   = tf(src.getEstimatedTaxRatePct() > 0 ? String.valueOf(src.getEstimatedTaxRatePct()) : "");
        refs.salTaxFld.setPromptText("e.g. 20.0");
        refs.salVpfFld   = tf(src.getVpfPct() > 0 ? String.valueOf(src.getVpfPct()) : "");
        refs.salVpfFld.setPromptText("optional, e.g. 5.0");

        refs.salAcctCb = accountCombo();
        prefillAccount(refs.salAcctCb, src.getDepositAccountId());

        refs.salDaySp = new Spinner<>(1, 28, src.getDepositDay() > 0 ? src.getDepositDay() : 1);
        refs.salDaySp.setEditable(true);
        refs.salDaySp.setMaxWidth(Double.MAX_VALUE);

        refs.salCatCb = categoryCombo();
        prefillCategory(refs.salCatCb, src.getCategoryId());

        refs.salPfAcctCb = pfAccountCombo();
        prefillPfAccount(refs.salPfAcctCb, src.getPfScheduleId());

        Hyperlink createPfLink = new Hyperlink("+ Add PF Account");
        createPfLink.getStyleClass().add("link-teal");
        createPfLink.setOnAction(e -> {
            InvestmentAccount created = showCreatePfDialog();
            if (created != null) {
                refs.salPfAcctCb.getItems().add(created);
                refs.salPfAcctCb.setValue(created);
            }
        });

        refs.gratuityChk = new CheckBox("Include in breakdown");
        refs.gratuityChk.setSelected(src.isGratuityEnabled());

        refs.esppChk = new CheckBox("Include in breakdown");
        refs.esppChk.setSelected(src.isEsppEnabled());

        refs.esppAmtFld = tf(src.isEsppEnabled() && src.getEsppAmountPaise() > 0
                ? fmtAmt(src.getEsppAmountPaise()) : "");
        refs.esppAmtFld.setPromptText("monthly amount");

        refs.esppAcctCb = equityAccountCombo();
        prefillEsppAccount(refs.esppAcctCb, src.getEsppScheduleId());

        Label esppAmtLbl  = new Label("SPP Amount (monthly, ₹)*");
        esppAmtLbl.getStyleClass().add("text-form-value");
        esppAmtLbl.setMinWidth(155);
        Label esppAcctLbl = new Label("SPP Investment Account");
        esppAcctLbl.getStyleClass().add("text-form-value");
        esppAcctLbl.setMinWidth(155);

        Runnable toggleEspp = () -> {
            boolean on = refs.esppChk.isSelected();
            esppAmtLbl.setVisible(on);   esppAmtLbl.setManaged(on);
            refs.esppAmtFld.setVisible(on); refs.esppAmtFld.setManaged(on);
            esppAcctLbl.setVisible(on);  esppAcctLbl.setManaged(on);
            refs.esppAcctCb.setVisible(on); refs.esppAcctCb.setManaged(on);
        };
        toggleEspp.run();
        refs.esppChk.selectedProperty().addListener((o, ov, nv) -> toggleEspp.run());

        int r = 0;
        row(g, r++, "Description*",               refs.descFld);
        row(g, r++, "Basic + DA (annual)*",        refs.salBasicFld);
        row(g, r++, "HRA (annual)",                refs.salHraFld);
        row(g, r++, "Other Allowances (annual)",   refs.salOtherFld);
        row(g, r++, "Estimated Tax Rate (%)",      refs.salTaxFld);
        row(g, r++, "VPF (%)",                     refs.salVpfFld);
        row(g, r++, "To Account*",                 refs.salAcctCb);
        row(g, r++, "Day of Month*",               refs.salDaySp);
        row(g, r++, "Category",                    refs.salCatCb);
        row(g, r++, "PF Account",                  refs.salPfAcctCb);
        g.add(createPfLink, 1, r++);
        row(g, r++, "Gratuity",                    refs.gratuityChk);
        row(g, r++, "Share Purchase Plan",         refs.esppChk);
        g.add(esppAmtLbl,       0, r); g.add(refs.esppAmtFld,  1, r++); GridPane.setFillWidth(refs.esppAmtFld, true);
        g.add(esppAcctLbl,      0, r); g.add(refs.esppAcctCb,  1, r);   GridPane.setFillWidth(refs.esppAcctCb, true);

        Node rightPanel = new SalaryDeductionPanel().build(refs, configured);

        ScrollPane leftScroll = new ScrollPane(g);
        leftScroll.setFitToWidth(true);
        leftScroll.getStyleClass().add("scroll-transparent");
        HBox.setHgrow(leftScroll, Priority.ALWAYS);

        HBox outer = new HBox(12, leftScroll, rightPanel);
        outer.setPadding(new Insets(0, 0, 8, 0));
        return outer;
    }

    // ── Salary breakdown panel (inner class) ──────────────────────────────────

    /**
     * Owns the salary deduction display table (right panel in the SALARY form).
     * Builds the calculation grid, wires recalc listeners on salary input fields,
     * and keeps all display-only labels private.
     */
    private class SalaryDeductionPanel {

        private final Label calcGross, calcEmpPf, calcTds, calcEspp, calcInHand,
                            calcEmpEpf, calcEps, calcPfDeposit, calcEpsDeposit, calcGratuity;

        SalaryDeductionPanel() {
            calcGross      = calcVal();
            calcEmpPf      = calcVal();
            calcTds        = calcVal();
            calcEspp       = calcVal();
            calcInHand     = calcVal();
            calcEmpEpf     = calcVal();
            calcEps        = calcVal();
            calcPfDeposit  = calcVal();
            calcEpsDeposit = calcVal();
            calcGratuity   = calcVal();
            calcInHand.getStyleClass().addAll("text-success", "text-result-value");
        }

        /** Builds and returns the right-panel VBox; wires recalc listeners on refs. */
        Node build(SourceFormRefs refs, boolean runInitialCalc) {
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

            Runnable recalc = () -> recalc(refs);
            refs.salBasicFld.textProperty().addListener((o, ov, nv) -> recalc.run());
            refs.salHraFld  .textProperty().addListener((o, ov, nv) -> recalc.run());
            refs.salOtherFld.textProperty().addListener((o, ov, nv) -> recalc.run());
            refs.salTaxFld  .textProperty().addListener((o, ov, nv) -> recalc.run());
            refs.salVpfFld  .textProperty().addListener((o, ov, nv) -> recalc.run());
            refs.gratuityChk.selectedProperty().addListener((o, ov, nv) -> recalc.run());
            refs.esppChk    .selectedProperty().addListener((o, ov, nv) -> recalc.run());
            refs.esppAmtFld .textProperty().addListener((o, ov, nv) -> recalc.run());
            if (runInitialCalc) recalc.run();

            Label calcTitle = new Label("Salary Breakdown (Monthly)");
            calcTitle.getStyleClass().add("text-section-title");
            VBox panel = new VBox(8, calcTitle, calc);
            panel.setPadding(new Insets(4, 4, 8, 8));
            panel.setMinWidth(330);
            panel.setMaxWidth(360);
            return panel;
        }

        private void recalc(SourceFormRefs refs) {
            long   basic  = parseAmountStr(refs.salBasicFld.getText()); // annual
            long   hra    = parseAmountStr(refs.salHraFld.getText());   // annual
            long   other  = parseAmountStr(refs.salOtherFld.getText()); // annual
            double taxPct = parseDoubleStr(refs.salTaxFld.getText());
            double vpfPct = parseDoubleStr(refs.salVpfFld.getText());

            long basicMo = basic / 12;
            long hraMo   = hra   / 12;
            long otherMo = other / 12;

            long gross        = basicMo + hraMo + otherMo;
            long mandatoryEpf = Math.round(basicMo * 0.12);
            long totalEmpPf   = Math.round(basicMo * (0.12 + vpfPct / 100.0));
            long tds          = Math.round((gross - mandatoryEpf) * taxPct / 100.0);
            boolean showEspp = refs.esppChk != null && refs.esppChk.isSelected();
            long espp         = showEspp ? parseAmountStr(refs.esppAmtFld.getText()) : 0;
            long inHand       = gross - totalEmpPf - tds - espp;
            long empEpf       = Math.max(0L, Math.round(basicMo * 0.12) - 125_000L);
            long eps          = basicMo > 0 ? Math.min(125_000L, Math.round(basicMo * 0.0833)) : 0;
            long pfDeposit    = totalEmpPf + empEpf;
            long epsDeposit   = eps;
            boolean showGratuity = refs.gratuityChk != null && refs.gratuityChk.isSelected();
            long gratuityPerYear = showGratuity ? Math.round(basicMo * 15.0 / (26.0)) : 0;

            calcGross     .setText(fmtR(gross));
            calcEmpPf     .setText(fmtR(totalEmpPf));
            calcTds       .setText(fmtR(tds));
            calcEspp      .setText(showEspp ? fmtR(espp) : "—");
            calcInHand    .setText(fmtR(inHand));
            calcEmpEpf    .setText(fmtR(empEpf));
            calcEps       .setText(basicMo > 0 ? fmtR(eps) : "—");
            calcPfDeposit .setText(fmtR(pfDeposit));
            calcEpsDeposit.setText(fmtR(epsDeposit));
            calcGratuity  .setText(showGratuity ? fmtR(gratuityPerYear) : "—");
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private Boolean saveAll(TabPane tabs) {
        try {
            for (EarningSource src : pendingDeletes) {
                if (src.getRecurringScheduleId() != null) ds.deleteRecurring(src.getRecurringScheduleId());
                if (src.getPfScheduleId() != null)        ds.deleteRecurring(src.getPfScheduleId());
                if (src.getEsppScheduleId() != null)      ds.deleteRecurring(src.getEsppScheduleId());
            }

            List<EarningSource> savedSources = new ArrayList<>();
            for (EarningSource src : workingSources) {
                SourceFormRefs refs = formRefs.get(src.getId());
                if (refs == null) continue;

                String name = refs.sourceNameFld.getText().trim();
                if (name.isEmpty())
                    throw new IllegalArgumentException("Source Name is required for all income sources.");
                src.setSourceName(name);

                if (src.getType() == FamilyMember.EarningType.SIMPLE)
                    validateAndReadSimple(src, refs);
                else
                    validateAndReadSalary(src, refs);

                createOrUpdateSchedule(src, refs);
                savedSources.add(src);
            }

            member.setEarningSources(savedSources);
            ds.updateFamilyMember(member);
            return Boolean.TRUE;

        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return null;
        }
    }

    private void validateAndReadSimple(EarningSource src, SourceFormRefs refs) {
        src.setScheduleDescription(req(refs.descFld, "Description"));
        src.setSimpleAmountPaise(parsePaise(refs.simpleAmtFld, "Amount"));
        src.setEstimatedTaxRatePct(parseOptionalDouble(refs.simpleTaxFld));
        if (refs.simpleFreqCb.getValue() == null)
            throw new IllegalArgumentException("Select a frequency for \"" + src.getSourceName() + "\".");
        src.setSimpleFrequency(refs.simpleFreqCb.getValue().name());
        src.setDepositAccountId(reqAccount(refs.simpleAcctCb).getId());
        src.setDepositDay(refs.simpleDaySp.getValue());
        src.setCategoryId(refs.simpleCatCb.getValue() != null ? refs.simpleCatCb.getValue().getId() : null);
    }

    private void validateAndReadSalary(EarningSource src, SourceFormRefs refs) {
        src.setScheduleDescription(req(refs.descFld, "Description"));
        src.setBasicDaPaise(parsePaise(refs.salBasicFld, "Basic + DA"));
        src.setHraPaise(parseOptionalPaise(refs.salHraFld));
        src.setOtherAllowancesPaise(parseOptionalPaise(refs.salOtherFld));
        src.setEstimatedTaxRatePct(parseOptionalDouble(refs.salTaxFld));
        src.setVpfPct(parseOptionalDouble(refs.salVpfFld));
        src.setDepositAccountId(reqAccount(refs.salAcctCb).getId());
        src.setDepositDay(refs.salDaySp.getValue());
        src.setCategoryId(refs.salCatCb.getValue() != null ? refs.salCatCb.getValue().getId() : null);
        src.setGratuityEnabled(refs.gratuityChk.isSelected());
        src.setEsppEnabled(refs.esppChk.isSelected());
        if (src.isEsppEnabled()) {
            src.setEsppAmountPaise(parsePaise(refs.esppAmtFld, "SPP Monthly Amount"));
        } else {
            src.setEsppAmountPaise(0);
        }
        if (src.computeScheduleAmountPaise() <= 0)
            throw new IllegalArgumentException(
                    "Computed in-hand is zero or negative for \"" + src.getSourceName() + "\" — check inputs.");
    }

    private void createOrUpdateSchedule(EarningSource src, SourceFormRefs refs) {
        long amount = src.computeScheduleAmountPaise();
        RecurringTransaction.Frequency freq = src.getType() == FamilyMember.EarningType.SALARY
                ? RecurringTransaction.Frequency.MONTHLY
                : RecurringTransaction.Frequency.valueOf(src.getSimpleFrequency());

        RecurringTransaction existing = ds.findRecurringById(src.getRecurringScheduleId());
        if (existing != null) {
            existing.setAmountPaise(amount);
            existing.setFrequency(freq);
            existing.setToAccountId(src.getDepositAccountId());
            existing.setDueDayOfMonth(src.getDepositDay());
            existing.setDescription(src.getScheduleDescription());
            existing.setCategoryId(src.getCategoryId());
            existing.setStatus(RecurringTransaction.Status.ACTIVE);
            ds.saveRecurringNow();
        } else {
            RecurringTransaction rt = new RecurringTransaction(
                    src.getScheduleDescription(), Transaction.Type.INCOME,
                    freq, src.getDepositDay(), LocalDate.now(), amount);
            rt.setToAccountId(src.getDepositAccountId());
            rt.setCategoryId(src.getCategoryId());
            rt.setAutoCreated(true);
            ds.addRecurring(rt);
            src.setRecurringScheduleId(rt.getId());
        }

        if (src.getType() == FamilyMember.EarningType.SALARY) {
            InvestmentAccount pfAcct = refs.salPfAcctCb.getValue();
            if (pfAcct != null) {
                long basicMo   = src.getBasicDaPaise() / 12;
                long empPf     = Math.round(basicMo * (0.12 + src.getVpfPct() / 100.0));
                long empEpf    = Math.max(0L, Math.round(basicMo * 0.12) - 125_000L);
                createOrUpdatePfSchedule(src, empPf + empEpf, pfAcct.getId());
            }

            if (src.isEsppEnabled() && refs.esppAcctCb.getValue() != null) {
                createOrUpdateEsppSchedule(src, src.getEsppAmountPaise(), refs.esppAcctCb.getValue().getId());
            } else if (!src.isEsppEnabled() && src.getEsppScheduleId() != null) {
                ds.deleteRecurring(src.getEsppScheduleId());
                src.setEsppScheduleId(null);
            }
        }
    }

    private void createOrUpdateEsppSchedule(EarningSource src, long amountPaise, String esppAccountId) {
        String desc = member.getName() + " — Share Purchase Plan (" + src.getSourceName() + ")";
        RecurringTransaction existing = ds.findRecurringById(src.getEsppScheduleId());
        if (existing != null) {
            existing.setAmountPaise(amountPaise);
            existing.setToAccountId(esppAccountId);
            existing.setDueDayOfMonth(src.getDepositDay());
            existing.setDescription(desc);
            existing.setStatus(RecurringTransaction.Status.ACTIVE);
            ds.saveRecurringNow();
        } else {
            RecurringTransaction rt = new RecurringTransaction(
                    desc, Transaction.Type.INVESTMENT,
                    RecurringTransaction.Frequency.MONTHLY, src.getDepositDay(),
                    LocalDate.now(), amountPaise);
            rt.setToAccountId(esppAccountId);
            rt.setAutoRecordAfterDays(1);
            rt.setAutoCreated(true);
            ds.addRecurring(rt);
            src.setEsppScheduleId(rt.getId());
        }
    }

    private void createOrUpdatePfSchedule(EarningSource src, long amountPaise, String pfAccountId) {
        String desc = member.getName() + " — PF Deposit (" + src.getSourceName() + ")";
        RecurringTransaction existing = ds.findRecurringById(src.getPfScheduleId());
        if (existing != null) {
            existing.setAmountPaise(amountPaise);
            existing.setToAccountId(pfAccountId);
            existing.setDueDayOfMonth(src.getDepositDay());
            existing.setDescription(desc);
            existing.setStatus(RecurringTransaction.Status.ACTIVE);
            ds.saveRecurringNow();
        } else {
            RecurringTransaction rt = new RecurringTransaction(
                    desc, Transaction.Type.INVESTMENT,
                    RecurringTransaction.Frequency.MONTHLY, src.getDepositDay(),
                    LocalDate.now(), amountPaise);
            rt.setToAccountId(pfAccountId);
            rt.setAutoRecordAfterDays(1);
            rt.setAutoCreated(true);
            ds.addRecurring(rt);
            src.setPfScheduleId(rt.getId());
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private GridPane formGrid() {
        GridPane g = UiUtils.buildFormGrid(160);
        g.setPadding(new Insets(16));
        return g;
    }

    private ScrollPane scrollPane(GridPane g) {
        ScrollPane sp = new ScrollPane(g);
        sp.setFitToWidth(true);
        sp.setMinHeight(280);
        sp.getStyleClass().add("scroll-transparent");
        return sp;
    }

    private void row(GridPane g, int rowIdx, String labelText, Node control) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("text-form-value");
        lbl.setMinWidth(155);
        g.add(lbl, 0, rowIdx);
        g.add(control, 1, rowIdx);
        GridPane.setFillWidth(control, true);
    }

    private void calcRow(GridPane g, int row, String labelText, Label val) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("text-body-muted");
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
        AccountCombos.style(cb);
        return cb;
    }

    private void prefillAccount(ComboBox<Account> cb, String accountId) {
        if (accountId == null) return;
        cb.getItems().stream().filter(a -> accountId.equals(a.getId()))
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
        cb.getItems().stream().filter(c -> categoryId.equals(c.getId()))
                .findFirst().ifPresent(cb::setValue);
    }

    private ComboBox<InvestmentAccount> pfAccountCombo() {
        ComboBox<InvestmentAccount> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("Select PF account (optional)");
        ds.getInvestmentAccounts().stream()
                .filter(a -> a.getInvestmentType() == InvestmentAccount.InvestmentType.PROVIDENT_FUND)
                .forEach(cb.getItems()::add);
        AccountCombos.style(cb);
        return cb;
    }

    private void prefillPfAccount(ComboBox<InvestmentAccount> cb, String pfScheduleId) {
        if (pfScheduleId == null) return;
        RecurringTransaction sched = ds.findRecurringById(pfScheduleId);
        if (sched == null || sched.getToAccountId() == null) return;
        String acctId = sched.getToAccountId();
        cb.getItems().stream().filter(a -> acctId.equals(a.getId()))
                .findFirst().ifPresent(cb::setValue);
    }

    private ComboBox<InvestmentAccount> equityAccountCombo() {
        ComboBox<InvestmentAccount> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("Select equity account (optional)");
        ds.getInvestmentAccounts().stream()
                .filter(a -> a.getInvestmentType() == InvestmentAccount.InvestmentType.EQUITY)
                .forEach(cb.getItems()::add);
        AccountCombos.style(cb);
        return cb;
    }

    private void prefillEsppAccount(ComboBox<InvestmentAccount> cb, String esppScheduleId) {
        if (esppScheduleId == null) return;
        RecurringTransaction sched = ds.findRecurringById(esppScheduleId);
        if (sched == null || sched.getToAccountId() == null) return;
        String acctId = sched.getToAccountId();
        cb.getItems().stream().filter(a -> acctId.equals(a.getId()))
                .findFirst().ifPresent(cb::setValue);
    }

    private Label calcVal() {
        Label l = new Label("—");
        l.getStyleClass().add("text-form-value");
        return l;
    }

    private ListCell<RecurringTransaction.Frequency> freqCell() {
        return new ListCell<>() {
            @Override protected void updateItem(RecurringTransaction.Frequency item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatFrequency(item));
            }
        };
    }

    private String req(TextField tf, String fieldName) {
        String v = tf.getText().trim();
        if (v.isEmpty()) throw new IllegalArgumentException(fieldName + " is required.");
        return v;
    }

    private long parsePaise(TextField tf, String fieldName) {
        String raw = tf.getText().replace(",", "").replace(MoneyFormatter.symbol(), "").trim();
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
        String raw = tf.getText().replace(",", "").replace(MoneyFormatter.symbol(), "").trim();
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

    private long parseAmountStr(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Math.round(Double.parseDouble(s.replace(",", "").replace(MoneyFormatter.symbol(), "").trim()) * 100); }
        catch (NumberFormatException e) { return 0; }
    }

    private double parseDoubleStr(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Double.parseDouble(s.replace(",", "").trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private String fmtAmt(long paise) { return String.format("%.2f",   paise / 100.0); }
    private String fmtR  (long paise) { return MoneyFormatter.format(paise); }

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

    private InvestmentAccount showCreatePfDialog() {
        Dialog<InvestmentAccount> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Add PF Account", "+", 360);

        GridPane g = UiUtils.buildFormGrid(130);
        g.setPadding(new Insets(16));

        TextField nameFld = new TextField(); nameFld.setPromptText("e.g. EPF — Employer"); nameFld.setMaxWidth(Double.MAX_VALUE);
        TextField uanFld  = new TextField(); uanFld.setPromptText("UAN number (optional)"); uanFld.setMaxWidth(Double.MAX_VALUE);
        TextField balFld  = new TextField(); balFld.setPromptText("0.00"); balFld.setMaxWidth(Double.MAX_VALUE);

        Label nameLbl = new Label("Account Name*"); nameLbl.getStyleClass().add("text-form-value");
        Label uanLbl  = new Label("UAN / A/C No."); uanLbl.getStyleClass().add("text-form-value");
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
            if (name.isEmpty()) { showError("Account name is required."); return null; }
            InvestmentAccount acc = new InvestmentAccount(name, InvestmentAccount.InvestmentType.PROVIDENT_FUND);
            acc.setFolioAccountNumber(uanFld.getText().trim());
            acc.setInvestedAmountPaise(parseAmountStr(balFld.getText()));
            ds.addAccount(acc);
            return acc;
        });

        return dlg.showAndWait().orElse(null);
    }

    private void showError(String msg) {
        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Validation Error", "⚠", 380);

        HBox iconRow = new HBox(14);
        iconRow.setAlignment(Pos.CENTER_LEFT);
        iconRow.setPadding(new Insets(16));
        Label iconLbl = new Label("⚠");
        iconLbl.getStyleClass().addAll("dialog-icon-box-lg", "dialog-icon-box-lg--error");
        Label msgLbl = new Label(msg);
        msgLbl.getStyleClass().add("form-label");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(280);
        iconRow.getChildren().addAll(iconLbl, msgLbl);

        dlg.getDialogPane().setContent(iconRow);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dlg.showAndWait();
    }
}
