package com.sanchay.model;

import java.util.UUID;

/**
 * A learned description → transaction-type mapping for bank imports.
 *
 * When a user edits an imported EXPENSE or INCOME transaction and changes it to a more
 * specific type (e.g. CC_PAYMENT, LOAN_PAYMENT, TRANSFER), this rule is saved so that
 * future imports with the same description are pre-classified automatically.
 *
 * Only applies to bank account transactions (not CC / loan / investment imports).
 */
public class TypeRule {

    private String id;
    private String normalizedKey;    // normalized form of the transaction description
    private String sourceType;       // "EXPENSE" or "INCOME" — the original imported direction
    private String targetType;       // Transaction.Type name to suggest, e.g. "CC_PAYMENT"
    private String secondAccountId;  // nullable — the "other" account for two-account types
    private int    count;            // times the user applied this mapping

    /** For Gson deserialization. */
    public TypeRule() {}

    public TypeRule(String normalizedKey, String sourceType,
                    String targetType, String secondAccountId) {
        this.id               = UUID.randomUUID().toString();
        this.normalizedKey    = normalizedKey;
        this.sourceType       = sourceType;
        this.targetType       = targetType;
        this.secondAccountId  = secondAccountId;
        this.count            = 1;
    }

    public void incrementCount() { count++; }

    public String getId()               { return id; }
    public String getNormalizedKey()    { return normalizedKey; }
    public String getSourceType()       { return sourceType; }
    public String getTargetType()       { return targetType; }
    public String getSecondAccountId()  { return secondAccountId; }
    public int    getCount()            { return count; }
}
