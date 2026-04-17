package com.sanchay.ui.accounts;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.NavigationContext;
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

    private final NavigationContext navCtx;

    // view is initialised ONCE; never reassigned so that the reference held by MainWindow stays valid
    private final StackPane view;

    // "Show Closed" checkbox per group — state persists across buildList() rebuilds
    private final CheckBox showClosedBank       = new CheckBox("Show Closed");
    private final CheckBox showClosedCC         = new CheckBox("Show Closed");
    private final CheckBox showClosedLoan       = new CheckBox("Show Closed");
    private final CheckBox showClosedInvestment = new CheckBox("Show Closed");

    public AccountsScreen(NavigationContext navCtx) {
        this.navCtx = navCtx;
        this.view = new StackPane();
        buildList();
    }

    public Node getView() { return view; }

    private DateTimeFormatter dateFmt() {
        return DataStore.getInstance().getDateFormatter();
    }

    // ── Account list ──────────────────────────────────────────────────────────

    private void buildList() {
        navCtx.setOnTransactionSaved(null);
        navCtx.setContextAccount(null);
        VBox content = new VBox(24);
        content.getStyleClass().add("main-panel");
        content.setPadding(new Insets(24));

        DataStore ds = DataStore.getInstance();

        boolean allCollapsed = ds.isGroupCollapsed("bank") && ds.isGroupCollapsed("cc")
                && ds.isGroupCollapsed("loan") && ds.isGroupCollapsed("investment");

        Label collapseAllChevron = new Label(allCollapsed ? "▸" : "▾");
        collapseAllChevron.getStyleClass().addAll("group-chevron", "icon-xl");

        Label title = new Label("Accounts");
        title.getStyleClass().add("screen-title");

        HBox titleRow = new HBox(10, collapseAllChevron, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.getStyleClass().add("cursor-hand");
        Tooltip.install(titleRow, new Tooltip(allCollapsed ? "Expand all" : "Collapse all"));
        titleRow.setOnMouseClicked(e -> {
            boolean collapse = !allCollapsed;
            ds.setGroupCollapsed("bank",       collapse);
            ds.setGroupCollapsed("cc",         collapse);
            ds.setGroupCollapsed("loan",       collapse);
            ds.setGroupCollapsed("investment", collapse);
            buildList();
        });

        Comparator<Account> byOrder = Comparator.comparingInt(Account::getDisplayOrder);

        List<Account> favourites = new ArrayList<>();
        ds.getAllBankAccounts().stream().filter(Account::isFavourite).forEach(favourites::add);
        ds.getAllCreditCardAccounts().stream().filter(Account::isFavourite).forEach(favourites::add);
        ds.getAllLoanAccounts().stream().filter(Account::isFavourite).forEach(favourites::add);
        ds.getAllInvestmentAccounts().stream().filter(Account::isFavourite).forEach(favourites::add);
        favourites.sort(byOrder);

        content.getChildren().addAll(
                titleRow,
                buildFavouritesGroup(favourites),
                buildGroup("Bank Accounts",   UiUtils.HEX_BRAND_LIGHT,
                        (showClosedBank.isSelected() ? ds.getAllBankAccounts()       : ds.getBankAccounts())
                                .stream().sorted(byOrder).collect(java.util.stream.Collectors.toList()),
                        "bank", showClosedBank, ds.getAllBankAccounts()),
                buildGroup("Credit Cards",    "#a78bfa",
                        (showClosedCC.isSelected()   ? ds.getAllCreditCardAccounts() : ds.getCreditCardAccounts())
                                .stream().sorted(byOrder).collect(java.util.stream.Collectors.toList()),
                        "cc", showClosedCC, ds.getAllCreditCardAccounts()),
                buildGroup("Loan Accounts",   "#f87171",
                        (showClosedLoan.isSelected()  ? ds.getAllLoanAccounts()       : ds.getActiveLoanAccounts())
                                .stream().sorted(byOrder).collect(java.util.stream.Collectors.toList()),
                        "loan", showClosedLoan, ds.getAllLoanAccounts()),
                buildGroup("Investments",     UiUtils.HEX_BRAND_ACCENT,
                        (showClosedInvestment.isSelected() ? ds.getAllInvestmentAccounts() : ds.getInvestmentAccounts())
                                .stream().sorted(byOrder).collect(java.util.stream.Collectors.toList()),
                        "investment", showClosedInvestment, ds.getAllInvestmentAccounts())
        );

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scroll.getStyleClass().add("scroll-page-bg");

        view.getChildren().setAll(scroll);
    }

    private <T extends Account> VBox buildGroup(String heading, String dotColor, List<T> accounts,
                                                String type, CheckBox showClosedCb, List<T> allInGroup) {
        VBox group = new VBox(10);

        boolean collapsed = DataStore.getInstance().isGroupCollapsed(type);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("cursor-hand");
        header.setOnMouseClicked(e -> {
            DataStore.getInstance().setGroupCollapsed(type, !collapsed);
            buildList();
        });

        Label chevron = new Label(collapsed ? "▸" : "▾");
        chevron.getStyleClass().addAll("filter-label", "icon-sm");

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
            Button reorderBtn = new Button("⇅ Reorder");
            reorderBtn.getStyleClass().addAll("btn-secondary", "btn-compact");
            reorderBtn.setOnAction(e -> {
                new ReorderAccountsDialog(heading, new java.util.ArrayList<>(allInGroup));
                buildList();
            });
            reorderBtn.setOnMouseClicked(javafx.event.Event::consume);

            showClosedCb.getStyleClass().add("text-hint");
            showClosedCb.setOnAction(e -> buildList());
            header.getChildren().addAll(chevron, dot, h, spacer, showClosedCb, reorderBtn, addBtn);
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

    private VBox buildFavouritesGroup(List<Account> accounts) {
        VBox group = new VBox(10);
        String type = "favourites";
        boolean collapsed = DataStore.getInstance().isGroupCollapsed(type);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("cursor-hand");
        header.setOnMouseClicked(e -> {
            DataStore.getInstance().setGroupCollapsed(type, !collapsed);
            buildList();
        });

        Label chevron = new Label(collapsed ? "▸" : "▾");
        chevron.getStyleClass().addAll("filter-label", "icon-sm");

        Circle dot = new Circle(4);
        dot.setFill(Color.web(UiUtils.HEX_BRAND_ACCENT));

        Label h = new Label("FAVOURITES");
        h.getStyleClass().add("filter-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(chevron, dot, h, spacer);
        group.getChildren().add(header);

        if (!collapsed) {
            if (accounts.isEmpty()) {
                Label none = new Label("No favourite accounts. Click ☆ on an account to add it here.");
                none.getStyleClass().add("text-empty");
                group.getChildren().add(none);
            } else {
                for (Account acc : accounts) {
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

        Label starLbl = new Label(acc.isFavourite() ? "★" : "☆");
        starLbl.getStyleClass().addAll("cursor-hand", acc.isFavourite() ? "account-star-active" : "account-star-inactive");
        starLbl.setTooltip(new Tooltip(acc.isFavourite() ? "Remove from Favourites" : "Add to Favourites"));
        starLbl.setOnMouseClicked(e -> {
            acc.setFavourite(!acc.isFavourite());
            DataStore.getInstance().saveAccountsNow();
            buildList();
            e.consume();
        });

        Label name = new Label(acc.getName());
        name.getStyleClass().addAll("stat-value", active ? "account-name-active" : "account-name-inactive");
        name.setMaxWidth(170);

        Label sub = new Label(acc.getAccountType());
        sub.getStyleClass().add("text-hint");

        VBox nameAndType = new VBox(3, name, sub);

        HBox info = new HBox(5, starLbl, nameAndType);
        info.setAlignment(Pos.TOP_LEFT);
        info.setMinWidth(200);
        info.setMaxWidth(200);

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
        txnBtn.setOnAction(e -> navCtx.navigateToTransactions(acc));

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
            value = MoneyFormatter.format(bal);
            valueColour = bal >= 0 ? "-brand-dark" : "-color-error";
        } else if (acc instanceof CreditCardAccount cc) {
            long out = ds.getCreditCardOutstandingPaise(cc.getId());
            long avail = cc.getCreditLimitPaise() - out;
            label = "Outstanding / Available";
            value = MoneyFormatter.format(out) + "  /  " + MoneyFormatter.format(avail);
            valueColour = out > 0 ? "-color-error" : "-brand-dark";
        } else if (acc instanceof LoanAccount la) {
            long outstanding = ds.getLoanOutstandingPaise(la);
            label = "Outstanding";
            value = MoneyFormatter.formatNoDecimal(outstanding);
            valueColour = outstanding > 0 ? "-color-error" : "-brand-dark";
        } else if (acc instanceof InvestmentAccount ia) {
            long invested = ds.getInvestedPaiseAsOf(ia, LocalDate.now());
            if (isMarketValueAccount(ia)) {
                MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
                if (mv != null) {
                    long gl = mv.getGainLossPaise();
                    label = "Invested  /  Market Value";
                    value = MoneyFormatter.formatNoDecimal(invested) + "  /  " + MoneyFormatter.formatNoDecimal(mv.getMarketValuePaise());
                    // Inline required: gain/loss colour is runtime data
                    valueColour = gl >= 0 ? "-brand-mid" : "-color-error";
                } else {
                    label = "Invested";
                    value = MoneyFormatter.formatNoDecimal(invested);
                    valueColour = "-brand-mid";
                }
            } else {
                label = "Invested";
                value = MoneyFormatter.formatNoDecimal(invested);
                valueColour = "-brand-mid";
            }
        } else {
            return new VBox();
        }

        Label lbl = new Label(label);
        lbl.getStyleClass().add("card-title");
        Label val = new Label(value);
        val.getStyleClass().add("text-value-lg");
        // Inline required: colour is runtime data (positive/negative/investment)
        val.setStyle("-fx-text-fill: " + valueColour + ";");
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
                    MoneyFormatter.format(computeRunningBalance(ba)));
        } else if (acc instanceof CreditCardAccount cc) {
            addField(fields, "Card Number",  cc.getMaskedCardNumber());
            addField(fields, "Issuer",       cc.getBankIssuer());
            addField(fields, "Credit Limit", MoneyFormatter.format(cc.getCreditLimitPaise()));
            long out = DataStore.getInstance().getCreditCardOutstandingPaise(cc.getId());
            addField(fields, "Outstanding",  MoneyFormatter.format(out));
            addField(fields, "Billing Date", cc.getBillingCycleDate() + " of month");
            addField(fields, "Payment Due",  cc.getPaymentDueDays() + " days after billing");
            if (cc.isAddOnCard())
                addField(fields, "Add-on Card Holder", cc.getAddOnCardHolderName());
        } else if (acc instanceof LoanAccount la) {
            addField(fields, "Loan A/C Number", la.getLoanAccountNumber());
            addField(fields, "Lender",          la.getLenderName());
            addField(fields, "Loan Amount",     MoneyFormatter.format(la.getLoanAmountPaise()));
            addField(fields, "Interest Rate",   la.getInterestRate() + "% p.a.");
            addField(fields, "Tenure",          la.getTenureMonths() + " months");
            addField(fields, "EMI",             MoneyFormatter.format(la.getEmiAmountPaise()));
            addField(fields, "EMI Due Day",     la.getEmiDueDay() + " of month");
            addField(fields, "Current Balance", MoneyFormatter.format(
                    DataStore.getInstance().getLoanOutstandingPaise(la)));
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
            addField(fields, "Current Invested Amount", MoneyFormatter.format(Math.max(0, invested)));
            if (isMarketValueAccount(ia)) {
                MarketValueEntry mv = DataStore.getInstance().getLatestMarketValue(ia.getId());
                if (mv != null) {
                    long gl    = mv.getGainLossPaise();
                    double pct = invested > 0 ? gl * 100.0 / invested : 0;
                    addField(fields, "Latest Market Value",
                            MoneyFormatter.format(mv.getMarketValuePaise()) + "  (as of " + mv.getValueDate().format(dateFmt()) + ")");
                    addField(fields, "Gain / Loss",
                            (gl >= 0 ? "+" : "") + MoneyFormatter.format(Math.abs(gl)) + "  (" + String.format("%+.2f%%", pct) + ")");
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
