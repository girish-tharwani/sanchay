package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.model.Transaction.Type;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.*;

/**
 * Modal dialog for creating or editing any transaction type.
 * A single form with a Type dropdown at the top; type-specific fields
 * swap in below the shared Date / Description / Amount rows.
 *
 * Each transaction type is handled by a dedicated package-private panel class
 * ({@link ExpensePanel}, {@link IncomePanel}, etc.) that implements
 * {@link TransactionPanel}.  This class is the coordinator: it owns the shared
 * fields, wires the type switcher, and delegates save / prefill / focus logic
 * to the active panel via the interface.
 */
public class TransactionDialog extends Dialog<Transaction> {

    // ── Shared state (package-private — accessed by panel classes) ───────────
    final DataStore ds = DataStore.getInstance();
    Transaction existing;
    Transaction pendingResult;

    // ── Shared form fields ────────────────────────────────────────────────────
    ComboBox<Type> typeCb;
    DatePicker     sharedDate;
    TextField      sharedDesc;
    TextField      sharedAmt;
    TextField      sharedNotes;

    // ── Layout nodes ──────────────────────────────────────────────────────────
    GridPane   topGrid, botGrid;
    VBox       typeSection;
    VBox       standardContent;
    ScrollPane scroll;

    // ── Context account (type-change pre-population) ──────────────────────────
    String  contextAccountId;
    boolean contextIsSource;

    // ── Panel routing ─────────────────────────────────────────────────────────
    private final InvestmentPanel            investmentPanel; // typed — special side-by-side layout
    private final Map<Type, TransactionPanel> panels;

    // ─────────────────────────────────────────────────────────────────────────

    public TransactionDialog() {
        getDialogPane().getStyleClass().add("dialog-pane");
        getDialogPane().setId("txn-dialog-pane");
        UiUtils.initDialog(this, "New Transaction", "+", 560);

        // Shared fields
        sharedDate  = new DatePicker(LocalDate.now());
        sharedDate.setId("txn-date-picker");
        UiUtils.styleOnShow(sharedDate);
        sharedDesc  = new TextField();
        sharedDesc.setId("txn-description-field");
        sharedDesc.setPromptText("e.g. Electricity Bill");
        UiUtils.wireDescriptionAutocomplete(sharedDesc, ds.getDistinctTransactionDescriptions());
        sharedAmt   = new TextField();
        sharedAmt.setId("txn-amount-field");
        sharedAmt.setPromptText("0.00");
        sharedNotes = new TextField();
        sharedNotes.setId("txn-notes-field");
        sharedNotes.setPromptText("optional");

        // Type selector
        typeCb = new ComboBox<>();
        typeCb.setId("txn-type-combo");
        typeCb.getItems().addAll(
                Type.EXPENSE, Type.INCOME, Type.TRANSFER,
                Type.REFUND, Type.INVESTMENT, Type.CC_PAYMENT, Type.REDEEM, Type.LOAN_PAYMENT);
        typeCb.setValue(Type.EXPENSE);
        typeCb.setMaxWidth(Double.MAX_VALUE);
        typeCb.setConverter(typeNameConverter());

        // Build panels (shared fields must be created first)
        ExpensePanel     expensePanel     = new ExpensePanel(this);
        IncomePanel      incomePanel      = new IncomePanel(this);
        TransferPanel    transferPanel    = new TransferPanel(this);
        RefundPanel      refundPanel      = new RefundPanel(this);
        investmentPanel                  = new InvestmentPanel(this);
        CCPaymentPanel   ccPaymentPanel   = new CCPaymentPanel(this);
        LoanPaymentPanel loanPaymentPanel = new LoanPaymentPanel(this);
        RedeemPanel      redeemPanel      = new RedeemPanel(this);

        panels = new EnumMap<>(Type.class);
        panels.put(Type.EXPENSE,      expensePanel);
        panels.put(Type.INCOME,       incomePanel);
        panels.put(Type.TRANSFER,     transferPanel);
        panels.put(Type.REFUND,       refundPanel);
        panels.put(Type.INVESTMENT,   investmentPanel);
        panels.put(Type.CC_PAYMENT,   ccPaymentPanel);
        panels.put(Type.LOAN_PAYMENT, loanPaymentPanel);
        panels.put(Type.REDEEM,       redeemPanel);
        panels.put(Type.GAIN,         redeemPanel);
        panels.put(Type.LOSE,         redeemPanel);

        // Shared top grid: Type, Date, Description, Amount
        topGrid = form();
        topGrid.setId("txn-top-grid");
        topGrid.setPadding(new Insets(10, 10, 4, 10));
        int r = 0;
        row(topGrid, r++, "Type*",        typeCb);
        row(topGrid, r++, "Date*",        sharedDate);
        row(topGrid, r++, "Description*", sharedDesc);
        row(topGrid, r,   "Amount (₹)*",  sharedAmt);

        // Shared bottom grid: Notes
        botGrid = form();
        botGrid.setId("txn-bottom-grid");
        botGrid.setPadding(new Insets(4, 10, 10, 10));
        row(botGrid, 0, "Notes", sharedNotes);

        // Swappable middle section
        typeSection = new VBox();
        typeSection.setId("txn-type-section");
        typeSection.getChildren().add(expensePanel.getNode());

        typeCb.valueProperty().addListener((obs, old, type) -> {
            if (type == Type.INVESTMENT) {
                standardContent.getChildren().clear();
                investmentPanel.leftContent.getChildren().setAll(
                        topGrid, investmentPanel.getNode(), botGrid);
                scroll.setContent(investmentPanel.content);
                InvestmentAccount sel = investmentPanel.destCb.getValue();
                investmentPanel.refreshDynamicFields(sel != null ? sel.getInvestmentType() : null);
            } else {
                investmentPanel.leftContent.getChildren().clear();
                investmentPanel.refreshDynamicFields(null);
                typeSection.getChildren().setAll(panelFor(type).getNode());
                standardContent.getChildren().setAll(topGrid, typeSection, botGrid);
                scroll.setContent(standardContent);
            }
            if (contextAccountId != null) {
                Account acct = ds.getAccounts().stream()
                        .filter(a -> a.getId().equals(contextAccountId))
                        .findFirst().orElse(null);
                if (acct != null) panelFor(type).applyContextAccount(acct, contextIsSource);
            }
        });

        wireAutoSuggest();

        standardContent = new VBox();
        standardContent.setId("txn-standard-content");
        standardContent.getChildren().addAll(topGrid, typeSection, botGrid);

        scroll = new ScrollPane(standardContent);
        scroll.setId("txn-scroll-pane");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        getDialogPane().setContent(scroll);

        ButtonType saveBtn   = new ButtonType("Save",   ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(saveBtn, cancelBtn);

        setResultConverter(bt -> bt == saveBtn ? pendingResult : null);

        Platform.runLater(() -> {
            Button btn = (Button) getDialogPane().lookupButton(saveBtn);
            if (btn != null) {
                btn.setId("txn-save-button");
                btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                    try { pendingResult = save(); }
                    catch (Exception ex) { showError(ex.getMessage()); ev.consume(); }
                });
            }
            Button cancel = (Button) getDialogPane().lookupButton(cancelBtn);
            if (cancel != null) cancel.setId("txn-cancel-button");
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

    // ── Panel routing ─────────────────────────────────────────────────────────

    private TransactionPanel panelFor(Type type) {
        TransactionPanel p = panels.get(type);
        return p != null ? p : panels.get(Type.EXPENSE);
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

    // ── Auto-suggest (routes by current type via panel interface) ─────────────

    private void wireAutoSuggest() {
        sharedDesc.focusedProperty().addListener((obs, was, isNow) -> {
            if (isNow) return;
            String desc = sharedDesc.getText();
            if (desc == null || desc.isBlank()) return;

            Type               type      = typeCb.getValue();
            TransactionPanel   panel     = panelFor(type);
            ComboBox<Category> catCb     = panel.getCatCombo();
            List<Category>     catMaster = panel.getCatMaster();
            ComboBox<Category> subCatCb  = panel.getSubCatCombo();
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

    // ── Save routing ──────────────────────────────────────────────────────────

    private Transaction save() {
        Type type = typeCb.getValue();
        if (type == Type.GAIN || type == Type.LOSE)
            throw new IllegalStateException("GAIN/LOSE cannot be saved directly");
        return panelFor(type).save();
    }

    // ── Persist ───────────────────────────────────────────────────────────────

    Transaction persistTransaction(Transaction t) {
        if (existing != null) {
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
        if (t.getDate()        != null) sharedDate.setValue(t.getDate());
        if (t.getDescription() != null) sharedDesc.setText(t.getDescription());
        sharedAmt.setText(String.format("%.2f", Math.abs(t.getAmountPaise()) / 100.0));
        if (t.getNotes()       != null) sharedNotes.setText(t.getNotes());

        // GAIN/LOSE are internal types — display them as REDEEM for editing
        Type displayType = (t.getType() == Type.GAIN || t.getType() == Type.LOSE)
                ? Type.REDEEM : t.getType();
        typeCb.setValue(displayType);

        panelFor(t.getType()).prefill(t);
    }

    void prefillCat(ComboBox<Category> catCb, ComboBox<Category> subCatCb, Transaction t) {
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

    // ── Context account pre-population ────────────────────────────────────────

    /**
     * Pre-populate account fields when a new transaction is opened from within
     * an account's transaction view.
     */
    public void setContextAccount(Account acc) {
        if (acc == null) return;
        contextAccountId = acc.getId();

        if (acc instanceof LoanAccount) {
            typeCb.getItems().setAll(Type.LOAN_PAYMENT);
            contextIsSource = false;
            typeCb.setValue(Type.LOAN_PAYMENT);
            // type-change listener fires → panelFor(LOAN_PAYMENT).applyContextAccount(acc, false)
        } else if (acc instanceof InvestmentAccount) {
            typeCb.getItems().setAll(Type.INVESTMENT, Type.REDEEM);
            contextIsSource = false;
            typeCb.setValue(Type.INVESTMENT);
            // type-change listener fires → panelFor(INVESTMENT).applyContextAccount(acc, false)
        } else if (acc instanceof CreditCardAccount) {
            typeCb.getItems().setAll(Type.EXPENSE, Type.REFUND, Type.CC_PAYMENT);
            contextIsSource = false;
            typeCb.setValue(Type.EXPENSE);
            // type-change listener fires → panelFor(EXPENSE).applyContextAccount(acc, false)
        } else {
            // BankAccount — all types available, no type change → call explicitly
            contextIsSource = true;
            panelFor(typeCb.getValue()).applyContextAccount(acc, contextIsSource);
        }
    }

    // ── Focus helper ──────────────────────────────────────────────────────────

    private void focusFirstEmpty() {
        if (sharedDesc.getText().isBlank()) { sharedDesc.requestFocus(); return; }
        if (sharedAmt.getText().isBlank())  { sharedAmt.requestFocus();  return; }
        if (!panelFor(typeCb.getValue()).focusFirstEmpty() && sharedNotes.getText().isBlank())
            sharedNotes.requestFocus();
    }

    // ── Layout helpers (package-private — used by panel classes) ─────────────

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
    GridPane panelGrid() {
        GridPane g = form();
        g.setPadding(new Insets(4, 10, 4, 10));
        return g;
    }

    void row(GridPane g, int row, String label, Node field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        if (field.getId() != null && !field.getId().isBlank()) {
            lbl.setId(field.getId() + "-label");
        }
        if (field instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        g.add(lbl, 0, row);
        g.add(field, 1, row);
    }

    TextField tf(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        return tf;
    }

    void setNodeId(Node node, String id) {
        if (node != null) node.setId(id);
    }

    ComboBox<String> payModeCombo() {
        ComboBox<String> cb = new ComboBox<>();
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPromptText("optional");
        cb.getItems().addAll("UPI", "Net Banking", "Debit Card", "Credit Card",
                "Cash", "Cheque", "Auto-debit", "Internal Transfer");
        cb.setValue("Net Banking");
        return cb;
    }

    void resizeDialog(double prefWidth) {
        getDialogPane().setPrefWidth(prefWidth);
        Stage stage = (Stage) getDialogPane().getScene().getWindow();
        if (stage != null) {
            double chrome = stage.getWidth() - getDialogPane().getWidth();
            stage.setWidth(prefWidth + Math.max(chrome, 0));
        }
    }

    // ── Value helpers (package-private — used by panel classes) ──────────────

    LocalDate requireDate() {
        LocalDate d = sharedDate.getValue();
        if (d == null) throw new IllegalArgumentException("Date is required.");
        return d;
    }

    String requireText(TextField tf, String label) {
        String s = tf.getText();
        if (s == null || s.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return s.trim();
    }

    Account requireAccount(ComboBox<Account> cb, String label) {
        Account a = cb.getValue();
        if (a == null) throw new IllegalArgumentException(label + " is required.");
        return a;
    }

    long parsePaise(TextField tf) {
        String s = tf.getText() == null ? "" : tf.getText().trim().replace(",", "");
        if (s.isBlank()) throw new IllegalArgumentException("Amount is required.");
        try { return Math.round(Double.parseDouble(s) * 100); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Amount must be a number."); }
    }

    void applyPayMode(Transaction t, ComboBox<String> cb) {
        if (cb.getValue() == null || cb.getValue().isBlank()) return;
        try {
            Transaction.PaymentMode mode = Transaction.PaymentMode.valueOf(
                    cb.getValue().replace(' ', '_').toUpperCase());
            if (t.getPayment() == null) t.setPayment(new Transaction.Payment());
            t.getPayment().setMode(mode);
        } catch (IllegalArgumentException ignored) {}
    }

    String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    void setText(TextField tf, String value) {
        if (tf != null && value != null) tf.setText(value);
    }

    void setAccount(ComboBox<Account> cb, String accountId) {
        if (accountId == null || cb == null) return;
        cb.getItems().stream()
                .filter(a -> a.getId().equals(accountId))
                .findFirst().ifPresent(cb::setValue);
    }

    void setPayMode(ComboBox<String> cb, Transaction.PaymentMode mode) {
        if (cb == null || mode == null) return;
        String s = mode.name().replace('_', ' ');
        cb.getItems().stream()
                .filter(item -> item.equalsIgnoreCase(s))
                .findFirst().ifPresent(cb::setValue);
    }

    void appendNote(StringBuilder sb, String key, TextField tf) {
        if (tf != null && !tf.getText().isBlank())
            sb.append(key).append(": ").append(tf.getText().trim()).append("\n");
    }

    void appendNote(StringBuilder sb, String key, String val) {
        if (val != null && !val.isBlank())
            sb.append(key).append(": ").append(val.trim()).append("\n");
    }

    String parseNote(String notes, String key) {
        if (notes == null) return null;
        for (String line : notes.split("\n"))
            if (line.startsWith(key + ": ")) return line.substring((key + ": ").length()).trim();
        return null;
    }

    // ── Error ─────────────────────────────────────────────────────────────────

    private void showError(String msg) {
        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Validation Error", "⚠", 380);
        dlg.getDialogPane().setId("txn-validation-dialog-pane");

        VBox body = new VBox(10);
        body.setId("txn-validation-dialog-body");
        body.setPadding(new Insets(16));

        HBox iconRow = new HBox(14);
        iconRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label iconLbl = new Label("⚠");
        iconLbl.setId("txn-validation-dialog-icon");
        iconLbl.getStyleClass().addAll("dialog-icon-box-lg", "dialog-icon-box-lg--error");
        Label msgLbl = new Label(msg);
        msgLbl.setId("txn-validation-dialog-message");
        msgLbl.getStyleClass().add("text-body-strong");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(280);
        iconRow.getChildren().addAll(iconLbl, msgLbl);
        body.getChildren().add(iconRow);

        dlg.getDialogPane().setContent(body);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.OK);
        Platform.runLater(() -> {
            Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
            if (okBtn != null) okBtn.setId("txn-validation-dialog-ok-button");
        });
        dlg.showAndWait();
    }
}
