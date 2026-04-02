package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.model.Transaction.Type;
import com.sanchay.service.AmortizationService;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Modal dialog for creating or editing any transaction type.
 * A single form with a Type dropdown at the top; type-specific fields
 * swap in below the shared Date / Description / Amount rows.
 */
public class TransactionDialog extends Dialog<Transaction> {

    private final DataStore ds = DataStore.getInstance();
    private Transaction existing;
    private Transaction pendingResult;

    // ── Shared fields (always visible) ───────────────────────────────────────
    private ComboBox<Type> typeCb;
    private DatePicker     sharedDate;
    private TextField      sharedDesc;
    private TextField      sharedAmt;
    private TextField      sharedNotes;

    // ── Category combos ───────────────────────────────────────────────────────
    private ComboBox<Category> expCatCb,  expSubCatCb;
    private ComboBox<Category> incCatCb,  incSubCatCb;
    private ComboBox<Category> trfCatCb,  trfSubCatCb;
    private ComboBox<Category> refCatCb,  refSubCatCb;
    private ComboBox<Category> lnCatCb,   lnSubCatCb;

    // ── Category master lists for autocomplete ────────────────────────────────
    private final List<Category> expCatMaster    = new ArrayList<>();
    private final List<Category> incCatMaster    = new ArrayList<>();
    private final List<Category> trfCatMaster    = new ArrayList<>();
    private final List<Category> refCatMaster    = new ArrayList<>();
    private final List<Category> lnCatMaster     = new ArrayList<>();
    private final List<Category> expSubCatMaster = new ArrayList<>();
    private final List<Category> incSubCatMaster = new ArrayList<>();
    private final List<Category> trfSubCatMaster = new ArrayList<>();
    private final List<Category> refSubCatMaster = new ArrayList<>();
    private final List<Category> lnSubCatMaster  = new ArrayList<>();

    // ── Account combos (per type) ─────────────────────────────────────────────
    private ComboBox<Account> expAcctCb;
    private ComboBox<Account> incAcctCb;
    private ComboBox<Account> trfFromCb, trfToCb;
    private ComboBox<Account> invFromCb;
    private ComboBox<Account> ccBankCb,  ccCardCb;
    private ComboBox<Account> refAcctCb;
    private ComboBox<Account> lnFromCb;
    private ComboBox<LoanAccount> lnToCb;

    // ── Other type-specific fields ────────────────────────────────────────────
    private ComboBox<String> expModeCb, refModeCb, lnModeCb;
    private TextField        expFamilyFld, expRefFld;
    private TextField        incFamilyFld;
    private TextField        refFamilyFld, refRefFld;
    private TextField        lnRefFld;
    private TextField        lnPrincipalFld;
    private Label            lnInterestLbl;

    // ── Redeem-specific ───────────────────────────────────────────────────────
    private ComboBox<InvestmentAccount> rdeFromCb;
    private ComboBox<Account>           rdeToCb;
    private TextField                   rdePrincipalFld;
    private Label                       rdeGainLossLbl;
    private ComboBox<Category>          rdeCatCb, rdeSubCatCb;
    private final List<Category>        rdeCatMaster    = new ArrayList<>();
    private final List<Category>        rdeSubCatMaster = new ArrayList<>();

    // ── Investment-specific ───────────────────────────────────────────────────
    private ComboBox<InvestmentAccount> invDestCb;
    private TextField invSchemeFld, invUnitsFld;
    private TextField invFdRefFld,  invFdRateFld, invFdMaturityAmtFld;
    private DatePicker invFdMaturityPicker;
    private ComboBox<Transaction.InterestPayable> invFdInterestPayableCb;
    private ComboBox<Transaction.PayoutAnchor>    invFdPayoutAnchorCb;
    private ComboBox<Month>                       invFdPayoutMonthCb;
    private Spinner<Integer>                      invFdPayoutDaySp;
    private ComboBox<String> invRdRefCb;
    private TextField invRdRateFld;
    private DatePicker invRdMaturityPicker;

    // ── Investment right-panel nodes (built in buildInvestmentPanel) ──────────
    private VBox    invDynamicBox;
    private VBox    invPreviewPanel;
    private VBox    invPrevScheduleBox;
    private Label   invPrevPrincipal, invPrevAnnualInt, invPrevTotalInt, invPrevTenor;

    // ── Investment layout: shared nodes promoted to fields so they can be
    //    moved between standardContent and invLeftContent on type switch ───────
    private GridPane topGrid, botGrid;
    private ScrollPane scroll;
    private VBox standardContent, invLeftContent;
    private HBox invContent;
    private VBox invRightPanel; // account-specific input fields, right side

    // ── Type → panel mapping ──────────────────────────────────────────────────
    private final Map<Type, Node> panels = new EnumMap<>(Type.class);
    private VBox typeSection;

    // ── Context account (edit-mode type changes) ──────────────────────────────
    private String  contextAccountId; // account in context when editing Expense/Income
    private boolean contextIsSource;  // true = "from" account (Expense), false = "to" (Income)
    private boolean rdeShowingExpenseCats = false;

    // ─────────────────────────────────────────────────────────────────────────

    public TransactionDialog() {
        setTitle("New Transaction");
        setHeaderText(null);
        getDialogPane().setPrefWidth(560);
        getDialogPane().getStyleClass().add("dialog-pane");
        UiUtils.applyStylesheet(this);
        UiUtils.setDialogHeader(this, "+", "New Transaction");

        // Shared fields
        sharedDate  = new DatePicker(LocalDate.now());
        UiUtils.styleOnShow(sharedDate);
        sharedDesc  = new TextField();
        sharedDesc.setPromptText("e.g. Electricity Bill");
        UiUtils.wireDescriptionAutocomplete(sharedDesc, ds.getDistinctTransactionDescriptions());
        sharedAmt   = new TextField();
        sharedAmt.setPromptText("0.00");
        sharedNotes = new TextField();
        sharedNotes.setPromptText("optional");

        // Type selector
        typeCb = new ComboBox<>();
        typeCb.getItems().addAll(
                Type.EXPENSE, Type.INCOME, Type.TRANSFER,
                Type.REFUND, Type.INVESTMENT, Type.CC_PAYMENT, Type.REDEEM, Type.LOAN_PAYMENT);
        typeCb.setValue(Type.EXPENSE);
        typeCb.setMaxWidth(Double.MAX_VALUE);
        typeCb.setConverter(typeNameConverter());

        // Build all panels up front
        panels.put(Type.EXPENSE,      buildExpensePanel());
        panels.put(Type.INCOME,       buildIncomePanel());
        panels.put(Type.TRANSFER,     buildTransferPanel());
        panels.put(Type.REFUND,       buildRefundPanel());
        panels.put(Type.INVESTMENT,   buildInvestmentPanel());
        panels.put(Type.CC_PAYMENT,   buildCCPaymentPanel());
        panels.put(Type.REDEEM,       buildRedeemPanel());
        panels.put(Type.LOAN_PAYMENT, buildLoanPaymentPanel());

        // Shared top grid: Type, Date, Description, Amount
        topGrid = form();
        topGrid.setPadding(new Insets(10, 10, 4, 10));
        int r = 0;
        row(topGrid, r++, "Type*",        typeCb);
        row(topGrid, r++, "Date*",        sharedDate);
        row(topGrid, r++, "Description*", sharedDesc);
        row(topGrid, r,   "Amount (₹)*",  sharedAmt);

        // Shared bottom grid: Notes
        botGrid = form();
        botGrid.setPadding(new Insets(4, 10, 10, 10));
        row(botGrid, 0, "Notes", sharedNotes);

        // Swappable middle section
        typeSection = new VBox();
        typeSection.getChildren().add(panels.get(Type.EXPENSE));

        // Investment side-by-side layout containers (built once, reused)
        invLeftContent = new VBox();
        HBox.setHgrow(invLeftContent, Priority.ALWAYS);
        Separator invSep = new Separator(Orientation.VERTICAL);
        invContent = new HBox(0, invLeftContent, invSep, invRightPanel);

        typeCb.valueProperty().addListener((obs, old, type) -> {
            if (type == Type.INVESTMENT) {
                // Move shared nodes into the investment HBox layout
                standardContent.getChildren().clear();
                invLeftContent.getChildren().setAll(topGrid, panels.get(Type.INVESTMENT), botGrid);
                scroll.setContent(invContent);
                // Re-populate right panel if account was already selected
                InvestmentAccount sel = invDestCb.getValue();
                refreshInvestmentDynamicFields(sel != null ? sel.getInvestmentType() : null);
            } else {
                // Return shared nodes to the standard VBox layout
                invLeftContent.getChildren().clear();
                refreshInvestmentDynamicFields(null); // clears dynamic content, hides invRightPanel, resizes
                typeSection.getChildren().setAll(panels.get(type));
                standardContent.getChildren().setAll(topGrid, typeSection, botGrid);
                scroll.setContent(standardContent);
            }
            if (contextAccountId != null)
                applyContextAccount(type);
        });

        wireAutoSuggest();

        standardContent = new VBox();
        standardContent.getChildren().addAll(topGrid, typeSection, botGrid);

        scroll = new ScrollPane(standardContent);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        getDialogPane().setContent(scroll);

        ButtonType saveBtn   = new ButtonType("Save",   ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(saveBtn, cancelBtn);

        setResultConverter(bt -> bt == saveBtn ? pendingResult : null);

        Platform.runLater(() -> {
            javafx.scene.control.Button btn =
                    (javafx.scene.control.Button) getDialogPane().lookupButton(saveBtn);
            if (btn != null) btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                try { pendingResult = save(); }
                catch (Exception ex) { showError(ex.getMessage()); ev.consume(); }
            });
            focusFirstEmpty();
        });
    }

    /** Edit / re-classify constructor. */
    public TransactionDialog(Transaction existing) {
        this();
        this.existing = existing;
        setTitle("Edit Transaction");
        UiUtils.setDialogHeader(this, "✎", "Edit Transaction");
        prefillFromTransaction(existing);
        Platform.runLater(this::focusFirstEmpty);
    }

    // ── Type name converter ───────────────────────────────────────────────────

    private StringConverter<Type> typeNameConverter() {
        return new StringConverter<>() {
            @Override public String toString(Type t) {
                if (t == null) return "";
                return switch (t) {
                    case EXPENSE      -> "Expense";
                    case INCOME       -> "Income";
                    case TRANSFER     -> "Transfer";
                    case INVESTMENT   -> "Investment";
                    case CC_PAYMENT   -> "CC Payment";
                    case REFUND       -> "Refund";
                    case REDEEM       -> "Redeem";
                    case LOAN_PAYMENT -> "Loan Payment";
                    case GAIN         -> "Gain";
                    case LOSE         -> "Loss";
                };
            }
            @Override public Type fromString(String s) { return null; }
        };
    }

    // ── Panel builders ────────────────────────────────────────────────────────

    private Node buildExpensePanel() {
        expCatMaster.addAll(ds.getExpenseCategories());
        expCatCb    = makeCatCb(expCatMaster, "Select category");
        expSubCatCb = makeSubCatCb(expSubCatMaster);
        wireCategory(expCatCb, expCatMaster, expSubCatCb, expSubCatMaster);

        expAcctCb  = accountCombo(true);  // bank + CC
        expModeCb  = payModeCombo();
        expFamilyFld = tf("optional");
        expRefFld    = tf("optional");

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "From Account*",  expAcctCb);
        row(g, r++, "Category",      expCatCb);
        row(g, r++, "Sub-category",  expSubCatCb);
        row(g, r++, "Payment Mode",  expModeCb);
        row(g, r++, "Family Member", expFamilyFld);
        row(g, r,   "Ref / UTR No",  expRefFld);
        return g;
    }

    private Node buildIncomePanel() {
        incCatMaster.addAll(ds.getIncomeCategories());
        incCatCb    = makeCatCb(incCatMaster, "Select category");
        incSubCatCb = makeSubCatCb(incSubCatMaster);
        wireCategory(incCatCb, incCatMaster, incSubCatCb, incSubCatMaster);

        incAcctCb    = accountCombo(false);
        incFamilyFld = tf("optional");

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "To Account*",    incAcctCb);
        row(g, r++, "Category",       incCatCb);
        row(g, r++, "Sub-category",   incSubCatCb);
        row(g, r,   "Family Member",  incFamilyFld);
        return g;
    }

    private Node buildTransferPanel() {
        trfCatMaster.addAll(ds.getExpenseCategories());
        trfCatCb    = makeCatCb(trfCatMaster, "Select category (optional)");
        trfSubCatCb = makeSubCatCb(trfSubCatMaster);
        wireCategory(trfCatCb, trfCatMaster, trfSubCatCb, trfSubCatMaster);

        trfFromCb = accountCombo(false); // bank only
        trfToCb   = accountCombo(false); // bank only

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "From Account*",  trfFromCb);
        row(g, r++, "To Account*",    trfToCb);
        row(g, r++, "Category",       trfCatCb);
        row(g, r,   "Sub-category",   trfSubCatCb);
        return g;
    }

    private Node buildRefundPanel() {
        refCatMaster.addAll(ds.getExpenseCategories());
        refCatCb    = makeCatCb(refCatMaster, "Select original expense category");
        refSubCatCb = makeSubCatCb(refSubCatMaster);
        wireCategory(refCatCb, refCatMaster, refSubCatCb, refSubCatMaster);

        refAcctCb    = accountCombo(true);  // bank + CC (where refund lands)
        refModeCb    = payModeCombo();
        refFamilyFld = tf("optional");
        refRefFld    = tf("optional");

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "To Account*",     refAcctCb);
        row(g, r++, "Category",        refCatCb);
        row(g, r++, "Sub-category",    refSubCatCb);
        row(g, r++, "Payment Mode",    refModeCb);
        row(g, r++, "Family Member",   refFamilyFld);
        row(g, r,   "Ref / UTR No",    refRefFld);
        return g;
    }

    private Node buildInvestmentPanel() {
        invFromCb = accountCombo(false);
        invFromCb.getItems().removeIf(a -> !(a instanceof BankAccount));

        invDestCb = new ComboBox<>();
        invDestCb.setMaxWidth(Double.MAX_VALUE);
        invDestCb.setPromptText("Select investment account");
        ds.getInvestmentAccounts().forEach(invDestCb.getItems()::add);
        invDestCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(InvestmentAccount ia, boolean empty) {
                super.updateItem(ia, empty);
                setText(empty || ia == null ? null : ia.getName());
            }
        });
        invDestCb.setButtonCell(invDestCb.getCellFactory().call(null));

        invDestCb.valueProperty().addListener((obs, old, ia) ->
                refreshInvestmentDynamicFields(ia == null ? null : ia.getInvestmentType()));

        // Right panel: initialised here, populated by refreshInvestmentDynamicFields
        invDynamicBox = new VBox(0);

        invPrevPrincipal   = previewVal();
        invPrevAnnualInt   = previewVal();
        invPrevTotalInt    = previewVal();
        invPrevTenor       = previewVal();
        invPrevScheduleBox = new VBox(4);

        GridPane prevSummaryGrid = new GridPane();
        prevSummaryGrid.setHgap(8); prevSummaryGrid.setVgap(6);
        prevSummaryGrid.setPadding(new Insets(8, 8, 4, 8));
        ColumnConstraints pc1 = new ColumnConstraints(110);
        ColumnConstraints pc2 = new ColumnConstraints(0, 100, Double.MAX_VALUE);
        pc2.setHgrow(Priority.ALWAYS);
        prevSummaryGrid.getColumnConstraints().addAll(pc1, pc2);
        prevRow(prevSummaryGrid, 0, "Principal",       invPrevPrincipal);
        prevRow(prevSummaryGrid, 1, "Annual Interest", invPrevAnnualInt);
        prevRow(prevSummaryGrid, 2, "Total Interest",  invPrevTotalInt);
        prevRow(prevSummaryGrid, 3, "Tenor",           invPrevTenor);

        VBox schedSection = new VBox(4);
        schedSection.setPadding(new Insets(4, 8, 8, 8));
        Label schedHeader = new Label("Payout Schedule");
        schedHeader.getStyleClass().add("text-body-muted");
        schedSection.getChildren().addAll(schedHeader, invPrevScheduleBox);

        Label fdPreviewHeader = new Label("FD / Bond Preview");
        fdPreviewHeader.getStyleClass().add("text-body-muted");
        fdPreviewHeader.setPadding(new Insets(8, 8, 0, 8));

        invPreviewPanel = new VBox(0, fdPreviewHeader, prevSummaryGrid, schedSection);
        invPreviewPanel.setVisible(false);
        invPreviewPanel.setManaged(false);

        invRightPanel = new VBox(0);
        invRightPanel.setMinWidth(256);
        invRightPanel.setMaxWidth(256);
        invRightPanel.setVisible(false);
        invRightPanel.setManaged(false);
        invRightPanel.getChildren().add(invDynamicBox);

        // Panel returned for panels.get(INVESTMENT): just From/To account selectors
        GridPane g = panelGrid();
        row(g, 0, "From Account*", invFromCb);
        row(g, 1, "To Account*",   invDestCb);
        return g;
    }

    private Node buildCCPaymentPanel() {
        ccBankCb = accountCombo(false);
        ccBankCb.getItems().removeIf(a -> !(a instanceof BankAccount));

        ccCardCb = accountCombo(true);
        ccCardCb.getItems().removeIf(a -> !(a instanceof CreditCardAccount));

        GridPane g = panelGrid();
        row(g, 0, "From Account*", ccBankCb);
        row(g, 1, "To Account*",   ccCardCb);
        return g;
    }

    private Node buildLoanPaymentPanel() {
        lnCatMaster.addAll(ds.getExpenseCategories());
        lnCatCb    = makeCatCb(lnCatMaster, "Select category (optional)");
        lnSubCatCb = makeSubCatCb(lnSubCatMaster);
        wireCategory(lnCatCb, lnCatMaster, lnSubCatCb, lnSubCatMaster);

        lnFromCb = accountCombo(true); // bank + CC

        lnToCb = new ComboBox<>();
        lnToCb.setMaxWidth(Double.MAX_VALUE);
        lnToCb.setPromptText("Select loan account");
        ds.getActiveLoanAccounts().forEach(lnToCb.getItems()::add);
        lnToCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(LoanAccount la, boolean empty) {
                super.updateItem(la, empty);
                setText(empty || la == null ? null : la.getName());
            }
        });
        lnToCb.setButtonCell(lnToCb.getCellFactory().call(null));

        lnModeCb = payModeCombo();
        lnRefFld = tf("optional");

        lnPrincipalFld = tf("Principal portion of this payment");
        lnInterestLbl  = new Label("—");
        lnInterestLbl.getStyleClass().add("text-hint");

        // Pre-fill principal from schedule when loan account or date changes
        Runnable prefillPrincipal = () -> {
            LoanAccount la = lnToCb.getValue();
            LocalDate   d  = sharedDate.getValue();
            if (la == null || d == null) return;
            List<AmortizationEntry> schedule = ds.getSchedule(la.getId());
            long sched = AmortizationService.getScheduledPrincipalForDate(schedule, d);
            if (sched > 0 && (lnPrincipalFld.getText() == null || lnPrincipalFld.getText().isBlank())) {
                lnPrincipalFld.setText(String.format("%.0f", sched / 100.0));
            }
        };
        lnToCb.valueProperty().addListener((obs, o, n) -> prefillPrincipal.run());
        sharedDate.valueProperty().addListener((obs, o, n) -> {
            if (typeCb.getValue() == Type.LOAN_PAYMENT) prefillPrincipal.run();
        });

        // Live interest = amount - principal
        Runnable updateInterest = () -> {
            try {
                long amt  = Math.round(Double.parseDouble(sharedAmt.getText().replace(",", "")) * 100);
                long prin = Math.round(Double.parseDouble(lnPrincipalFld.getText().replace(",", "")) * 100);
                long interest = amt - prin;
                lnInterestLbl.setText(interest >= 0
                        ? String.format("₹%,.2f", interest / 100.0)
                        : "⚠ Principal exceeds EMI amount");
            } catch (NumberFormatException e) {
                lnInterestLbl.setText("—");
            }
        };
        lnPrincipalFld.textProperty().addListener((obs, o, n) -> updateInterest.run());
        sharedAmt.textProperty().addListener((obs, o, n) -> {
            if (typeCb.getValue() == Type.LOAN_PAYMENT) updateInterest.run();
        });

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "From Account*",  lnFromCb);
        row(g, r++, "To Account*",    lnToCb);
        row(g, r++, "Principal (₹)",  lnPrincipalFld);
        row(g, r++, "Interest (₹)",   lnInterestLbl);
        row(g, r++, "Category",       lnCatCb);
        row(g, r++, "Sub-category",   lnSubCatCb);
        row(g, r++, "Payment Mode",   lnModeCb);
        row(g, r,   "Ref / UTR No",   lnRefFld);
        return g;
    }

    private Node buildRedeemPanel() {
        rdeCatMaster.addAll(ds.getIncomeCategories());
        rdeCatCb    = makeCatCb(rdeCatMaster, "Select gain category (optional)");
        rdeSubCatCb = makeSubCatCb(rdeSubCatMaster);
        wireCategory(rdeCatCb, rdeCatMaster, rdeSubCatCb, rdeSubCatMaster);

        rdeFromCb = new ComboBox<>();
        rdeFromCb.setMaxWidth(Double.MAX_VALUE);
        rdeFromCb.setPromptText("Select investment account");
        ds.getInvestmentAccounts().forEach(rdeFromCb.getItems()::add);
        rdeFromCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(InvestmentAccount ia, boolean empty) {
                super.updateItem(ia, empty);
                setText(empty || ia == null ? null : ia.getName());
            }
        });
        rdeFromCb.setButtonCell(rdeFromCb.getCellFactory().call(null));

        rdeToCb = accountCombo(false); // bank accounts only

        rdePrincipalFld = tf("Original invested amount being returned");
        rdeGainLossLbl  = new Label("—");
        rdeGainLossLbl.getStyleClass().add("text-hint");

        // Live gain/loss computation
        rdePrincipalFld.textProperty().addListener((obs, old, val) -> updateRedeemGainLoss());
        sharedAmt.textProperty().addListener((obs, old, val) -> {
            if (typeCb.getValue() == Type.REDEEM) updateRedeemGainLoss();
        });

        GridPane g = panelGrid();
        int r = 0;
        row(g, r++, "From Account*", rdeFromCb);
        row(g, r++, "To Account*",   rdeToCb);
        row(g, r++, "Principal (₹)*",     rdePrincipalFld);
        row(g, r++, "Gain / Loss",        rdeGainLossLbl);
        row(g, r++, "Category",           rdeCatCb);
        row(g, r,   "Sub-category",       rdeSubCatCb);
        return g;
    }

    private void updateRedeemGainLoss() {
        try {
            long total     = Math.round(Double.parseDouble(sharedAmt.getText().replace(",", "")) * 100);
            long principal = Math.round(Double.parseDouble(rdePrincipalFld.getText().replace(",", "")) * 100);
            long gainLoss  = total - principal;
            String sign  = gainLoss >= 0 ? "+" : "";
            // Inline required: runtime gain/loss colour is data-driven
            String color = gainLoss >= 0 ? "-color-success-dark" : "-color-error";
            rdeGainLossLbl.setText(sign + String.format("₹%,.2f", gainLoss / 100.0));
            rdeGainLossLbl.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
            switchRedeemCatList(gainLoss < 0);
        } catch (NumberFormatException e) {
            rdeGainLossLbl.setText("—");
            rdeGainLossLbl.setStyle(""); // clears inline; text-hint class applies
        }
    }

    private void switchRedeemCatList(boolean toLoss) {
        if (toLoss == rdeShowingExpenseCats) return;
        rdeShowingExpenseCats = toLoss;
        rdeCatMaster.clear();
        rdeCatMaster.addAll(toLoss ? ds.getExpenseCategories() : ds.getIncomeCategories());
        rdeCatCb.setValue(null);
        rdeCatCb.getEditor().clear();
        rdeCatCb.getItems().setAll(rdeCatMaster);
        rdeCatCb.setPromptText(toLoss ? "Select loss category (optional)" : "Select gain category (optional)");
    }

    // ── Category combo factories ──────────────────────────────────────────────

    private ComboBox<Category> makeCatCb(List<Category> master, String prompt) {
        ComboBox<Category> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText(prompt);
        cb.getItems().setAll(master);
        cb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.getName());
            }
        });
        return cb;
    }

    private ComboBox<Category> makeSubCatCb(List<Category> master) {
        ComboBox<Category> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("Select sub-category (optional)");
        cb.setVisible(false);
        cb.setManaged(false);
        cb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : "  └ " + c.getName());
                setStyle(empty || c == null ? "" : "-fx-text-fill: #1A1A2E;");
            }
        });
        return cb;
    }

    private void wireCategory(ComboBox<Category> catCb, List<Category> catMaster,
                              ComboBox<Category> subCatCb, List<Category> subMaster) {
        wireCatSubCat(catCb, subCatCb, subMaster);
        UiUtils.wireAutoComplete(catCb,    catMaster);
        UiUtils.wireAutoComplete(subCatCb, subMaster);
    }

    private void wireCatSubCat(ComboBox<Category> catCb, ComboBox<Category> subCatCb,
                                List<Category> subMaster) {
        catCb.valueProperty().addListener((obs, old, sel) -> {
            subCatCb.getItems().clear();
            subCatCb.setValue(null);
            if (sel != null) {
                List<Category> subs = ds.getSubCategories(sel.getId());
                if (!subs.isEmpty()) {
                    subCatCb.getItems().addAll(subs);
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
            subMaster.clear();
            subMaster.addAll(subCatCb.getItems());
        });
    }

    // ── Auto-suggest (unified, routes by current type) ────────────────────────

    private void wireAutoSuggest() {
        sharedDesc.focusedProperty().addListener((obs, was, isNow) -> {
            if (isNow) return;
            String desc = sharedDesc.getText();
            if (desc == null || desc.isBlank()) return;

            Type               type      = typeCb.getValue();
            ComboBox<Category> catCb     = catCbFor(type);
            List<Category>     catMaster = catMasterFor(type);
            ComboBox<Category> subCatCb  = subCatCbFor(type);
            if (catCb == null || catMaster == null) return;

            List<Category> sorted = ds.sortCategoriesByUsage(new ArrayList<>(catMaster), desc, type);
            catMaster.clear();
            catMaster.addAll(sorted);
            catCb.getItems().setAll(catMaster);

            if (catCb.getValue() != null) return;
            ds.suggestCategoryForDescription(desc.trim(), type).ifPresent(rule ->
                    catCb.getItems().stream()
                            .filter(c -> c.getId().equals(rule.getCategoryId()))
                            .findFirst()
                            .ifPresent(cat -> {
                                catCb.setValue(cat);
                                if (subCatCb != null && rule.getSubCategoryId() != null)
                                    subCatCb.getItems().stream()
                                            .filter(s -> s.getId().equals(rule.getSubCategoryId()))
                                            .findFirst()
                                            .ifPresent(subCatCb::setValue);
                            }));
        });
    }

    private ComboBox<Category> catCbFor(Type type) {
        return switch (type) {
            case EXPENSE      -> expCatCb;
            case INCOME       -> incCatCb;
            case TRANSFER     -> trfCatCb;
            case REFUND       -> refCatCb;
            case REDEEM       -> rdeCatCb;
            case LOAN_PAYMENT -> lnCatCb;
            default           -> null;
        };
    }

    private List<Category> catMasterFor(Type type) {
        return switch (type) {
            case EXPENSE      -> expCatMaster;
            case INCOME       -> incCatMaster;
            case TRANSFER     -> trfCatMaster;
            case REFUND       -> refCatMaster;
            case REDEEM       -> rdeCatMaster;
            case LOAN_PAYMENT -> lnCatMaster;
            default           -> null;
        };
    }

    private ComboBox<Category> subCatCbFor(Type type) {
        return switch (type) {
            case EXPENSE      -> expSubCatCb;
            case INCOME       -> incSubCatCb;
            case TRANSFER     -> trfSubCatCb;
            case REFUND       -> refSubCatCb;
            case REDEEM       -> rdeSubCatCb;
            case LOAN_PAYMENT -> lnSubCatCb;
            default           -> null;
        };
    }

    // ── Save routing ──────────────────────────────────────────────────────────

    private Transaction save() {
        return switch (typeCb.getValue()) {
            case EXPENSE      -> saveExpense();
            case INCOME       -> saveIncome();
            case TRANSFER     -> saveTransfer();
            case INVESTMENT   -> saveInvestment();
            case CC_PAYMENT   -> saveCCPayment();
            case REFUND       -> saveRefund();
            case REDEEM       -> saveRedeem();
            case LOAN_PAYMENT -> saveLoanPayment();
            case GAIN, LOSE   -> throw new IllegalStateException("GAIN/LOSE cannot be saved directly");
        };
    }

    private Transaction saveExpense() {
        LocalDate date = requireDate();
        String    desc = requireText(sharedDesc, "Description");
        long      amt  = parsePaise(sharedAmt);
        Account   acct = requireAccount(expAcctCb, "From Account");

        Transaction t = new Transaction(Type.EXPENSE, date, desc, amt);
        t.setFromAccountId(acct.getId());
        Transaction.Classification expCl = new Transaction.Classification();
        if (expCatCb.getValue()    != null) expCl.setCategoryId(expCatCb.getValue().getId());
        if (expSubCatCb.getValue() != null) expCl.setSubCategoryId(expSubCatCb.getValue().getId());
        expCl.setFamilyMember(nullIfBlank(expFamilyFld.getText()));
        t.setClassification(expCl);
        applyPayMode(t, expModeCb);
        String expRef = nullIfBlank(expRefFld.getText());
        if (expRef != null) {
            if (t.getPayment() == null) t.setPayment(new Transaction.Payment());
            t.getPayment().setReferenceNumber(expRef);
        }
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    private Transaction saveIncome() {
        LocalDate date = requireDate();
        String    desc = requireText(sharedDesc, "Description");
        long      amt  = parsePaise(sharedAmt);
        Account   acct = requireAccount(incAcctCb, "To Account");

        Transaction t = new Transaction(Type.INCOME, date, desc, amt);
        t.setToAccountId(acct.getId());
        Transaction.Classification incCl = new Transaction.Classification();
        if (incCatCb.getValue()    != null) incCl.setCategoryId(incCatCb.getValue().getId());
        if (incSubCatCb.getValue() != null) incCl.setSubCategoryId(incSubCatCb.getValue().getId());
        incCl.setFamilyMember(nullIfBlank(incFamilyFld.getText()));
        t.setClassification(incCl);
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    private Transaction saveLoanPayment() {
        LocalDate   date = requireDate();
        String      desc = requireText(sharedDesc, "Description");
        long        amt  = parsePaise(sharedAmt);
        Account     from = requireAccount(lnFromCb, "From Account");
        LoanAccount to   = lnToCb.getValue();
        if (to == null) throw new IllegalArgumentException("Please select a loan account.");

        Transaction t = new Transaction(Type.LOAN_PAYMENT, date, desc, amt);
        t.setFromAccountId(from.getId());
        t.setToAccountId(to.getId());
        Transaction.Classification lnCl = new Transaction.Classification();
        if (lnCatCb.getValue()    != null) lnCl.setCategoryId(lnCatCb.getValue().getId());
        if (lnSubCatCb.getValue() != null) lnCl.setSubCategoryId(lnSubCatCb.getValue().getId());
        t.setClassification(lnCl);
        applyPayMode(t, lnModeCb);
        String lnRef = nullIfBlank(lnRefFld.getText());
        if (lnRef != null) {
            if (t.getPayment() == null) t.setPayment(new Transaction.Payment());
            t.getPayment().setReferenceNumber(lnRef);
        }
        t.setNotes(nullIfBlank(sharedNotes.getText()));

        // Store actual principal for accurate outstanding calculation.
        // Derived as total payment minus the scheduled interest for this month so that
        // prepayments (amount > EMI) are recorded correctly — the principal field in the
        // UI is a display hint and may still show the scheduled principal when the user
        // hasn't manually updated it.
        List<AmortizationEntry> lnSchedule = ds.getSchedule(to.getId());
        long scheduledInterest = 0;
        for (AmortizationEntry ae : lnSchedule) {
            if (ae.getPaymentDate().getYear()  == date.getYear()
             && ae.getPaymentDate().getMonth() == date.getMonth()) {
                scheduledInterest = ae.getInterestPaymentPaise();
                break;
            }
        }
        long actualPrincipal = Math.max(0, amt - scheduledInterest);
        if (actualPrincipal > 0) {
            Transaction.RedeemDetails rd = new Transaction.RedeemDetails();
            rd.setPrincipalPaise(actualPrincipal);
            t.setRedeemDetails(rd);
        }

        Transaction saved = persistTransaction(t);
        checkAndHandlePrepayment(saved, to, date);
        return saved;
    }

    /**
     * After saving a LOAN_PAYMENT, check if the principal paid exceeds the
     * scheduled principal for that month. If so, prompt for prepayment mode
     * (or use the loan's saved default) and regenerate the amortization schedule.
     */
    private void checkAndHandlePrepayment(Transaction t, LoanAccount loan, LocalDate date) {
        List<AmortizationEntry> schedule = ds.getSchedule(loan.getId());
        if (schedule == null || schedule.isEmpty()) return;

        // Find the schedule entry for this payment month
        int entryIndex = -1;
        for (int i = 0; i < schedule.size(); i++) {
            AmortizationEntry e = schedule.get(i);
            if (e.getPaymentDate().getYear()  == date.getYear()
             && e.getPaymentDate().getMonth() == date.getMonth()) {
                entryIndex = i;
                break;
            }
        }
        if (entryIndex < 0) return;

        AmortizationEntry entry         = schedule.get(entryIndex);
        long              scheduledPrin = entry.getPrincipalRepaymentPaise();
        // Derive actual principal from the total payment minus the fixed interest for the month.
        // Interest is determined by opening balance × rate, not by what the user typed in the
        // principal field — so this correctly detects prepayments even when the field is unchanged.
        long              actualPrin    = Math.max(0, t.getAmountPaise() - entry.getInterestPaymentPaise());
        long              extraPrin     = actualPrin - scheduledPrin;
        if (extraPrin <= 0) return; // no prepayment

        // Determine mode — use saved default or ask the user
        LoanAccount.PrepaymentMode mode = loan.getDefaultPrepaymentMode();
        if (mode == null) {
            mode = promptPrepaymentMode(loan);
            if (mode == null) return; // user dismissed without choosing
        }

        // New balance after actual principal payment
        long newBalance = entry.getOpeningBalancePaise() - actualPrin;
        int  nextIndex  = entryIndex + 1;

        if (newBalance <= 0 || nextIndex >= schedule.size()) {
            // Loan effectively paid off — trim remaining entries
            ds.saveSchedule(loan.getId(), new ArrayList<>(schedule.subList(0, nextIndex)));
            return;
        }

        List<AmortizationEntry> updated = AmortizationService.recalculateFromBalance(
                schedule, nextIndex, loan, newBalance, mode);
        ds.saveSchedule(loan.getId(), updated);

        if (mode == LoanAccount.PrepaymentMode.REDUCE_EMI && nextIndex < updated.size()) {
            offerEmiSyncAfterPrepayment(loan, updated.get(nextIndex).getEmiAmountPaise());
        }
    }

    /**
     * If a LOAN_PAYMENT recurring schedule exists and the new EMI after prepayment
     * differs from the scheduled amount, offers to update it.
     */
    private void offerEmiSyncAfterPrepayment(LoanAccount loan, long newEmiPaise) {
        RecurringTransaction rt = ds.findLoanPaymentSchedule(loan.getId());
        if (rt == null || rt.getAmountPaise() == newEmiPaise) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(getDialogPane().getScene().getWindow());
        confirm.setTitle("Update Payment Schedule");
        confirm.setHeaderText(null);
        confirm.setContentText(String.format(
                "EMI has changed to \u20b9%.0f after prepayment.\nUpdate the linked payment schedule?",
                newEmiPaise / 100.0));
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                rt.setAmountPaise(newEmiPaise);
                ds.saveRecurringNow();
            }
        });
    }

    /**
     * Shows a dialog asking whether the prepayment should reduce tenure or EMI.
     * Offers a "Remember my choice" checkbox so the user is not asked again.
     * Returns the chosen mode, or null if the dialog was cancelled.
     */
    private LoanAccount.PrepaymentMode promptPrepaymentMode(LoanAccount loan) {
        ButtonType reduceTenure = new ButtonType("Reduce Tenure", ButtonBar.ButtonData.OTHER);
        ButtonType reduceEmi    = new ButtonType("Reduce EMI",    ButtonBar.ButtonData.OTHER);
        ButtonType cancel       = new ButtonType("Cancel",        ButtonBar.ButtonData.CANCEL_CLOSE);

        Label bodyLabel = new Label(
                "How should the amortization schedule be recalculated?\n\n" +
                "• Reduce Tenure — keep the same EMI, fewer months remaining.\n" +
                "• Reduce EMI    — keep the same tenure, lower monthly EMI.");
        bodyLabel.setWrapText(true);

        CheckBox rememberChk = new CheckBox("Remember my choice for this loan");
        rememberChk.getStyleClass().add("form-label");

        VBox content = new VBox(12, bodyLabel, rememberChk);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initOwner(getDialogPane().getScene().getWindow());
        dlg.initStyle(StageStyle.UNDECORATED);
        dlg.setHeaderText("This payment includes an extra principal amount.");
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(reduceTenure, reduceEmi, cancel);
        dlg.getDialogPane().getStylesheets().addAll(getDialogPane().getStylesheets());

        // Widen the Reduce Tenure button so it is not truncated
        Button reduceTenureBtn = (Button) dlg.getDialogPane().lookupButton(reduceTenure);
        reduceTenureBtn.setMinWidth(150);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() == cancel) return null;

        LoanAccount.PrepaymentMode chosen = (result.get() == reduceTenure)
                ? LoanAccount.PrepaymentMode.REDUCE_TENURE
                : LoanAccount.PrepaymentMode.REDUCE_EMI;

        if (rememberChk.isSelected()) {
            loan.setDefaultPrepaymentMode(chosen);
            ds.saveAccountsNow();
        }

        return chosen;
    }

    private Transaction saveTransfer() {
        LocalDate date = requireDate();
        String    desc = requireText(sharedDesc, "Description");
        long      amt  = parsePaise(sharedAmt);
        Account   from = requireAccount(trfFromCb, "From Account");
        Account   to   = requireAccount(trfToCb,   "To Account");
        if (from.getId().equals(to.getId()))
            throw new IllegalArgumentException("From and To accounts must differ.");

        Transaction t = new Transaction(Type.TRANSFER, date, desc, amt);
        t.setFromAccountId(from.getId());
        t.setToAccountId(to.getId());
        if (trfCatCb.getValue() != null || trfSubCatCb.getValue() != null) {
            Transaction.Classification trfCl = new Transaction.Classification();
            if (trfCatCb.getValue()    != null) trfCl.setCategoryId(trfCatCb.getValue().getId());
            if (trfSubCatCb.getValue() != null) trfCl.setSubCategoryId(trfSubCatCb.getValue().getId());
            t.setClassification(trfCl);
        }
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    private Transaction saveInvestment() {
        LocalDate         date = requireDate();
        String            desc = requireText(sharedDesc, "Description");
        long              amt  = parsePaise(sharedAmt);
        Account           from = requireAccount(invFromCb, "From Account");
        InvestmentAccount dest = invDestCb.getValue();
        if (dest == null) throw new IllegalArgumentException("Please select an investment account.");

        Transaction t = new Transaction(Type.INVESTMENT, date, desc, amt);
        t.setFromAccountId(from.getId());
        t.setToAccountId(dest.getId());

        String userNotes = nullIfBlank(sharedNotes.getText());
        switch (dest.getInvestmentType()) {
            case MUTUAL_FUNDS, EQUITY -> {
                Transaction.InvestmentDetails mfInv = new Transaction.InvestmentDetails();
                if (invSchemeFld != null) mfInv.setSchemeScriptName(nullIfBlank(invSchemeFld.getText()));
                if (invUnitsFld  != null && !invUnitsFld.getText().isBlank()) {
                    try { mfInv.setUnitsNav(Double.parseDouble(invUnitsFld.getText().trim())); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException("Units/NAV must be a number."); }
                }
                t.setInvestmentDetails(mfInv);
                t.setNotes(userNotes);
            }
            case FIXED_DEPOSIT, DEBT_BONDS -> {
                Transaction.FdDetails fd = new Transaction.FdDetails();
                fd.setRef(nullIfBlank(invFdRefFld != null ? invFdRefFld.getText() : null));
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
                List<String> rdRefs = ds.getRdRefsForAccount(dest.getId());
                if (rdRefs.isEmpty())
                    throw new IllegalArgumentException(
                            "No RD schedules found for this account. Please create a recurring schedule first.");
                String rdRef = invRdRefCb != null ? invRdRefCb.getValue() : null;
                if (rdRef == null || rdRef.isBlank())
                    throw new IllegalArgumentException("Please select an RD reference number.");
                StringBuilder sb = new StringBuilder();
                appendNote(sb, "RD Ref", rdRef);
                if (userNotes != null) sb.append("Notes: ").append(userNotes);
                t.setNotes(sb.toString().stripTrailing());
            }
            default -> t.setNotes(userNotes);
        }
        return persistTransaction(t);
    }

    private Transaction saveCCPayment() {
        LocalDate date = requireDate();
        long      amt  = parsePaise(sharedAmt);
        Account   bank = requireAccount(ccBankCb, "From Bank Account");
        Account   card = requireAccount(ccCardCb, "To Credit Card");

        String desc = nullIfBlank(sharedDesc.getText());
        if (desc == null) desc = (existing != null && existing.getDescription() != null)
                ? existing.getDescription() : "CC Payment — " + card.getName();

        Transaction t = new Transaction(Type.CC_PAYMENT, date, desc, amt);
        t.setFromAccountId(bank.getId());
        t.setToAccountId(card.getId());
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    private Transaction saveRefund() {
        LocalDate date = requireDate();
        String    desc = requireText(sharedDesc, "Description");
        long      amt  = parsePaise(sharedAmt);
        Account   acct = requireAccount(refAcctCb, "To Account");

        Transaction t = new Transaction(Type.REFUND, date, desc, amt);
        t.setToAccountId(acct.getId());
        Transaction.Classification refCl = new Transaction.Classification();
        if (refCatCb.getValue()    != null) refCl.setCategoryId(refCatCb.getValue().getId());
        if (refSubCatCb.getValue() != null) refCl.setSubCategoryId(refSubCatCb.getValue().getId());
        refCl.setFamilyMember(nullIfBlank(refFamilyFld.getText()));
        t.setClassification(refCl);
        applyPayMode(t, refModeCb);
        String refNum = nullIfBlank(refRefFld.getText());
        if (refNum != null) {
            if (t.getPayment() == null) t.setPayment(new Transaction.Payment());
            t.getPayment().setReferenceNumber(refNum);
        }
        t.setNotes(nullIfBlank(sharedNotes.getText()));
        return persistTransaction(t);
    }

    private Transaction saveRedeem() {
        LocalDate         date      = requireDate();
        String            desc      = requireText(sharedDesc, "Description");
        long              total     = parsePaise(sharedAmt);
        long              principal = parsePaise(rdePrincipalFld);
        InvestmentAccount from      = rdeFromCb.getValue();
        if (from == null) throw new IllegalArgumentException("From Investment Account is required.");
        Account           to        = requireAccount(rdeToCb, "To Bank Account");
        //if (principal > total)
        //    throw new IllegalArgumentException("Principal cannot exceed total redemption amount.");

        String  notes    = nullIfBlank(sharedNotes.getText());
        String  groupId  = UUID.randomUUID().toString();
        long    gainLoss = total - principal;

        // 1. Investment-side: outgoing REDEEM from investment account (amount = principal)
        Transaction invTxn = new Transaction(Type.REDEEM, date, desc, principal);
        invTxn.setFromAccountId(from.getId());
        Transaction.RedeemDetails invRd = new Transaction.RedeemDetails();
        invRd.setPrincipalPaise(principal);
        invTxn.setRedeemDetails(invRd);
        invTxn.setGroupTransactionId(groupId);
        invTxn.setNotes(notes);
        invTxn.setSourceIndicator(Transaction.SourceIndicator.MANUAL);

        // 2. Bank-side: incoming REDEEM (principal returned to bank).
        // If we're editing an imported transaction, carry its hash over so it stays reconciled.
        boolean existingWasImported = existing != null && existing.getImportHash() != null;
        Transaction bankPrincipal = new Transaction(Type.REDEEM, date, desc, principal);
        bankPrincipal.setToAccountId(to.getId());
        Transaction.RedeemDetails bankRd = new Transaction.RedeemDetails();
        bankRd.setPrincipalPaise(principal);
        bankPrincipal.setRedeemDetails(bankRd);
        bankPrincipal.setGroupTransactionId(groupId);
        bankPrincipal.setNotes(notes);
        if (existingWasImported) {
            bankPrincipal.setImportHash(existing.getImportHash());
            bankPrincipal.setSourceIndicator(Transaction.SourceIndicator.RECONCILED);
        } else {
            bankPrincipal.setSourceIndicator(Transaction.SourceIndicator.MANUAL);
        }

        // 3. Bank-side: GAIN or LOSE (only if gain/loss is non-zero)
        Transaction gainLossTxn = null;
        if (gainLoss > 0) {
            gainLossTxn = new Transaction(Type.GAIN, date, desc, gainLoss);
            gainLossTxn.setToAccountId(to.getId());
        } else if (gainLoss < 0) {
            gainLossTxn = new Transaction(Type.LOSE, date, desc, -gainLoss);
            gainLossTxn.setFromAccountId(to.getId());
        }
        if (gainLossTxn != null) {
            gainLossTxn.setGroupTransactionId(groupId);
            gainLossTxn.setNotes(notes);
            if (existingWasImported) {
                gainLossTxn.setImportHash(existing.getImportHash());
                gainLossTxn.setSourceIndicator(Transaction.SourceIndicator.RECONCILED);
            } else {
                gainLossTxn.setSourceIndicator(Transaction.SourceIndicator.MANUAL);
            }
            if (rdeCatCb.getValue() != null || rdeSubCatCb.getValue() != null) {
                Transaction.Classification gainCl = new Transaction.Classification();
                if (rdeCatCb.getValue()    != null) gainCl.setCategoryId(rdeCatCb.getValue().getId());
                if (rdeSubCatCb.getValue() != null) gainCl.setSubCategoryId(rdeSubCatCb.getValue().getId());
                gainLossTxn.setClassification(gainCl);
            }
        }

        // If editing, delete the old group or old single REDEEM (no save yet)
        if (existing != null) {
            String oldGroup = existing.getGroupTransactionId();
            if (oldGroup != null) {
                ds.deleteTransactionGroupInternal(oldGroup);
            } else {
                ds.deleteTransactionByIdInternal(existing.getId());
            }
        }

        // Persist all new transactions atomically
        ds.addTransactionInternal(invTxn);
        ds.addTransactionInternal(bankPrincipal);
        if (gainLossTxn != null) ds.addTransactionInternal(gainLossTxn);
        ds.saveTransactionsNow();

        if (gainLossTxn != null) ds.learnFromTransaction(gainLossTxn);
        return invTxn; // representative for table selection restore
    }

    // ── Persist ───────────────────────────────────────────────────────────────

    private Transaction persistTransaction(Transaction t) {
        if (existing != null) {
            // Editing an imported/auto-categorized transaction means the user has
            // reviewed it — upgrade to RECONCILED (green border) to mark it as confirmed.
            // MANUAL stays MANUAL; RECONCILED stays RECONCILED.
            Transaction.SourceIndicator indicator = existing.getSourceIndicator();
            if (indicator == Transaction.SourceIndicator.AUTO_CATEGORIZED
                    || indicator == Transaction.SourceIndicator.IMPORTED)
                indicator = Transaction.SourceIndicator.RECONCILED;
            t.setSourceIndicator(indicator);
            t.setImportHash(existing.getImportHash());
            ds.updateTransactionInPlace(existing.getId(), t);
        } else {
            ds.addTransaction(t);
        }
        // Learn a type rule when the user reclassifies an imported EXPENSE/INCOME
        // to a more specific type (e.g. CC_PAYMENT, LOAN_PAYMENT, TRANSFER).
        if (existing != null
                && (existing.getType() == Transaction.Type.EXPENSE
                    || existing.getType() == Transaction.Type.INCOME)
                && (existing.getSourceIndicator() == Transaction.SourceIndicator.IMPORTED
                    || existing.getSourceIndicator() == Transaction.SourceIndicator.AUTO_CATEGORIZED)
                && t.getType() != existing.getType()) {
            ds.learnTypeRule(existing.getType(), t);
        }
        ds.learnFromTransaction(t);
        return t;
    }

    // ── Prefill (edit mode) ───────────────────────────────────────────────────

    private void prefillFromTransaction(Transaction t) {
        // Shared fields
        if (t.getDate()        != null) sharedDate.setValue(t.getDate());
        if (t.getDescription() != null) sharedDesc.setText(t.getDescription());
        sharedAmt.setText(String.format("%.2f", Math.abs(t.getAmountPaise()) / 100.0));
        if (t.getNotes()       != null) sharedNotes.setText(t.getNotes());

        // GAIN/LOSE are internal types — display them as REDEEM for editing
        Type displayType = (t.getType() == Type.GAIN || t.getType() == Type.LOSE)
                ? Type.REDEEM : t.getType();
        typeCb.setValue(displayType);

        // Type-specific fields
        switch (t.getType()) {
            case EXPENSE -> {
                contextAccountId = t.getFromAccountId();
                contextIsSource  = true;
                setAccount(expAcctCb, t.getFromAccountId());
                prefillCat(expCatCb, expSubCatCb, t);
                setPayMode(expModeCb, t.getPayment() != null ? t.getPayment().getMode() : null);
                setText(expFamilyFld, t.getClassification() != null ? t.getClassification().getFamilyMember() : null);
                setText(expRefFld,    t.getPayment() != null ? t.getPayment().getReferenceNumber() : null);
            }
            case INCOME -> {
                String id = t.getToAccountId() != null ? t.getToAccountId() : t.getFromAccountId();
                contextAccountId = id;
                contextIsSource  = false;
                setAccount(incAcctCb, id);
                prefillCat(incCatCb, incSubCatCb, t);
                setText(incFamilyFld, t.getClassification() != null ? t.getClassification().getFamilyMember() : null);
            }
            case TRANSFER -> {
                setAccount(trfFromCb, t.getFromAccountId());
                setAccount(trfToCb,   t.getToAccountId());
                prefillCat(trfCatCb, trfSubCatCb, t);
            }
            case INVESTMENT -> prefillInvestment(t);
            case CC_PAYMENT -> {
                setAccount(ccBankCb, t.getFromAccountId());
                setAccount(ccCardCb, t.getToAccountId());
            }
            case REFUND -> {
                setAccount(refAcctCb, t.getToAccountId());
                prefillCat(refCatCb, refSubCatCb, t);
                setPayMode(refModeCb, t.getPayment() != null ? t.getPayment().getMode() : null);
                setText(refFamilyFld, t.getClassification() != null ? t.getClassification().getFamilyMember() : null);
                setText(refRefFld,    t.getPayment() != null ? t.getPayment().getReferenceNumber() : null);
            }
            case REDEEM, GAIN, LOSE -> prefillRedeemForm(t);
            case LOAN_PAYMENT -> {
                setAccount(lnFromCb, t.getFromAccountId());
                if (t.getToAccountId() != null)
                    ds.getActiveLoanAccounts().stream()
                            .filter(la -> la.getId().equals(t.getToAccountId()))
                            .findFirst().ifPresent(lnToCb::setValue);
                long lnPrin = t.getRedeemDetails() != null ? t.getRedeemDetails().getPrincipalPaise() : 0;
                if (lnPrin > 0)
                    setText(lnPrincipalFld, String.format("%.0f", lnPrin / 100.0));
                prefillCat(lnCatCb, lnSubCatCb, t);
                setPayMode(lnModeCb, t.getPayment() != null ? t.getPayment().getMode() : null);
                setText(lnRefFld, t.getPayment() != null ? t.getPayment().getReferenceNumber() : null);
            }
        }
    }

    private void prefillCat(ComboBox<Category> catCb, ComboBox<Category> subCatCb, Transaction t) {
        String catId    = t.getClassification() != null ? t.getClassification().getCategoryId() : null;
        String subCatId = t.getClassification() != null ? t.getClassification().getSubCategoryId() : null;
        if (catId != null) {
            catCb.getItems().stream()
                    .filter(c -> c.getId().equals(catId))
                    .findFirst().ifPresent(cat -> {
                        catCb.setValue(cat);
                        if (subCatCb != null && subCatId != null)
                            subCatCb.getItems().stream()
                                    .filter(s -> s.getId().equals(subCatId))
                                    .findFirst().ifPresent(subCatCb::setValue);
                    });
        } else if (t.getDescription() != null) {
            ds.suggestCategoryForDescription(t.getDescription(), t.getType())
              .ifPresent(rule -> catCb.getItems().stream()
                      .filter(c -> c.getId().equals(rule.getCategoryId()))
                      .findFirst().ifPresent(cat -> {
                          catCb.setValue(cat);
                          if (subCatCb != null && rule.getSubCategoryId() != null)
                              subCatCb.getItems().stream()
                                      .filter(s -> s.getId().equals(rule.getSubCategoryId()))
                                      .findFirst().ifPresent(subCatCb::setValue);
                      }));
        }
    }

    private void prefillInvestment(Transaction t) {
        setAccount(invFromCb, t.getFromAccountId());
        if (t.getToAccountId() == null) return;
        ds.getInvestmentAccounts().stream()
                .filter(ia -> ia.getId().equals(t.getToAccountId()))
                .findFirst().ifPresent(ia -> {
                    invDestCb.setValue(ia); // triggers refreshInvestmentDynamicFields
                    switch (ia.getInvestmentType()) {
                        case MUTUAL_FUNDS, EQUITY -> {
                            Transaction.InvestmentDetails mfInv = t.getInvestmentDetails();
                            if (mfInv != null) {
                                setText(invSchemeFld, mfInv.getSchemeScriptName());
                                if (mfInv.getUnitsNav() != null && invUnitsFld != null)
                                    invUnitsFld.setText(mfInv.getUnitsNav().toString());
                            }
                        }
                        case FIXED_DEPOSIT, DEBT_BONDS -> {
                            Transaction.FdDetails fd = t.getInvestmentDetails() != null
                                    ? t.getInvestmentDetails().getFd() : null;
                            if (fd != null) {
                                setText(invFdRefFld, fd.getRef());
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
                            sharedNotes.setText(t.getNotes() != null ? t.getNotes() : "");
                        }
                        case RECURRING_DEPOSIT -> {
                            if (invRdRefCb != null) {
                                String rdRef = parseNote(t.getNotes(), "RD Ref");
                                if (rdRef != null) invRdRefCb.setValue(rdRef);
                                invRdRefCb.setDisable(true);
                                invRdRefCb.getStyleClass().add("combo-locked");
                            }
                            String userNotes = parseNote(t.getNotes(), "Notes");
                            sharedNotes.setText(userNotes != null ? userNotes : "");
                        }
                        default -> {}
                    }
                });
    }

    /**
     * Fills the Redeem panel fields. Handles both the old single-transaction format and
     * the new grouped format (three linked transactions sharing a groupTransactionId).
     * Also called when editing a GAIN or LOSE transaction (which belong to a REDEEM group).
     */
    private void prefillRedeemForm(Transaction t) {
        if (t.getGroupTransactionId() != null) {
            // New grouped format: load all siblings and reconstruct the form
            List<Transaction> group = ds.getTransactions().stream()
                    .filter(tx -> t.getGroupTransactionId().equals(tx.getGroupTransactionId()))
                    .collect(Collectors.toList());

            Transaction invTxn = group.stream()
                    .filter(tx -> tx.getType() == Type.REDEEM && tx.getFromAccountId() != null)
                    .findFirst().orElse(null);
            Transaction bankTxn = group.stream()
                    .filter(tx -> tx.getType() == Type.REDEEM
                               && tx.getToAccountId() != null && tx.getFromAccountId() == null)
                    .findFirst().orElse(null);
            Transaction gainLossTxn = group.stream()
                    .filter(tx -> tx.getType() == Type.GAIN || tx.getType() == Type.LOSE)
                    .findFirst().orElse(null);

            // Investment account
            if (invTxn != null && invTxn.getFromAccountId() != null)
                ds.getInvestmentAccounts().stream()
                        .filter(ia -> ia.getId().equals(invTxn.getFromAccountId()))
                        .findFirst().ifPresent(rdeFromCb::setValue);

            // Bank account (from the bank-side REDEEM or the GAIN/LOSE transaction)
            String bankId = bankTxn != null ? bankTxn.getToAccountId()
                    : gainLossTxn != null && gainLossTxn.getType() == Type.GAIN
                            ? gainLossTxn.getToAccountId()
                    : gainLossTxn != null ? gainLossTxn.getFromAccountId()
                    : null;
            if (bankId != null) setAccount(rdeToCb, bankId);

            // Principal and total
            long principal = invTxn != null ? invTxn.getAmountPaise() : 0;
            if (principal > 0)
                rdePrincipalFld.setText(String.format("%.2f", principal / 100.0));
            long total = principal + (gainLossTxn == null ? 0
                    : gainLossTxn.getType() == Type.GAIN
                            ?  gainLossTxn.getAmountPaise()
                            : -gainLossTxn.getAmountPaise());
            sharedAmt.setText(String.format("%.2f", total / 100.0));

            // Category from the GAIN/LOSE transaction — switch list to expense if LOSE
            if (gainLossTxn != null) {
                switchRedeemCatList(gainLossTxn.getType() == Type.LOSE);
                prefillCat(rdeCatCb, rdeSubCatCb, gainLossTxn);
            }

        } else {
            // Old single-transaction format (backward compatibility)
            if (t.getFromAccountId() != null)
                ds.getInvestmentAccounts().stream()
                        .filter(ia -> ia.getId().equals(t.getFromAccountId()))
                        .findFirst().ifPresent(rdeFromCb::setValue);
            setAccount(rdeToCb, t.getToAccountId());
            long rdePrin = t.getRedeemDetails() != null ? t.getRedeemDetails().getPrincipalPaise() : 0;
            if (rdePrin > 0)
                rdePrincipalFld.setText(String.format("%.2f", rdePrin / 100.0));
            // sharedAmt already set from t.getAmountPaise() = total for old format
            prefillCat(rdeCatCb, rdeSubCatCb, t);
        }
    }

    // ── Context account pre-population on type change ─────────────────────────

    private void applyContextAccount(Type newType) {
        Account acct = ds.getAccounts().stream()
                .filter(a -> a.getId().equals(contextAccountId))
                .findFirst().orElse(null);
        if (acct == null) return;
        switch (newType) {
            case EXPENSE -> {
                // Bank and CC are both valid expense accounts
                if (acct instanceof BankAccount || acct instanceof CreditCardAccount)
                    setAccount(expAcctCb, contextAccountId);
            }
            case INCOME -> {
                // Income lands in a bank account
                if (acct instanceof BankAccount)
                    setAccount(incAcctCb, contextAccountId);
            }
            case TRANSFER -> {
                // Bank → bank; use contextIsSource to decide direction
                if (acct instanceof BankAccount) {
                    if (contextIsSource) setAccount(trfFromCb, contextAccountId);
                    else                 setAccount(trfToCb,   contextAccountId);
                }
            }
            case INVESTMENT -> {
                // Bank funds the investment; investment account is the destination
                if (acct instanceof BankAccount)
                    setAccount(invFromCb, contextAccountId);
                else if (acct instanceof InvestmentAccount)
                    invDestCb.getItems().stream()
                            .filter(a -> a.getId().equals(contextAccountId))
                            .findFirst().ifPresent(invDestCb::setValue);
            }
            case CC_PAYMENT -> {
                // Bank pays the CC; CC is the destination
                if (acct instanceof BankAccount)
                    setAccount(ccBankCb, contextAccountId);
                else if (acct instanceof CreditCardAccount)
                    setAccount(ccCardCb, contextAccountId);
            }
            case REFUND -> {
                // Refund returns to the account that was originally charged (bank or CC)
                if (acct instanceof BankAccount || acct instanceof CreditCardAccount)
                    setAccount(refAcctCb, contextAccountId);
            }
            case REDEEM -> {
                // Investment is redeemed from; bank receives the proceeds
                if (acct instanceof InvestmentAccount)
                    rdeFromCb.getItems().stream()
                            .filter(a -> a.getId().equals(contextAccountId))
                            .findFirst().ifPresent(rdeFromCb::setValue);
                else if (acct instanceof BankAccount)
                    setAccount(rdeToCb, contextAccountId);
            }
            case LOAN_PAYMENT -> {
                // Payment comes from bank or CC; loan account is the destination
                if (acct instanceof LoanAccount)
                    lnToCb.getItems().stream()
                            .filter(a -> a.getId().equals(contextAccountId))
                            .findFirst().ifPresent(lnToCb::setValue);
                else if (acct instanceof BankAccount || acct instanceof CreditCardAccount)
                    setAccount(lnFromCb, contextAccountId);
            }
            default -> {}
        }
    }

    /**
     * Pre-populate account fields when a new transaction is opened from within
     * an account's transaction view.  Sets the context account so that
     * subsequent type-changes continue to track the correct account.
     */
    public void setContextAccount(Account acc) {
        if (acc == null) return;
        contextAccountId = acc.getId();

        if (acc instanceof LoanAccount) {
            typeCb.getItems().setAll(Type.LOAN_PAYMENT);
            contextIsSource = false;
            typeCb.setValue(Type.LOAN_PAYMENT);
            lnToCb.getItems().stream()
                    .filter(a -> a.getId().equals(acc.getId()))
                    .findFirst().ifPresent(lnToCb::setValue);
        } else if (acc instanceof InvestmentAccount) {
            typeCb.getItems().setAll(Type.INVESTMENT, Type.REDEEM);
            contextIsSource = false;
            typeCb.setValue(Type.INVESTMENT);
            invDestCb.getItems().stream()
                    .filter(a -> a.getId().equals(acc.getId()))
                    .findFirst().ifPresent(invDestCb::setValue);
        } else if (acc instanceof CreditCardAccount) {
            typeCb.getItems().setAll(Type.EXPENSE, Type.REFUND, Type.CC_PAYMENT);
            contextIsSource = false;
            typeCb.setValue(Type.EXPENSE);
            setAccount(expAcctCb, acc.getId());
        } else {
            // BankAccount — all types available
            contextIsSource = true;
            setAccount(expAcctCb, acc.getId());
        }
    }

    // ── Investment dynamic fields ─────────────────────────────────────────────

    private void refreshInvestmentDynamicFields(InvestmentAccount.InvestmentType itype) {
        invDynamicBox.getChildren().clear();
        invSchemeFld = invUnitsFld = null;
        invFdRefFld  = invFdRateFld = invFdMaturityAmtFld = null;
        invFdMaturityPicker = null;
        invFdInterestPayableCb = null;
        invFdPayoutAnchorCb = null;
        invFdPayoutMonthCb  = null;
        invFdPayoutDaySp    = null;
        invRdRefCb  = null;
        invRdRateFld = null;
        invRdMaturityPicker = null;
        invPrevScheduleBox.getChildren().clear();
        if (itype == null) {
            invRightPanel.setVisible(false);
            invRightPanel.setManaged(false);
            resizeDialog(560);
            return;
        }
        invRightPanel.setVisible(true);
        invRightPanel.setManaged(true);
        resizeDialog(828);

        VBox g = new VBox(4);
        g.setPadding(new Insets(8, 10, 4, 10));
        switch (itype) {
            case MUTUAL_FUNDS, EQUITY -> {
                invSchemeFld = tf("optional");
                invUnitsFld  = tf("e.g. 100.5");
                dynStackRow(g, "Scheme / Script", invSchemeFld);
                dynStackRow(g, "Units / NAV",     invUnitsFld);
            }
            case FIXED_DEPOSIT, DEBT_BONDS -> {
                invFdRefFld            = tf("optional");
                invFdRateFld           = tf("e.g. 7.5");
                invFdMaturityPicker    = new DatePicker();
                UiUtils.styleOnShow(invFdMaturityPicker);
                invFdMaturityAmtFld    = tf("optional");
                invFdInterestPayableCb = new ComboBox<>();
                invFdInterestPayableCb.getItems().addAll(Transaction.InterestPayable.values());
                invFdInterestPayableCb.setPromptText("Select");
                invFdInterestPayableCb.setMaxWidth(Double.MAX_VALUE);
                invFdInterestPayableCb.setCellFactory(lv -> new ListCell<>() {
                    @Override protected void updateItem(Transaction.InterestPayable ip, boolean empty) {
                        super.updateItem(ip, empty);
                        if (empty || ip == null) { setText(null); return; }
                        // AT_MATURITY → "At Maturity", YEARLY → "Yearly"
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
                invFdPayoutDaySp.setMaxWidth(Double.MAX_VALUE);
                invFdPayoutDaySp.setEditable(true);

                invFdPayoutDaySp.setPrefWidth(72);
                invFdPayoutDaySp.setMaxWidth(72);
                HBox payoutDateBox = new HBox(8, invFdPayoutMonthCb, new Label("Day"), invFdPayoutDaySp);
                payoutDateBox.setAlignment(Pos.CENTER_LEFT);
                payoutDateBox.setVisible(false);
                payoutDateBox.setManaged(false);
                HBox.setHgrow(invFdPayoutMonthCb, Priority.ALWAYS);
                invFdPayoutAnchorCb.valueProperty().addListener((obs, old, val) -> {
                    boolean fixed = val == Transaction.PayoutAnchor.FIXED_DATE;
                    payoutDateBox.setVisible(fixed);
                    payoutDateBox.setManaged(fixed);
                });

                dynStackRow(g, "Reference No",      invFdRefFld);
                dynStackRow(g, "Interest Rate (%)", invFdRateFld);
                dynStackRow(g, "Maturity Date",     invFdMaturityPicker);
                dynStackRow(g, "Maturity Amount",   invFdMaturityAmtFld);
                dynStackRow(g, "Interest Payable",  invFdInterestPayableCb);
                dynStackRow(g, "Payout Anchor",     invFdPayoutAnchorCb);
                dynStackRow(g, "Payout Date",       payoutDateBox);
            }
            case RECURRING_DEPOSIT -> {
                InvestmentAccount rdAcc = invDestCb.getValue();
                List<String> rdRefs = rdAcc != null
                        ? ds.getRdRefsForAccount(rdAcc.getId())
                        : java.util.Collections.emptyList();
                if (rdRefs.isEmpty()) {
                    Label err = new Label("No RD schedules found for this account.\nCreate a recurring schedule first.");
                    err.getStyleClass().add("text-error");
                    err.setWrapText(true);
                    g.getChildren().add(err);
                } else {
                    invRdRefCb = new ComboBox<>();
                    invRdRefCb.getItems().addAll(rdRefs);
                    invRdRefCb.setEditable(false);
                    invRdRefCb.setMaxWidth(Double.MAX_VALUE);
                    invRdRefCb.setPromptText("Select RD reference");
                    dynStackRow(g, "RD Reference No*", invRdRefCb);
                }
            }
            default -> { /* PROVIDENT_FUND — no extra fields */ }
        }
        invDynamicBox.getChildren().add(g);
    }

    private void dynStackRow(VBox container, String labelText, Node field) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        if (field instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        VBox wrapper = new VBox(3, lbl, field);
        container.getChildren().add(wrapper);
    }

    // ── FD/Bond preview panel ─────────────────────────────────────────────────

    private void recalcFdPreview() {
        if (invPreviewPanel == null || !invPreviewPanel.isVisible()) return;

        long   principal   = parseAmountSafe(sharedAmt.getText());
        Double rate        = parseDoubleSafe(invFdRateFld);
        LocalDate start    = sharedDate.getValue();
        LocalDate maturity = invFdMaturityPicker != null ? invFdMaturityPicker.getValue() : null;
        long   matAmt      = invFdMaturityAmtFld != null ? parseAmountSafe(invFdMaturityAmtFld.getText()) : 0;
        Transaction.InterestPayable freq = invFdInterestPayableCb != null ? invFdInterestPayableCb.getValue() : null;
        Transaction.PayoutAnchor anchor  = invFdPayoutAnchorCb   != null
                ? invFdPayoutAnchorCb.getValue() : Transaction.PayoutAnchor.ANNIVERSARY;
        Month   payoutMonth = invFdPayoutMonthCb != null ? invFdPayoutMonthCb.getValue() : null;
        Integer payoutDay   = invFdPayoutDaySp   != null ? invFdPayoutDaySp.getValue()   : null;

        // Summary
        invPrevPrincipal.setText(principal > 0 ? fmtPaise(principal) : "—");
        long annualInt = (principal > 0 && rate != null && rate > 0)
                ? Math.round(principal * rate / 100.0) : 0;
        invPrevAnnualInt.setText(annualInt > 0 ? fmtPaise(annualInt) : "—");
        long totalInt = matAmt > 0 && principal > 0 ? matAmt - principal : 0;
        invPrevTotalInt.setText(totalInt > 0 ? fmtPaise(totalInt) : "—");

        if (start != null && maturity != null && !maturity.isBefore(start)) {
            long days  = java.time.temporal.ChronoUnit.DAYS.between(start, maturity);
            long yrs   = days / 365;
            long mos   = (days % 365) / 30;
            StringBuilder sb = new StringBuilder();
            if (yrs > 0) sb.append(yrs).append(yrs == 1 ? " yr" : " yrs");
            if (mos > 0) { if (sb.length() > 0) sb.append(' '); sb.append(mos).append(" mo"); }
            if (sb.length() == 0) sb.append(days).append(" days");
            invPrevTenor.setText(sb.toString());
        } else {
            invPrevTenor.setText("—");
        }

        // Payout schedule
        invPrevScheduleBox.getChildren().clear();
        if (start == null || maturity == null || freq == null || rate == null
                || rate <= 0 || principal <= 0) return;

        List<LocalDate> dates = computePayoutDates(start, maturity, freq, anchor,
                payoutMonth != null ? payoutMonth.getValue() : null, payoutDay);
        if (dates.isEmpty()) return;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        LocalDate prev = start;
        for (int i = 0; i < dates.size(); i++) {
            LocalDate d     = dates.get(i);
            boolean isLast  = (i == dates.size() - 1);
            long days       = java.time.temporal.ChronoUnit.DAYS.between(prev, d);
            long interest   = Math.round(principal * rate / 100.0 * days / 365.0);

            Label dateLbl = new Label(d.format(fmt));
            dateLbl.getStyleClass().add("text-body-muted");
            dateLbl.setMinWidth(110);

            Label amtLbl = new Label(fmtPaise(interest) + (isLast ? " + principal" : ""));
            amtLbl.getStyleClass().addAll(isLast
                    ? List.of("text-form-value", "text-success") : List.of("text-form-value"));

            invPrevScheduleBox.getChildren().add(new HBox(8, dateLbl, amtLbl));
            prev = d;
        }
    }

    private List<LocalDate> computePayoutDates(
            LocalDate start, LocalDate maturity,
            Transaction.InterestPayable freq,
            Transaction.PayoutAnchor anchor,
            Integer payoutMonth, Integer payoutDay) {
        List<LocalDate> dates = new ArrayList<>();
        if (freq == Transaction.InterestPayable.AT_MATURITY) {
            dates.add(maturity);
            return dates;
        }
        int months = switch (freq) {
            case YEARLY    -> 12;
            case QUARTERLY ->  3;
            case MONTHLY   ->  1;
            default        ->  0;
        };
        if (months == 0) return dates;

        LocalDate current;
        if (anchor == Transaction.PayoutAnchor.FIXED_DATE
                && payoutMonth != null && payoutDay != null) {
            current = LocalDate.of(start.getYear(), payoutMonth, payoutDay);
            if (!current.isAfter(start)) current = current.plusMonths(months);
        } else {
            current = start.plusMonths(months);
        }
        while (!current.isAfter(maturity)) {
            dates.add(current);
            current = current.plusMonths(months);
        }
        if (dates.isEmpty() || !dates.get(dates.size() - 1).equals(maturity))
            dates.add(maturity);
        return dates;
    }

    private long   parseAmountSafe(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Math.round(Double.parseDouble(s.replace(",", "").replace("₹", "").trim()) * 100); }
        catch (NumberFormatException e) { return 0; }
    }

    private Double parseDoubleSafe(TextField tf) {
        if (tf == null || tf.getText().isBlank()) return null;
        try { return Double.parseDouble(tf.getText().trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private String fmtPaise(long paise) { return String.format("₹%,.0f", paise / 100.0); }

    private Label previewVal() {
        Label l = new Label("—");
        l.getStyleClass().add("text-form-value");
        return l;
    }

    private void prevRow(GridPane g, int row, String labelText, Label val) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("text-body-muted");
        g.add(lbl, 0, row);
        g.add(val, 1, row);
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private void resizeDialog(double prefWidth) {
        getDialogPane().setPrefWidth(prefWidth);
        javafx.stage.Stage stage = (javafx.stage.Stage) getDialogPane().getScene().getWindow();
        if (stage != null) {
            // Adjust width only — sizeToScene() also collapses height, which is wrong here
            double chrome = stage.getWidth() - getDialogPane().getWidth();
            stage.setWidth(prefWidth + Math.max(chrome, 0));
        }
    }

    /** Standard 2-column form GridPane (label 120px | field expands). */
    private GridPane form() {
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10);
        g.setPadding(new Insets(10));
        ColumnConstraints cc1 = new ColumnConstraints(120);
        ColumnConstraints cc2 = new ColumnConstraints(0, 200, Double.MAX_VALUE);
        cc2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(cc1, cc2);
        return g;
    }

    /** Panel grid with tighter top/bottom padding so panels flow into shared sections. */
    private GridPane panelGrid() {
        GridPane g = form();
        g.setPadding(new Insets(4, 10, 4, 10));
        return g;
    }

    private void row(GridPane g, int row, String label, Node field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        if (field instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        g.add(lbl, 0, row);
        g.add(field, 1, row);
    }

    private TextField tf(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        return tf;
    }

    private ComboBox<Account> accountCombo(boolean includeCreditCards) {
        ComboBox<Account> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("Select account");
        ds.getBankAccounts().forEach(cb.getItems()::add);
        if (includeCreditCards) ds.getCreditCardAccounts().forEach(cb.getItems()::add);
        styleAccountCombo(cb);
        return cb;
    }

    private void styleAccountCombo(ComboBox<Account> cb) {
        cb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Account a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? null : a.getName());
            }
        });
        cb.setButtonCell(cb.getCellFactory().call(null));
    }

    private ComboBox<String> payModeCombo() {
        ComboBox<String> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("optional");
        cb.getItems().addAll("UPI", "Net Banking", "Debit Card", "Credit Card",
                "Cash", "Cheque", "Auto-debit", "NEFT", "IMPS");
        cb.setValue("UPI");
        return cb;
    }

    // ── Value helpers ─────────────────────────────────────────────────────────

    private LocalDate requireDate() {
        LocalDate d = sharedDate.getValue();
        if (d == null) throw new IllegalArgumentException("Date is required.");
        return d;
    }

    private String requireText(TextField tf, String label) {
        String s = tf.getText();
        if (s == null || s.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return s.trim();
    }

    private Account requireAccount(ComboBox<Account> cb, String label) {
        Account a = cb.getValue();
        if (a == null) throw new IllegalArgumentException(label + " is required.");
        return a;
    }

    private long parsePaise(TextField tf) {
        String s = tf.getText() == null ? "" : tf.getText().trim().replace(",", "");
        if (s.isBlank()) throw new IllegalArgumentException("Amount is required.");
        try { return Math.round(Double.parseDouble(s) * 100); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Amount must be a number."); }
    }

    private void applyPayMode(Transaction t, ComboBox<String> cb) {
        if (cb.getValue() == null || cb.getValue().isBlank()) return;
        try {
            Transaction.PaymentMode mode = Transaction.PaymentMode.valueOf(
                    cb.getValue().replace(' ', '_').toUpperCase());
            if (t.getPayment() == null) t.setPayment(new Transaction.Payment());
            t.getPayment().setMode(mode);
        } catch (IllegalArgumentException ignored) {}
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void setText(TextField tf, String value) {
        if (tf != null && value != null) tf.setText(value);
    }

    private void setAccount(ComboBox<Account> cb, String accountId) {
        if (accountId == null || cb == null) return;
        cb.getItems().stream()
                .filter(a -> a.getId().equals(accountId))
                .findFirst().ifPresent(cb::setValue);
    }

    private void setPayMode(ComboBox<String> cb, Transaction.PaymentMode mode) {
        if (cb == null || mode == null) return;
        String s = mode.name().replace('_', ' ');
        // match case-insensitively against items
        cb.getItems().stream()
                .filter(item -> item.equalsIgnoreCase(s))
                .findFirst().ifPresent(cb::setValue);
    }

    private void setDateFromNote(DatePicker dp, String dateStr) {
        if (dp == null || dateStr == null) return;
        try { dp.setValue(LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"))); }
        catch (Exception ignored) {}
    }

    // ── Note parsing ──────────────────────────────────────────────────────────

    private void appendNote(StringBuilder sb, String key, TextField tf) {
        if (tf != null && !tf.getText().isBlank())
            sb.append(key).append(": ").append(tf.getText().trim()).append("\n");
    }

    private void appendNote(StringBuilder sb, String key, String val) {
        if (val != null && !val.isBlank())
            sb.append(key).append(": ").append(val.trim()).append("\n");
    }

    private String parseNote(String notes, String key) {
        if (notes == null) return null;
        for (String line : notes.split("\n"))
            if (line.startsWith(key + ": ")) return line.substring((key + ": ").length()).trim();
        return null;
    }

    // ── Error ─────────────────────────────────────────────────────────────────

    /** Moves focus to the first field in document order that has no value. */
    private void focusFirstEmpty() {
        if (sharedDesc.getText().isBlank()) { sharedDesc.requestFocus(); return; }
        if (sharedAmt.getText().isBlank())  { sharedAmt.requestFocus();  return; }
        switch (typeCb.getValue()) {
            case EXPENSE -> {
                if (expAcctCb.getValue()  == null) { expAcctCb.requestFocus();  return; }
                if (expCatCb.getValue()   == null) { expCatCb.requestFocus();   return; }
                if (expSubCatCb.getValue()== null) { expSubCatCb.requestFocus();return; }
            }
            case INCOME -> {
                if (incAcctCb.getValue()  == null) { incAcctCb.requestFocus();  return; }
                if (incCatCb.getValue()   == null) { incCatCb.requestFocus();   return; }
            }
            case TRANSFER -> {
                if (trfFromCb.getValue()  == null) { trfFromCb.requestFocus();  return; }
                if (trfToCb.getValue()    == null) { trfToCb.requestFocus();    return; }
            }
            case REFUND -> {
                if (refAcctCb.getValue()  == null) { refAcctCb.requestFocus();  return; }
                if (refCatCb.getValue()   == null) { refCatCb.requestFocus();   return; }
            }
            case CC_PAYMENT -> {
                if (ccBankCb.getValue()   == null) { ccBankCb.requestFocus();   return; }
                if (ccCardCb.getValue()   == null) { ccCardCb.requestFocus();   return; }
            }
            case INVESTMENT -> {
                if (invFromCb.getValue()  == null) { invFromCb.requestFocus();  return; }
                if (invDestCb.getValue()  == null) { invDestCb.requestFocus();  return; }
            }
            case REDEEM -> {
                if (rdeFromCb.getValue()              == null) { rdeFromCb.requestFocus();      return; }
                if (rdeToCb.getValue()                == null) { rdeToCb.requestFocus();        return; }
                if (rdePrincipalFld.getText().isBlank())       { rdePrincipalFld.requestFocus(); return; }
            }
        }
        if (sharedNotes.getText().isBlank()) sharedNotes.requestFocus();
    }

    private void showError(String msg) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Validation Error");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(380);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "⚠", "Validation Error");

        VBox body = new VBox(10);
        body.setPadding(new Insets(16));

        HBox iconRow = new HBox(14);
        iconRow.setAlignment(Pos.CENTER_LEFT);
        Label iconLbl = new Label("⚠");
        iconLbl.getStyleClass().addAll("dialog-icon-box-lg", "dialog-icon-box-lg--error");
        Label msgLbl = new Label(msg);
        msgLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: -brand-dark;");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(280);
        iconRow.getChildren().addAll(iconLbl, msgLbl);
        body.getChildren().add(iconRow);

        dlg.getDialogPane().setContent(body);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dlg.showAndWait();
    }
}
