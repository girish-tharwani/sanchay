package com.sanchay.ui.accounts;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.ui.MainWindow;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/** Accounts module. */
public class AccountsScreen {

    private final MainWindow mainWindow;

    // view is initialised ONCE; never reassigned so that the reference held by MainWindow stays valid
    private final StackPane view;

    // "Show Closed" checkbox per group — state persists across buildList() rebuilds
    private final CheckBox showClosedBank       = new CheckBox("Show Closed");
    private final CheckBox showClosedCC         = new CheckBox("Show Closed");
    private final CheckBox showClosedLoan       = new CheckBox("Show Closed");
    private final CheckBox showClosedInvestment = new CheckBox("Show Closed");

    public AccountsScreen(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.view = new StackPane();
        buildList();
    }

    public Node getView() { return view; }

    private DateTimeFormatter dateFmt() {
        return DataStore.getInstance().getDateFormatter();
    }

    // ── Account list ──────────────────────────────────────────────────────────

    private void buildList() {
        mainWindow.setPostTransactionCallback(null);
        mainWindow.setTransactionContextAccount(null);
        VBox content = new VBox(24);
        content.getStyleClass().add("main-panel");
        content.setPadding(new Insets(24));

        DataStore ds = DataStore.getInstance();

        boolean allCollapsed = ds.isGroupCollapsed("bank") && ds.isGroupCollapsed("cc")
                && ds.isGroupCollapsed("loan") && ds.isGroupCollapsed("investment");

        Label collapseAllChevron = new Label(allCollapsed ? "▸" : "▾");
        collapseAllChevron.getStyleClass().add("group-chevron");
        // Inline required: font-size is a sizing tweak, not a colour or theme token
        collapseAllChevron.setStyle("-fx-font-size: 24px;");

        Label title = new Label("Accounts");
        title.getStyleClass().add("screen-title");

        HBox titleRow = new HBox(10, collapseAllChevron, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setStyle("-fx-cursor: hand;");
        Tooltip.install(titleRow, new Tooltip(allCollapsed ? "Expand all" : "Collapse all"));
        titleRow.setOnMouseClicked(e -> {
            boolean collapse = !allCollapsed;
            ds.setGroupCollapsed("bank",       collapse);
            ds.setGroupCollapsed("cc",         collapse);
            ds.setGroupCollapsed("loan",       collapse);
            ds.setGroupCollapsed("investment", collapse);
            buildList();
        });
        content.getChildren().addAll(
                titleRow,
                buildGroup("Bank Accounts",   "#3db89a",
                        showClosedBank.isSelected() ? ds.getAllBankAccounts()       : ds.getBankAccounts(),
                        "bank", showClosedBank),
                buildGroup("Credit Cards",    "#a78bfa",
                        showClosedCC.isSelected()   ? ds.getAllCreditCardAccounts() : ds.getCreditCardAccounts(),
                        "cc", showClosedCC),
                buildGroup("Loan Accounts",   "#f87171",
                        showClosedLoan.isSelected()  ? ds.getAllLoanAccounts()       : ds.getActiveLoanAccounts(),
                        "loan", showClosedLoan),
                buildGroup("Investments",     "#f0a500",
                        showClosedInvestment.isSelected() ? ds.getAllInvestmentAccounts() : ds.getInvestmentAccounts(),
                        "investment", showClosedInvestment)
        );

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-page-bg");

        view.getChildren().setAll(scroll);
    }

    private <T extends Account> VBox buildGroup(String heading, String dotColor, List<T> accounts, String type, CheckBox showClosedCb) {
        VBox group = new VBox(10);

        boolean collapsed = DataStore.getInstance().isGroupCollapsed(type);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-cursor: hand;");
        header.setOnMouseClicked(e -> {
            DataStore.getInstance().setGroupCollapsed(type, !collapsed);
            buildList();
        });

        Label chevron = new Label(collapsed ? "▸" : "▾");
        chevron.getStyleClass().add("filter-label");
        chevron.setStyle("-fx-font-size: 16px;");

        // Shape.fill cannot be set via style class; data-driven colour stays inline
        Circle dot = new Circle(4);
        dot.setFill(Color.web(dotColor));

        Label h = new Label(heading.toUpperCase());
        h.getStyleClass().add("filter-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("btn-gold");
        addBtn.setOnAction(e -> { openAddAccountDialog(type); buildList(); });
        // Consume click so addBtn doesn't also trigger the header toggle
        addBtn.setOnMouseClicked(javafx.event.Event::consume);

        if (collapsed) {
            header.getChildren().addAll(chevron, dot, h, spacer, addBtn);
        } else {
            showClosedCb.getStyleClass().add("text-hint");
            showClosedCb.setOnAction(e -> buildList());
            header.getChildren().addAll(chevron, dot, h, spacer, showClosedCb, addBtn);
        }
        group.getChildren().add(header);

        if (!collapsed) {
            if (accounts.isEmpty()) {
                Label none = new Label("No accounts added yet.");
                none.getStyleClass().add("text-empty");
                group.getChildren().add(none);
            } else {
                for (T acc : accounts) {
                    group.getChildren().add(buildAccountCard(acc));
                }
            }
        }
        return group;
    }

    private HBox buildAccountCard(Account acc) {
        HBox card = new HBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setAlignment(Pos.CENTER_LEFT);

        boolean active = acc.isActive();

        VBox info = new VBox(3);
        info.setMinWidth(200);
        info.setMaxWidth(200);
        Label name = new Label(acc.getName());
        // Inline required: active/inactive text colour is runtime data
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;"
                + (active ? " -fx-text-fill: -brand-dark;" : " -fx-text-fill: -text-hint;"));
        name.setMaxWidth(200);
        Label sub  = new Label(acc.getAccountType());
        sub.getStyleClass().add("text-hint");
        info.getChildren().addAll(name, sub);

        Label descLbl = new Label(
                (acc.getDescription() != null && !acc.getDescription().isBlank())
                        ? acc.getDescription() : "");
        descLbl.getStyleClass().add("text-hint");
        descLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(descLbl, Priority.ALWAYS);

        VBox balanceBox;
        if (active) {
            balanceBox = buildBalanceBox(acc);
        } else {
            Label statusBadge = new Label(formatAccountStatus(acc));
            statusBadge.getStyleClass().add("status-closed");
            balanceBox = new VBox(statusBadge);
        }
        balanceBox.setAlignment(Pos.CENTER_RIGHT);

        Button detailsBtn = new Button("ⓘ");
        detailsBtn.getStyleClass().add("btn-icon");
        detailsBtn.setTooltip(new Tooltip("Details"));
        detailsBtn.setOnAction(e -> showAccountDetails(acc));

        Button txnBtn = new Button("≡");
        txnBtn.getStyleClass().add("btn-icon");
        txnBtn.setTooltip(new Tooltip("Transactions"));
        txnBtn.setOnAction(e -> mainWindow.navigateToTransactions(acc));

        card.getChildren().addAll(info, descLbl, balanceBox, detailsBtn, txnBtn);

        return card;
    }

    private VBox buildBalanceBox(Account acc) {
        DataStore ds = DataStore.getInstance();
        String label = "";
        String value = "";
        String valueColour = "-brand-dark";

        if (acc instanceof BankAccount ba) {
            long bal = computeRunningBalance(ba);
            label = "Balance";
            value = String.format("₹%,.2f", bal / 100.0);
            valueColour = bal >= 0 ? "-brand-dark" : "-color-error";
        } else if (acc instanceof CreditCardAccount cc) {
            long out = ds.getCreditCardOutstandingPaise(cc.getId());
            long avail = cc.getCreditLimitPaise() - out;
            label = "Outstanding / Available";
            value = String.format("₹%,.2f  /  ₹%,.2f", out / 100.0, avail / 100.0);
            valueColour = out > 0 ? "-color-error" : "-brand-dark";
        } else if (acc instanceof LoanAccount la) {
            long outstanding = ds.getLoanOutstandingPaise(la);
            label = "Outstanding";
            value = String.format("₹%,.0f", outstanding / 100.0);
            valueColour = outstanding > 0 ? "-color-error" : "-brand-dark";
        } else if (acc instanceof InvestmentAccount ia) {
            long invested = ds.getInvestedPaiseAsOf(ia, LocalDate.now());
            if (isMarketValueAccount(ia)) {
                MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
                if (mv != null) {
                    long gl = mv.getGainLossPaise();
                    label = "Invested  /  Market Value";
                    value = String.format("₹%,.0f  /  ₹%,.0f",
                            invested / 100.0, mv.getMarketValuePaise() / 100.0);
                    // Inline required: gain/loss colour is runtime data
                    valueColour = gl >= 0 ? "#27AE60" : "#C62828";
                } else {
                    label = "Invested";
                    value = String.format("₹%,.0f", invested / 100.0);
                    valueColour = "-brand-mid";
                }
            } else {
                label = "Invested";
                value = String.format("₹%,.0f", invested / 100.0);
                valueColour = "-brand-mid";
            }
        } else {
            return new VBox();
        }

        Label lbl = new Label(label);
        lbl.getStyleClass().add("card-title");
        Label val = new Label(value);
        // Inline required: positive/negative/investment colour is runtime data
        val.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: " + valueColour + ";");
        VBox box = new VBox(2, lbl, val);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    // ── Account Details view ──────────────────────────────────────────────────

    private void showAccountDetails(Account acc) {
        VBox panel = new VBox(16);
        panel.getStyleClass().add("main-panel");
        panel.setPadding(new Insets(24));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Button back = new Button("⬅");
        back.getStyleClass().add("btn-secondary");
        back.setTooltip(new Tooltip("Back"));
        back.setOnAction(e -> buildList());
        Label title = new Label(acc.getName() + " — Details");
        title.getStyleClass().add("screen-title");
        header.getChildren().addAll(back, title);

        VBox fields = new VBox(8);
        addField(fields, "Account Type", acc.getAccountType());
        if (acc.getDescription() != null && !acc.getDescription().isBlank())
            addField(fields, "Description", acc.getDescription());
        addField(fields, "Currency",     acc.getCurrency());
        addField(fields, "Status",       formatAccountStatus(acc));
        if (acc.getOpeningDate() != null)
            addField(fields, "Opening Date", acc.getOpeningDate().format(dateFmt()));
        if (acc.isJointAccount() && !(acc instanceof LoanAccount))
            addField(fields, "Joint Holder", acc.getSecondHolderName());

        if (acc instanceof BankAccount ba) {
            addField(fields, "Account Number", ba.getMaskedAccountNumber());
            addField(fields, "Bank",
                    ba.getBankName());
            addField(fields, "Holder",         ba.getAccountHolder());
            addField(fields, "Current Balance",
                    String.format("₹%,.2f", computeRunningBalance(ba) / 100.0));
        } else if (acc instanceof CreditCardAccount cc) {
            addField(fields, "Card Number",  cc.getMaskedCardNumber());
            addField(fields, "Issuer",       cc.getBankIssuer());
            addField(fields, "Credit Limit", String.format("₹%,.2f", cc.getCreditLimitPaise() / 100.0));
            long out = DataStore.getInstance().getCreditCardOutstandingPaise(cc.getId());
            addField(fields, "Outstanding",  String.format("₹%,.2f", out / 100.0));
            addField(fields, "Billing Date", cc.getBillingCycleDate() + " of month");
            addField(fields, "Payment Due",  cc.getPaymentDueDays() + " days after billing");
            if (cc.isAddOnCard())
                addField(fields, "Add-on Card Holder", cc.getAddOnCardHolderName());
        } else if (acc instanceof LoanAccount la) {
            addField(fields, "Loan A/C Number", la.getLoanAccountNumber());
            addField(fields, "Lender",          la.getLenderName());
            addField(fields, "Loan Amount",     String.format("₹%,.2f", la.getLoanAmountPaise() / 100.0));
            addField(fields, "Interest Rate",   la.getInterestRate() + "% p.a.");
            addField(fields, "Tenure",          la.getTenureMonths() + " months");
            addField(fields, "EMI",             String.format("₹%,.2f", la.getEmiAmountPaise() / 100.0));
            addField(fields, "EMI Due Day",     la.getEmiDueDay() + " of month");
            addField(fields, "Current Balance", String.format("₹%,.2f",
                    DataStore.getInstance().getLoanOutstandingPaise(la) / 100.0));
            if (la.isJointAccount() && la.getCoApplicantName() != null && !la.getCoApplicantName().isBlank())
                addField(fields, "Co-applicant", la.getCoApplicantName());
        } else if (acc instanceof InvestmentAccount ia) {
            long invested = DataStore.getInstance().getBaseInvestedPaise(ia)
                    + DataStore.getInstance().getTransactions().stream()
                        .filter(t -> t.getType() == Transaction.Type.INVESTMENT
                                  && ia.getId().equals(t.getToAccountId()))
                        .mapToLong(Transaction::getAmountPaise).sum()
                    - DataStore.getInstance().getTransactions().stream()
                        .filter(t -> t.getType() == Transaction.Type.TRANSFER
                                  && ia.getId().equals(t.getFromAccountId()))
                        .mapToLong(Transaction::getAmountPaise).sum()
                    - DataStore.getInstance().getTransactions().stream()
                        .filter(t -> t.getType() == Transaction.Type.REDEEM
                                  && ia.getId().equals(t.getFromAccountId()))
                        .mapToLong(t -> t.getRedeemDetails() != null && t.getRedeemDetails().getPrincipalPaise() > 0
                                ? t.getRedeemDetails().getPrincipalPaise() : t.getAmountPaise()).sum();
            addField(fields, "Investment Type",         ia.getAccountType());
            addField(fields, "Account Number",           ia.getFolioAccountNumber());
            addField(fields, "Current Invested Amount", String.format("₹%,.2f", Math.max(0, invested) / 100.0));
            if (isMarketValueAccount(ia)) {
                MarketValueEntry mv = DataStore.getInstance().getLatestMarketValue(ia.getId());
                if (mv != null) {
                    long gl    = mv.getGainLossPaise();
                    double pct = invested > 0 ? gl * 100.0 / invested : 0;
                    addField(fields, "Latest Market Value",
                            String.format("₹%,.2f  (as of %s)",
                                    mv.getMarketValuePaise() / 100.0,
                                    mv.getValueDate().format(dateFmt())));
                    addField(fields, "Gain / Loss",
                            String.format("%s₹%,.2f  (%+.2f%%)",
                                    gl >= 0 ? "+" : "", gl / 100.0, pct));
                }
            }
        }

        if (acc.getNotes() != null && !acc.getNotes().isBlank())
            addField(fields, "Notes", acc.getNotes());

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("btn-gold");
        editBtn.setOnAction(e -> { openEditAccountDialog(acc); showAccountDetails(acc); });

        HBox actionRow = new HBox(10, editBtn);
        if (acc instanceof LoanAccount la) {
            Button schedBtn = new Button("View Repayment Schedule");
            schedBtn.getStyleClass().add("btn-gold");
            schedBtn.setOnAction(e -> new LoanScheduleDialog(la).show());
            actionRow.getChildren().add(schedBtn);
        }
        if (acc instanceof InvestmentAccount ia && isMarketValueAccount(ia)) {
            Button recMvBtn = new Button("Record Market Value");
            recMvBtn.getStyleClass().add("btn-gold");
            recMvBtn.setOnAction(e -> {
                new RecordMarketValueDialog(ia).showAndWait().ifPresent(entry -> {
                    DataStore.getInstance().addMarketValue(entry);
                    showAccountDetails(acc); // refresh details to show updated market value
                });
            });
            Button histBtn = new Button("Value History");
            histBtn.getStyleClass().add("btn-gold");
            histBtn.setOnAction(e -> new MarketValueHistoryDialog(ia).showAndWait());
            actionRow.getChildren().addAll(recMvBtn, histBtn);
        }

        panel.getChildren().addAll(header, fields, actionRow);

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-page-bg");

        view.getChildren().setAll(scroll);
    }


    // ── Add Account Dialogs ───────────────────────────────────────────────────

    private void openAddAccountDialog(String type) {
        switch (type) {
            case "bank"       -> AccountDialog.showForBank(null);
            case "cc"         -> AccountDialog.showForCreditCard(null);
            case "loan"       -> AccountDialog.showForLoan(null);
            case "investment" -> AccountDialog.showForInvestment(null);
        }
    }

    private void openEditAccountDialog(Account acc) {
        if (acc instanceof BankAccount ba)            AccountDialog.showForBank(ba);
        else if (acc instanceof CreditCardAccount cc) AccountDialog.showForCreditCard(cc);
        else if (acc instanceof LoanAccount la)       AccountDialog.showForLoan(la);
        else if (acc instanceof InvestmentAccount ia) AccountDialog.showForInvestment(ia);
    }

    // ── Account status formatting helpers ─────────────────────────────────────

    private boolean isMarketValueAccount(InvestmentAccount ia) {
        return ia.getInvestmentType() == InvestmentAccount.InvestmentType.MUTUAL_FUNDS
                || ia.getInvestmentType() == InvestmentAccount.InvestmentType.EQUITY;
    }

    private String formatCardStatus(CreditCardAccount.CardStatus s) {
        return switch (s) {
            case ACTIVE    -> "Active";
            case BLOCKED   -> "Blocked";
            case CANCELLED -> "Cancelled";
        };
    }

    private String formatLoanStatus(LoanAccount.LoanStatus s) {
        return switch (s) {
            case ACTIVE  -> "Active";
            case CLOSED  -> "Closed";
            case SETTLED -> "Settled";
        };
    }

    private String formatInvestmentStatus(InvestmentAccount.InvestmentStatus s) {
        return switch (s) {
            case ACTIVE   -> "Active";
            case CLOSED   -> "Closed";
            case REDEEMED -> "Redeemed";
        };
    }

    /** Returns the richest available status string for an account, in title case. */
    private String formatAccountStatus(Account acc) {
        if (acc instanceof CreditCardAccount cc)
            return formatCardStatus(cc.getCardStatus());
        if (acc instanceof LoanAccount la)
            return formatLoanStatus(la.getLoanStatus());
        if (acc instanceof InvestmentAccount ia)
            return formatInvestmentStatus(ia.getInvestmentStatus());
        return acc.isActive() ? "Active" : "Closed";
    }


    private long computeRunningBalance(BankAccount ba) {
        long balance = ba.getOpeningBalancePaise();
        for (Transaction t : DataStore.getInstance().getTransactions()) {
            if (ba.getId().equals(t.getFromAccountId())) balance -= t.getAmountPaise();
            if (ba.getId().equals(t.getToAccountId()))   balance += t.getAmountPaise();
        }
        return balance;
    }

    private void addField(VBox container, String label, String value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label + ":");
        lbl.setMinWidth(180);
        lbl.getStyleClass().add("text-form-value");
        Label val = new Label(value != null ? value : "—");
        val.getStyleClass().add("text-body-muted");
        row.getChildren().addAll(lbl, val);
        container.getChildren().add(row);
    }
}
