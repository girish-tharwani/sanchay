package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

/** Builds and refreshes the account-type-specific stats row shown at the top of the transactions screen. */
class TransactionStatsPanel {

    private final Account account;
    private Runnable refreshRunnable;

    TransactionStatsPanel(Account account) {
        this.account = account;
    }

    /**
     * Adds stats nodes to the correct layout containers and populates initial values.
     * For CreditCardAccount, adds both header and a ccSummary card to panel.
     * For all other account types, adds stats inline to header, then adds header to panel.
     */
    void addToLayout(HBox header, VBox panel) {
        DataStore ds = DataStore.getInstance();

        if (account instanceof CreditCardAccount cc) {
            Label outstandingVal = new Label();
            Label availableVal   = new Label();
            outstandingVal.setId("txn-cc-outstanding-value");
            availableVal.setId("txn-cc-available-value");
            outstandingVal.getStyleClass().add("stat-value");
            availableVal.getStyleClass().add("stat-value");

            HBox ccSummary = new HBox(24);
            ccSummary.getStyleClass().add("card");
            ccSummary.setPadding(new Insets(12, 16, 12, 16));
            ccSummary.setAlignment(Pos.CENTER_LEFT);
            ccSummary.getChildren().addAll(
                    ccStat("Credit Limit",  MoneyFormatter.format(cc.getCreditLimitPaise()), "-text-neutral"),
                    ccStatWithLabel("Outstanding",   outstandingVal, "-color-expense"),
                    ccStatWithLabel("Available",     availableVal,   "-color-income"),
                    ccStat("Billing Date",  cc.getBillingCycleDate() + " of month", "-text-neutral"),
                    ccStat("Payment Due",   cc.getPaymentDueDays() + " days after billing", "-text-neutral")
            );
            panel.getChildren().addAll(header, ccSummary);

            refreshRunnable = () -> {
                long out = DataStore.getInstance().getCreditCardOutstandingPaise(cc.getId());
                long avail = Math.min(cc.getCreditLimitPaise(), cc.getCreditLimitPaise() - out);
                outstandingVal.setText(MoneyFormatter.format(out));
                availableVal.setText(MoneyFormatter.format(avail));
            };
        } else {
            if (account instanceof BankAccount ba) {
                Label balVal = new Label();
                balVal.setId("txn-balance-value");
                balVal.getStyleClass().add("stat-value");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                header.getChildren().addAll(spacer, ccStatWithLabel("Balance", balVal, "-brand-dark"));
                refreshRunnable = () -> {
                    long bal = ba.getOpeningBalancePaise();
                    for (Transaction t : DataStore.getInstance().getTransactions()) {
                        if (ba.getId().equals(t.getFromAccountId())) bal -= t.getAmountPaise();
                        if (ba.getId().equals(t.getToAccountId()))   bal += t.getAmountPaise();
                    }
                    balVal.setText(MoneyFormatter.format(bal));
                };
            } else if (account instanceof LoanAccount la) {
                Label outVal = new Label();
                outVal.setId("txn-loan-outstanding-value");
                outVal.getStyleClass().add("stat-value");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                header.getChildren().addAll(spacer, ccStatWithLabel("Outstanding", outVal, "-color-error"));
                refreshRunnable = () -> {
                    long outstanding = DataStore.getInstance().getLoanOutstandingPaise(la);
                    outVal.setText(MoneyFormatter.formatNoDecimal(outstanding));
                    outVal.setStyle("-fx-text-fill: " + (outstanding > 0 ? "-color-error" : "-brand-dark") + ";");
                };
            } else if (account instanceof InvestmentAccount ia) {
                Label investedVal = new Label();
                investedVal.setId("txn-invested-value");
                investedVal.getStyleClass().add("stat-value");
                if (isMarketValueAccount(ia)) {
                    MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
                    if (mv != null) {
                        Label mvVal = new Label();
                        Label glVal = new Label();
                        mvVal.setId("txn-market-value");
                        glVal.setId("txn-gain-loss-value");
                        mvVal.getStyleClass().add("stat-value");
                        glVal.getStyleClass().add("stat-value");
                        Region spacer2 = new Region();
                        HBox.setHgrow(spacer2, Priority.ALWAYS);
                        header.getChildren().addAll(
                                spacer2,
                                ccStatWithLabel("Market Value", mvVal, "-brand-dark"),
                                ccStatWithLabel("Gain / Loss",  glVal, "-brand-dark")
                        );
                        refreshRunnable = () -> {
                            MarketValueEntry latest = DataStore.getInstance().getLatestMarketValue(ia.getId());
                            if (latest != null) {
                                long gl = latest.getGainLossPaise();
                                mvVal.setText(MoneyFormatter.formatNoDecimal(latest.getMarketValuePaise()));
                                glVal.setText((gl >= 0 ? "+" : "") + MoneyFormatter.formatNoDecimal(Math.abs(gl)));
                                glVal.setStyle("-fx-text-fill: " + (gl >= 0 ? "-color-income" : "-color-error") + ";");
                            }
                        };
                    }
                } else {
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    header.getChildren().addAll(spacer, ccStatWithLabel("Invested", investedVal, "-brand-dark"));
                    refreshRunnable = () -> {
                        long invested = ds.getBaseInvestedPaise(ia);
                        for (Transaction t : DataStore.getInstance().getTransactions()) {
                            if (t.getType() == Transaction.Type.INVESTMENT && ia.getId().equals(t.getToAccountId()))
                                invested += t.getAmountPaise();
                            if (t.getType() == Transaction.Type.REDEEM && ia.getId().equals(t.getFromAccountId())) {
                                long rdPrin = t.getRedeemDetails() != null ? t.getRedeemDetails().getPrincipalPaise() : 0;
                                invested -= rdPrin > 0 ? rdPrin : t.getAmountPaise();
                            }
                        }
                        investedVal.setText(MoneyFormatter.formatNoDecimal(Math.max(0, invested)));
                    };
                }
            }
            panel.getChildren().add(header);
        }

        if (refreshRunnable != null) refreshRunnable.run();
    }

    void refresh() {
        if (refreshRunnable != null) refreshRunnable.run();
    }

    private boolean isMarketValueAccount(InvestmentAccount ia) {
        return ia.getInvestmentType() == InvestmentAccount.InvestmentType.MUTUAL_FUNDS
                || ia.getInvestmentType() == InvestmentAccount.InvestmentType.EQUITY;
    }

    private VBox ccStat(String label, String value, String colour) {
        VBox b = new VBox(2);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("stat-label");
        Label val = new Label(value);
        val.getStyleClass().add("stat-value");
        // Inline required: colour is runtime data (balance direction / CC outstanding)
        val.setStyle("-fx-text-fill: " + colour + ";");
        b.getChildren().addAll(lbl, val);
        return b;
    }

    private VBox ccStatWithLabel(String label, Label val, String colour) {
        VBox b = new VBox(2);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("stat-label");
        val.setStyle("-fx-text-fill: " + colour + ";");
        b.getChildren().addAll(lbl, val);
        return b;
    }
}
