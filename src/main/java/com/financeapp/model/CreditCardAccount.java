package com.financeapp.model;

import java.time.LocalDate;

/** Credit card account — modelled as a liability. Outstanding balance is computed dynamically. */
public class CreditCardAccount extends Account {

    public enum CardStatus { ACTIVE, BLOCKED, CANCELLED }

    private String cardNumber;
    private String bankIssuer;
    private long   creditLimitPaise;
    private int    billingCycleDate;
    private int    paymentDueDays;
    private String cardHolderName;
    private boolean addOnCard;
    private String addOnCardHolderName;
    private CardStatus cardStatus;

    public CreditCardAccount(String name) {
        super(name);
        this.cardStatus = CardStatus.ACTIVE;
    }

    @Override public AccountCategory getAccountCategory() { return AccountCategory.CREDIT_CARD; }

    @Override public String getAccountType() { return "Credit Card"; }

    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() <= 4) return cardNumber;
        return "xxxx-xxxx-xxxx-" + cardNumber.substring(cardNumber.length() - 4);
    }

    public LocalDate getCurrentPaymentDueDate() {
        LocalDate today = LocalDate.now();
        LocalDate billingDate = today.withDayOfMonth(
                Math.min(billingCycleDate, today.lengthOfMonth()));
        return billingDate.plusDays(paymentDueDays);
    }

    public String getCardNumber()                           { return cardNumber; }
    public String getBankIssuer()                           { return bankIssuer; }
    public long getCreditLimitPaise()                       { return creditLimitPaise; }
    public int getBillingCycleDate()                        { return billingCycleDate; }
    public int getPaymentDueDays()                          { return paymentDueDays; }
    public String getCardHolderName()                       { return cardHolderName; }
    public boolean isAddOnCard()                            { return addOnCard; }
    public String getAddOnCardHolderName()                  { return addOnCardHolderName; }
    public CardStatus getCardStatus()                       { return cardStatus; }

    public void setCardNumber(String n)                     { this.cardNumber = n; }
    public void setBankIssuer(String b)                     { this.bankIssuer = b; }
    public void setCreditLimitPaise(long p)                 { this.creditLimitPaise = p; }
    public void setBillingCycleDate(int d)                  { this.billingCycleDate = d; }
    public void setPaymentDueDays(int d)                    { this.paymentDueDays = d; }
    public void setCardHolderName(String n)                 { this.cardHolderName = n; }
    public void setAddOnCard(boolean b)                     { this.addOnCard = b; }
    public void setAddOnCardHolderName(String n)            { this.addOnCardHolderName = n; }
    public void setCardStatus(CardStatus s)                 { this.cardStatus = s; }
}
