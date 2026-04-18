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

class SimpleEarningsPanel implements EarningFormPanel {

    private final EarningSource src;
    private final FamilyMember  member;
    private final DataStore     ds;

    private TextField                                descFld, amtFld, taxFld;
    private ComboBox<RecurringTransaction.Frequency> freqCb;
    private ComboBox<Account>                        acctCb;
    private Spinner<Integer>                         daySp;
    private ComboBox<Category>                       catCb;
    private Label                                    netHint;

    SimpleEarningsPanel(EarningSource src, FamilyMember member, DataStore ds) {
        this.src    = src;
        this.member = member;
        this.ds     = ds;
    }

    @Override
    public Node buildPanel() {
        GridPane g = formGrid();
        boolean configured = src.getSimpleAmountPaise() > 0;

        descFld = tf(src.getScheduleDescription() != null
                ? src.getScheduleDescription()
                : member.getName() + " — " + (src.getSourceName() != null ? src.getSourceName() : "Income"));
        amtFld = tf(configured ? fmtAmt(src.getSimpleAmountPaise()) : "");
        amtFld.setPromptText("0.00 (gross)");

        freqCb = new ComboBox<>();
        freqCb.getItems().addAll(RecurringTransaction.Frequency.values());
        freqCb.setMaxWidth(Double.MAX_VALUE);
        freqCb.setCellFactory(lv -> freqCell());
        freqCb.setButtonCell(freqCell());
        RecurringTransaction.Frequency freq = RecurringTransaction.Frequency.MONTHLY;
        if (src.getSimpleFrequency() != null) {
            try { freq = RecurringTransaction.Frequency.valueOf(src.getSimpleFrequency()); }
            catch (IllegalArgumentException ignored) {}
        }
        freqCb.setValue(freq);

        taxFld = tf(src.getEstimatedTaxRatePct() > 0 ? String.valueOf(src.getEstimatedTaxRatePct()) : "");
        taxFld.setPromptText("e.g. 10.0 (optional)");

        netHint = new Label("");
        netHint.getStyleClass().add("text-body-muted");

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

        Runnable updateHint = this::updateNetHint;
        amtFld.textProperty().addListener((o, ov, nv) -> updateHint.run());
        taxFld.textProperty().addListener((o, ov, nv) -> updateHint.run());
        if (configured) updateHint.run();

        int r = 0;
        row(g, r++, "Description*",          descFld);
        row(g, r++, "Amount (₹, gross)*",     amtFld);
        g.add(netHint, 1, r++);
        row(g, r++, "Frequency*",             freqCb);
        row(g, r++, "Estimated Tax Rate (%)", taxFld);
        row(g, r++, "Into Account*",          acctCb);
        row(g, r++, "Day of Month*",          daySp);
        row(g, r,   "Category",               catCb);

        ScrollPane sp = new ScrollPane(g);
        sp.setFitToWidth(true);
        sp.setMinHeight(280);
        sp.getStyleClass().add("scroll-transparent");
        return sp;
    }

    @Override
    public void collectValues(EarningSource src) {
        src.setScheduleDescription(req(descFld, "Description"));
        src.setSimpleAmountPaise(parsePaise(amtFld, "Amount"));
        src.setEstimatedTaxRatePct(parseOptional(taxFld));
        if (freqCb.getValue() == null)
            throw new IllegalArgumentException("Select a frequency for \"" + src.getSourceName() + "\".");
        src.setSimpleFrequency(freqCb.getValue().name());
        Account acct = acctCb.getValue();
        if (acct == null) throw new IllegalArgumentException("Select an account.");
        src.setDepositAccountId(acct.getId());
        src.setDepositDay(daySp.getValue());
        src.setCategoryId(catCb.getValue() != null ? catCb.getValue().getId() : null);
    }

    private void updateNetHint() {
        long   gross = parseAmt(amtFld.getText());
        double tax   = parseD(taxFld.getText());
        long   net   = Math.round(gross * (1.0 - tax / 100.0));
        netHint.setText(gross > 0 ? "Net schedule amount: " + MoneyFormatter.format(net) : "");
    }

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

    private ListCell<RecurringTransaction.Frequency> freqCell() {
        return new ListCell<>() {
            @Override protected void updateItem(RecurringTransaction.Frequency item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatFrequency(item));
            }
        };
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
}
