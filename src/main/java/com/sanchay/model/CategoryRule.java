package com.sanchay.model;

import java.util.UUID;

/**
 * Represents a learned description → category mapping.
 * One rule entry per unique (normalizedKey, transactionType, categoryId, subCategoryId) tuple.
 * The count field tracks how many times the user applied this combination,
 * used to sort category dropdowns by recency of use.
 */
public class CategoryRule {

    private String id;
    private String normalizedKey;    // normalized form of the transaction description
    private String transactionType;  // Transaction.Type name, e.g. "EXPENSE"
    private String categoryId;
    private String subCategoryId;    // nullable
    private int    count;            // number of times user chose this category for this description

    /** For Gson deserialization. */
    public CategoryRule() {}

    public CategoryRule(String normalizedKey, String transactionType,
                        String categoryId, String subCategoryId) {
        this.id             = UUID.randomUUID().toString();
        this.normalizedKey  = normalizedKey;
        this.transactionType = transactionType;
        this.categoryId     = categoryId;
        this.subCategoryId  = subCategoryId;
        this.count          = 1;
    }

    public void incrementCount() { count++; }

    public String getId()              { return id; }
    public String getNormalizedKey()   { return normalizedKey; }
    public String getTransactionType() { return transactionType; }
    public String getCategoryId()      { return categoryId; }
    public String getSubCategoryId()   { return subCategoryId; }
    public int    getCount()           { return count; }
}
