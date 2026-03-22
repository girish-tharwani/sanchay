package com.sanchay.model;

import java.util.UUID;

/**
 * Stores the per-account column-mapping configuration used when importing
 * a CSV bank / credit-card statement.  Persisted to import_mappings.json.
 *
 * Column references are stored as header-name strings (not column indices) so
 * that the mapping survives column reordering between exports.
 * headerSnapshot is the joined first row of the CSV at mapping-save time; it is
 * compared against the next import's header row to decide whether the saved
 * mapping can be reused (always shown for confirmation regardless).
 */
public class ImportMapping {

    private String  id;
    private String  accountId;

    /** Header cell text that identifies the date column. */
    private String  columnDate;

    /** Header cell text that identifies the narrative / description column. */
    private String  columnDescription;

    /**
     * Header cell text for a single net-amount column.
     * Positive value = credit / income, negative = debit / expense.
     * Used when {@code amountSplit == false}.
     */
    private String  columnAmount;

    /** Header cell text for the debit (expense) column. Used when {@code amountSplit == true}. */
    private String  columnDebit;

    /** Header cell text for the credit (income) column. Used when {@code amountSplit == true}. */
    private String  columnCredit;

    /** True when the statement uses separate Debit and Credit columns instead of a single Amount. */
    private boolean amountSplit;

    /** {@link java.time.format.DateTimeFormatter} pattern, e.g. {@code "dd/MM/yyyy"}. */
    private String  dateFormat;

    /**
     * The first CSV row joined with commas, captured at mapping-save time.
     * A match against the next import's header row indicates this mapping is
     * likely still valid and can be pre-filled.
     */
    private String  headerSnapshot;

    public ImportMapping() {
        this.id = UUID.randomUUID().toString();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String  getId()               { return id; }
    public String  getAccountId()        { return accountId; }
    public String  getColumnDate()       { return columnDate; }
    public String  getColumnDescription(){ return columnDescription; }
    public String  getColumnAmount()     { return columnAmount; }
    public String  getColumnDebit()      { return columnDebit; }
    public String  getColumnCredit()     { return columnCredit; }
    public boolean isAmountSplit()       { return amountSplit; }
    public String  getDateFormat()       { return dateFormat; }
    public String  getHeaderSnapshot()   { return headerSnapshot; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id)                     { this.id = id; }
    public void setAccountId(String a)               { this.accountId = a; }
    public void setColumnDate(String s)              { this.columnDate = s; }
    public void setColumnDescription(String s)       { this.columnDescription = s; }
    public void setColumnAmount(String s)            { this.columnAmount = s; }
    public void setColumnDebit(String s)             { this.columnDebit = s; }
    public void setColumnCredit(String s)            { this.columnCredit = s; }
    public void setAmountSplit(boolean b)            { this.amountSplit = b; }
    public void setDateFormat(String s)              { this.dateFormat = s; }
    public void setHeaderSnapshot(String s)          { this.headerSnapshot = s; }
}
