# Plan: Transaction Data Model — Hierarchical JSON + Field Cleanup

## Context
The Transaction model is a flat JSON with 26 fields at the root, mixing always-present fields with type-specific ones.
This plan:
1. Restructures the model into a hierarchical JSON with typed sub-objects
2. Drops unused `incomeType` and `source` fields (captured but never read outside the dialog)
3. Adds `interestPayable` field to the FD group (At Maturity / Yearly / Quarterly / Monthly)
4. Fixes DEBT_BONDS investment type to show FD-style fields (currently shows MF/Equity fields)
5. No data migration code — assume clean slate

---

## Final JSON Structure

### INVESTMENT (FD or Bonds)
```json
{
  "id": "...",
  "type": "INVESTMENT",
  "date": "2026-03-15",
  "description": "TRF TO FD no. 098513082956",
  "amountPaise": 30000000,
  "fromAccountId": "...",
  "toAccountId": "...",
  "notes": null,
  "sourceIndicator": "MANUAL",
  "importHash": null,
  "groupTransactionId": null,
  "classification": null,
  "payment": null,
  "recurring": null,
  "redeemDetails": null,
  "investmentDetails": {
    "schemeScriptName": null,
    "unitsNav": null,
    "fd": {
      "ref": "098513082956",
      "interestRate": 7.25,
      "maturityDate": "2026-06-25",
      "maturityAmountPaise": 32819100,
      "interestPayable": "AT_MATURITY"
    }
  }
}
```

### EXPENSE
```json
{
  "id": "...",
  "type": "EXPENSE",
  "date": "2026-03-20",
  "description": "Electricity Bill",
  "amountPaise": 250000,
  "fromAccountId": "...",
  "toAccountId": null,
  "notes": "March 2026 quarter",
  "sourceIndicator": "MANUAL",
  "importHash": null,
  "groupTransactionId": null,
  "classification": {
    "categoryId": "cat-utilities",
    "subCategoryId": "sub-electricity",
    "familyMember": "Girish"
  },
  "payment": {
    "mode": "UPI",
    "referenceNumber": "UPI-REF-8821"
  },
  "recurring": null,
  "redeemDetails": null,
  "investmentDetails": null
}
```

---

## Field Mapping

| Was (flat) | Now | Sub-object |
|---|---|---|
| `id` | `id` | root |
| `type` | `type` | root |
| `date` | `date` | root |
| `description` | `description` | root |
| `amountPaise` | `amountPaise` | root |
| `fromAccountId` | `fromAccountId` | root |
| `toAccountId` | `toAccountId` | root |
| `notes` | `notes` | root |
| `sourceIndicator` | `sourceIndicator` | root |
| `importHash` | `importHash` | root |
| `groupTransactionId` | `groupTransactionId` | root |
| `categoryId` | `classification.categoryId` | Classification |
| `subCategoryId` | `classification.subCategoryId` | Classification |
| `familyMember` | `classification.familyMember` | Classification |
| `paymentMode` | `payment.mode` | Payment |
| `referenceNumber` | `payment.referenceNumber` | Payment |
| `fromRecurring` + `recurringId` | `recurring.recurringId` (presence = fromRecurring) | Recurring |
| `schemeScriptName` | `investmentDetails.schemeScriptName` | InvestmentDetails |
| `unitsNav` | `investmentDetails.unitsNav` | InvestmentDetails |
| `fdRef` | `investmentDetails.fd.ref` | FdDetails |
| `fdInterestRate` | `investmentDetails.fd.interestRate` | FdDetails |
| `fdMaturityDate` (String) | `investmentDetails.fd.maturityDate` (**LocalDate**) | FdDetails |
| `fdMaturityAmountPaise` | `investmentDetails.fd.maturityAmountPaise` | FdDetails |
| *(new)* | `investmentDetails.fd.interestPayable` | FdDetails |
| `principalPaise` | `redeemDetails.principalPaise` | RedeemDetails |
| `incomeType` | **DROPPED** | — |
| `source` | **DROPPED** | — |

---

## Step-by-Step Implementation

### Step 1 — Update `Transaction.java`
File: `src/main/java/com/sanchay/model/Transaction.java`

- Remove `IncomeType` enum entirely
- Remove fields: `incomeType`, `source`
- Remove getters/setters for both
- Remove flat fields: `categoryId`, `subCategoryId`, `familyMember`, `paymentMode`, `referenceNumber`, `fromRecurring`, `recurringId`, `schemeScriptName`, `unitsNav`, `fdRef`, `fdInterestRate`, `fdMaturityDate`, `fdMaturityAmountPaise`, `principalPaise`
- Remove their getters/setters
- Add 6 static inner classes:

```
static Classification  { String categoryId, subCategoryId, familyMember }
static Payment         { PaymentMode mode, String referenceNumber }
static Recurring       { String recurringId }
static InvestmentDetails { String schemeScriptName, Double unitsNav, FdDetails fd
    static FdDetails   { String ref, Double interestRate, LocalDate maturityDate,
                         Long maturityAmountPaise, InterestPayable interestPayable }
}
static RedeemDetails   { long principalPaise }
```

- Add new `InterestPayable` enum to `Transaction.java`:
  `AT_MATURITY, YEARLY, QUARTERLY, MONTHLY`
- Add sub-object fields to Transaction: `classification`, `payment`, `recurring`, `investmentDetails`, `redeemDetails`
- Add getters/setters for all sub-object fields
- Fix `fdMaturityDate` type: was `String`, now `LocalDate` inside `FdDetails` — the existing LocalDate TypeAdapter in `PersistenceService` handles serialization automatically

---

### Step 2 — Update `TransactionDialog.java`
File: `src/main/java/com/sanchay/ui/transactions/TransactionDialog.java`

#### 2a — Remove income source field
- Remove `incSrcFld` field declaration (~line 71)
- Remove from income panel layout — delete the `row(g, r++, "Source", incSrcFld)` line (~line 268)
- Remove `incSrcFld = tf(...)` initialization (~line 261)
- Remove `t.setSource(...)` from save path (~line 678)
- Remove `setText(incSrcFld, ...)` from prefill path (~line 961)

#### 2b — Add `invFdInterestPayableCb` ComboBox for FD interest payable
- Add field declaration alongside other FD field declarations (~line 90):
  `private ComboBox<Transaction.InterestPayable> invFdInterestPayableCb;`
- In `refreshInvestmentDynamicFields()` reset block (~line 1232), add:
  `invFdInterestPayableCb = null;`
- In `refreshInvestmentDynamicFields()` FIXED_DEPOSIT/DEBT_BONDS case (see Step 2c), create and add row:
  ```java
  invFdInterestPayableCb = new ComboBox<>();
  invFdInterestPayableCb.getItems().addAll(Transaction.InterestPayable.values());
  invFdInterestPayableCb.setPromptText("Select");
  dynRow(g, 4, "Interest Payable", invFdInterestPayableCb);
  ```

#### 2c — Fix DEBT_BONDS to show FD fields
In `refreshInvestmentDynamicFields()` (~line 1241), change:
```java
// BEFORE
case MUTUAL_FUNDS, EQUITY, DEBT_BONDS -> { /* scheme/units fields */ }
case FIXED_DEPOSIT -> { /* fd fields */ }

// AFTER
case MUTUAL_FUNDS, EQUITY -> { /* scheme/units fields */ }
case FIXED_DEPOSIT, DEBT_BONDS -> { /* fd fields + interestPayable */ }
```

#### 2d — Update save path (~line 745)
Change `case MUTUAL_FUNDS, EQUITY, DEBT_BONDS` to `case MUTUAL_FUNDS, EQUITY`.
Change `case FIXED_DEPOSIT` to `case FIXED_DEPOSIT, DEBT_BONDS`.

Update FIXED_DEPOSIT/DEBT_BONDS save block to use new sub-object setters:
```java
case FIXED_DEPOSIT, DEBT_BONDS -> {
    Transaction.FdDetails fd = new Transaction.FdDetails();  // via InvestmentDetails
    fd.setRef(nullIfBlank(...));
    fd.setInterestRate(...);
    fd.setMaturityDate(invFdMaturityPicker.getValue());      // LocalDate directly
    fd.setMaturityAmountPaise(...);
    fd.setInterestPayable(invFdInterestPayableCb.getValue());
    Transaction.InvestmentDetails inv = new Transaction.InvestmentDetails();
    inv.setFd(fd);
    t.setInvestmentDetails(inv);
    t.setNotes(userNotes);
}
```

Update MUTUAL_FUNDS/EQUITY save block to use new sub-object setters:
```java
case MUTUAL_FUNDS, EQUITY -> {
    Transaction.InvestmentDetails inv = new Transaction.InvestmentDetails();
    inv.setSchemeScriptName(nullIfBlank(invSchemeFld.getText()));
    if (...) inv.setUnitsNav(Double.parseDouble(...));
    t.setInvestmentDetails(inv);
    t.setNotes(userNotes);
}
```

Update all other save paths (EXPENSE, INCOME, TRANSFER, etc.) to use new sub-object setters for `classification`, `payment`, `recurring`.

#### 2e — Update prefill/edit path (~line 1026)
Change `case MUTUAL_FUNDS, EQUITY, DEBT_BONDS` to `case MUTUAL_FUNDS, EQUITY`.
Change `case FIXED_DEPOSIT` to `case FIXED_DEPOSIT, DEBT_BONDS`.

Update FIXED_DEPOSIT/DEBT_BONDS prefill block:
```java
case FIXED_DEPOSIT, DEBT_BONDS -> {
    Transaction.FdDetails fd = t.getInvestmentDetails() != null ? t.getInvestmentDetails().getFd() : null;
    if (fd != null) {
        setText(invFdRefFld, fd.getRef());
        if (fd.getInterestRate() != null) invFdRateFld.setText(fd.getInterestRate().toString());
        if (fd.getMaturityDate() != null) invFdMaturityPicker.setValue(fd.getMaturityDate()); // LocalDate directly
        if (fd.getMaturityAmountPaise() != null)
            invFdMaturityAmtFld.setText(String.format("%.2f", fd.getMaturityAmountPaise() / 100.0));
        if (fd.getInterestPayable() != null) invFdInterestPayableCb.setValue(fd.getInterestPayable());
    }
    sharedNotes.setText(t.getNotes() != null ? t.getNotes() : "");
}
```

---

### Step 3 — Update `DataStore.java`
File: `src/main/java/com/sanchay/service/DataStore.java`

Update ~14 call sites that access fields now in sub-objects:
- `t.getCategoryId()` → `t.getClassification() != null ? t.getClassification().getCategoryId() : null`
- `t.getSubCategoryId()` → via `t.getClassification()`
- `t.isFromRecurring()` → `t.getRecurring() != null`
- `t.getRecurringId()` → `t.getRecurring() != null ? t.getRecurring().getRecurringId() : null`
- `t.getPrincipalPaise()` → `t.getRedeemDetails() != null ? t.getRedeemDetails().getPrincipalPaise() : 0`
- `t.setSourceIndicator(...)` → stays at root, no change

---

### Step 4 — Update `AccountsScreen.java`
File: `src/main/java/com/sanchay/ui/accounts/AccountsScreen.java`

Update ~6 call sites:
- `t.getPrincipalPaise()` → via `t.getRedeemDetails()`
- `t.getCategoryId()` / `t.getSubCategoryId()` → via `t.getClassification()`
- `t.setSourceIndicator(...)` → stays at root, no change

---

### Step 5 — Update `ImportService.java`
File: `src/main/java/com/sanchay/service/ImportService.java`

Update ~5 call sites:
- `t.setRecurringId(...)` / `t.setFromRecurring(true)` → create `Recurring` object and set via `t.setRecurring(...)`
- `t.setCategoryId(...)` / `t.setSubCategoryId(...)` → create `Classification` object and set via `t.setClassification(...)`

---

### Step 6 — Update `CategoriesScreen.java`
File: `src/main/java/com/sanchay/ui/categories/CategoriesScreen.java`

Update ~4 call sites:
- `t.getCategoryId()` / `t.getSubCategoryId()` → via `t.getClassification()`

---

### Step 7 — Update `RecordRecurringDialog.java`
File: `src/main/java/com/sanchay/ui/recurring/RecordRecurringDialog.java`

Update ~4 call sites:
- `t.setFromRecurring(true)` + `t.setRecurringId(...)` → `t.setRecurring(new Transaction.Recurring(id))`
- `t.setCategoryId(...)` / `t.setSubCategoryId(...)` → via `t.setClassification(...)`

---

### Step 8 — Update `ReportsScreen.java`
File: `src/main/java/com/sanchay/ui/reports/ReportsScreen.java`

Update ~2 call sites:
- `t.getCategoryId()` / `t.getSubCategoryId()` → via `t.getClassification()`

---

### Step 9 — Compile and verify
- `bash build.sh compile` — must be clean
- Run app → Add Transaction → Investment → select FD account → verify 5 FD fields shown including Interest Payable dropdown
- Run app → Add Transaction → Investment → select Bonds account → verify same 5 FD fields shown (not Scheme/Units)
- Run app → Add Transaction → Income → verify Source field is gone
- Save an FD transaction, inspect `transactions.json` → verify nested structure with `investmentDetails.fd.*`
- Reopen the FD transaction for editing → verify all fields round-trip correctly

---

## Files Modified (summary)

| File | Nature of change |
|---|---|
| `model/Transaction.java` | Add 6 inner classes + InterestPayable enum; remove 15 flat fields + IncomeType enum |
| `ui/transactions/TransactionDialog.java` | ~45 sites: remove source field, add interestPayable ComboBox, fix DEBT_BONDS, update all save/prefill paths |
| `service/DataStore.java` | ~14 sites: mechanical sub-object dereferences |
| `ui/accounts/AccountsScreen.java` | ~6 sites |
| `service/ImportService.java` | ~5 sites |
| `ui/categories/CategoriesScreen.java` | ~4 sites |
| `ui/recurring/RecordRecurringDialog.java` | ~4 sites |
| `ui/reports/ReportsScreen.java` | ~2 sites |
