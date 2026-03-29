package com.sanchay.model;

import java.time.LocalDate;
import java.util.UUID;

/** Records a user-entered market value snapshot for an Equity or Mutual Fund account. */
public class MarketValueEntry {

    private String    id;
    private String    accountId;
    private LocalDate valueDate;
    private long      marketValuePaise;  // user-entered total market value
    private long      investedPaise;     // invested amount as-of valueDate (computed snapshot)

    public MarketValueEntry() {}

    public MarketValueEntry(String accountId, LocalDate valueDate,
                            long marketValuePaise, long investedPaise) {
        this.id               = UUID.randomUUID().toString();
        this.accountId        = accountId;
        this.valueDate        = valueDate;
        this.marketValuePaise = marketValuePaise;
        this.investedPaise    = investedPaise;
    }

    public String    getId()                             { return id; }
    public String    getAccountId()                      { return accountId; }
    public LocalDate getValueDate()                      { return valueDate; }
    public long      getMarketValuePaise()               { return marketValuePaise; }
    public long      getInvestedPaise()                  { return investedPaise; }
    public long      getGainLossPaise()                  { return marketValuePaise - investedPaise; }
}
