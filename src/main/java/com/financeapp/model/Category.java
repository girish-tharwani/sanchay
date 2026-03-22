package com.financeapp.model;

import java.util.UUID;

/** Expense or income category, optionally nested under a parent. */
public class Category {

    public enum CategoryType { EXPENSE, INCOME }

    private String id;
    private String name;
    private CategoryType type;
    private String parentId;
    private boolean active;

    public Category(String name, CategoryType type, String parentId) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
        this.parentId = parentId;
        this.active = true;
    }

    public boolean isSubCategory()  { return parentId != null; }
    public String getId()           { return id; }
    public String getName()         { return name; }
    public CategoryType getType()   { return type; }
    public String getParentId()     { return parentId; }
    public boolean isActive()       { return active; }
    public void setName(String n)     { this.name = n; }
    public void setActive(boolean b)  { this.active = b; }
    public void setParentId(String p) { this.parentId = p; }

    @Override public String toString() { return name; }
}
